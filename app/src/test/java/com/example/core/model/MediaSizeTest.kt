package com.example.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSizeTest {

    @Test
    fun testMediaSizeExact() {
        val size = MediaSize.fromBytes(filesize = 52428800L, filesizeApprox = null)
        assertTrue(size is MediaSize.Exact)
        assertEquals(52428800L, size.bytesOrNull)
        assertFalse(size.isApproximate)
        assertTrue(size.isKnown)
        assertEquals("50.00 MB", size.displayString)
    }

    @Test
    fun testMediaSizeApproximate() {
        val size = MediaSize.fromBytes(filesize = null, filesizeApprox = 10485760L)
        assertTrue(size is MediaSize.Approximate)
        assertEquals(10485760L, size.bytesOrNull)
        assertTrue(size.isApproximate)
        assertTrue(size.isKnown)
        assertEquals("~10.00 MB", size.displayString)
    }

    @Test
    fun testMediaSizeHttpContentLength() {
        val size = MediaSize.fromBytes(filesize = null, filesizeApprox = null, httpContentLength = 2097152L)
        assertTrue(size is MediaSize.HttpContentLength)
        assertEquals(2097152L, size.bytesOrNull)
        assertFalse(size.isApproximate)
        assertTrue(size.isKnown)
        assertEquals("2.00 MB", size.displayString)
    }

    @Test
    fun testMediaSizeUnknown() {
        val size = MediaSize.fromBytes(filesize = null, filesizeApprox = null, httpContentLength = null)
        assertTrue(size is MediaSize.Unknown)
        assertNull(size.bytesOrNull)
        assertFalse(size.isApproximate)
        assertFalse(size.isKnown)
        assertEquals("Unknown size", size.displayString)
    }
}
