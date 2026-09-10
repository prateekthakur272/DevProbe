package dev.prateekthakur.devprobe.presentation.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/**
 * A deliberately simple, per-line, regex-based syntax highlighter — not a real
 * lexer (no multi-line comment/string tracking across line boundaries), but
 * enough to make source-like file previews in the APK file explorer readable
 * at a glance without pulling in a highlighting library.
 */
enum class CodeLanguage { JSON, XML, JS, JVM, PROPERTIES, GENERIC }

object CodeColors {
    val Keyword = Color(0xFFAB47BC)
    val StringLiteral = Color(0xFF2E7D32)
    val NumberLiteral = Color(0xFFEF6C00)
    val Comment = Color(0xFF78909C)
    val Key = Color(0xFF1565C0)
}

private val JSON_PATTERNS: List<Pair<Regex, Color>> = listOf(
    Regex(""""(?:[^"\\]|\\.)*"(?=\s*:)""") to CodeColors.Key,
    Regex(""""(?:[^"\\]|\\.)*"""") to CodeColors.StringLiteral,
    Regex("""-?\b\d+\.?\d*(?:[eE][+-]?\d+)?\b""") to CodeColors.NumberLiteral,
    Regex("""\b(?:true|false|null)\b""") to CodeColors.Keyword,
)

private val XML_PATTERNS: List<Pair<Regex, Color>> = listOf(
    Regex("""<!--.*-->""") to CodeColors.Comment,
    Regex(""""[^"]*"""") to CodeColors.StringLiteral,
    Regex("""(?<=</?)[a-zA-Z_][\w:.-]*""") to CodeColors.Key,
    Regex("""\b[a-zA-Z_][\w:.-]*(?=\s*=)""") to CodeColors.Keyword,
)

private val JS_KEYWORDS = listOf(
    "var", "let", "const", "function", "return", "if", "else", "for", "while", "do", "switch",
    "case", "default", "break", "continue", "new", "this", "typeof", "instanceof", "in", "of",
    "class", "extends", "super", "import", "export", "from", "try", "catch", "finally", "throw",
    "async", "await", "yield", "void", "delete", "null", "undefined", "true", "false",
)
private val JS_PATTERNS: List<Pair<Regex, Color>> = listOf(
    Regex("""//.*""") to CodeColors.Comment,
    Regex(""""(?:[^"\\\n]|\\.)*"|'(?:[^'\\\n]|\\.)*'|`(?:[^`\\]|\\.)*`""") to CodeColors.StringLiteral,
    Regex("""\b\d+\.?\d*\b""") to CodeColors.NumberLiteral,
    Regex("""\b(?:${JS_KEYWORDS.joinToString("|")})\b""") to CodeColors.Keyword,
)

private val JVM_KEYWORDS = listOf(
    "fun", "val", "var", "class", "interface", "object", "data", "enum", "sealed", "companion",
    "override", "open", "abstract", "final", "static", "public", "private", "protected",
    "internal", "import", "package", "return", "if", "else", "for", "while", "do", "when", "is",
    "as", "try", "catch", "finally", "throw", "throws", "new", "this", "super", "null", "true",
    "false", "void", "int", "long", "double", "float", "boolean", "char", "byte", "short",
    "String", "extends", "implements", "suspend", "inline", "lateinit", "const", "typealias",
)
private val JVM_PATTERNS: List<Pair<Regex, Color>> = listOf(
    Regex("""//.*""") to CodeColors.Comment,
    Regex(""""(?:[^"\\\n]|\\.)*"|'(?:[^'\\\n]|\\.)*'""") to CodeColors.StringLiteral,
    Regex("""\b\d+\.?\d*[fFlLdD]?\b""") to CodeColors.NumberLiteral,
    Regex("""\b(?:${JVM_KEYWORDS.joinToString("|")})\b""") to CodeColors.Keyword,
)

private val PROPERTIES_PATTERNS: List<Pair<Regex, Color>> = listOf(
    Regex("""^\s*[#;].*""") to CodeColors.Comment,
    Regex("""^[^=:#;\s][^=:]*(?=[:=])""") to CodeColors.Key,
)

private val GENERIC_PATTERNS: List<Pair<Regex, Color>> = listOf(
    Regex("""//.*|#.*""") to CodeColors.Comment,
    Regex(""""(?:[^"\\\n]|\\.)*"|'(?:[^'\\\n]|\\.)*'""") to CodeColors.StringLiteral,
    Regex("""\b\d+\.?\d*\b""") to CodeColors.NumberLiteral,
)

private fun patternsFor(language: CodeLanguage): List<Pair<Regex, Color>> = when (language) {
    CodeLanguage.JSON -> JSON_PATTERNS
    CodeLanguage.XML -> XML_PATTERNS
    CodeLanguage.JS -> JS_PATTERNS
    CodeLanguage.JVM -> JVM_PATTERNS
    CodeLanguage.PROPERTIES -> PROPERTIES_PATTERNS
    CodeLanguage.GENERIC -> GENERIC_PATTERNS
}

/** Colors one line — patterns are applied in priority order and never re-color an already-claimed span. */
fun highlightLine(line: String, language: CodeLanguage): AnnotatedString {
    if (line.isEmpty()) return AnnotatedString(line)
    val colors = arrayOfNulls<Color>(line.length)
    val claimed = BooleanArray(line.length)
    for ((regex, color) in patternsFor(language)) {
        regex.findAll(line).forEach { match ->
            for (i in match.range) {
                if (i in colors.indices && !claimed[i]) {
                    colors[i] = color
                    claimed[i] = true
                }
            }
        }
    }
    return buildAnnotatedString {
        var i = 0
        while (i < line.length) {
            val color = colors[i]
            var j = i + 1
            while (j < line.length && colors[j] == color) j++
            withStyle(SpanStyle(color = color ?: Color.Unspecified)) { append(line.substring(i, j)) }
            i = j
        }
    }
}
