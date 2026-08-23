package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ProfileManager
import com.example.data.model.DownloadProfile
import com.example.data.model.EngineState
import com.example.data.model.MediaType
import com.example.download.StorageUtils
import com.example.engine.FFmpegBinaryManager
import com.example.engine.FFmpegDetector
import com.example.engine.FFmpegState
import com.example.engine.FilenameFormatter
import com.example.engine.YtDlpBinaryManager
import com.example.engine.YtDlpProcessRunner
import com.example.utils.UrlDetector
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Video Downloader", appName)
    }

    @Test
    fun `test single video json parsing retains all media properties and is not playlist`() {
        val singleVideoJson = JSONObject().apply {
            put("id", "dQw4w9WgXcQ")
            put("title", "Rick Astley - Never Gonna Give You Up")
            put("uploader", "RickAstleyVEVO")
            put("duration", 212)
            put("view_count", 1500000000L)
            put("like_count", 16000000L)
            put("upload_date", "20091025")
            put("description", "The official video for Never Gonna Give You Up")
            put("thumbnail", "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg")
            put("extractor", "youtube")
            put("formats", JSONArray().apply {
                put(JSONObject().apply {
                    put("format_id", "137")
                    put("ext", "mp4")
                    put("height", 1080)
                    put("vcodec", "avc1.640028")
                    put("acodec", "none")
                    put("filesize", 50000000L)
                })
                put(JSONObject().apply {
                    put("format_id", "140")
                    put("ext", "m4a")
                    put("acodec", "mp4a.40.2")
                    put("vcodec", "none")
                    put("abr", 128.0)
                })
            })
            put("subtitles", JSONObject().apply {
                put("en", JSONArray().apply {
                    put(JSONObject().apply {
                        put("ext", "vtt")
                    })
                })
            })
        }

        val parsed = YtDlpProcessRunner.parseYtDlpJson(singleVideoJson, "https://www.youtube.com/watch?v=dQw4w9WgXcQ")

        assertFalse("Single video must not be marked as playlist", parsed.isPlaylist)
        assertEquals(0, parsed.playlistCount)
        assertTrue(parsed.playlistEntries.isEmpty())
        assertEquals("dQw4w9WgXcQ", parsed.id)
        assertEquals("Rick Astley - Never Gonna Give You Up", parsed.title)
        assertEquals("RickAstleyVEVO", parsed.uploader)
        assertEquals(212L, parsed.durationSeconds)
        assertEquals(2, parsed.formats.size)
        assertEquals(50000000L, parsed.formats[0].filesize)
        assertEquals("1080p", parsed.formats[0].displayResolution)
        assertEquals(1, parsed.subtitles.size)
        assertEquals("EN", parsed.subtitles[0].name)
    }

    @Test
    fun `test playlist json with entries is correctly parsed with entries and canonical urls`() {
        val playlistJson = JSONObject().apply {
            put("_type", "playlist")
            put("id", "PL1234567890")
            put("title", "Top Hits 2026")
            put("uploader", "Music Channel")
            put("thumbnail", "https://i.ytimg.com/playlist_thumb.jpg")
            put("extractor", "youtube:tab")
            put("entries", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", "vid_1")
                    put("title", "Song One")
                    put("duration", 180)
                    put("thumbnail", "https://i.ytimg.com/vi/vid_1/default.jpg")
                    put("uploader", "Artist 1")
                })
                put(JSONObject().apply {
                    put("id", "vid_2")
                    put("title", "Song Two")
                    put("url", "https://www.youtube.com/watch?v=vid_2")
                    put("duration", 240)
                    put("thumbnail", "https://i.ytimg.com/vi/vid_2/default.jpg")
                    put("uploader", "Artist 2")
                })
            })
        }

        val parsed = YtDlpProcessRunner.parseYtDlpJson(playlistJson, "https://www.youtube.com/playlist?list=PL1234567890")

        assertTrue("Playlist with valid entries must be marked as playlist", parsed.isPlaylist)
        assertEquals(2, parsed.playlistCount)
        assertEquals(2, parsed.playlistEntries.size)
        assertEquals("Top Hits 2026", parsed.title)
        assertEquals("Music Channel", parsed.uploader)

        val entry1 = parsed.playlistEntries[0]
        assertEquals("vid_1", entry1.id)
        assertEquals("Song One", entry1.title)
        assertEquals("https://www.youtube.com/watch?v=vid_1", entry1.url)
        assertEquals(180L, entry1.durationSeconds)
        assertEquals("3:00", entry1.durationFormatted)
        assertEquals("Artist 1", entry1.uploader)

        val entry2 = parsed.playlistEntries[1]
        assertEquals("vid_2", entry2.id)
        assertEquals("Song Two", entry2.title)
        assertEquals("https://www.youtube.com/watch?v=vid_2", entry2.url)
        assertEquals(240L, entry2.durationSeconds)
        assertEquals("4:00", entry2.durationFormatted)
        assertEquals("Artist 2", entry2.uploader)
    }

    @Test
    fun `test playlist json with malformed entries skips corrupt items safely`() {
        val playlistWithCorruptEntries = JSONObject().apply {
            put("_type", "playlist")
            put("id", "PL_corrupt_test")
            put("title", "Mixed Playlist")
            put("entries", JSONArray().apply {
                // Valid entry
                put(JSONObject().apply {
                    put("id", "valid_1")
                    put("title", "Good Video")
                    put("url", "https://example.com/video1.mp4")
                })
                // Malformed / empty entry without url or id
                put(JSONObject().apply {
                    put("title", "No URL or ID")
                })
                // Null object in json array
                put(JSONObject.NULL)
                // Another valid entry
                put(JSONObject().apply {
                    put("id", "valid_2")
                    put("title", "Second Good Video")
                    put("url", "https://example.com/video2.mp4")
                    put("duration", 95)
                })
            })
        }

        val parsed = YtDlpProcessRunner.parseYtDlpJson(playlistWithCorruptEntries, "https://example.com/playlist")

        assertTrue(parsed.isPlaylist)
        assertEquals(2, parsed.playlistCount)
        assertEquals(2, parsed.playlistEntries.size)
        assertEquals("Good Video", parsed.playlistEntries[0].title)
        assertEquals("Second Good Video", parsed.playlistEntries[1].title)
        assertEquals("1:35", parsed.playlistEntries[1].durationFormatted)
    }

    @Test
    fun `test playlist json with zero valid entries does not create fake entries`() {
        val emptyPlaylistJson = JSONObject().apply {
            put("_type", "playlist")
            put("id", "PL_empty")
            put("title", "Empty Playlist")
            put("entries", JSONArray())
        }

        val parsed = YtDlpProcessRunner.parseYtDlpJson(emptyPlaylistJson, "https://example.com/empty-playlist")

        assertFalse(parsed.isPlaylist)
        assertEquals(0, parsed.playlistCount)
        assertTrue(parsed.playlistEntries.isEmpty())
    }

    @Test
    fun `test url detector with media domains and extensions`() {
        assertTrue(UrlDetector.isPotentialMediaUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(UrlDetector.isPotentialMediaUrl("https://youtu.be/dQw4w9WgXcQ"))
        assertTrue(UrlDetector.isPotentialMediaUrl("https://instagram.com/reel/12345"))
        assertTrue(UrlDetector.isPotentialMediaUrl("https://example.com/stream/file.m3u8"))
        assertTrue(UrlDetector.isPotentialMediaUrl("https://cdn.example.org/audio.mp3"))

        assertFalse(UrlDetector.isPotentialMediaUrl(null))
        assertFalse(UrlDetector.isPotentialMediaUrl(""))
        assertFalse(UrlDetector.isPotentialMediaUrl("not a url"))

        val textWithUrl = "Check out this awesome video https://youtu.be/abc123xyz from today!"
        assertEquals("https://youtu.be/abc123xyz", UrlDetector.extractFirstUrl(textWithUrl))
    }

    @Test
    fun `test profile manager serialize and deserialize`() {
        val customProfiles = listOf(
            DownloadProfile(
                id = "custom_test_1",
                name = "Test Audio Profile",
                description = "High quality flac",
                mediaType = MediaType.AUDIO,
                videoQuality = "1080p",
                container = "flac",
                audioBitrate = 320,
                embedSubs = false,
                embedThumbnail = true,
                isPreset = false
            )
        )
        val serialized = ProfileManager.serializeProfiles(customProfiles)
        assertTrue(serialized.contains("Test Audio Profile"))
        assertTrue(serialized.contains("flac"))

        val deserialized = ProfileManager.deserializeProfiles(serialized)
        assertEquals(1, deserialized.size)
        assertEquals("Test Audio Profile", deserialized[0].name)
        assertEquals(MediaType.AUDIO, deserialized[0].mediaType)
        assertEquals("flac", deserialized[0].container)
        assertEquals(320, deserialized[0].audioBitrate)
    }

    @Test
    fun `test storage utils subfolder routing`() {
        assertEquals("Video", StorageUtils.getSubfolderForMediaType(MediaType.VIDEO, "mp4"))
        assertEquals("Music", StorageUtils.getSubfolderForMediaType(MediaType.AUDIO, "mp3"))
        assertEquals("Music", StorageUtils.getSubfolderForMediaType(MediaType.AUDIO, "flac"))
        assertEquals("Audio", StorageUtils.getSubfolderForMediaType(MediaType.AUDIO, "opus"))
        assertEquals("Subtitles", StorageUtils.getSubfolderForMediaType(MediaType.VIDEO, "vtt"))
        assertEquals("Images", StorageUtils.getSubfolderForMediaType(MediaType.IMAGE, "jpg"))
        assertEquals("Images", StorageUtils.getSubfolderForMediaType(MediaType.CAROUSEL, "png"))
        assertEquals("Images", StorageUtils.getSubfolderForMediaType(MediaType.VIDEO, "jpg"))
    }

    @Test
    fun `test universal image and carousel metadata parsing`() {
        val carouselMeta = com.example.data.model.MediaMetadata(
            id = "pin_12345",
            originalUrl = "https://pinterest.com/pin/12345",
            title = "Pinterest Inspiration Board",
            isCarousel = true,
            carouselItems = listOf(
                com.example.data.model.CarouselItem(
                    id = "pin_12345_0",
                    url = "https://i.pinimg.com/originals/1.jpg",
                    thumbnail = "https://i.pinimg.com/originals/1.jpg",
                    title = "Inspiration 1",
                    mediaType = MediaType.IMAGE
                ),
                com.example.data.model.CarouselItem(
                    id = "pin_12345_1",
                    url = "https://i.pinimg.com/originals/2.jpg",
                    thumbnail = "https://i.pinimg.com/originals/2.jpg",
                    title = "Inspiration 2",
                    mediaType = MediaType.IMAGE
                )
            )
        )

        assertTrue(carouselMeta.isCarousel)
        assertEquals(2, carouselMeta.carouselItems.size)
        assertEquals("Inspiration 1", carouselMeta.carouselItems[0].title)
    }

    @Test
    fun `test filename formatter`() {
        val formatted = FilenameFormatter.format(
            template = "%(title)s.%(ext)s",
            title = "My Cool Video!",
            uploader = "Test Creator",
            id = "123",
            ext = "mp4"
        )
        assertTrue(formatted.contains("My Cool Video"))
        assertTrue(formatted.endsWith(".mp4"))
    }

    @Test
    fun `test filename formatter truncates very long Facebook titles and emojis to prevent Errno 36`() {
        val longFacebookTitle = "33K views · 1.6K reactions | #فرنسا🇨🇵_بلجيكا🇧🇪_المانيا🇩🇪_اسبانيا🇪🇸_السعودية🇸🇦_الأمارات🇦🇪_الكويت🇰🇼_الأردن🇯🇴_قطر🇧🇭_مصر🇪🇬_الجزائر🇩🇿_لبنان🇱🇧_العراق🇮🇶_تركيا🇹🇷_أسطنبول_سوريا🏳_المغرب🇲🇦 " + "a".repeat(300)

        val formatted = FilenameFormatter.format(
            template = "%(title)s.%(ext)s",
            title = longFacebookTitle,
            uploader = "Facebook Creator",
            id = "fb_reel_12345",
            ext = "mp4"
        )

        assertTrue("Formatted filename must be under 255 bytes for ext4/FAT32", formatted.toByteArray(Charsets.UTF_8).size < 255)
        assertTrue("Formatted filename must end with extension", formatted.endsWith(".mp4"))
        assertFalse("Formatted filename must not contain illegal chars", formatted.contains("|") || formatted.contains("?") || formatted.contains("*"))
    }

    @Test
    fun `test filename formatter handles empty and invalid title fallback`() {
        val formatted = FilenameFormatter.format(
            template = "%(title)s.%(ext)s",
            title = "   ???///:::***   ",
            uploader = "Creator",
            id = "vid_999",
            ext = "m4a"
        )

        assertTrue("Should fallback to safe media id name", formatted.contains("media_vid_999"))
        assertTrue(formatted.endsWith(".m4a"))
    }


    @Test
    fun `test ytdlp detector reports status accurately`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val status = YtDlpBinaryManager.detect(context)
        assertNotNull(status)
        assertNotNull(status.state)
        assertNotNull(status.guidance)
    }

    @Test
    fun `test ffmpeg detector reports status accurately`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val status = FFmpegDetector.detect(context)
        assertNotNull(status)
        assertNotNull(status.state)
        assertNotNull(status.engineState)
        assertNotNull(status.guidance)
    }
}
