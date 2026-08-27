package com.example

import com.example.data.model.CarouselItem
import com.example.data.model.ExtractedMedia
import com.example.data.model.FormatInfo
import com.example.data.model.MediaCollection
import com.example.data.model.MediaKind
import com.example.data.model.MediaType
import com.example.data.model.SizeProvenance
import com.example.engine.DirectMediaInspector
import com.example.engine.EmbeddedExtractorEngine
import com.example.engine.PageMetadataExtractor
import com.example.engine.YtDlpProcessRunner
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalExtractionPhase7Test {

    @Test
    fun `test direct media inspector produces canonical MediaCollection`() = runBlocking {
        // Direct media inspector URL classification test
        val pair = DirectMediaInspector.classifyContentType("image/jpeg")
        assertNotNull(pair)
        assertEquals(MediaType.IMAGE, pair?.first)

        val audioPair = DirectMediaInspector.classifyContentType("audio/mp4")
        assertNotNull(audioPair)
        assertEquals(MediaType.AUDIO, audioPair?.first)

        val videoPair = DirectMediaInspector.classifyContentType("video/mp4")
        assertNotNull(videoPair)
        assertEquals(MediaType.VIDEO, videoPair?.first)
    }

    @Test
    fun `test ExtractedMedia Image to canonical MediaCollection`() {
        val extractedImage = ExtractedMedia.Image(
            id = "img_direct_01",
            title = "Sample Image",
            webpageUrl = "https://example.com/photo.jpg",
            directDownloadUrl = "https://example.com/photo.jpg",
            thumbnail = "https://example.com/photo.jpg",
            mimeType = "image/jpeg",
            width = 1920,
            height = 1080,
            fileSize = 1048576L,
            uploader = "Photographer"
        )

        val collection = extractedImage.toMediaCollection()
        assertTrue(collection.isSingleItem)
        assertEquals(MediaKind.IMAGE, collection.mediaKind)
        assertEquals(1, collection.items.size)

        val item = collection.items[0]
        assertEquals("Sample Image", item.title)
        assertEquals("https://example.com/photo.jpg", item.sourceUrl)
        assertEquals(MediaKind.IMAGE, item.mediaKind)
        assertEquals(1920, item.width)
        assertEquals(1080, item.height)
        assertEquals(1048576L, item.fileSize)
        assertEquals(SizeProvenance.EXACT, item.sizeProvenance)
        assertEquals(0, item.index)
        assertTrue(item.isSuccess)
    }

    @Test
    fun `test ExtractedMedia Carousel to canonical MediaCollection`() {
        val carouselItems = listOf(
            CarouselItem(
                id = "item_1",
                title = "Slide 1",
                mediaType = MediaType.IMAGE,
                sourceUrl = "https://example.com/slide1.jpg",
                thumbnail = "https://example.com/slide1.jpg",
                fileSize = 500000L
            ),
            CarouselItem(
                id = "item_2",
                title = "Slide 2",
                mediaType = MediaType.VIDEO,
                sourceUrl = "https://example.com/slide2.mp4",
                thumbnail = "https://example.com/slide2_thumb.jpg",
                fileSize = 2500000L
            )
        )

        val extractedCarousel = ExtractedMedia.Carousel(
            id = "carousel_ig_01",
            title = "Instagram Post",
            webpageUrl = "https://instagram.com/p/12345",
            uploader = "creator",
            thumbnail = "https://example.com/slide1.jpg",
            items = carouselItems
        )

        val collection = extractedCarousel.toMediaCollection()
        assertTrue(collection.isMultiItem)
        assertEquals(MediaKind.CAROUSEL, collection.mediaKind)
        assertEquals(2, collection.items.size)
        assertTrue(collection.isMixedCollection)

        val item1 = collection.items[0]
        assertEquals(MediaKind.IMAGE, item1.mediaKind)
        assertEquals("Slide 1", item1.title)
        assertEquals(0, item1.index)
        assertEquals(SizeProvenance.EXACT, item1.sizeProvenance)

        val item2 = collection.items[1]
        assertEquals(MediaKind.VIDEO, item2.mediaKind)
        assertEquals("Slide 2", item2.title)
        assertEquals(1, item2.index)
        assertEquals(SizeProvenance.EXACT, item2.sizeProvenance)
        assertEquals(1, item2.formats.size)
        assertEquals("carousel_video_1", item2.formats[0].formatId)
    }

    @Test
    fun `test yt-dlp JSON parser produces canonical MediaCollection for single video`() {
        val json = JSONObject().apply {
            put("id", "yt_vid_123")
            put("title", "Kotlin Coroutines Deep Dive")
            put("uploader", "Kotlin by JetBrains")
            put("duration", 1800)
            put("thumbnail", "https://i.ytimg.com/vi/yt_vid_123/hqdefault.jpg")
            put("extractor", "youtube")
            put("formats", JSONArray().apply {
                put(JSONObject().apply {
                    put("format_id", "137")
                    put("ext", "mp4")
                    put("height", 1080)
                    put("vcodec", "avc1.640028")
                    put("acodec", "none")
                    put("filesize", 150000000L)
                })
                put(JSONObject().apply {
                    put("format_id", "140")
                    put("ext", "m4a")
                    put("acodec", "mp4a.40.2")
                    put("vcodec", "none")
                    put("filesize", 25000000L)
                })
            })
        }

        val collection = YtDlpProcessRunner.parseYtDlpMediaCollection(json, "https://www.youtube.com/watch?v=yt_vid_123")
        assertTrue(collection.isSingleItem)
        assertEquals(MediaKind.VIDEO, collection.mediaKind)
        assertEquals("yt_vid_123", collection.id)
        assertEquals("Kotlin Coroutines Deep Dive", collection.title)
        assertEquals("Kotlin by JetBrains", collection.uploader)
        assertEquals("youtube", collection.extractorName)
        assertEquals(1, collection.items.size)

        val item = collection.items[0]
        assertEquals("Kotlin Coroutines Deep Dive", item.title)
        assertEquals(1800L, item.durationSeconds)
        assertEquals(1080, item.height)
        assertEquals(2, item.formats.size)
        assertEquals(SizeProvenance.EXACT, item.sizeProvenance)
        assertEquals(0, item.index)
        assertTrue(item.isSuccess)
    }

    @Test
    fun `test yt-dlp JSON parser produces canonical MediaCollection for playlist`() {
        val json = JSONObject().apply {
            put("_type", "playlist")
            put("id", "PL123456789")
            put("title", "Android Masterclass Playlist")
            put("uploader", "Android Developers")
            put("extractor", "youtube:tab")
            put("entries", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", "vid_1")
                    put("title", "Lesson 1: Jetpack Compose")
                    put("duration", 600)
                    put("thumbnail", "https://i.ytimg.com/vi/vid_1/default.jpg")
                })
                put(JSONObject().apply {
                    put("id", "vid_2")
                    put("title", "Lesson 2: Architecture Components")
                    put("duration", 900)
                    put("thumbnail", "https://i.ytimg.com/vi/vid_2/default.jpg")
                })
                put(JSONObject().apply {
                    put("id", "vid_3")
                    put("title", "Lesson 3: Room Database")
                    put("duration", 1200)
                    put("thumbnail", "https://i.ytimg.com/vi/vid_3/default.jpg")
                })
            })
        }

        val collection = YtDlpProcessRunner.parseYtDlpMediaCollection(json, "https://www.youtube.com/playlist?list=PL123456789")
        assertTrue(collection.isMultiItem)
        assertTrue(collection.isPlaylist)
        assertEquals(MediaKind.PLAYLIST, collection.mediaKind)
        assertEquals("PL123456789", collection.id)
        assertEquals("Android Masterclass Playlist", collection.title)
        assertEquals(3, collection.items.size)

        assertEquals("Lesson 1: Jetpack Compose", collection.items[0].title)
        assertEquals("https://www.youtube.com/watch?v=vid_1", collection.items[0].sourceUrl)
        assertEquals(600L, collection.items[0].durationSeconds)
        assertEquals(0, collection.items[0].index)

        assertEquals("Lesson 2: Architecture Components", collection.items[1].title)
        assertEquals("https://www.youtube.com/watch?v=vid_2", collection.items[1].sourceUrl)
        assertEquals(900L, collection.items[1].durationSeconds)
        assertEquals(1, collection.items[1].index)

        assertEquals("Lesson 3: Room Database", collection.items[2].title)
        assertEquals("https://www.youtube.com/watch?v=vid_3", collection.items[2].sourceUrl)
        assertEquals(1200L, collection.items[2].durationSeconds)
        assertEquals(2, collection.items[2].index)
    }

    @Test
    fun `test yt-dlp audio detection size provenance derived from duration`() {
        val json = JSONObject().apply {
            put("id", "sc_track_99")
            put("title", "Ambient Soundscape")
            put("uploader", "Sound Artist")
            put("duration", 300)
            put("extractor", "soundcloud")
            put("formats", JSONArray().apply {
                put(JSONObject().apply {
                    put("format_id", "audio_only")
                    put("ext", "mp3")
                    put("acodec", "mp3")
                    put("vcodec", "none")
                })
            })
        }

        val collection = YtDlpProcessRunner.parseYtDlpMediaCollection(json, "https://soundcloud.com/artist/track")
        assertTrue(collection.isSingleItem)
        assertEquals(MediaKind.AUDIO, collection.mediaKind)
        assertEquals(1, collection.items.size)

        val item = collection.items[0]
        assertEquals(MediaKind.AUDIO, item.mediaKind)
        assertEquals(SizeProvenance.DERIVED, item.sizeProvenance)
        assertEquals(300L * 16000L, item.fileSize)
    }

    @Test
    fun `test EmbeddedExtractor produces direct MediaCollection without legacy conversion`() = runBlocking {
        val directUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        val isDirect = EmbeddedExtractorEngine.isDirectMediaUrl(directUrl)
        assertTrue(isDirect)

        val audioDirect = "https://example.com/audio/podcast.mp3"
        assertTrue(EmbeddedExtractorEngine.isDirectMediaUrl(audioDirect))
    }

    @Test
    fun `test Instagram carousel partial failure preservation`() {
        val carouselItems = listOf(
            CarouselItem(
                id = "ig_slide_1",
                title = "Photo #1",
                mediaType = MediaType.IMAGE,
                sourceUrl = "https://instagram.com/photo1.jpg",
                thumbnail = "https://instagram.com/photo1.jpg"
            ),
            CarouselItem(
                id = "ig_slide_2",
                title = "Photo #2",
                mediaType = MediaType.IMAGE,
                sourceUrl = "",
                thumbnail = "",
                isSelected = false,
                errorMessage = "Instagram media stream unavailable for slide #2"
            ),
            CarouselItem(
                id = "ig_slide_3",
                title = "Video #3",
                mediaType = MediaType.VIDEO,
                sourceUrl = "https://instagram.com/video3.mp4",
                thumbnail = "https://instagram.com/thumb3.jpg"
            )
        )

        val extracted = ExtractedMedia.Carousel(
            id = "ig_car_123",
            title = "Test IG Carousel",
            webpageUrl = "https://instagram.com/p/test",
            items = carouselItems
        )

        val collection = extracted.toMediaCollection()
        assertEquals(3, collection.items.size)
        assertEquals(MediaKind.CAROUSEL, collection.mediaKind)

        // Item 0: Success
        val item0 = collection.items[0]
        assertEquals(0, item0.index)
        assertTrue(item0.isSuccess)
        assertFalse(item0.isFailed)
        assertTrue(item0.isSelected)

        // Item 1: Failed
        val item1 = collection.items[1]
        assertEquals(1, item1.index)
        assertFalse(item1.isSuccess)
        assertTrue(item1.isFailed)
        assertFalse(item1.isSelected)
        assertEquals("Instagram media stream unavailable for slide #2", item1.errorMessage)
        assertEquals(SizeProvenance.UNKNOWN, item1.sizeProvenance)

        // Item 2: Success
        val item2 = collection.items[2]
        assertEquals(2, item2.index)
        assertTrue(item2.isSuccess)
        assertFalse(item2.isFailed)
        assertTrue(item2.isSelected)

        // Collection filtering
        assertEquals(2, collection.validItems.size)
        assertEquals(1, collection.failedItems.size)
        assertEquals(2, collection.selectedItems.size)

        // Download planning excludes failed item
        val plan = com.example.download.DownloadPlanner.planDownloads(
            collection,
            com.example.download.DownloadPlanningOptions()
        )
        assertEquals(2, plan.requests.size)
        assertEquals("Photo #1", plan.requests[0].title)
        assertEquals("Video #3", plan.requests[1].title)
    }

    @Test
    fun `test TikTok photo carousel partial failure preservation`() {
        val carouselItems = listOf(
            CarouselItem(
                id = "tt_slide_1",
                title = "Photo #1",
                mediaType = MediaType.IMAGE,
                sourceUrl = "https://p16-sign.tiktok.com/photo1.jpeg",
                thumbnail = "https://p16-sign.tiktok.com/photo1.jpeg"
            ),
            CarouselItem(
                id = "tt_slide_2",
                title = "Photo #2",
                mediaType = MediaType.IMAGE,
                sourceUrl = "",
                thumbnail = "",
                isSelected = false,
                errorMessage = "TikTok photo stream unavailable for photo #2"
            )
        )

        val extracted = ExtractedMedia.Carousel(
            id = "tt_car_456",
            title = "TikTok Photo Album",
            webpageUrl = "https://www.tiktok.com/@user/photo/123456",
            items = carouselItems
        )

        val collection = extracted.toMediaCollection()
        assertEquals(2, collection.items.size)
        assertEquals(0, collection.items[0].index)
        assertTrue(collection.items[0].isSuccess)

        assertEquals(1, collection.items[1].index)
        assertFalse(collection.items[1].isSuccess)
        assertEquals(SizeProvenance.UNKNOWN, collection.items[1].sizeProvenance)
        assertEquals("TikTok photo stream unavailable for photo #2", collection.items[1].errorMessage)
    }

    @Test
    fun `test Reddit gallery partial failure preservation`() {
        val carouselItems = listOf(
            CarouselItem(
                id = "gallery_item_1",
                title = "Image #1",
                mediaType = MediaType.IMAGE,
                sourceUrl = "https://i.redd.it/valid1.jpg",
                thumbnail = "https://i.redd.it/valid1.jpg"
            ),
            CarouselItem(
                id = "gallery_item_2",
                title = "Image #2",
                mediaType = MediaType.IMAGE,
                sourceUrl = "",
                thumbnail = "",
                isSelected = false,
                errorMessage = "Reddit image metadata unavailable for gallery item #2 (gallery_item_2)"
            ),
            CarouselItem(
                id = "gallery_item_3",
                title = "Image #3",
                mediaType = MediaType.IMAGE,
                sourceUrl = "https://i.redd.it/valid3.jpg",
                thumbnail = "https://i.redd.it/valid3.jpg"
            )
        )

        val extracted = ExtractedMedia.Carousel(
            id = "reddit_gal_789",
            title = "Reddit Art Gallery",
            webpageUrl = "https://reddit.com/r/pics/comments/abc/gallery",
            items = carouselItems
        )

        val collection = extracted.toMediaCollection()
        assertEquals(3, collection.items.size)

        assertEquals(0, collection.items[0].index)
        assertTrue(collection.items[0].isSuccess)

        assertEquals(1, collection.items[1].index)
        assertFalse(collection.items[1].isSuccess)
        assertEquals("Reddit image metadata unavailable for gallery item #2 (gallery_item_2)", collection.items[1].errorMessage)

        assertEquals(2, collection.items[2].index)
        assertTrue(collection.items[2].isSuccess)
    }

    @Test
    fun `test YouTube playlist partial failure preservation for deleted or private entries`() {
        val json = JSONObject().apply {
            put("_type", "playlist")
            put("id", "PL_PARTIAL_TEST")
            put("title", "Mixed Status Playlist")
            put("uploader", "Test Creator")
            put("extractor", "youtube:tab")
            put("entries", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", "valid_vid_1")
                    put("title", "Intro Video")
                    put("duration", 300)
                    put("thumbnail", "https://i.ytimg.com/vi/valid_vid_1/default.jpg")
                })
                put(JSONObject().apply {
                    put("id", "deleted_vid_2")
                    put("title", "[Deleted video]")
                    put("duration", 0)
                })
                put(JSONObject().apply {
                    put("id", "valid_vid_3")
                    put("title", "Chapter 2 Video")
                    put("duration", 450)
                    put("thumbnail", "https://i.ytimg.com/vi/valid_vid_3/default.jpg")
                })
                put(JSONObject().apply {
                    put("id", "private_vid_4")
                    put("title", "[Private video]")
                    put("duration", 0)
                })
                put(JSONObject().apply {
                    put("id", "valid_vid_5")
                    put("title", "Conclusion Video")
                    put("duration", 600)
                    put("thumbnail", "https://i.ytimg.com/vi/valid_vid_5/default.jpg")
                })
            })
        }

        val collection = YtDlpProcessRunner.parseYtDlpMediaCollection(json, "https://www.youtube.com/playlist?list=PL_PARTIAL_TEST")
        assertEquals(5, collection.items.size)
        assertEquals(MediaKind.PLAYLIST, collection.mediaKind)

        // 0: Valid
        assertEquals(0, collection.items[0].index)
        assertEquals("Intro Video", collection.items[0].title)
        assertTrue(collection.items[0].isSuccess)
        assertTrue(collection.items[0].isSelected)

        // 1: Deleted
        assertEquals(1, collection.items[1].index)
        assertEquals("[Deleted video]", collection.items[1].title)
        assertFalse(collection.items[1].isSuccess)
        assertFalse(collection.items[1].isSelected)
        assertEquals(SizeProvenance.UNKNOWN, collection.items[1].sizeProvenance)

        // 2: Valid
        assertEquals(2, collection.items[2].index)
        assertEquals("Chapter 2 Video", collection.items[2].title)
        assertTrue(collection.items[2].isSuccess)
        assertTrue(collection.items[2].isSelected)

        // 3: Private
        assertEquals(3, collection.items[3].index)
        assertEquals("[Private video]", collection.items[3].title)
        assertFalse(collection.items[3].isSuccess)
        assertFalse(collection.items[3].isSelected)

        // 4: Valid
        assertEquals(4, collection.items[4].index)
        assertEquals("Conclusion Video", collection.items[4].title)
        assertTrue(collection.items[4].isSuccess)
        assertTrue(collection.items[4].isSelected)

        // Planner verification
        val plan = com.example.download.DownloadPlanner.planDownloads(
            collection,
            com.example.download.DownloadPlanningOptions()
        )
        assertEquals(3, plan.requests.size)
        assertEquals("Intro Video", plan.requests[0].title)
        assertEquals("Chapter 2 Video", plan.requests[1].title)
        assertEquals("Conclusion Video", plan.requests[2].title)
    }
}
