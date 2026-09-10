package dev.prateekthakur.devprobe.data.apk

import dev.prateekthakur.devprobe.domain.model.DetectedSecret
import dev.prateekthakur.devprobe.domain.model.SecretType
import dev.prateekthakur.devprobe.domain.model.Severity
import dev.prateekthakur.devprobe.domain.model.StringSource

/**
 * Deterministic pattern matching for likely secrets/sensitive data in extracted
 * strings (§23: API keys, passwords, tokens, private URLs, emails). Regex-based —
 * no AI, no false sense of exhaustiveness; flagged matches still need human review.
 */
object SecretPatternMatcher {

    private data class Pattern(val type: SecretType, val severity: Severity, val regex: Regex)

    private val patterns = listOf(
        Pattern(SecretType.AWS_KEY, Severity.CRITICAL, Regex("""AKIA[0-9A-Z]{16}""")),
        Pattern(SecretType.GOOGLE_API_KEY, Severity.HIGH, Regex("""AIza[0-9A-Za-z_\-]{35}""")),
        Pattern(SecretType.JWT, Severity.HIGH, Regex("""eyJ[A-Za-z0-9_-]{5,}\.[A-Za-z0-9_-]{5,}\.[A-Za-z0-9_-]{5,}""")),
        Pattern(SecretType.PRIVATE_KEY, Severity.CRITICAL, Regex("""-----BEGIN\s?(RSA|EC|DSA|OPENSSH)?\s?PRIVATE KEY-----""")),
        Pattern(
            SecretType.GENERIC_SECRET_ASSIGNMENT,
            Severity.HIGH,
            Regex("""(?i)(api[_-]?key|secret|password|passwd|token|access[_-]?key)\s*[:=]\s*["']?[A-Za-z0-9+/_\-]{8,}["']?"""),
        ),
        Pattern(SecretType.URL, Severity.INFO, Regex("""https?://[^\s"'<>]{4,}""")),
        Pattern(SecretType.EMAIL, Severity.INFO, Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")),
    )

    fun scan(text: String, source: StringSource): List<DetectedSecret> {
        val found = mutableListOf<DetectedSecret>()
        for (pattern in patterns) {
            val match = pattern.regex.find(text) ?: continue
            found += DetectedSecret(
                type = pattern.type,
                matchedText = match.value,
                context = text.take(160),
                source = source,
                severity = pattern.severity,
            )
        }
        return found
    }
}
