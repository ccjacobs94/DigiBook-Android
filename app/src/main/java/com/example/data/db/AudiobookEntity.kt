package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audiobooks")
data class AudiobookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val narrator: String,
    val description: String,
    val duration: Long, // total duration in milliseconds
    val coverUrl: String,
    val audioUrl: String,
    val localPath: String? = null,
    val isDownloaded: Boolean = false,
    val downloadProgress: Float = 0f,
    val currentPosition: Long = 0L, // current playback position in milliseconds
    val lastPlaybackTime: Long = 0L, // last timestamp when the user played this book
    val isCompleted: Boolean = false,
    val serverSyncStatus: Int = SYNC_STATUS_OK // 0 = OK, 1 = PENDING_SYNC
) {
    companion object {
        const val SYNC_STATUS_OK = 0
        const val SYNC_STATUS_PENDING = 1
    }
}
