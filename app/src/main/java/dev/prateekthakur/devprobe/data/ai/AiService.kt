package dev.prateekthakur.devprobe.data.ai

import dev.prateekthakur.devprobe.domain.model.CrashReport
import dev.prateekthakur.devprobe.domain.model.SecurityFinding

/** Structured AI response distinguishing fact from speculation (§17.2). */
data class AiExplanation(
    val summary: String,
    val confirmedFacts: List<String>,
    val likelyCause: String?,
    val possibleCauses: List<String>,
    val recommendations: List<String>,
    val confidence: Double,
    val source: String,
)

/**
 * AI is an optional layer on top of deterministic analysis (§17, §29). This
 * abstraction lets a real cloud/local LLM provider be swapped in later without
 * touching call sites — see MockAiService for the current, fully local implementation.
 */
interface AiService {
    suspend fun explainCrash(crash: CrashReport, appPackage: String?): AiExplanation
    suspend fun explainSecurityFindings(findings: List<SecurityFinding>): AiExplanation
    val isLocalOnly: Boolean
    val providerName: String
}
