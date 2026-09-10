package dev.prateekthakur.devprobe.data.apk

import dev.prateekthakur.devprobe.domain.model.DetectedSecret
import dev.prateekthakur.devprobe.domain.model.SecretType
import dev.prateekthakur.devprobe.domain.model.StringScanResult
import dev.prateekthakur.devprobe.domain.model.StringSource
import dev.prateekthakur.devprobe.domain.model.WeakCryptoAlgorithm
import dev.prateekthakur.devprobe.domain.model.WeakCryptoUsage
import java.io.File

/**
 * Eager (runs as part of the main analysis pipeline) string extraction + secret and
 * weak-cryptography-literal detection over DEX literal strings and the resources.arsc
 * string pool. Native library string scanning is deferred to the on-demand Natives tab
 * since .so files can be large and this is meant to stay fast for every import.
 */
class StringScanner {

    private val maxSecretsPerType = 20
    private val maxCryptoUsagesPerAlgorithm = 10

    fun scan(apkFile: File): StringScanResult {
        val dexStrings = DexBinaryReader.readDexEntries(apkFile)
            .flatMap { (_, bytes) -> runCatching { DexBinaryReader.readAllStrings(bytes) }.getOrDefault(emptyList()) }
        val resourceStrings = runCatching { ResourcesArscReader.readGlobalStringPool(apkFile) }.getOrDefault(emptyList())

        val secrets = mutableListOf<DetectedSecret>()
        val secretCountByType = HashMap<SecretType, Int>()
        val seenSecrets = HashSet<String>()

        val cryptoUsages = mutableListOf<WeakCryptoUsage>()
        val cryptoCountByAlgorithm = HashMap<WeakCryptoAlgorithm, Int>()
        val seenCrypto = HashSet<String>()

        fun scanAll(strings: List<String>, source: StringSource) {
            for (s in strings) {
                for (secret in SecretPatternMatcher.scan(s, source)) {
                    val dedupeKey = "${secret.type}:${secret.matchedText}"
                    if (!seenSecrets.add(dedupeKey)) continue
                    val countSoFar = secretCountByType.getOrDefault(secret.type, 0)
                    if (countSoFar >= maxSecretsPerType) continue
                    secretCountByType[secret.type] = countSoFar + 1
                    secrets += secret
                }
                WeakCryptoPatternMatcher.scan(s, source)?.let { usage ->
                    val dedupeKey = "${usage.algorithm}:${usage.matchedText}"
                    if (!seenCrypto.add(dedupeKey)) return@let
                    val countSoFar = cryptoCountByAlgorithm.getOrDefault(usage.algorithm, 0)
                    if (countSoFar >= maxCryptoUsagesPerAlgorithm) return@let
                    cryptoCountByAlgorithm[usage.algorithm] = countSoFar + 1
                    cryptoUsages += usage
                }
            }
        }

        scanAll(dexStrings, StringSource.DEX)
        scanAll(resourceStrings, StringSource.RESOURCES)

        return StringScanResult(
            totalStringsScanned = dexStrings.size + resourceStrings.size,
            secrets = secrets,
            weakCryptoUsages = cryptoUsages,
        )
    }
}
