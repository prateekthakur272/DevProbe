package dev.prateekthakur.devprobe.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [AnalysisSessionEntity::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun analysisSessionDao(): AnalysisSessionDao
}
