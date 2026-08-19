package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.EngineState
import com.example.data.model.MediaType
import com.example.download.StorageUtils
import com.example.engine.FFmpegBinaryManager
import com.example.engine.FFmpegDetector
import com.example.engine.FFmpegState
import com.example.engine.FilenameFormatter
import com.example.engine.YtDlpBinaryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

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
    fun `test storage utils subfolder routing`() {
        assertEquals("Video", StorageUtils.getSubfolderForMediaType(MediaType.VIDEO, "mp4"))
        assertEquals("Music", StorageUtils.getSubfolderForMediaType(MediaType.AUDIO, "mp3"))
        assertEquals("Music", StorageUtils.getSubfolderForMediaType(MediaType.AUDIO, "flac"))
        assertEquals("Audio", StorageUtils.getSubfolderForMediaType(MediaType.AUDIO, "opus"))
        assertEquals("Subtitles", StorageUtils.getSubfolderForMediaType(MediaType.VIDEO, "vtt"))
        assertEquals("Images", StorageUtils.getSubfolderForMediaType(MediaType.VIDEO, "jpg"))
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
    fun `test ytdlp detector reports status accurately`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val status = YtDlpBinaryManager.detect(context)
        assertNotNull(status)
        assertNotNull(status.state)
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

    @Test
    fun `test ffmpeg candidate sources resolution`() {
        val sources = FFmpegBinaryManager.getCandidateSourcesForDevice()
        assertTrue("Must have at least one candidate source for device", sources.isNotEmpty())
        for (candidate in sources) {
            assertTrue("Candidate must have valid URLs", candidate.urls.isNotEmpty())
            assertTrue("URL must point to zip", candidate.urls.all { it.endsWith(".zip") })
        }
    }

    @Test
    fun `test detector cleans up 0-byte corrupt file`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ffmpegFile = FFmpegDetector.getPreferredFFmpegFile(context)

        // Write 0-byte file
        ffmpegFile.parentFile?.mkdirs()
        ffmpegFile.writeBytes(ByteArray(0))
        assertTrue(ffmpegFile.exists())
        assertEquals(0L, ffmpegFile.length())

        val status = FFmpegDetector.detect(context)
        assertEquals(FFmpegState.MISSING, status.state)
        assertEquals(EngineState.MISSING, status.engineState)
        assertFalse("0-byte file should be automatically purged", ffmpegFile.exists())
    }

    @Test
    fun `test ABI priority selector chooses arm64-v8a over armeabi-v7a`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testDir = File(context.cacheDir, "abi_test_${System.currentTimeMillis()}")
        testDir.mkdirs()

        val arm64Dir = File(testDir, "arm64-v8a/bin").apply { mkdirs() }
        val armv7Dir = File(testDir, "armeabi-v7a/bin").apply { mkdirs() }
        val armDir = File(testDir, "armeabi/bin").apply { mkdirs() }

        val arm64Ffmpeg = File(arm64Dir, "ffmpeg").apply { writeBytes(ByteArray(5000)) }
        val armv7Ffmpeg = File(armv7Dir, "ffmpeg").apply { writeBytes(ByteArray(5000)) }
        val armFfmpeg = File(armDir, "ffmpeg").apply { writeBytes(ByteArray(5000)) }

        val deviceAbis = arrayOf("arm64-v8a", "armeabi-v7a", "armeabi")
        val selected = FFmpegBinaryManager.findBestExecutableForDevice(testDir, "ffmpeg", deviceAbis)

        assertNotNull(selected)
        assertEquals(arm64Ffmpeg.absolutePath, selected?.absolutePath)

        // Cleanup
        testDir.deleteRecursively()
    }
}
