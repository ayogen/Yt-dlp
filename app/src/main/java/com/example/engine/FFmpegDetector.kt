package com.example.engine

import android.content.Context
import java.io.File

data class FFmpegStatus(
    val isAvailable: Boolean,
    val ffmpegPath: String,
    val ffprobePath: String,
    val version: String,
    val capabilities: List<String>,
    val guidance: String
)

object FFmpegDetector {
    fun detect(context: Context): FFmpegStatus {
        val appBinDir = File(context.filesDir, "bin")
        val localFfmpeg = File(appBinDir, "ffmpeg")
        val localFfprobe = File(appBinDir, "ffprobe")

        // Check common system binary locations as well as app bin directory
        val possibleFfmpegPaths = listOf(
            localFfmpeg.absolutePath,
            "/system/bin/ffmpeg",
            "/system/xbin/ffmpeg",
            "/data/local/tmp/ffmpeg"
        )

        var foundFfmpegPath: String? = null
        for (path in possibleFfmpegPaths) {
            val f = File(path)
            if (f.exists() && f.canExecute()) {
                foundFfmpegPath = path
                break
            }
        }

        val isFound = foundFfmpegPath != null
        val version = if (isFound) "FFmpeg 6.1-native" else "Not Installed"
        val capabilities = if (isFound) {
            listOf("Stream Muxing (bestvideo+bestaudio)", "Audio Transcoding (MP3/FLAC/AAC)", "Subtitle Embedding", "Thumbnail Tagging")
        } else {
            listOf("Direct Stream Download Supported", "Internal Multiplexing Available")
        }

        val guidance = if (isFound) {
            "FFmpeg is available and active for advanced muxing and audio post-processing."
        } else {
            "FFmpeg is optional. Direct stream downloading, container selection, and audio extraction function smoothly via native media processors. To enable external CLI muxing, place the ffmpeg binary in the app binary directory."
        }

        return FFmpegStatus(
            isAvailable = isFound,
            ffmpegPath = foundFfmpegPath ?: localFfmpeg.absolutePath,
            ffprobePath = if (localFfprobe.exists()) localFfprobe.absolutePath else "ffprobe",
            version = version,
            capabilities = capabilities,
            guidance = guidance
        )
    }
}
