package com.example.engine

import com.example.data.model.CarouselItem
import com.example.data.model.ExtractedMedia
import com.example.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticClassifierTest {

    @Test
    fun testDirectImageClassification() {
        val url = "https://dfcdn.defacto.com.tr/838/G8354AX_26SP_NV86_01_02.jpg"
        val classification = SemanticClassifier.classify(url)
        assertEquals(MediaIntent.DIRECT_MEDIA, classification.intent)
        assertFalse(classification.isYtDlpEligible)
    }

    @Test
    fun testDirectVideoClassification() {
        val url = "https://example.com/videos/sample.mp4?token=abc"
        val classification = SemanticClassifier.classify(url)
        assertEquals(MediaIntent.DIRECT_MEDIA, classification.intent)
        assertFalse(classification.isYtDlpEligible)
    }

    @Test
    fun testTikTokVideoClassification() {
        val url = "https://www.tiktok.com/@user/video/71234567890"
        val classification = SemanticClassifier.classify(url)
        assertEquals(MediaIntent.PLATFORM_VIDEO, classification.intent)
        assertTrue(classification.isYtDlpEligible)

        val gate = YtDlpEligibilityGate.evaluate(url, classification, null)
        assertTrue(gate.isEligible)
    }

    @Test
    fun testTikTokPhotoClassification() {
        val url = "https://www.tiktok.com/@user/photo/7672451320091102484"
        val classification = SemanticClassifier.classify(url)
        assertEquals(MediaIntent.PLATFORM_CAROUSEL, classification.intent)
        assertFalse(classification.isYtDlpEligible)

        val gate = YtDlpEligibilityGate.evaluate(url, classification, null)
        assertFalse(gate.isEligible)
    }

    @Test
    fun testRedditVideoClassification() {
        val url = "https://v.redd.it/abc123xyz"
        val classification = SemanticClassifier.classify(url)
        assertEquals(MediaIntent.PLATFORM_VIDEO, classification.intent)
        assertTrue(classification.isYtDlpEligible)

        val gate = YtDlpEligibilityGate.evaluate(url, classification, null)
        assertTrue(gate.isEligible)
    }

    @Test
    fun testRedditGalleryClassification() {
        val url = "https://www.reddit.com/gallery/12345"
        val classification = SemanticClassifier.classify(url)
        assertEquals(MediaIntent.PLATFORM_CAROUSEL, classification.intent)
        assertFalse(classification.isYtDlpEligible)

        val gate = YtDlpEligibilityGate.evaluate(url, classification, null)
        assertFalse(gate.isEligible)
    }

    @Test
    fun testRedditImageHostClassification() {
        val url = "https://i.redd.it/example123.png"
        val classification = SemanticClassifier.classify(url)
        // Handled as direct media or platform image; in either case, NOT yt-dlp eligible
        assertFalse(classification.isYtDlpEligible)

        val gate = YtDlpEligibilityGate.evaluate(url, classification, null)
        assertFalse(gate.isEligible)
    }

    @Test
    fun testInstagramReelClassification() {
        val url = "https://www.instagram.com/reel/C123abc456/"
        val classification = SemanticClassifier.classify(url)
        assertEquals(MediaIntent.PLATFORM_VIDEO, classification.intent)
        assertTrue(classification.isYtDlpEligible)

        val gate = YtDlpEligibilityGate.evaluate(url, classification, null)
        assertTrue(gate.isEligible)
    }

    @Test
    fun testInstagramPostWhenPageMediaExtractedImage() {
        val url = "https://www.instagram.com/p/C123abc456/"
        val classification = SemanticClassifier.classify(url)
        
        // Simulating that PageMetadataExtractor extracted an Image
        val extractedImage = ExtractedMedia.Image(
            id = "test_img",
            title = "Test Image",
            webpageUrl = url,
            directDownloadUrl = "https://instagram.fcdn.net/image.jpg"
        )
        val gate = YtDlpEligibilityGate.evaluate(url, classification, extractedImage)
        assertFalse("yt-dlp must be blocked when an image was already extracted", gate.isEligible)
    }

    @Test
    fun testYouTubeClassification() {
        val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        val classification = SemanticClassifier.classify(url)
        assertEquals(MediaIntent.PLATFORM_VIDEO, classification.intent)
        assertTrue(classification.isYtDlpEligible)

        val gate = YtDlpEligibilityGate.evaluate(url, classification, null)
        assertTrue(gate.isEligible)
    }
}
