package dev.prateekthakur.devprobe.data.repository

import dev.prateekthakur.devprobe.data.local.db.AnalysisSessionDao
import dev.prateekthakur.devprobe.data.local.db.AnalysisSessionEntity
import dev.prateekthakur.devprobe.domain.model.ApkAnalysisResult
import dev.prateekthakur.devprobe.domain.model.LogAnalysisResult
import dev.prateekthakur.devprobe.domain.model.Severity
import dev.prateekthakur.devprobe.domain.model.AnalysisSession
import dev.prateekthakur.devprobe.domain.model.SessionSource
import dev.prateekthakur.devprobe.domain.model.SessionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Persists and reconstructs analysis sessions (§20, §22). */
class AnalysisRepository(private val dao: AnalysisSessionDao) {

    private val json = Json { ignoreUnknownKeys = true }

    fun observeRecent(limit: Int = 20): Flow<List<AnalysisSession>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    fun observeAll(): Flow<List<AnalysisSession>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun saveApkAnalysis(
        title: String,
        result: ApkAnalysisResult,
        createdAt: Long,
        sourceFilePath: String?,
        source: SessionSource = SessionSource.MANUAL,
    ): Long {
        val counts = severityCounts(result.securityFindings.map { it.severity })
        val entity = AnalysisSessionEntity(
            title = title,
            type = SessionType.APK_ANALYSIS.name,
            createdAtEpochMillis = createdAt,
            packageName = result.metadata.packageName,
            findingsCount = result.securityFindings.size,
            securityCount = counts.first,
            warningCount = counts.second,
            infoCount = counts.third,
            resultJson = json.encodeToString(result),
            sourceFilePath = sourceFilePath,
            source = source.name,
        )
        return dao.insert(entity)
    }

    suspend fun saveLogAnalysis(
        title: String,
        result: LogAnalysisResult,
        createdAt: Long,
        packageName: String?,
        source: SessionSource = SessionSource.MANUAL,
    ): Long {
        val severities = result.crashReports.map { it.severity }
        val counts = severityCounts(severities)
        val entity = AnalysisSessionEntity(
            title = title,
            type = SessionType.LOG_ANALYSIS.name,
            createdAtEpochMillis = createdAt,
            packageName = packageName,
            findingsCount = result.crashReports.size + result.detectedErrors.size,
            securityCount = counts.first,
            warningCount = counts.second,
            infoCount = counts.third,
            resultJson = json.encodeToString(result),
            source = source.name,
        )
        return dao.insert(entity)
    }

    suspend fun getById(id: Long): AnalysisSession? = dao.getById(id)?.toDomain()

    suspend fun clearAll() = dao.deleteAll()

    suspend fun delete(session: AnalysisSession) {
        dao.getById(session.id)?.let { dao.delete(it) }
    }

    /** Returns (high+critical, medium, low+info) counts, matching §20's Security/Warnings/Info buckets. */
    private fun severityCounts(severities: List<Severity>): Triple<Int, Int, Int> {
        val security = severities.count { it == Severity.HIGH || it == Severity.CRITICAL }
        val warnings = severities.count { it == Severity.MEDIUM }
        val info = severities.count { it == Severity.LOW || it == Severity.INFO }
        return Triple(security, warnings, info)
    }

    private fun AnalysisSessionEntity.toDomain(): AnalysisSession {
        val type = SessionType.valueOf(type)
        val apkResult = if (type == SessionType.APK_ANALYSIS) {
            runCatching { json.decodeFromString<ApkAnalysisResult>(resultJson) }.getOrNull()
        } else null
        val logResult = if (type == SessionType.LOG_ANALYSIS) {
            runCatching { json.decodeFromString<LogAnalysisResult>(resultJson) }.getOrNull()
        } else null
        return AnalysisSession(
            id = id,
            title = title,
            type = type,
            createdAtEpochMillis = createdAtEpochMillis,
            packageName = packageName,
            findingsCount = findingsCount,
            securityCount = securityCount,
            warningCount = warningCount,
            infoCount = infoCount,
            source = runCatching { SessionSource.valueOf(source) }.getOrDefault(SessionSource.MANUAL),
            apkResult = apkResult,
            logResult = logResult,
        )
    }
}
