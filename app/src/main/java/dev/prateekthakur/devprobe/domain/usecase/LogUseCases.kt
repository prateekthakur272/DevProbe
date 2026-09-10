package dev.prateekthakur.devprobe.domain.usecase

import dev.prateekthakur.devprobe.data.ai.AiExplanation
import dev.prateekthakur.devprobe.data.ai.AiService
import dev.prateekthakur.devprobe.data.log.CrashAnalyzer
import dev.prateekthakur.devprobe.domain.model.CrashReport
import dev.prateekthakur.devprobe.domain.model.LogAnalysisResult
import dev.prateekthakur.devprobe.domain.model.SecurityFinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ParseLogUseCase(private val crashAnalyzer: CrashAnalyzer) {
    suspend operator fun invoke(rawText: String, appPackage: String? = null): LogAnalysisResult =
        withContext(Dispatchers.Default) { crashAnalyzer.analyze(rawText, appPackage) }
}

class ExplainCrashUseCase(private val aiService: AiService) {
    suspend operator fun invoke(crash: CrashReport, appPackage: String?): AiExplanation =
        aiService.explainCrash(crash, appPackage)
}

class ExplainSecurityUseCase(private val aiService: AiService) {
    suspend operator fun invoke(findings: List<SecurityFinding>): AiExplanation =
        aiService.explainSecurityFindings(findings)
}
