package com.example.extraction

import com.example.core.model.CandidateSource
import com.example.core.model.MediaCandidate
import com.example.core.model.MediaFormat
import com.example.core.model.MediaSize
import com.example.core.policy.MediaRole
import com.example.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrimaryResourceResolverTest {

    @Test
    fun testKickYtDlpVideoWinsOverOpenGraphThumbnail() {
        val ytdlpVideo = MediaCandidate(
            id = "kick_vod_123",
            source = CandidateSource.YTDLP,
            role = MediaRole.PRIMARY_VIDEO,
            mediaType = MediaType.VIDEO,
            url = "https://kick.com/video/123",
            pageUrl = "https://kick.com/video/123",
            title = "Awesome Stream",
            formats = listOf(
                MediaFormat(
                    formatId = "1080p",
                    ext = "mp4",
                    size = MediaSize.Exact(100000000L)
                )
            )
        )

        val ogThumbnail = MediaCandidate(
            id = "kick_thumb_og",
            source = CandidateSource.OPENGRAPH,
            role = MediaRole.THUMBNAIL,
            mediaType = MediaType.IMAGE,
            url = "https://static.kick.com/thumbs/123.webp",
            pageUrl = "https://kick.com/video/123",
            title = "Awesome Stream Thumbnail",
            isDownloadable = false
        )

        val decision = PrimaryResourceResolver.resolve(
            candidates = listOf(ogThumbnail, ytdlpVideo),
            sourceUrl = "https://kick.com/video/123",
            canonicalUrl = "https://kick.com/video/123",
            platform = "kick",
            intent = "video"
        )

        assertTrue(decision.isResolved)
        assertNotNull(decision.primaryCandidate)
        assertEquals(MediaRole.PRIMARY_VIDEO, decision.primaryCandidate?.role)
        assertEquals(MediaType.VIDEO, decision.primaryCandidate?.mediaType)
        assertEquals("kick_vod_123", decision.primaryCandidate?.id)

        assertNotNull(decision.thumbnailCandidate)
        assertEquals("https://static.kick.com/thumbs/123.webp", decision.thumbnailCandidate?.url)
    }

    @Test
    fun testKickThumbnailIsNotPrimary() {
        val ogThumbnail = MediaCandidate(
            id = "kick_thumb_og",
            source = CandidateSource.OPENGRAPH,
            role = MediaRole.THUMBNAIL,
            mediaType = MediaType.IMAGE,
            url = "https://static.kick.com/thumbs/123.webp",
            pageUrl = "https://kick.com/video/123",
            isDownloadable = false
        )

        val decision = PrimaryResourceResolver.resolve(
            candidates = listOf(ogThumbnail),
            sourceUrl = "https://kick.com/video/123",
            canonicalUrl = "https://kick.com/video/123",
            platform = "kick",
            intent = "video"
        )

        assertFalse("Thumbnail alone must NOT resolve as primary video", decision.isResolved)
        assertNull(decision.primaryCandidate)
        assertNotNull(decision.thumbnailCandidate)
    }

    @Test
    fun testKickYtDlpFailureDoesNotBecomeImage() {
        val ogThumbnail = MediaCandidate(
            id = "og_thumb",
            source = CandidateSource.OPENGRAPH,
            role = MediaRole.THUMBNAIL,
            mediaType = MediaType.IMAGE,
            url = "https://static.kick.com/thumb.webp",
            pageUrl = "https://kick.com/video/999",
            isDownloadable = false
        )

        val decision = PrimaryResourceResolver.resolve(
            candidates = listOf(ogThumbnail),
            sourceUrl = "https://kick.com/video/999",
            canonicalUrl = "https://kick.com/video/999",
            platform = "kick",
            intent = "video"
        )

        assertFalse(decision.isResolved)
        assertNull(decision.primaryCandidate)
        assertTrue(decision.reason.contains("video platform", ignoreCase = true) || decision.reason.contains("playable", ignoreCase = true))
    }

    @Test
    fun testKickDirectImageStillWorks() {
        val directImage = MediaCandidate(
            id = "direct_img",
            source = CandidateSource.DIRECT_HTTP,
            role = MediaRole.DIRECT_MEDIA,
            mediaType = MediaType.IMAGE,
            url = "https://static.kick.com/uploads/photo.jpg",
            pageUrl = "https://static.kick.com/uploads/photo.jpg",
            isDownloadable = true
        )

        val decision = PrimaryResourceResolver.resolve(
            candidates = listOf(directImage),
            sourceUrl = "https://static.kick.com/uploads/photo.jpg",
            canonicalUrl = "https://static.kick.com/uploads/photo.jpg",
            platform = "kick",
            intent = "image"
        )

        assertTrue(decision.isResolved)
        assertEquals(MediaType.IMAGE, decision.primaryCandidate?.mediaType)
        assertEquals("https://static.kick.com/uploads/photo.jpg", decision.primaryCandidate?.url)
    }

    @Test
    fun testCandidateOrderDoesNotChangeKickResult() {
        val ytdlpVideo = MediaCandidate(
            id = "kick_v",
            source = CandidateSource.YTDLP,
            role = MediaRole.PRIMARY_VIDEO,
            mediaType = MediaType.VIDEO,
            url = "https://kick.com/video/1",
            pageUrl = "https://kick.com/video/1"
        )
        val ogThumbnail = MediaCandidate(
            id = "kick_t",
            source = CandidateSource.OPENGRAPH,
            role = MediaRole.THUMBNAIL,
            mediaType = MediaType.IMAGE,
            url = "https://static.kick.com/thumb.webp",
            pageUrl = "https://kick.com/video/1"
        )

        val decision1 = PrimaryResourceResolver.resolve(
            candidates = listOf(ytdlpVideo, ogThumbnail),
            sourceUrl = "https://kick.com/video/1",
            canonicalUrl = "https://kick.com/video/1",
            platform = "kick",
            intent = "video"
        )

        val decision2 = PrimaryResourceResolver.resolve(
            candidates = listOf(ogThumbnail, ytdlpVideo),
            sourceUrl = "https://kick.com/video/1",
            canonicalUrl = "https://kick.com/video/1",
            platform = "kick",
            intent = "video"
        )

        assertEquals(decision1.primaryCandidate?.id, decision2.primaryCandidate?.id)
        assertEquals(decision1.thumbnailCandidate?.url, decision2.thumbnailCandidate?.url)
    }
}
