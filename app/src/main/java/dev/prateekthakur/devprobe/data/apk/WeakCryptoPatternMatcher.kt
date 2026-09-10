package dev.prateekthakur.devprobe.data.apk

import dev.prateekthakur.devprobe.domain.model.StringSource
import dev.prateekthakur.devprobe.domain.model.WeakCryptoAlgorithm
import dev.prateekthakur.devprobe.domain.model.WeakCryptoUsage

/**
 * Detects likely weak-cryptography usage from string literals. Java/Android crypto
 * APIs (Cipher.getInstance, MessageDigest.getInstance) take the algorithm/transformation
 * name as a string argument, so that literal string appears verbatim in the DEX string
 * pool — the same technique tools like MobSF use for this class of finding. Matches are
 * exact (not substring) against known transformation/algorithm names to keep the false
 * positive rate low; still a heuristic, not proof the algorithm is actually invoked insecurely.
 */
object WeakCryptoPatternMatcher {

    private val exactMatches: Map<String, WeakCryptoAlgorithm> = mapOf(
        "DES" to WeakCryptoAlgorithm.DES,
        "DES/CBC/PKCS5Padding" to WeakCryptoAlgorithm.DES,
        "DES/ECB/PKCS5Padding" to WeakCryptoAlgorithm.DES,
        "DESede" to WeakCryptoAlgorithm.TRIPLE_DES,
        "DESede/CBC/PKCS5Padding" to WeakCryptoAlgorithm.TRIPLE_DES,
        "RC2" to WeakCryptoAlgorithm.RC2,
        "RC4" to WeakCryptoAlgorithm.RC4,
        "ARCFOUR" to WeakCryptoAlgorithm.RC4,
        "MD5" to WeakCryptoAlgorithm.MD5,
        "SHA1" to WeakCryptoAlgorithm.SHA1,
        "SHA-1" to WeakCryptoAlgorithm.SHA1,
    )

    private val ecbTransformationRegex = Regex("""^(AES|DES|DESede|Blowfish|RC2)/ECB/[A-Za-z0-9]+$""")

    fun scan(text: String, source: StringSource): WeakCryptoUsage? {
        exactMatches[text]?.let { return WeakCryptoUsage(it, text, source) }
        if (ecbTransformationRegex.matches(text)) {
            return WeakCryptoUsage(WeakCryptoAlgorithm.ECB_MODE, text, source)
        }
        return null
    }
}
