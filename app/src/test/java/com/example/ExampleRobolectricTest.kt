package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.MediaType
import com.example.download.StorageUtils
import com.example.engine.FFmpegDetector
import com.example.engine.FilenameFormatter
import org.junit.Assert.assertEquals
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
    fun `test ffmpeg detector reports status`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val status = FFmpegDetector.detect(context)
        assertNotNull(status)
        assertNotNull(status.state)
    }
}
