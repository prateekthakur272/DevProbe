package dev.prateekthakur.devprobe.data.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LiveCrashDetectorTest {

    private val detector = LiveCrashDetector()

    private val crashLines = listOf(
        "09-10 12:00:00.100  4821  4821 E AndroidRuntime: FATAL EXCEPTION: main",
        "09-10 12:00:00.101  4821  4821 E AndroidRuntime: Process: com.example.foodapp, PID: 4821",
        "09-10 12:00:00.102  4821  4821 E AndroidRuntime: java.lang.NullPointerException: Attempt to invoke virtual method on a null object reference",
        "09-10 12:00:00.103  4821  4821 E AndroidRuntime: \tat com.example.foodapp.PaymentActivity.onCreate(PaymentActivity.kt:142)",
        "09-10 12:00:00.104  4821  4821 E AndroidRuntime: \tat android.app.Activity.performCreate(Activity.java:8000)",
    )

    @Test
    fun `emits nothing while a crash trace is still in progress`() {
        crashLines.dropLast(1).forEach { line ->
            assertNull(detector.feed(line))
        }
    }

    @Test
    fun `emits a captured crash once a line from another pid appears`() {
        crashLines.forEach { detector.feed(it) }
        val captured = detector.feed("09-10 12:00:00.200  512  512 I ActivityManager: Process com.example.foodapp (pid 4821) has died")

        assertNotNull(captured)
        assertEquals("NullPointerException", captured!!.crashReport.exceptionType.substringAfterLast('.'))
        assertEquals("com.example.foodapp", captured.packageName)
        assertEquals(1, captured.result.crashReports.size)
    }

    @Test
    fun `ignores unrelated log lines`() {
        assertNull(detector.feed("09-10 12:00:00.050  200  200 I System: ready"))
        assertNull(detector.feed("09-10 12:00:00.051  201  201 D SomeTag: doing work"))
    }
}
