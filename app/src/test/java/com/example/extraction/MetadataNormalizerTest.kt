package com.example.extraction

import com.example.core.model.MediaSize
import com.example.extraction.model.YtDlpFormatDto
import com.example.extraction.model.YtDlpInfoDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataNormalizerTest {

    @Test
    fun testDailymotionExactFilesizeSurvivesNormalization() {
        val dto = YtDlpFormatDto(
            formatId = "dm-1080",
            ext = "mp4",
            height = 1080,
            filesize = 65000000L,
            vcodec = "h264",
            acodec = "aac"
        )

        val normalized = MetadataNormalizer.normalizeFormat(dto)

        assertTrue(normalized.size is MediaSize.Exact)
        assertEquals(65000000L, (normalized.size as MediaSize.Exact).bytes)
        assertEquals("62.00 MB", (normalized.size as MediaSize.Exact).bytes.let { normalized.size.displayString })
    }

    @Test
    fun testDailymotionApproximateFilesizeSurvivesNormalization() {
        val dto = YtDlpFormatDto(
            formatId = "dm-720",
            ext = "mp4",
            height = 720,
            filesizeApprox = 30000000L,
            vcodec = "h264",
            acodec = "aac"
        )

        val normalized = MetadataNormalizer.normalizeFormat(dto)

        assertTrue(normalized.size is MediaSize.Approximate)
        assertEquals(30000000L, (normalized.size as MediaSize.Approximate).bytes)
        assertTrue(normalized.size.displayString.startsWith("~"))
    }

    @Test
    fun testMissingSizeBecomesExplicitUnknown() {
        val dto = YtDlpFormatDto(
            formatId = "unknown-fmt",
            ext = "mp4",
            filesize = null,
            filesizeApprox = null
        )

        val normalized = MetadataNormalizer.normalizeFormat(dto)

        assertTrue(normalized.size is MediaSize.Unknown)
        assertEquals("Unknown size", normalized.size.displayString)
    }

    @Test
    fun testVideoOnlyAudioOnlyFormatsRemainDistinct() {
        val videoOnly = MetadataNormalizer.normalizeFormat(
            YtDlpFormatDto(
                formatId = "v1",
                vcodec = "avc1",
                acodec = "none"
            )
        )
        val audioOnly = MetadataNormalizer.normalizeFormat(
            YtDlpFormatDto(
                formatId = "a1",
                vcodec = "none",
                acodec = "mp4a"
            )
        )

        assertTrue(videoOnly.isVideoOnly)
        assertFalse(videoOnly.isAudioOnly)

        assertTrue(audioOnly.isAudioOnly)
        assertFalse(audioOnly.isVideoOnly)
    }
}
