package dev.prateekthakur.devprobe.data.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StackTraceParserTest {

    private val parser = StackTraceParser()

    private val sampleTrace = """
        FATAL EXCEPTION: main
        Process: com.example.foodapp, PID: 4821
        java.lang.NullPointerException: Attempt to invoke virtual method on a null object reference
            at com.example.foodapp.PaymentActivity.onCreate(PaymentActivity.kt:142)
            at android.app.Activity.performCreate(Activity.java:8000)
            at androidx.core.app.ActivityCompat.recreate(ActivityCompat.java:100)
        Caused by: java.lang.IllegalStateException: ViewModel not attached
            at com.example.foodapp.PaymentViewModel.requireState(PaymentViewModel.kt:40)
    """.trimIndent()

    @Test
    fun `parses exception type message and thread`() {
        val result = parser.parse(sampleTrace, "com.example.foodapp")
        assertNotNull(result)
        assertEquals("NullPointerException", result!!.exceptionType.substringAfterLast('.'))
        assertTrue(result.message!!.contains("null object reference"))
        assertEquals("main", result.thread)
    }

    @Test
    fun `identifies first application frame`() {
        val result = parser.parse(sampleTrace, "com.example.foodapp")!!
        val appFrame = result.frames.firstOrNull { it.isAppFrame }
        assertNotNull(appFrame)
        assertEquals("com.example.foodapp.PaymentActivity", appFrame!!.declaringClass)
        assertEquals(142, appFrame.line)
    }

    @Test
    fun `parses caused-by chain`() {
        val result = parser.parse(sampleTrace, "com.example.foodapp")!!
        assertNotNull(result.causedBy)
        assertEquals("IllegalStateException", result.causedBy!!.exceptionType.substringAfterLast('.'))
    }

    @Test
    fun `frames outside app package are not marked as app frames`() {
        val result = parser.parse(sampleTrace, "com.example.foodapp")!!
        val frameworkFrame = result.frames.first { it.declaringClass.startsWith("android.") }
        assertTrue(!frameworkFrame.isAppFrame)
    }
}
