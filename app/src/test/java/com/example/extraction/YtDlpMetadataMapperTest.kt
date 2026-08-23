package com.example.extraction

import com.example.extraction.model.YtDlpFormatDto
import com.example.extraction.model.YtDlpInfoDto
import com.example.extraction.model.YtDlpPlaylistEntryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpMetadataMapperTest {

    @Test
    fun testMapToMediaMetadataPreservesFilesizesAndFormats() {
        val dto = YtDlpInfoDto(
            id = "test_vid",
            title = "Test Title",
            webpageUrl = "https://example.com/watch?v=test_vid",
            uploader = "Tester",
            duration = 120,
            formats = listOf(
                YtDlpFormatDto(
                    formatId = "1080p",
                    ext = "mp4",
                    height = 1080,
                    filesize = 55000000L,
                    vcodec = "h264",
                    acodec = "aac"
                ),
                YtDlpFormatDto(
                    formatId = "720p",
                    ext = "mp4",
                    height = 720,
                    filesizeApprox = 25000000L,
                    vcodec = "h264",
                    acodec = "aac"
                )
            )
        )

        val metadata = YtDlpMetadataMapper.mapToMediaMetadata(dto, "https://example.com/watch?v=test_vid")

        assertEquals("test_vid", metadata.id)
        assertEquals("Test Title", metadata.title)
        assertEquals(2, metadata.formats.size)

        val f1 = metadata.formats[0]
        assertEquals("1080p", f1.formatId)
        assertEquals(55000000L, f1.filesize)
        assertNull(f1.filesizeApprox)
        assertEquals("1080p", f1.displayResolution)
        assertTrue(f1.isMuxed)

        val f2 = metadata.formats[1]
        assertEquals("720p", f2.formatId)
        assertNull(f2.filesize)
        assertEquals(25000000L, f2.filesizeApprox)
        assertEquals("720p", f2.displayResolution)
        assertTrue(f2.isMuxed)
    }

    @Test
    fun testMapPlaylistEntries() {
        val dto = YtDlpInfoDto(
            id = "pl_1",
            title = "My Playlist",
            type = "playlist",
            entries = listOf(
                YtDlpPlaylistEntryDto(
                    id = "entry_1",
                    title = "First Song",
                    url = "https://example.com/song1",
                    duration = 200,
                    uploader = "Artist 1"
                )
            )
        )

        val metadata = YtDlpMetadataMapper.mapToMediaMetadata(dto, "https://example.com/playlist")

        assertTrue(metadata.isPlaylist)
        assertEquals(1, metadata.playlistCount)
        assertEquals(1, metadata.playlistEntries.size)
        assertEquals("First Song", metadata.playlistEntries[0].title)
    }
}
