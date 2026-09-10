package dev.prateekthakur.devprobe.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single stored analysis session (§20). The heavy structured result (manifest,
 * permissions, findings, parsed log/crash data) is serialized to JSON in [resultJson]
 * rather than normalized into many tables — this is history/detail data, not
 * something queried relationally, so JSON keeps the schema simple (§22).
 */
@Entity(tableName = "analysis_sessions")
data class AnalysisSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val type: String,
    val createdAtEpochMillis: Long,
    val packageName: String?,
    val findingsCount: Int,
    val securityCount: Int,
    val warningCount: Int,
    val infoCount: Int,
    val resultJson: String,
    val sourceFilePath: String? = null,
    val source: String = "MANUAL",
)
