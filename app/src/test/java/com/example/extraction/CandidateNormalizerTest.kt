package com.example.extraction

import com.example.core.model.CandidateSource
import com.example.core.policy.MediaRole
import com.example.data.model.MediaType
import com.example.extraction.model.YtDlpFormatDto
import com.example.extraction.model.YtDlpInfoDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateNormalizerTest {

    @Test
    fun testYtDlpCandidateEmitsPrimaryVideoAndSeparateThumbnail() {
        val dto = YtDlpInfoDto(
            id = "yt_123",
            title = "Test Video",
            thumbnail = "https://example.com/thumb.webp",
            formats = listOf(
                YtDlpFormatDto(
                    formatId = "1080p",
                    ext = "mp4",
                    filesize = 50000000L
                )
            )
        )

        val candidates = CandidateNormalizer.fromYtDlpInfo(dto, "https://example.com/video")
        assertEquals(2, candidates.size)

        val primary = candidates[0]
        assertEquals(CandidateSource.YTDLP, primary.source)
        assertEquals(MediaRole.PRIMARY_VIDEO, primary.role)
        assertEquals(MediaType.VIDEO, primary.mediaType)
        assertTrue(primary.isDownloadable)

        val thumb = candidates[1]
        assertEquals(CandidateSource.YTDLP, thumb.source)
        assertEquals(MediaRole.THUMBNAIL, thumb.role)
        assertEquals(MediaType.IMAGE, thumb.mediaType)
        assertFalse(thumb.isDownloadable)
    }

    @Test
    fun testOpenGraphImageAttachedToVideoPageIsNotDownloadablePrimary() {
        val ogCandidate = CandidateNormalizer.fromOpenGraphImage(
            imageUrl = "https://static.kick.com/thumb.webp",
            pageUrl = "https://kick.com/video/12345",
            isExplicitImageIntent = false
        )

        assertEquals(CandidateSource.OPENGRAPH, ogCandidate.source)
        assertEquals(MediaRole.THUMBNAIL, ogCandidate.role)
        assertEquals(MediaType.IMAGE, ogCandidate.mediaType)
        assertFalse("OpenGraph image on video page must NOT be downloadable as primary media", ogCandidate.isDownloadable)
    }

    @Test
    fun testOpenGraphImageWithExplicitImageIntentIsDownloadable() {
        val directImageCandidate = CandidateNormalizer.fromOpenGraphImage(
            imageUrl = "https://example.com/photo.jpg",
            pageUrl = "https://example.com/photo.jpg",
            isExplicitImageIntent = true
        )

        assertEquals(MediaRole.DIRECT_MEDIA, directImageCandidate.role)
        assertTrue(directImageCandidate.isDownloadable)
    }
}
