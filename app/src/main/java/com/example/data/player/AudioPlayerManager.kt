package com.example.data.player

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.data.db.AudiobookEntity
import com.example.data.repository.AudiobookRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class AudioPlayerManager(
    private val context: Context,
    private val repository: AudiobookRepository
) {
    private val tag = "AudioPlayerManager"
    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null

    // ------------------- Player State flows -------------------

    private val _currentBook = MutableStateFlow<AudiobookEntity?>(null)
    val currentBook: StateFlow<AudiobookEntity?> = _currentBook.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        // Collect real-time progress updates and write periodic database sync states
        startProgressCollector()
    }

    /**
     * Start playing an audiobook, loading from local path if downloaded, or streaming from remote audioUrl.
     */
    fun playBook(book: AudiobookEntity) {
        val prevBook = _currentBook.value
        
        // Save previous progress before transferring to next book
        if (prevBook != null && prevBook.id != book.id) {
            saveCurrentProgressToDb()
        }

        _currentBook.value = book
        _playbackSpeed.value = 1.0f
        _isLoading.value = true

        try {
            mediaPlayer?.release()
            
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )

                // Decider: Stream vs Local
                val audioSource = if (book.isDownloaded && book.localPath != null && File(book.localPath).exists()) {
                    Log.i(tag, "Source: LOCAL downloaded file at ${book.localPath}")
                    Uri.fromFile(File(book.localPath))
                } else {
                    Log.i(tag, "Source: STREAMING from remote URL: ${book.audioUrl}")
                    Uri.parse(book.audioUrl)
                }

                setDataSource(context, audioSource)
                
                setOnPreparedListener { mp ->
                    _isLoading.value = false
                    _duration.value = mp.duration.toLong()
                    
                    // Seek to previous saved progress if available
                    val savedPos = book.currentPosition
                    if (savedPos > 0 && savedPos < mp.duration) {
                        mp.seekTo(savedPos.toInt())
                        _currentPosition.value = savedPos
                    } else {
                        _currentPosition.value = 0L
                    }

                    // Start playing
                    mp.start()
                    _isPlaying.value = true
                    
                    // Apply playback speed
                    setSpeed(_playbackSpeed.value)
                    
                    updatePlaybackService()
                }

                setOnCompletionListener {
                    _isPlaying.value = false
                    _currentPosition.value = _duration.value
                    saveCurrentProgressToDb(isCompleted = true)
                    stopPlaybackService()
                }

                setOnErrorListener { _, what, extra ->
                    Log.e(tag, "MediaPlayer Error: what=$what, extra=$extra")
                    _isLoading.value = false
                    _isPlaying.value = false
                    stopPlaybackService()
                    false
                }

                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to prep MediaPlayer", e)
            _isLoading.value = false
        }
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: return
        val book = _currentBook.value ?: return

        if (player.isPlaying) {
            player.pause()
            _isPlaying.value = false
            saveCurrentProgressToDb()
            updatePlaybackService()
        } else {
            player.start()
            _isPlaying.value = true
            setSpeed(_playbackSpeed.value) // setSpeed calls updatePlaybackService internally
        }
    }

    fun seekTo(position: Long) {
        val player = mediaPlayer ?: return
        player.seekTo(position.toInt())
        _currentPosition.value = position
        saveCurrentProgressToDb()
        updatePlaybackService()
    }

    fun skipForward() {
        val player = mediaPlayer ?: return
        val target = (player.currentPosition + 30000).coerceAtMost(player.duration)
        seekTo(target.toLong())
    }

    fun skipBackward() {
        val player = mediaPlayer ?: return
        val target = (player.currentPosition - 10000).coerceAtLeast(0)
        seekTo(target.toLong())
    }

    fun setSpeed(speed: Float) {
        _playbackSpeed.value = speed
        val player = mediaPlayer ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                if (player.isPlaying) {
                    player.playbackParams = player.playbackParams.setSpeed(speed)
                } else {
                    player.playbackParams = PlaybackParams().setSpeed(speed)
                    player.pause() // PlaybackParams set holds playing status by default; restore pause state
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to apply speed params", e)
            }
        }
        updatePlaybackService()
    }

    fun stop() {
        saveCurrentProgressToDb()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        _isPlaying.value = false
        _currentBook.value = null
        stopPlaybackService()
    }

    // ------------------- Helpers & Background Syncs -------------------

    private fun startProgressCollector() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val player = mediaPlayer
                if (player != null && _isPlaying.value && player.isPlaying) {
                    _currentPosition.value = player.currentPosition.toLong()
                    
                    // Periodically (every 10 seconds) sync position to local Room & Server database silently
                    if (System.currentTimeMillis() % 10000 < 1000) {
                        saveCurrentProgressToDb()
                    }
                    
                    // Periodic update to keep notifications/lockscreen up to date
                    updatePlaybackService()
                }
                delay(1000)
            }
        }
    }

    private fun saveCurrentProgressToDb(isCompleted: Boolean = false) {
        val book = _currentBook.value ?: return
        val position = _currentPosition.value
        val durationVal = _duration.value
        
        scope.launch {
            Log.d(tag, "Saving progress to DB: ${book.title} (Pos: $position / Duration: $durationVal)")
            repository.updatePlaybackProgress(
                id = book.id,
                position = position,
                duration = durationVal,
                isCompleted = isCompleted
            )
        }
    }

    private fun updatePlaybackService() {
        val book = _currentBook.value ?: return
        AudioPlaybackService.startService(
            context = context,
            title = book.title,
            author = book.author,
            isPlaying = _isPlaying.value,
            position = _currentPosition.value,
            duration = _duration.value,
            speed = _playbackSpeed.value
        )
    }

    private fun stopPlaybackService() {
        AudioPlaybackService.stopService(context)
    }
}
