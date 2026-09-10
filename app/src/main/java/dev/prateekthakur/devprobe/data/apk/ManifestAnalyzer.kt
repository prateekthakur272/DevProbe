package dev.prateekthakur.devprobe.data.apk

import dev.prateekthakur.devprobe.domain.model.ComponentInfo
import dev.prateekthakur.devprobe.domain.model.ComponentType
import dev.prateekthakur.devprobe.domain.model.IntentFilterInfo
import dev.prateekthakur.devprobe.domain.model.ManifestInfo
import dev.prateekthakur.devprobe.domain.model.ProviderInfo
import java.io.File
import java.util.zip.ZipFile

class ManifestAnalyzer {

    fun analyze(apkFile: File): ManifestInfo {
        val manifestBytes = ZipFile(apkFile).use { zip ->
            val entry = zip.getEntry("AndroidManifest.xml")
                ?: throw IllegalStateException("AndroidManifest.xml not found in APK.")
            zip.getInputStream(entry).use { it.readBytes() }
        }
        val root = AxmlParser.parse(manifestBytes)
        val manifestNode = root.children.firstOrNull { it.name == "manifest" }
            ?: throw IllegalStateException("Malformed manifest: no <manifest> root element.")
        val packageName = manifestNode.attributes["package"].orEmpty()
        val compileSdkVersion = manifestNode.attributes["compileSdkVersion"]?.toIntOrNull()
        val appNode = manifestNode.children.firstOrNull { it.name == "application" }

        val debuggable = appNode?.attributes?.get("debuggable") == "true"
        val allowBackup = appNode?.attributes?.get("allowBackup")?.let { it == "true" } ?: true
        val usesCleartext = appNode?.attributes?.get("usesCleartextTraffic")?.let { it == "true" }
        val hasNetworkSecurityConfig = appNode?.attributes?.containsKey("networkSecurityConfig") == true
        val hasDataExtractionRules = appNode?.attributes?.containsKey("dataExtractionRules") == true
        val appLabel = appNode?.attributes?.get("label").orEmpty()

        val activities = mutableListOf<ComponentInfo>()
        val services = mutableListOf<ComponentInfo>()
        val receivers = mutableListOf<ComponentInfo>()
        val providers = mutableListOf<ProviderInfo>()

        appNode?.children?.forEach { child ->
            when (child.name) {
                "activity", "activity-alias" -> activities += toComponent(child, ComponentType.ACTIVITY)
                "service" -> services += toComponent(child, ComponentType.SERVICE)
                "receiver" -> receivers += toComponent(child, ComponentType.RECEIVER)
                "provider" -> providers += toProvider(child)
            }
        }

        return ManifestInfo(
            packageName = packageName,
            appLabel = appLabel,
            debuggable = debuggable,
            allowBackup = allowBackup,
            usesCleartextTraffic = usesCleartext,
            hasNetworkSecurityConfig = hasNetworkSecurityConfig,
            hasDataExtractionRules = hasDataExtractionRules,
            activities = activities,
            services = services,
            receivers = receivers,
            providers = providers,
            compileSdkVersion = compileSdkVersion,
        )
    }

    private fun toComponent(node: AxmlParser.Node, type: ComponentType): ComponentInfo {
        val intentFilters = node.children.filter { it.name == "intent-filter" }.map { filterNode ->
            IntentFilterInfo(
                actions = filterNode.children.filter { it.name == "action" }
                    .mapNotNull { it.attributes["name"] },
                categories = filterNode.children.filter { it.name == "category" }
                    .mapNotNull { it.attributes["name"] },
                dataSchemes = filterNode.children.filter { it.name == "data" }
                    .mapNotNull { it.attributes["scheme"] },
            )
        }
        val declaredExported = node.attributes["exported"]
        val exported = when (declaredExported) {
            "true" -> true
            "false" -> false
            else -> intentFilters.isNotEmpty() // Android default: exported if it has an intent-filter.
        }
        return ComponentInfo(
            name = node.attributes["name"].orEmpty(),
            type = type,
            exported = exported,
            permission = node.attributes["permission"],
            intentFilters = intentFilters,
            launchMode = node.attributes["launchMode"],
        )
    }

    private fun toProvider(node: AxmlParser.Node): ProviderInfo {
        return ProviderInfo(
            name = node.attributes["name"].orEmpty(),
            authority = node.attributes["authorities"].orEmpty(),
            exported = node.attributes["exported"] == "true",
            readPermission = node.attributes["readPermission"] ?: node.attributes["permission"],
            writePermission = node.attributes["writePermission"] ?: node.attributes["permission"],
            grantUriPermissions = node.attributes["grantUriPermissions"] == "true",
        )
    }
}
