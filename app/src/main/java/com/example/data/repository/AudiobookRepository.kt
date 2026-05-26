package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.api.AuthRequest
import com.example.data.api.DigiBookApiService
import com.example.data.api.DigiBookClient
import com.example.data.api.SyncProgressRequest
import com.example.data.db.AudiobookDao
import com.example.data.db.AudiobookEntity
import com.example.data.preferences.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class AudiobookRepository(
    private val context: Context,
    private val audiobookDao: AudiobookDao,
    val preferenceManager: PreferenceManager
) {
    private val tag = "AudiobookRepository"

    val allAudiobooks: Flow<List<AudiobookEntity>> = audiobookDao.getAllAudiobooks()

    fun getAudiobookById(id: String): Flow<AudiobookEntity?> = audiobookDao.getAudiobookById(id)

    private fun getApiService(): DigiBookApiService? {
        val url = preferenceManager.serverUrl
        return if (url.isNotEmpty()) {
            try {
                DigiBookClient.createService(url) { preferenceManager.cfAccessToken }
            } catch (e: Exception) {
                Log.e(tag, "Failed to create API service", e)
                null
            }
        } else {
            null
        }
    }

    /**
     * Send Cloudflare Access OTP Code.
     * Generates a POST request to <serverUrl>/cdn-cgi/access/emailsend with the email.
     */
    suspend fun sendCfAccessOtp(serverUrl: String, email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
            val url = URL("${cleanUrl}cdn-cgi/access/emailsend")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            
            val postData = "email=${java.net.URLEncoder.encode(email, "UTF-8")}"
            connection.outputStream.use { os ->
                os.write(postData.toByteArray(Charsets.UTF_8))
            }
            
            val responseCode = connection.responseCode
            if (responseCode in 200..399) {
                Result.success(Unit)
            } else {
                val errorMsg = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                Log.e(tag, "sendCfAccessOtp failed: $errorMsg")
                Result.failure(Exception("Cloudflare responded with error: $responseCode"))
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to send Cloudflare Access OTP", e)
            Result.failure(e)
        }
    }

    /**
     * Verify Cloudflare Access OTP Code and extract the CF_Authorization cookie.
     */
    suspend fun verifyCfAccessOtp(serverUrl: String, email: String, code: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
            val url = URL("${cleanUrl}cdn-cgi/access/verify")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.instanceFollowRedirects = false // Prevent automatic redirect to capture cookie
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            
            val postData = "email=${java.net.URLEncoder.encode(email, "UTF-8")}&pin=${java.net.URLEncoder.encode(code, "UTF-8")}"
            connection.outputStream.use { os ->
                os.write(postData.toByteArray(Charsets.UTF_8))
            }
            
            val responseCode = connection.responseCode
            if (responseCode in 200..399) {
                var cfToken: String? = null
                val headerFields = connection.headerFields
                val cookies = headerFields["Set-Cookie"] ?: headerFields["set-cookie"]
                
                if (cookies != null) {
                    for (cookie in cookies) {
                        if (cookie.contains("CF_Authorization=")) {
                            cfToken = cookie.substringAfter("CF_Authorization=").substringBefore(";")
                            break
                        }
                    }
                }
                
                if (cfToken != null) {
                    preferenceManager.cfAccessToken = cfToken
                    preferenceManager.cfAccessEmail = email
                    Result.success(cfToken)
                } else {
                    Log.e(tag, "CF_Authorization cookie not found in headers")
                    Result.failure(Exception("Verified but failed to extract credentials. Check if your code is correct."))
                }
            } else {
                val errorMsg = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                Log.e(tag, "verifyCfAccessOtp failed: $errorMsg")
                Result.failure(Exception("Verification failed: code invalid or expired (Status: $responseCode)"))
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to verify Cloudflare Access OTP", e)
            Result.failure(e)
        }
    }

    /**
     * Connect and authenticate. Fetch initial book list and cache to local DB.
     * Keeps any local play stats if offline changes exist.
     */
    suspend fun connect(serverUrl: String, token: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val api = DigiBookClient.createService(serverUrl) { preferenceManager.cfAccessToken }
            val authResponse = api.validateConnection(
                token = "Bearer $token",
                request = AuthRequest(token = token)
            )

            if (authResponse.success) {
                // Save settings
                preferenceManager.serverUrl = serverUrl
                preferenceManager.sessionToken = token
                
                // Fetch and cache books
                refreshAudiobooks()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(tag, "Connection/Auth failed: ${e.message}", e)
            false
        }
    }

    /**
     * Refresh audiobook library from server and save in local Room DB.
     */
    suspend fun refreshAudiobooks(): Boolean = withContext(Dispatchers.IO) {
        val api = getApiService() ?: return@withContext false
        val token = preferenceManager.sessionToken
        try {
            val books = api.getAudiobooks("Bearer $token")
            
            // Map to Room entities, merging with any existing local-only states (like downloads and local playback positions)
            val entities = books.map { dto ->
                // Check if book already cached locally, to preserve progress and local path
                val localBook = audiobookDao.getAudiobookByIdSync(dto.id)
                AudiobookEntity(
                    id = dto.id,
                    title = dto.title,
                    author = dto.author,
                    narrator = dto.narrator ?: "Unknown Narrator",
                    description = dto.description ?: "No description provided.",
                    duration = dto.duration,
                    coverUrl = dto.coverUrl,
                    audioUrl = dto.audioUrl,
                    localPath = localBook?.localPath,
                    isDownloaded = localBook?.isDownloaded ?: false,
                    downloadProgress = localBook?.downloadProgress ?: 0f,
                    // Use server position if server has newer or default, else merge local
                    currentPosition = localBook?.currentPosition ?: 0L,
                    lastPlaybackTime = localBook?.lastPlaybackTime ?: 0L,
                    isCompleted = localBook?.isCompleted ?: false,
                    serverSyncStatus = localBook?.serverSyncStatus ?: AudiobookEntity.SYNC_STATUS_OK
                )
            }
            audiobookDao.insertAudiobooks(entities)
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to refresh audiobooks", e)
            false
        }
    }

    /**
     * Updates playback progress locally, and attempts to sync to the server.
     * If offline or request fails, marks play position as PENDING_SYNC.
     */
    suspend fun updatePlaybackProgress(
        id: String,
        position: Long,
        duration: Long,
        isCompleted: Boolean
    ): Unit = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        val api = getApiService()
        val token = preferenceManager.sessionToken

        if (api != null && token.isNotEmpty()) {
            try {
                // Update local DB instantly first for UI responsiveness
                audiobookDao.updatePlaybackProgress(id, position, timestamp, AudiobookEntity.SYNC_STATUS_OK)
                
                // Send sync pass to API
                api.syncProgress(
                    token = "Bearer $token",
                    request = SyncProgressRequest(
                        bookId = id,
                        currentPosition = position,
                        lastPlaybackTime = timestamp,
                        isCompleted = isCompleted
                    )
                )
            } catch (e: Exception) {
                Log.e(tag, "Network sync failed, saving progress offline", e)
                // Failed to sync online: tag progress as pending sync for offline persistence
                audiobookDao.updatePlaybackProgress(id, position, timestamp, AudiobookEntity.SYNC_STATUS_PENDING)
            }
        } else {
            // No api found, fallback to offline
            audiobookDao.updatePlaybackProgress(id, position, timestamp, AudiobookEntity.SYNC_STATUS_PENDING)
        }
    }

    /**
     * Synchronizes any locally modified playback progress records that were modified while offline.
     */
    suspend fun syncPendingProgress(): Boolean = withContext(Dispatchers.IO) {
        val api = getApiService() ?: return@withContext false
        val token = preferenceManager.sessionToken
        if (token.isEmpty()) return@withContext false

        try {
            val pending = audiobookDao.getPendingSyncAudiobooks()
            if (pending.isEmpty()) return@withContext true

            var allSuccessful = true
            for (book in pending) {
                try {
                    api.syncProgress(
                        token = "Bearer $token",
                        request = SyncProgressRequest(
                            bookId = book.id,
                            currentPosition = book.currentPosition,
                            lastPlaybackTime = book.lastPlaybackTime,
                            isCompleted = book.isCompleted
                        )
                    )
                    // Mark as successfully synced!
                    audiobookDao.updatePlaybackProgress(
                        book.id,
                        book.currentPosition,
                        book.lastPlaybackTime,
                        AudiobookEntity.SYNC_STATUS_OK
                    )
                } catch (e: Exception) {
                    Log.e(tag, "Continuing sync, failed to update progress for book ${book.id}", e)
                    allSuccessful = false
                }
            }
            allSuccessful
        } catch (e: Exception) {
            Log.e(tag, "Bulk sync pending progress failed", e)
            false
        }
    }

    /**
     * Downloads audiobook audio file to internal storage for offline playback.
     */
    suspend fun downloadAudiobook(id: String) = withContext(Dispatchers.IO) {
        val book = audiobookDao.getAudiobookByIdSync(id) ?: return@withContext
        if (book.isDownloaded) return@withContext

        try {
            // Update UI to download starting state
            audiobookDao.updateDownloadStatus(id, null, false, 0.05f)

            val url = URL(book.audioUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            // Inject Cloudflare Access headers if present
            val cfToken = preferenceManager.cfAccessToken
            if (cfToken.isNotEmpty()) {
                connection.setRequestProperty("Cf-Access-Jwt-Assertion", cfToken)
                connection.setRequestProperty("Cookie", "CF_Authorization=$cfToken")
            }
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(tag, "Server returned http code ${connection.responseCode} on download")
                audiobookDao.updateDownloadStatus(id, null, false, 0f)
                return@withContext
            }

            val fileLength = connection.contentLength
            val downloadsDir = File(context.filesDir, "downloads")
            if (!downloadsDir.exists()) downloadsDir.mkdirs()

            val localFile = File(downloadsDir, "$id.mp3")
            
            val input = connection.inputStream
            val output = FileOutputStream(localFile)

            val data = ByteArray(4096)
            var total: Long = 0
            var count: Int
            var lastUpdate = 0L

            while (input.read(data).also { count = it } != -1) {
                total += count
                output.write(data, 0, count)

                // Throttle local database updates for performance
                val currentTime = System.currentTimeMillis()
                if (fileLength > 0 && currentTime - lastUpdate > 300) {
                    val progress = total.toFloat() / fileLength.toFloat()
                    audiobookDao.updateDownloadStatus(id, null, false, progress)
                    lastUpdate = currentTime
                }
            }

            output.flush()
            output.close()
            input.close()

            // Download completed! Save the path and update database
            audiobookDao.updateDownloadStatus(id, localFile.absolutePath, true, 1.0f)
            Log.i(tag, "Audiobook $id downloaded successfully to: ${localFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(tag, "Failed to download audiobook $id", e)
            // Reset download state
            audiobookDao.updateDownloadStatus(id, null, false, 0f)
        }
    }

    /**
     * Delete downloaded audiobook file.
     */
    suspend fun removeDownloadedFile(id: String) = withContext(Dispatchers.IO) {
        val book = audiobookDao.getAudiobookByIdSync(id) ?: return@withContext
        book.localPath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        }
        audiobookDao.updateDownloadStatus(id, null, false, 0f)
    }

    /**
     * Clean everything (logout).
     */
    suspend fun logout() {
        preferenceManager.clear()
        audiobookDao.clearAll()
    }
}
