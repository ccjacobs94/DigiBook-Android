package com.example.data.player

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.media.MediaMetadata
import com.example.MainActivity

class AudioPlaybackService : Service() {

    private var mediaSession: MediaSession? = null

    companion object {
        const val CHANNEL_ID = "digibook_playback_channel"
        const val NOTIFICATION_ID = 4829
        
        const val ACTION_START = "com.example.ACTION_START"
        const val ACTION_STOP = "com.example.ACTION_STOP"
        const val ACTION_PLAY_PAUSE = "com.example.ACTION_PLAY_PAUSE"
        const val ACTION_SKIP_FORWARD = "com.example.ACTION_SKIP_FORWARD"
        const val ACTION_SKIP_BACKWARD = "com.example.ACTION_SKIP_BACKWARD"
        
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_AUTHOR = "extra_author"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_POSITION = "extra_position"
        const val EXTRA_DURATION = "extra_duration"
        const val EXTRA_SPEED = "extra_speed"

        // Global static access back to our AudioPlayerManager initialized from Application/Activity
        var playerManagerInstance: AudioPlayerManager? = null

        fun startService(
            context: Context,
            title: String,
            author: String,
            isPlaying: Boolean,
            position: Long = 0L,
            duration: Long = 0L,
            speed: Float = 1.0f
        ) {
            val intent = Intent(context, AudioPlaybackService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_AUTHOR, author)
                putExtra(EXTRA_IS_PLAYING, isPlaying)
                putExtra(EXTRA_POSITION, position)
                putExtra(EXTRA_DURATION, duration)
                putExtra(EXTRA_SPEED, speed)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, AudioPlaybackService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupMediaSession()
    }

    private fun setupMediaSession() {
        mediaSession = MediaSession(this, "DigiBookMediaSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    playerManagerInstance?.togglePlayPause()
                }

                override fun onPause() {
                    playerManagerInstance?.togglePlayPause()
                }

                override fun onSkipToNext() {
                    playerManagerInstance?.skipForward()
                }

                override fun onSkipToPrevious() {
                    playerManagerInstance?.skipBackward()
                }

                override fun onSeekTo(pos: Long) {
                    playerManagerInstance?.seekTo(pos)
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        val action = intent.action
        if (action == ACTION_STOP) {
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        if (action == ACTION_PLAY_PAUSE) {
            playerManagerInstance?.togglePlayPause()
            return START_STICKY
        }

        if (action == ACTION_SKIP_FORWARD) {
            playerManagerInstance?.skipForward()
            return START_STICKY
        }

        if (action == ACTION_SKIP_BACKWARD) {
            playerManagerInstance?.skipBackward()
            return START_STICKY
        }

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Audiobook Client"
        val author = intent.getStringExtra(EXTRA_AUTHOR) ?: "DigiBook Server"
        val isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)
        val position = intent.getLongExtra(EXTRA_POSITION, 0L)
        val duration = intent.getLongExtra(EXTRA_DURATION, 0L)
        val speed = intent.getFloatExtra(EXTRA_SPEED, 1.0f)

        updateMediaSessionState(isPlaying, position, duration, speed, title, author)
        showNotification(title, author, isPlaying)
        return START_STICKY
    }

    private fun updateMediaSessionState(
        isPlaying: Boolean,
        position: Long,
        duration: Long,
        speed: Float,
        title: String,
        author: String
    ) {
        val stateBuilder = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_SEEK_TO
            )
        
        val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        stateBuilder.setState(state, position, speed)
        mediaSession?.setPlaybackState(stateBuilder.build())

        val metadataBuilder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, author)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, duration)
        mediaSession?.setMetadata(metadataBuilder.build())
    }

    private fun showNotification(title: String, author: String, isPlaying: Boolean) {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Notification actions
        val playPauseIntent = Intent(this, AudioPlaybackService::class.java).apply {
            action = ACTION_PLAY_PAUSE
        }
        val playPausePendingIntent = PendingIntent.getService(
            this, 1, playPauseIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val skipForwardIntent = Intent(this, AudioPlaybackService::class.java).apply {
            action = ACTION_SKIP_FORWARD
        }
        val skipForwardPendingIntent = PendingIntent.getService(
            this, 2, skipForwardIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val skipBackwardIntent = Intent(this, AudioPlaybackService::class.java).apply {
            action = ACTION_SKIP_BACKWARD
        }
        val skipBackwardPendingIntent = PendingIntent.getService(
            this, 3, skipBackwardIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseIcon = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        val playPauseLabel = if (isPlaying) "Pause" else "Play"

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(author)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .setStyle(
                    Notification.MediaStyle()
                        .setMediaSession(mediaSession?.sessionToken)
                        .setShowActionsInCompactView(0, 1, 2)
                )
                .addAction(
                    Notification.Action.Builder(
                        android.R.drawable.ic_media_rew,
                        "Skip Backward 10s",
                        skipBackwardPendingIntent
                    ).build()
                )
                .addAction(
                    Notification.Action.Builder(
                        playPauseIcon,
                        playPauseLabel,
                        playPausePendingIntent
                    ).build()
                )
                .addAction(
                    Notification.Action.Builder(
                        android.R.drawable.ic_media_ff,
                        "Skip Forward 30s",
                        skipForwardPendingIntent
                    ).build()
                )
                .setOngoing(isPlaying)
                .build()
        } else {
            // standard builder for older APIs
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(author)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .addAction(android.R.drawable.ic_media_rew, "Back 10s", skipBackwardPendingIntent)
                .addAction(playPauseIcon, playPauseLabel, playPausePendingIntent)
                .addAction(android.R.drawable.ic_media_ff, "Forward 30s", skipForwardPendingIntent)
                .setOngoing(isPlaying)
                .build()
        }

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "DigiBook Audio Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live audiobook playback status and controls."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            isActive = false
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
