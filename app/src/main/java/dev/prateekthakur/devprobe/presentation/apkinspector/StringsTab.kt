package dev.prateekthakur.devprobe.presentation.apkinspector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.prateekthakur.devprobe.domain.model.DetectedSecret
import dev.prateekthakur.devprobe.domain.model.SecretType
import dev.prateekthakur.devprobe.domain.model.StringScanResult
import dev.prateekthakur.devprobe.presentation.components.AppCard
import dev.prateekthakur.devprobe.presentation.components.EmptyState
import dev.prateekthakur.devprobe.presentation.components.SectionHeader
import dev.prateekthakur.devprobe.presentation.components.SeverityBadge
import dev.prateekthakur.devprobe.presentation.components.StatChip

@Composable
fun StringsTab(stringScan: StringScanResult) {
    val secrets = stringScan.secrets
    val credentialLike = secrets.filter { it.type != SecretType.URL && it.type != SecretType.EMAIL }
    val urls = secrets.filter { it.type == SecretType.URL }
    val emails = secrets.filter { it.type == SecretType.EMAIL }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StatChip("Strings scanned", stringScan.totalStringsScanned.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                StatChip("Possible secrets", credentialLike.size.toString(), MaterialTheme.colorScheme.error, Modifier.weight(1f))
            }
        }
        if (credentialLike.isEmpty() && urls.isEmpty() && emails.isEmpty()) {
            item { EmptyState("No secrets, URLs, or emails matched in DEX/resource strings.") }
        }
        items(credentialLike, key = { it.type.name + it.matchedText }) { secret ->
            SecretCard(secret, Modifier.animateItem())
        }
        if (urls.isNotEmpty()) {
            item { SectionHeader("URLs (${urls.size})") }
            items(urls.take(50), key = { "url:" + it.matchedText }) { secret ->
                AppCard(modifier = Modifier.animateItem()) {
                    Text(secret.matchedText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(16.dp))
                }
            }
        }
        if (emails.isNotEmpty()) {
            item { SectionHeader("Emails (${emails.size})") }
            items(emails.take(50), key = { "email:" + it.matchedText }) { secret ->
                AppCard(modifier = Modifier.animateItem()) {
                    Text(secret.matchedText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SecretCard(secret: DetectedSecret, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SeverityBadge(secret.severity)
                Text(secretTypeLabel(secret.type), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(secret.matchedText, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(2.dp))
            Text("Source: ${secret.source.name.lowercase()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun secretTypeLabel(type: SecretType): String = when (type) {
    SecretType.AWS_KEY -> "AWS access key"
    SecretType.GOOGLE_API_KEY -> "Google API key"
    SecretType.JWT -> "JWT token"
    SecretType.PRIVATE_KEY -> "Private key material"
    SecretType.GENERIC_SECRET_ASSIGNMENT -> "Hardcoded credential"
    SecretType.URL -> "URL"
    SecretType.EMAIL -> "Email"
}
