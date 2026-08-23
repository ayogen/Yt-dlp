package com.example.extraction

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpJsonParserTest {

    @Test
    fun testParseVideoWithExactAndApproxFilesizes() {
        val json = JSONObject().apply {
            put("id", "dm_12345")
            put("title", "Dailymotion Sample Video")
            put("uploader", "DailymotionUser")
            put("duration", 150)
            put("extractor", "dailymotion")
            put("filesize", 80000000L)
            put("formats", JSONArray().apply {
                put(JSONObject().apply {
                    put("format_id", "http-1080")
                    put("ext", "mp4")
                    put("height", 1080)
                    put("filesize", 45000000L)
                    put("vcodec", "h264")
                    put("acodec", "aac")
                })
                put(JSONObject().apply {
                    put("format_id", "http-720")
                    put("ext", "mp4")
                    put("height", 720)
                    put("filesize_approx", 22000000L)
                    put("vcodec", "h264")
                    put("acodec", "aac")
                })
                put(JSONObject().apply {
                    put("format_id", "audio-only")
                    put("ext", "m4a")
                    put("vcodec", "none")
                    put("acodec", "mp4a.40.2")
                    put("abr", 128.0)
                })
            })
        }

        val dto = YtDlpJsonParser.parse(json, "https://www.dailymotion.com/video/x12345")

        assertEquals("dm_12345", dto.id)
        assertEquals("Dailymotion Sample Video", dto.title)
        assertEquals("DailymotionUser", dto.uploader)
        assertEquals(150L, dto.duration)
        assertEquals("dailymotion", dto.extractor)
        assertEquals(80000000L, dto.filesize)
        assertEquals(3, dto.formats.size)

        // Format 1: Exact size
        val f1 = dto.formats[0]
        assertEquals("http-1080", f1.formatId)
        assertEquals(1080, f1.height)
        assertEquals(45000000L, f1.filesize)
        assertNull(f1.filesizeApprox)

        // Format 2: Approx size
        val f2 = dto.formats[1]
        assertEquals("http-720", f2.formatId)
        assertEquals(720, f2.height)
        assertNull(f2.filesize)
        assertEquals(22000000L, f2.filesizeApprox)

        // Format 3: No size fields
        val f3 = dto.formats[2]
        assertEquals("audio-only", f3.formatId)
        assertNull(f3.filesize)
        assertNull(f3.filesizeApprox)
    }

    @Test
    fun testParseHandlesNullAndMissingFieldsGracefully() {
        val minimalJson = JSONObject().apply {
            put("id", "min_1")
            put("title", "Minimal Video")
            put("filesize", JSONObject.NULL)
            put("duration", JSONObject.NULL)
        }

        val dto = YtDlpJsonParser.parse(minimalJson, "https://example.com/video")
        assertEquals("min_1", dto.id)
        assertEquals("Minimal Video", dto.title)
        assertNull(dto.filesize)
        assertEquals(0L, dto.duration)
        assertTrue(dto.formats.isEmpty())
        assertFalse(dto.isPlaylist)
    }
}
