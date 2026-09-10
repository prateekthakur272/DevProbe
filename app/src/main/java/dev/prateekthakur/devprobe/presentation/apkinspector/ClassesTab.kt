package dev.prateekthakur.devprobe.presentation.apkinspector

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.prateekthakur.devprobe.domain.model.AppError
import dev.prateekthakur.devprobe.domain.model.DexClassInfo
import dev.prateekthakur.devprobe.presentation.components.AppCard
import dev.prateekthakur.devprobe.presentation.components.EmptyState
import dev.prateekthakur.devprobe.presentation.components.ErrorState
import dev.prateekthakur.devprobe.presentation.components.EyebrowLabel
import dev.prateekthakur.devprobe.presentation.components.FullScreenLoading
import dev.prateekthakur.devprobe.presentation.components.RowDivider
import dev.prateekthakur.devprobe.presentation.components.StatChip

@Composable
fun ClassesTab(viewModel: ApkInspectorViewModel) {
    LaunchedEffect(Unit) { viewModel.loadClasses() }
    val state by viewModel.classesState.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    when (val s = state) {
        is ReToolState.NotLoaded, is ReToolState.Loading -> FullScreenLoading("Parsing classes… this can take a moment for large apps")
        is ReToolState.Failed -> Column(modifier = Modifier.padding(16.dp)) { ErrorState(AppError.unexpected(s.message)) }
        is ReToolState.Loaded -> {
            val classes = s.data
            val filtered = remember(query, classes) {
                if (query.isBlank()) classes else classes.filter { it.name.contains(query, ignoreCase = true) }
            }
            Column(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    StatChip("Classes found", classes.size.toString(), MaterialTheme.colorScheme.primary, Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search class name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                    )
                }
                if (filtered.isEmpty()) {
                    EmptyState("No classes match \"$query\".")
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(filtered, key = { it.name }) { classInfo ->
                            ClassCard(classInfo, Modifier.animateItem())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassCard(classInfo: DexClassInfo, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    AppCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = classInfo.name, style = MaterialTheme.typography.titleSmall)
            Text(
                "${classInfo.accessFlags.joinToString(" ")}${if (classInfo.isInterface) " interface" else " class"}" +
                    (classInfo.superclass?.let { " extends $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            EyebrowLabel(
                "${classInfo.fields.size} fields · ${classInfo.methods.size} methods",
                modifier = Modifier.padding(top = 6.dp).clickable { expanded = !expanded },
            )
            if (expanded) {
                if (classInfo.fields.isNotEmpty()) {
                    RowDivider()
                    EyebrowLabel("Fields", modifier = Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    classInfo.fields.forEach { field ->
                        Text(
                            "${field.accessFlags.joinToString(" ")} ${field.type} ${field.name}".trim(),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (classInfo.methods.isNotEmpty()) {
                    RowDivider()
                    EyebrowLabel("Methods", modifier = Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    classInfo.methods.forEach { method ->
                        Text(
                            "${method.accessFlags.joinToString(" ")} ${method.signature}".trim(),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

