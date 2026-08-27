package com.example

import com.example.data.model.FormatInfo
import com.example.data.model.MediaCollection
import com.example.data.model.MediaItem
import com.example.data.model.MediaKind
import com.example.data.model.MediaMetadata
import com.example.data.model.MediaType
import com.example.data.model.OutputContainer
import com.example.data.model.PlaylistEntry
import com.example.data.model.SizeProvenance
import com.example.download.DownloadPlanner
import com.example.download.DownloadPlanningOptions
import com.example.ui.home.MediaUiMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalCollectionPhase6Test {

    @Test
    fun `test single image to MediaCollection to AnalysisUiModel mapping`() {
        val directImageMeta = MediaMetadata(
            id = "img_123",
            title = "Nature Landscape",
            webpageUrl = "https://example.com/nature.jpg",
            directDownloadUrl = "https://example.com/nature.jpg",
            thumbnail = "https://example.com/nature.jpg",
            mediaType = MediaType.IMAGE,
            width = 1920,
            height = 1080,
            fileSize = 2500000L,
            extractorName = "DirectImage"
        )

        val collection = MediaCollection.fromMediaMetadata(directImageMeta)
        assertTrue(collection.isSingleItem)
        assertEquals(MediaKind.IMAGE, collection.mediaKind)
        assertEquals(1, collection.items.size)
        assertEquals(SizeProvenance.EXACT, collection.items[0].sizeProvenance)

        val uiModel = MediaUiMapper.mapCollectionToUiModel(collection)
        assertTrue(uiModel.isSingleItem)
        assertFalse(uiModel.isMultiItem)
        assertEquals(MediaKind.IMAGE, uiModel.mediaKind)
        assertEquals(1, uiModel.totalCount)
        assertEquals(1, uiModel.selectedCount)
        assertEquals(0, uiModel.failedCount)
        assertEquals("1920x1080", uiModel.items[0].dimensionsFormatted)
        assertTrue(uiModel.items[0].displayFileSize.contains("2.38 MB") || uiModel.items[0].displayFileSize.contains("MB"))
    }

    @Test
    fun `test single video to MediaCollection to AnalysisUiModel mapping`() {
        val videoMeta = MediaMetadata(
            id = "vid_456",
            title = "Kotlin Tutorial",
            webpageUrl = "https://youtube.com/watch?v=12345",
            uploader = "AndroidDev",
            durationSeconds = 600,
            formats = listOf(
                FormatInfo(formatId = "137", ext = "mp4", height = 1080, filesize = 50000000L),
                FormatInfo(formatId = "136", ext = "mp4", height = 720, filesize = 25000000L)
            ),
            mediaType = MediaType.VIDEO
        )

        val collection = MediaCollection.fromMediaMetadata(videoMeta)
        val uiModel = MediaUiMapper.mapCollectionToUiModel(collection)

        assertTrue(uiModel.isSingleItem)
        assertEquals(MediaKind.VIDEO, uiModel.mediaKind)
        assertEquals(2, uiModel.primaryFormats.size)
        assertEquals("1080p", uiModel.defaultFormat?.displayResolution)
    }

    @Test
    fun `test single audio to MediaCollection to AnalysisUiModel mapping`() {
        val audioMeta = MediaMetadata(
            id = "aud_789",
            title = "Podcast Episode",
            webpageUrl = "https://soundcloud.com/podcast",
            uploader = "Host",
            durationSeconds = 1800,
            mediaType = MediaType.AUDIO,
            extractorName = "soundcloud"
        )

        val collection = MediaCollection.fromMediaMetadata(audioMeta)
        val uiModel = MediaUiMapper.mapCollectionToUiModel(collection)

        assertTrue(uiModel.isSingleItem)
        assertEquals(MediaKind.AUDIO, uiModel.mediaKind)
        assertTrue(uiModel.items[0].isAudio)
    }

    @Test
    fun `test YouTube playlist preservation and entry indices`() {
        val playlistMeta = MediaMetadata(
            id = "pl_1",
            title = "Best Hits",
            webpageUrl = "https://youtube.com/playlist?list=PL123",
            isPlaylist = true,
            mediaType = MediaType.PLAYLIST,
            playlistEntries = listOf(
                PlaylistEntry(id = "p1", title = "Track 1", url = "https://youtube.com/watch?v=1", durationSeconds = 180),
                PlaylistEntry(id = "p2", title = "Track 2", url = "https://youtube.com/watch?v=2", durationSeconds = 200),
                PlaylistEntry(id = "p3", title = "Track 3", url = "https://youtube.com/watch?v=3", durationSeconds = 240)
            )
        )

        val collection = MediaCollection.fromMediaMetadata(playlistMeta)
        assertTrue(collection.isMultiItem)
        assertTrue(collection.isPlaylist)
        assertEquals(3, collection.items.size)
        assertEquals(0, collection.items[0].index)
        assertEquals(1, collection.items[1].index)
        assertEquals(2, collection.items[2].index)

        val uiModel = MediaUiMapper.mapCollectionToUiModel(collection)
        assertTrue(uiModel.isPlaylist)
        assertEquals(3, uiModel.totalCount)
        assertEquals(3, uiModel.selectedCount)

        // Test planning with subset selection (only items 0 and 2)
        val plan = DownloadPlanner.planDownloads(
            collection = collection,
            options = DownloadPlanningOptions(
                targetMediaType = MediaType.VIDEO,
                selectedIndices = setOf(0, 2)
            )
        )

        assertEquals(2, plan.requests.size)
        assertEquals("Best Hits - Track 1", plan.requests[0].title)
        assertEquals(1, plan.requests[0].playlistIndex)
        assertEquals(2, plan.requests[0].playlistTotal)
        assertEquals("Best Hits - Track 3", plan.requests[1].title)
        assertEquals(2, plan.requests[1].playlistIndex)
        assertEquals(2, plan.requests[1].playlistTotal)
    }

    @Test
    fun `test mixed image and video collection rendering and safe handling`() {
        val mixedItems = listOf(
            MediaItem(id = "m1", title = "Slide 1 Image", sourceUrl = "https://inst.com/1.jpg", mediaKind = MediaKind.IMAGE, width = 1080, height = 1080, index = 0),
            MediaItem(id = "m2", title = "Slide 2 Video", sourceUrl = "https://inst.com/2.mp4", mediaKind = MediaKind.VIDEO, durationSeconds = 30, index = 1),
            MediaItem(id = "m3", title = "Slide 3 Image", sourceUrl = "https://inst.com/3.jpg", mediaKind = MediaKind.IMAGE, width = 1080, height = 1350, index = 2)
        )

        val mixedCollection = MediaCollection(
            id = "instagram_sidecar",
            title = "Instagram Post",
            webpageUrl = "https://instagram.com/p/abc",
            mediaKind = MediaKind.CAROUSEL,
            items = mixedItems
        )

        assertTrue(mixedCollection.isMixedCollection)
        val uiModel = MediaUiMapper.mapCollectionToUiModel(mixedCollection)

        assertTrue(uiModel.isMixedCollection)
        assertEquals(3, uiModel.totalCount)
        assertTrue(uiModel.items[0].isImage)
        assertTrue(uiModel.items[1].isVideo)
        assertTrue(uiModel.items[2].isImage)
    }

    @Test
    fun `test partial collection with failed item isolation`() {
        val partialItems = listOf(
            MediaItem(id = "p1", title = "Photo 1", sourceUrl = "https://tiktok.com/photo1.jpg", mediaKind = MediaKind.IMAGE, index = 0),
            MediaItem(id = "p2", title = "Photo 2", sourceUrl = "https://tiktok.com/photo2.jpg", mediaKind = MediaKind.IMAGE, index = 1),
            MediaItem(id = "p3", title = "Photo 3", sourceUrl = "", mediaKind = MediaKind.IMAGE, errorMessage = "Corrupt CDN stream", index = 2),
            MediaItem(id = "p4", title = "Photo 4", sourceUrl = "https://tiktok.com/photo4.jpg", mediaKind = MediaKind.IMAGE, index = 3)
        )

        val collection = MediaCollection(
            id = "tiktok_album",
            title = "TikTok Photo Album",
            webpageUrl = "https://tiktok.com/@user/photo/123",
            mediaKind = MediaKind.CAROUSEL,
            items = partialItems
        )

        val uiModel = MediaUiMapper.mapCollectionToUiModel(collection)
        assertEquals(4, uiModel.totalCount)
        assertEquals(3, uiModel.selectedCount) // Failed item excluded from initial selection
        assertEquals(1, uiModel.failedCount)

        assertFalse(uiModel.items[2].isSuccess)
        assertTrue(uiModel.items[2].isFailed)
        assertEquals("Corrupt CDN stream", uiModel.items[2].errorMessage)

        // Planner should automatically skip failed items
        val plan = DownloadPlanner.planDownloads(
            collection = collection,
            options = DownloadPlanningOptions(
                targetMediaType = MediaType.IMAGE,
                selectedIndices = setOf(0, 1, 2, 3)
            )
        )
        assertEquals(3, plan.requests.size)
    }

    @Test
    fun `test size provenance presentation rules`() {
        val exactItem = MediaItem(
            id = "1", title = "Exact", sourceUrl = "https://test.com/1",
            fileSize = 1048576L, sizeProvenance = SizeProvenance.EXACT
        )
        val approxItem = MediaItem(
            id = "2", title = "Approx", sourceUrl = "https://test.com/2",
            fileSize = 2097152L, sizeProvenance = SizeProvenance.APPROXIMATE
        )
        val derivedItem = MediaItem(
            id = "3", title = "Derived", sourceUrl = "https://test.com/3",
            fileSize = 3145728L, sizeProvenance = SizeProvenance.DERIVED
        )
        val unknownItem = MediaItem(
            id = "4", title = "Unknown", sourceUrl = "https://test.com/4",
            fileSize = null, sizeProvenance = SizeProvenance.UNKNOWN
        )

        assertEquals("1.00 MB", exactItem.displayFileSize)
        assertTrue(approxItem.displayFileSize.startsWith("~"))
        assertTrue(derivedItem.displayFileSize.startsWith("≈"))
        assertEquals("Unknown size", unknownItem.displayFileSize)
    }

    @Test
    fun `test audio download planning with custom bitrate and format`() {
        val audioCollection = MediaCollection(
            id = "aud_test",
            title = "Podcast Audio",
            webpageUrl = "https://example.com/audio",
            mediaKind = MediaKind.AUDIO,
            items = listOf(
                MediaItem(
                    id = "aud_item_1",
                    title = "Podcast Audio",
                    sourceUrl = "https://example.com/audio",
                    mediaKind = MediaKind.AUDIO,
                    index = 0
                )
            )
        )

        val plan = DownloadPlanner.planDownloads(
            collection = audioCollection,
            options = DownloadPlanningOptions(
                targetMediaType = MediaType.AUDIO,
                targetContainer = OutputContainer.MP3,
                audioBitrate = 256
            )
        )

        assertEquals(1, plan.requests.size)
        val req = plan.requests[0]
        assertEquals("bestaudio/best", req.formatId)
        assertEquals(MediaType.AUDIO, req.mediaType)
        assertEquals("mp3", req.targetContainer)
        assertEquals(256, req.audioBitrate)
        assertTrue(req.formatDescription.contains("256kbps"))
    }
}
