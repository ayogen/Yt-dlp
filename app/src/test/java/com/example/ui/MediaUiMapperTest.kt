package com.example.ui

import com.example.core.model.CanonicalMediaResult
import com.example.core.model.CanonicalMetadata
import com.example.core.model.MediaFormat
import com.example.core.model.MediaSize
import com.example.ui.model.MediaUiMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaUiMapperTest {

    @Test
    fun testCanonicalResultMapsToUiModelAccurately() {
        val canonical = CanonicalMediaResult(
            sourceUrl = "https://www.dailymotion.com/video/x123",
            canonicalUrl = "https://www.dailymotion.com/video/x123",
            platform = "dailymotion",
            intent = "video",
            metadata = CanonicalMetadata(
                title = "Dailymotion Sample",
                uploader = "DM Creator",
                durationSeconds = 180,
                thumbnail = "https://example.com/thumb.jpg"
            ),
            formats = listOf(
                MediaFormat(
                    formatId = "1080p",
                    ext = "mp4",
                    resolution = "1080p",
                    height = 1080,
                    size = MediaSize.Exact(50000000L),
                    vcodec = "h264",
                    acodec = "aac"
                ),
                MediaFormat(
                    formatId = "720p",
                    ext = "mp4",
                    resolution = "720p",
                    height = 720,
                    size = MediaSize.Approximate(25000000L),
                    vcodec = "h264",
                    acodec = "aac"
                ),
                MediaFormat(
                    formatId = "unknown-fmt",
                    ext = "mp4",
                    resolution = "480p",
                    height = 480,
                    size = MediaSize.Unknown(),
                    vcodec = "h264",
                    acodec = "aac"
                )
            )
        )

        val uiModel = MediaUiMapper.mapToUiModel(canonical)

        assertEquals("Dailymotion Sample", uiModel.title)
        assertEquals("DM Creator", uiModel.uploader)
        assertEquals("3:00", uiModel.durationText)
        assertEquals(3, uiModel.formatOptions.size)

        // Selected Format 1 (Exact)
        val opt1 = uiModel.formatOptions[0]
        assertEquals("1080p", opt1.displayResolution)
        assertEquals("47.68 MB", opt1.displaySize)
        assertEquals(50000000L, opt1.filesize)

        // Selected Format 2 (Approx)
        val opt2 = uiModel.formatOptions[1]
        assertEquals("720p", opt2.displayResolution)
        assertTrue(opt2.displaySize.startsWith("~"))
        assertEquals(25000000L, opt2.filesizeApprox)

        // Selected Format 3 (Unknown)
        val opt3 = uiModel.formatOptions[2]
        assertEquals("Unknown size", opt3.displaySize)
    }
}
