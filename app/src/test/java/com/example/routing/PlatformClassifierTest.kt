package com.example.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformClassifierTest {

    @Test
    fun testKickVODClassifiesAsVideo() {
        val match = PlatformClassifier.classify("https://kick.com/video/abcdef123")
        assertEquals("kick", match.platformId)
        assertEquals("video", match.intent)
        assertTrue(match.isYtDlpEligible)
    }

    @Test
    fun testKickVODRoutesYtDlpBeforeGenericImage() {
        val decision = RoutingPlanner.plan("https://kick.com/video/abcdef123")
        assertEquals("kick", decision.platform)
        assertEquals("video", decision.intent)
        assertTrue(decision.allowYtDlp)
        assertFalse(decision.allowGenericImageFallback)
        assertEquals("YTDLP", decision.strategyOrder.first())
    }

    @Test
    fun testDailymotionIsYtDlpEligible() {
        val match = PlatformClassifier.classify("https://www.dailymotion.com/video/x12345")
        assertEquals("dailymotion", match.platformId)
        assertEquals("video", match.intent)
        assertTrue(match.isYtDlpEligible)

        val shortMatch = PlatformClassifier.classify("https://dai.ly/x12345")
        assertEquals("dailymotion", shortMatch.platformId)
        assertTrue(shortMatch.isYtDlpEligible)
    }

    @Test
    fun testUnrelatedHostDoesNotMatchKick() {
        val match = PlatformClassifier.classify("https://notkick.com/video/123")
        assertEquals("generic", match.platformId)
    }

    @Test
    fun testYouTubePlaylistsAndShorts() {
        val playlist = PlatformClassifier.classify("https://www.youtube.com/playlist?list=PL12345")
        assertEquals("youtube", playlist.platformId)
        assertEquals("playlist", playlist.intent)

        val shorts = PlatformClassifier.classify("https://www.youtube.com/shorts/abcd123")
        assertEquals("youtube", shorts.platformId)
        assertEquals("video", shorts.intent)
    }

    @Test
    fun testTikTokVideoAndPhoto() {
        val video = PlatformClassifier.classify("https://www.tiktok.com/@user/video/123456789")
        assertEquals("tiktok", video.platformId)
        assertEquals("video", video.intent)

        val photo = PlatformClassifier.classify("https://www.tiktok.com/@user/photo/123456789")
        assertEquals("tiktok", photo.platformId)
        assertEquals("carousel", photo.intent)
    }
}
