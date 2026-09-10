package dev.prateekthakur.devprobe.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeHighlighterTest {

    @Test
    fun `json key gets a different color than its string value`() {
        val result = highlightLine("""  "name": "value",""", CodeLanguage.JSON)
        val keySpan = result.spanStyles.first { result.text.substring(it.start, it.end) == "\"name\"" }
        val valueSpan = result.spanStyles.first { result.text.substring(it.start, it.end) == "\"value\"" }

        assertEquals(CodeColors.Key, keySpan.item.color)
        assertEquals(CodeColors.StringLiteral, valueSpan.item.color)
    }

    @Test
    fun `js line comment colors the whole remainder of the line`() {
        val result = highlightLine("""val x = 1 // trailing comment""", CodeLanguage.JVM)
        val commentSpan = result.spanStyles.first { result.text.substring(it.start, it.end).startsWith("//") }

        assertEquals(CodeColors.Comment, commentSpan.item.color)
        assertTrue(result.text.substring(commentSpan.start, commentSpan.end).contains("trailing comment"))
    }

    @Test
    fun `empty line produces no spans and no crash`() {
        val result = highlightLine("", CodeLanguage.GENERIC)
        assertEquals("", result.text)
    }

    @Test
    fun `jvm keyword is colored`() {
        val result = highlightLine("fun main() {}", CodeLanguage.JVM)
        val keywordSpan = result.spanStyles.firstOrNull { result.text.substring(it.start, it.end) == "fun" }

        assertEquals(CodeColors.Keyword, keywordSpan?.item?.color)
    }
}
