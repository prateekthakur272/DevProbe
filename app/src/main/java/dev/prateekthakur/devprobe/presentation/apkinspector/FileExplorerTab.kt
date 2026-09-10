package dev.prateekthakur.devprobe.presentation.apkinspector

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.prateekthakur.devprobe.domain.model.ApkFileEntryDetail
import dev.prateekthakur.devprobe.domain.model.AppError
import dev.prateekthakur.devprobe.presentation.components.CodeLanguage
import dev.prateekthakur.devprobe.presentation.components.EmptyState
import dev.prateekthakur.devprobe.presentation.components.ErrorState
import dev.prateekthakur.devprobe.presentation.components.FolderIcon
import dev.prateekthakur.devprobe.presentation.components.FullScreenLoading
import dev.prateekthakur.devprobe.presentation.components.RowDivider
import dev.prateekthakur.devprobe.presentation.components.SecondaryActionButton
import dev.prateekthakur.devprobe.presentation.components.highlightLine

/** A node in the folder tree synthesized from the APK's flat zip-entry path list. */
private data class FolderView(val currentPath: String, val folders: List<String>, val files: List<ApkFileEntryDetail>)

@Composable
fun FileExplorerTab(viewModel: ApkInspectorViewModel) {
    LaunchedEffect(Unit) { viewModel.loadFileTree() }
    val state by viewModel.fileTreeState.collectAsStateWithLifecycle()
    var currentPath by remember { mutableStateOf("") }
    var selectedFile by remember { mutableStateOf<ApkFileEntryDetail?>(null) }

    when (val s = state) {
        is ReToolState.NotLoaded, is ReToolState.Loading -> FullScreenLoading("Reading APK contents…")
        is ReToolState.Failed -> ErrorState(AppError.unexpected(s.message))
        is ReToolState.Loaded -> {
            val entries = s.data
            val selected = selectedFile
            if (selected != null) {
                FileViewer(viewModel = viewModel, entry = selected, onBack = { selectedFile = null })
            } else {
                val view = remember(currentPath, entries) { buildFolderView(entries, currentPath) }
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            text = "/" + view.currentPath,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (view.currentPath.isNotEmpty()) {
                            item {
                                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    SecondaryActionButton(".. (up)", onClick = { currentPath = currentPath.substringBeforeLast('/', "") })
                                }
                            }
                        }
                        items(view.folders, key = { "dir:$it" }) { folder ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { currentPath = if (view.currentPath.isEmpty()) folder else "${view.currentPath}/$folder" }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(FolderIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(folder, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        items(view.files, key = { "file:" + it.path }) { file ->
                            Column(modifier = Modifier.fillMaxWidth().clickable { selectedFile = file }) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(file.path.substringAfterLast('/'), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Text(formatBytes(file.sizeBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                RowDivider()
                            }
                        }
                        if (view.folders.isEmpty() && view.files.isEmpty()) {
                            item { EmptyState("Empty folder.") }
                        }
                    }
                }
            }
        }
    }
}

private fun buildFolderView(entries: List<ApkFileEntryDetail>, currentPath: String): FolderView {
    val prefix = if (currentPath.isEmpty()) "" else "$currentPath/"
    val folders = sortedSetOf<String>()
    val files = mutableListOf<ApkFileEntryDetail>()
    for (entry in entries) {
        if (entry.isDirectory) continue
        if (!entry.path.startsWith(prefix)) continue
        val remainder = entry.path.removePrefix(prefix)
        if (remainder.isEmpty()) continue
        val slashIdx = remainder.indexOf('/')
        if (slashIdx == -1) {
            files += entry
        } else {
            folders += remainder.substring(0, slashIdx)
        }
    }
    return FolderView(currentPath, folders.toList(), files.sortedBy { it.path })
}

/** How a file's raw bytes are rendered — the set offered and the default both depend on file type. */
private enum class FileViewMode(val label: String) {
    IMAGE("Image"),
    TEXT("Text"),
    HEX("Hex"),
    RAW("Raw"),
}

private val TEXT_EXTENSIONS = setOf(
    "xml", "json", "txt", "properties", "pro", "cfg", "conf", "ini", "md",
    "yml", "yaml", "gradle", "kt", "kts", "java", "js", "ts", "html", "htm",
    "css", "sql", "proto", "mf", "sf", "version", "kotlin_module", "kotlin_builtins",
)
private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
private const val IMAGE_READ_CAP_BYTES = 8 * 1024 * 1024

private fun extensionOf(path: String) = path.substringAfterLast('.', "").lowercase()
private fun isTextPath(path: String) = extensionOf(path) in TEXT_EXTENSIONS
private fun isImagePath(path: String) = extensionOf(path) in IMAGE_EXTENSIONS

private fun languageFor(path: String): CodeLanguage = when (extensionOf(path)) {
    "json" -> CodeLanguage.JSON
    "xml", "html", "htm" -> CodeLanguage.XML
    "js", "ts" -> CodeLanguage.JS
    "kt", "kts", "java", "gradle" -> CodeLanguage.JVM
    "properties", "pro", "cfg", "conf", "ini", "yml", "yaml", "mf", "sf", "version" -> CodeLanguage.PROPERTIES
    else -> CodeLanguage.GENERIC
}

/** Every mode offered for this file — Image only appears for recognized image extensions. */
private fun viewModesFor(path: String): List<FileViewMode> =
    if (isImagePath(path)) FileViewMode.entries else FileViewMode.entries.filter { it != FileViewMode.IMAGE }

private fun defaultViewModeFor(path: String): FileViewMode = when {
    isImagePath(path) -> FileViewMode.IMAGE
    isTextPath(path) -> FileViewMode.TEXT
    else -> FileViewMode.HEX
}

@Composable
private fun FileViewer(viewModel: ApkInspectorViewModel, entry: ApkFileEntryDetail, onBack: () -> Unit) {
    val modes = remember(entry.path) { viewModesFor(entry.path) }
    var mode by remember(entry.path) { mutableStateOf(defaultViewModeFor(entry.path)) }
    var bytes by remember(entry.path, mode) { mutableStateOf<ByteArray?>(null) }

    LaunchedEffect(entry.path, mode) {
        bytes = null
        val cap = if (mode == FileViewMode.IMAGE) IMAGE_READ_CAP_BYTES else 64 * 1024
        bytes = viewModel.readFileEntryBytes(entry.path, cap)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                entry.path.substringAfterLast('/'),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            ViewModeDropdown(modes = modes, selected = mode, onSelect = { mode = it })
        }
        Text(
            "${formatBytes(entry.sizeBytes)} · compressed ${formatBytes(entry.compressedSizeBytes)} · CRC32 ${entry.crc32.toString(16)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(modifier = Modifier.height(6.dp))
        val currentBytes = bytes
        when {
            currentBytes == null -> FullScreenLoading("Reading file…")
            else -> {
                if (mode != FileViewMode.IMAGE && entry.sizeBytes > currentBytes.size) {
                    Text(
                        "Showing first ${formatBytes(currentBytes.size.toLong())} of ${formatBytes(entry.sizeBytes)}.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                when (mode) {
                    FileViewMode.HEX -> HexContent(currentBytes)
                    FileViewMode.TEXT -> CodeContent(currentBytes.toString(Charsets.UTF_8), languageFor(entry.path))
                    FileViewMode.RAW -> RawTextContent(currentBytes.toString(Charsets.ISO_8859_1))
                    FileViewMode.IMAGE -> ImageContent(currentBytes, entry.path)
                }
            }
        }
    }
}

/** A compact trigger + dropdown for picking the view mode, kept out of the content
 * area entirely so the file preview gets the full remaining height. */
@Composable
private fun ViewModeDropdown(modes: List<FileViewMode>, selected: FileViewMode, onSelect: (FileViewMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(selected.label)
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.height(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            modes.forEach { m ->
                DropdownMenuItem(text = { Text(m.label) }, onClick = { onSelect(m); expanded = false })
            }
        }
    }
}

@Composable
private fun HexContent(bytes: ByteArray) {
    LazyColumn(modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState()), contentPadding = PaddingValues(16.dp)) {
        items(hexLines(bytes)) { line ->
            Text(line, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
        }
    }
}

/** Pretty-printed source view: a line-number gutter plus per-line syntax highlighting
 * (see CodeHighlighter.kt) keyed off the file's extension. */
@Composable
private fun CodeContent(text: String, language: CodeLanguage) {
    val lines = remember(text) { text.split("\n") }
    val gutterWidth = remember(lines.size) { lines.size.toString().length }
    LazyColumn(modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState()), contentPadding = PaddingValues(vertical = 12.dp)) {
        itemsIndexed(lines) { index, line ->
            val highlighted = remember(line, language) { highlightLine(line, language) }
            Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = (index + 1).toString().padStart(gutterWidth),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = highlighted, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
            }
        }
    }
}

/** Raw mode decodes as Latin-1, a lossless 1-byte-to-1-char mapping that surfaces embedded
 * ASCII strings in any file, text or not, without ever failing to decode — unformatted on purpose. */
@Composable
private fun RawTextContent(text: String) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item { Text(text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)) }
    }
}

@Composable
private fun ImageContent(bytes: ByteArray, path: String) {
    val imageBitmap = remember(bytes) {
        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
    }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val bitmap = imageBitmap
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = path,
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "${bitmap.width} × ${bitmap.height}px",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            EmptyState("Unable to decode this image — it may be truncated or an unsupported format.")
        }
    }
}

private fun hexLines(bytes: ByteArray, bytesPerLine: Int = 16): List<String> {
    val lines = mutableListOf<String>()
    var offset = 0
    while (offset < bytes.size) {
        val end = minOf(offset + bytesPerLine, bytes.size)
        val hex = (offset until end).joinToString(" ") { "%02X".format(bytes[it]) }
        val ascii = (offset until end).joinToString("") { i ->
            val b = bytes[i].toInt() and 0xFF
            if (b in 32..126) b.toChar().toString() else "."
        }
        lines += "%08X  %-47s  %s".format(offset, hex, ascii)
        offset = end
    }
    return lines
}

