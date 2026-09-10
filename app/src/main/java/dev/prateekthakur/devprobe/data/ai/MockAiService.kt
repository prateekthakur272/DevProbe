package dev.prateekthakur.devprobe.data.ai

import dev.prateekthakur.devprobe.domain.model.CrashReport
import dev.prateekthakur.devprobe.domain.model.SecurityFinding
import dev.prateekthakur.devprobe.domain.model.Severity
import kotlinx.coroutines.delay

/**
 * Fully local, template-based "AI" that reasons only over the structured facts it
 * is given — no network calls, no data leaves the device. This satisfies the
 * AiService contract today; a real LLM-backed implementation can replace it later
 * without any caller changes.
 */
class MockAiService : AiService {

    override val isLocalOnly: Boolean = true
    override val providerName: String = "On-device template engine (no cloud call)"

    override suspend fun explainCrash(crash: CrashReport, appPackage: String?): AiExplanation {
        delay(300) // Simulate analysis latency so the UI's loading state is exercised.

        val confirmed = buildList {
            add("Exception type: ${crash.exceptionType}")
            crash.message?.let { add("Exception message: $it") }
            crash.thread?.let { add("Thread: $it") }
            crash.firstAppFrame?.let {
                add("First application frame: ${it.declaringClass}.${it.method}(${it.file}:${it.line ?: "?"})")
            }
        }

        val likelyCause = crash.firstAppFrame?.let {
            "The crash originates in application code at ${it.declaringClass}.${it.method}" +
                (it.line?.let { line -> " (line $line)" } ?: "") +
                ", triggered by a ${crash.exceptionType}."
        } ?: "The crash trace shows no application-owned frame; it likely originates in a framework or library call made by the app."

        val possibleCauses = mutableListOf<String>()
        when {
            crash.exceptionType.contains("NullPointerException") ->
                possibleCauses += "A field, view, or dependency was accessed before it was initialized (common with lateinit vars or async callbacks that outlive their owner)."
            crash.exceptionType.contains("IllegalStateException") ->
                possibleCauses += "A component was used outside its expected lifecycle state (e.g. Fragment/Activity accessed after being destroyed)."
            crash.exceptionType.contains("OutOfMemoryError") ->
                possibleCauses += "Large bitmaps/buffers were allocated without recycling, or a memory leak is retaining objects across screens."
            crash.exceptionType.contains("IndexOutOfBounds") ->
                possibleCauses += "A collection was accessed with a stale or unvalidated index, possibly after the underlying data changed."
            crash.exceptionType.contains("ClassCastException") ->
                possibleCauses += "An object was cast to a type it does not actually implement, often from a misconfigured adapter/ViewHolder or serialization mismatch."
            else -> possibleCauses += "Review the first application frame and the condition that leads to it for the specific trigger."
        }
        crash.fullTrace.causedBy?.let {
            possibleCauses += "This exception was caused by a nested ${it.exceptionType}${it.message?.let { m -> ": $m" } ?: ""} — the root issue may be there instead."
        }

        val recommendations = buildList {
            add("Reproduce the crash with a debugger attached at ${crash.firstAppFrame?.declaringClass ?: "the top application frame"}.")
            add("Add null/state checks around the failing call, or guard it with the appropriate lifecycle check.")
            if (crash.severity == Severity.CRITICAL) add("Treat this as release-blocking given its severity (${crash.severity}).")
            add("Add a regression test that exercises this code path.")
        }

        return AiExplanation(
            summary = "A ${crash.exceptionType} occurred${crash.thread?.let { " on thread \"$it\"" } ?: ""}.",
            confirmedFacts = confirmed,
            likelyCause = likelyCause,
            possibleCauses = possibleCauses,
            recommendations = recommendations,
            confidence = if (crash.firstAppFrame != null) 0.7 else 0.4,
            source = providerName,
        )
    }

    override suspend fun explainSecurityFindings(findings: List<SecurityFinding>): AiExplanation {
        delay(300)
        if (findings.isEmpty()) {
            return AiExplanation(
                summary = "No security findings were reported by the rule engine.",
                confirmedFacts = emptyList(),
                likelyCause = null,
                possibleCauses = emptyList(),
                recommendations = listOf("Findings are limited to the deterministic rules currently implemented; a clean result does not guarantee the app has no security issues."),
                confidence = 0.5,
                source = providerName,
            )
        }
        val bySeverity = findings.groupingBy { it.severity }.eachCount()
        val confirmed = findings.map { "[${it.severity}] ${it.title}" }
        val topFinding = findings.maxByOrNull { it.severity.ordinal }!!
        val recommendations = findings.map { "${it.title}: ${it.recommendation}" }.distinct()

        return AiExplanation(
            summary = "${findings.size} security finding(s) detected — highest severity: ${topFinding.severity}. " +
                "Breakdown: " + bySeverity.entries.joinToString { "${it.key}=${it.value}" },
            confirmedFacts = confirmed,
            likelyCause = "The most impactful issue is \"${topFinding.title}\": ${topFinding.description}",
            possibleCauses = listOf("These findings reflect configuration choices in the manifest/permissions, not runtime behavior — verify each against actual intended use."),
            recommendations = recommendations,
            confidence = 0.6,
            source = providerName,
        )
    }
}
