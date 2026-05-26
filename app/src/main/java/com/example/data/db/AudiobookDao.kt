package com.example.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AudiobookDao {
    @Query("SELECT * FROM audiobooks ORDER BY lastPlaybackTime DESC, title ASC")
    fun getAllAudiobooks(): Flow<List<AudiobookEntity>>

    @Query("SELECT * FROM audiobooks WHERE id = :id")
    fun getAudiobookById(id: String): Flow<AudiobookEntity?>

    @Query("SELECT * FROM audiobooks WHERE id = :id")
    suspend fun getAudiobookByIdSync(id: String): AudiobookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudiobooks(audiobooks: List<AudiobookEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudiobook(audiobook: AudiobookEntity)

    @Update
    suspend fun updateAudiobook(audiobook: AudiobookEntity)

    @Query("UPDATE audiobooks SET currentPosition = :position, lastPlaybackTime = :timestamp, serverSyncStatus = :syncStatus WHERE id = :id")
    suspend fun updatePlaybackProgress(id: String, position: Long, timestamp: Long, syncStatus: Int)

    @Query("UPDATE audiobooks SET localPath = :localPath, isDownloaded = :isDownloaded, downloadProgress = :progress WHERE id = :id")
    suspend fun updateDownloadStatus(id: String, localPath: String?, isDownloaded: Boolean, progress: Float)

    @Query("SELECT * FROM audiobooks WHERE serverSyncStatus = 1")
    suspend fun getPendingSyncAudiobooks(): List<AudiobookEntity>

    @Query("DELETE FROM audiobooks WHERE id = :id")
    suspend fun deleteAudiobookById(id: String)

    @Query("DELETE FROM audiobooks")
    suspend fun clearAll()
}
