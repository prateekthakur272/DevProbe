package dev.prateekthakur.devprobe.data.apk

import dev.prateekthakur.devprobe.domain.model.SecretType
import dev.prateekthakur.devprobe.domain.model.StringSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretPatternMatcherTest {

    @Test
    fun `detects an AWS access key`() {
        val result = SecretPatternMatcher.scan("aws_key = AKIAIOSFODNN7EXAMPLE", StringSource.DEX)
        assertTrue(result.any { it.type == SecretType.AWS_KEY })
    }

    @Test
    fun `detects a google api key`() {
        val result = SecretPatternMatcher.scan("AIzaSyD-9tSrke72PouQMnMX-a7eZSW0jkFMBWY", StringSource.RESOURCES)
        assertTrue(result.any { it.type == SecretType.GOOGLE_API_KEY })
    }

    @Test
    fun `detects a jwt`() {
        val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dQw4w9WgXcQ_examplesignature"
        val result = SecretPatternMatcher.scan(jwt, StringSource.DEX)
        assertTrue(result.any { it.type == SecretType.JWT })
    }

    @Test
    fun `detects a pem private key header`() {
        val result = SecretPatternMatcher.scan("-----BEGIN RSA PRIVATE KEY-----", StringSource.NATIVE_LIB)
        assertTrue(result.any { it.type == SecretType.PRIVATE_KEY })
    }

    @Test
    fun `detects a generic secret assignment`() {
        val result = SecretPatternMatcher.scan("api_key: \"sk_live_51H8xyzABCDEF12345\"", StringSource.DEX)
        assertTrue(result.any { it.type == SecretType.GENERIC_SECRET_ASSIGNMENT })
    }

    @Test
    fun `detects a url and an email`() {
        val urlResult = SecretPatternMatcher.scan("https://api.example.com/v1/login", StringSource.RESOURCES)
        assertEquals(SecretType.URL, urlResult.first().type)

        val emailResult = SecretPatternMatcher.scan("contact: support@example.com", StringSource.RESOURCES)
        assertTrue(emailResult.any { it.type == SecretType.EMAIL })
    }

    @Test
    fun `does not flag an ordinary short string`() {
        val result = SecretPatternMatcher.scan("MainActivity", StringSource.DEX)
        assertTrue(result.isEmpty())
    }
}
