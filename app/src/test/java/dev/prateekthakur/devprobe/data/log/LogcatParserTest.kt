package dev.prateekthakur.devprobe.data.log

import dev.prateekthakur.devprobe.domain.model.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class LogcatParserTest {

    private val parser = LogcatParser()

    @Test
    fun `parses threadtime format`() {
        val line = "09-09 19:32:12.123  1234  1256 E PaymentService: Payment failed"
        val entries = parser.parse(line)
        assertEquals(1, entries.size)
        val entry = entries.first()
        assertEquals(LogLevel.ERROR, entry.level)
        assertEquals("PaymentService", entry.tag)
        assertEquals("1234", entry.pid)
        assertEquals("1256", entry.tid)
        assertEquals("Payment failed", entry.message)
    }

    @Test
    fun `parses brief format`() {
        val line = "E/PaymentService( 1234): Payment failed"
        val entries = parser.parse(line)
        assertEquals(1, entries.size)
        assertEquals(LogLevel.ERROR, entries.first().level)
        assertEquals("PaymentService", entries.first().tag)
    }

    @Test
    fun `unrecognized line falls back to UNKNOWN level`() {
        val entries = parser.parse("some unrelated text")
        assertEquals(LogLevel.UNKNOWN, entries.first().level)
    }
}
