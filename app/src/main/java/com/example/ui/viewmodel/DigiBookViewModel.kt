package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.db.AudiobookEntity
import com.example.data.player.AudioPlaybackService
import com.example.data.player.AudioPlayerManager
import com.example.data.preferences.PreferenceManager
import com.example.data.repository.AudiobookRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class Screen {
    object Connect : Screen()
    object Dashboard : Screen()
    data class BookDetail(val bookId: String) : Screen()
    data class Player(val bookId: String) : Screen()
}

class DigiBookViewModel(application: Application) : AndroidViewModel(application) {

    private val preferenceManager = PreferenceManager(application)
    private val database = AppDatabase.getDatabase(application)
    private val audiobookDao = database.audiobookDao()
    
    val repository = AudiobookRepository(application, audiobookDao, preferenceManager)
    val playerManager = AudioPlayerManager(application, repository)

    init {
        // Tie the player service static manager to this player manager
        AudioPlaybackService.playerManagerInstance = playerManager
    }

    // ------------------ Screen navigation state ------------------
    private val _currentScreen = MutableStateFlow<Screen>(
        if (preferenceManager.isConfigured) Screen.Dashboard else Screen.Connect
    )
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // ------------------ Connect screen input fields ------------------
    val serverUrlInput = MutableStateFlow(preferenceManager.serverUrl.ifEmpty { "https://digibook.example.com" })
    val sessionTokenInput = MutableStateFlow(preferenceManager.sessionToken)
    
    // Cloudflare Zero Trust States
    val cfEmailInput = MutableStateFlow(preferenceManager.cfAccessEmail)
    val cfCodeInput = MutableStateFlow("")
    
    private val _isCfOtpSent = MutableStateFlow(preferenceManager.cfAccessToken.isNotEmpty())
    val isCfOtpSent: StateFlow<Boolean> = _isCfOtpSent.asStateFlow()
    
    private val _isCfConnecting = MutableStateFlow(false)
    val isCfConnecting: StateFlow<Boolean> = _isCfConnecting.asStateFlow()
    
    private val _cfError = MutableStateFlow<String?>(null)
    val cfError: StateFlow<String?> = _cfError.asStateFlow()
    
    private val _cfSuccessMessage = MutableStateFlow<String?>(if (preferenceManager.cfAccessToken.isNotEmpty()) "Cloudflare Zero Trust Active" else null)
    val cfSuccessMessage: StateFlow<String?> = _cfSuccessMessage.asStateFlow()
    
    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    // ------------------ Books library state ------------------
    val audiobooks: StateFlow<List<AudiobookEntity>> = repository.allAudiobooks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // ------------------ Active book / Player state binds ------------------
    val currentPlayingBook: StateFlow<AudiobookEntity?> = playerManager.currentBook
    val isPlaying: StateFlow<Boolean> = playerManager.isPlaying
    val currentPosition: StateFlow<Long> = playerManager.currentPosition
    val duration: StateFlow<Long> = playerManager.duration
    val playbackSpeed: StateFlow<Float> = playerManager.playbackSpeed
    val isPlayerLoading: StateFlow<Boolean> = playerManager.isLoading

    // ------------------ Sleep Timer ------------------
    private val _sleepTimerMinutes = MutableStateFlow<Int?>(null)
    val sleepTimerMinutes: StateFlow<Int?> = _sleepTimerMinutes.asStateFlow()
    private var sleepTimerJob: Job? = null

    init {
        // Auto-refresh books list and run background sync for offline play history on start
        if (preferenceManager.isConfigured) {
            triggerBackgroundSync()
        }
    }

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    fun handleBackPress() {
        when (val screen = _currentScreen.value) {
            is Screen.BookDetail -> _currentScreen.value = Screen.Dashboard
            is Screen.Player -> _currentScreen.value = Screen.BookDetail(screen.bookId)
            else -> {} // Connect & Dashboard do not pop back
        }
    }

    /**
     * Send Cloudflare Access OTP
     */
    fun sendCfOtp() {
        val server = serverUrlInput.value.trim()
        val email = cfEmailInput.value.trim()
        
        if (server.isEmpty() || email.isEmpty()) {
            _cfError.value = "Server URL and Email cannot be empty."
            return
        }
        
        viewModelScope.launch {
            _isCfConnecting.value = true
            _cfError.value = null
            _cfSuccessMessage.value = null
            
            val result = repository.sendCfAccessOtp(server, email)
            _isCfConnecting.value = false
            
            if (result.isSuccess) {
                _isCfOtpSent.value = true
                _cfSuccessMessage.value = "OTP Code sent to $email. Please check your inbox / spam folder."
            } else {
                _cfError.value = result.exceptionOrNull()?.message ?: "Failed to request OTP from Cloudflare."
            }
        }
    }

    /**
     * Verify Cloudflare Access OTP
     */
    fun verifyCfOtp() {
        val server = serverUrlInput.value.trim()
        val email = cfEmailInput.value.trim()
        val code = cfCodeInput.value.trim()
        
        if (server.isEmpty() || email.isEmpty() || code.isEmpty()) {
            _cfError.value = "Server URL, Email, and OTP code cannot be empty."
            return
        }
        
        viewModelScope.launch {
            _isCfConnecting.value = true
            _cfError.value = null
            _cfSuccessMessage.value = null
            
            val result = repository.verifyCfAccessOtp(server, email, code)
            _isCfConnecting.value = false
            
            if (result.isSuccess) {
                _cfSuccessMessage.value = "Successfully authenticated with Cloudflare Access!"
            } else {
                _cfError.value = result.exceptionOrNull()?.message ?: "Verification failed."
            }
        }
    }

    /**
     * Authenticate and validate URL config. Saves properties.
     */
    fun validateAndConnect() {
        val server = serverUrlInput.value.trim()
        val token = sessionTokenInput.value.trim()

        if (server.isEmpty() || token.isEmpty()) {
            _connectionError.value = "Server URL and session token cannot be empty."
            return
        }

        viewModelScope.launch {
            _isConnecting.value = true
            _connectionError.value = null

            val success = repository.connect(server, token)
            _isConnecting.value = false

            if (success) {
                _currentScreen.value = Screen.Dashboard
            } else {
                _connectionError.value = "Failed to connect to server. Check URL, credentials, or network."
            }
        }
    }

    /**
     * Refresh audiobook library and sync pending progress
     */
    fun refreshLibrary() {
        viewModelScope.launch {
            _isRefreshing.value = true
            
            // 1. Sync any offline progress changes to the server
            repository.syncPendingProgress()
            
            // 2. Fetch latest catalog
            repository.refreshAudiobooks()
            
            _isRefreshing.value = false
        }
    }

    private fun triggerBackgroundSync() {
        viewModelScope.launch {
            repository.syncPendingProgress()
            repository.refreshAudiobooks()
        }
    }

    // ------------------ Player interactions ------------------

    fun playAudiobook(book: AudiobookEntity) {
        playerManager.playBook(book)
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    fun seekTo(position: Long) {
        playerManager.seekTo(position)
    }

    fun skipForward() {
        playerManager.skipForward()
    }

    fun skipBackward() {
        playerManager.skipBackward()
    }

    fun setPlaybackSpeed(speed: Float) {
        playerManager.setSpeed(speed)
    }

    fun toggleSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (_sleepTimerMinutes.value == minutes) {
            _sleepTimerMinutes.value = null
        } else {
            _sleepTimerMinutes.value = minutes
            sleepTimerJob = viewModelScope.launch {
                var secondsPending = minutes * 60
                while (secondsPending > 0) {
                    delay(1000)
                    secondsPending--
                    _sleepTimerMinutes.value = (secondsPending + 59) / 60
                }
                // Time's up! Stop audio playback safely
                if (isPlaying.value) {
                    playerManager.togglePlayPause()
                }
                _sleepTimerMinutes.value = null
            }
        }
    }

    // ------------------ Download cache management ------------------

    fun downloadAsset(id: String) {
        viewModelScope.launch {
            repository.downloadAudiobook(id)
        }
    }

    fun removeDownloadAsset(id: String) {
        viewModelScope.launch {
            repository.removeDownloadedFile(id)
        }
    }

    fun logout() {
        viewModelScope.launch {
            playerManager.stop()
            repository.logout()
            _currentScreen.value = Screen.Connect
        }
    }

    override fun onCleared() {
        super.onCleared()
        sleepTimerJob?.cancel()
    }
}
