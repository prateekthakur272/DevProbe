package dev.prateekthakur.devprobe.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalysisSessionDao {

    @Insert
    suspend fun insert(session: AnalysisSessionEntity): Long

    @Query("SELECT * FROM analysis_sessions ORDER BY createdAtEpochMillis DESC")
    fun observeAll(): Flow<List<AnalysisSessionEntity>>

    @Query("SELECT * FROM analysis_sessions ORDER BY createdAtEpochMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AnalysisSessionEntity>>

    @Query("SELECT * FROM analysis_sessions WHERE id = :id")
    suspend fun getById(id: Long): AnalysisSessionEntity?

    @Delete
    suspend fun delete(session: AnalysisSessionEntity)

    @Query("DELETE FROM analysis_sessions")
    suspend fun deleteAll()
}
