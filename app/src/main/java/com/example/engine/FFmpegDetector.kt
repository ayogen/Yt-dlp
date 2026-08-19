package com.example.engine

import android.content.Context
import java.io.File

enum class FFmpegState(val displayName: String) {
    AVAILABLE("FFmpeg Available"),
    MISSING("FFmpeg Missing"),
    INVALID_NOT_EXECUTABLE("FFmpeg Invalid/Not Executable")
}

data class FFmpegStatus(
    val state: FFmpegState,
    val binaryPath: String?,
    val ffprobePath: String?,
    val version: String?,
    val isExecutable: Boolean,
    val capabilities: List<String>,
    val guidance: String
) {
    val isAvailable: Boolean get() = state == FFmpegState.AVAILABLE
}

object FFmpegDetector {
    fun detect(context: Context): FFmpegStatus {
        val appBinDir = File(context.filesDir, "bin")
        val localFfmpeg = File(appBinDir, "ffmpeg")
        val localFfprobe = File(appBinDir, "ffprobe")

        val possibleFfmpegPaths = listOf(
            localFfmpeg.absolutePath,
            "/system/bin/ffmpeg",
            "/system/xbin/ffmpeg",
            "/data/local/tmp/ffmpeg"
        )

        var foundFile: File? = null
        for (path in possibleFfmpegPaths) {
            val f = File(path)
            if (f.exists()) {
                foundFile = f
                break
            }
        }

        if (foundFile == null) {
            return FFmpegStatus(
                state = FFmpegState.MISSING,
                binaryPath = null,
                ffprobePath = null,
                version = null,
                isExecutable = false,
                capabilities = emptyList(),
                guidance = "FFmpeg is not installed on this device. A real FFmpeg executable binary is required to merge video and audio streams (1080p, 1440p, 4K) and convert audio formats (MP3, M4A, FLAC, Opus). Please tap 'Install FFmpeg' below to download and configure it."
            )
        }

        // If it is in the application's private files directory, ensure executable permissions
        if (foundFile.parentFile?.absolutePath == appBinDir.absolutePath) {
            try {
                foundFile.setExecutable(true, false)
                foundFile.setReadable(true, false)
            } catch (e: Exception) {
                AppLogger.w("FFmpegDetector", "Could not set executable permission: ${e.message}")
            }
        }

        // Test running ffmpeg -version
        val versionInfo = testRunVersion(foundFile)
        if (versionInfo != null) {
            val ffprobeExecutablePath = if (localFfprobe.exists()) {
                try { localFfprobe.setExecutable(true, false) } catch (e: Exception) {}
                localFfprobe.absolutePath
            } else null

            return FFmpegStatus(
                state = FFmpegState.AVAILABLE,
                binaryPath = foundFile.absolutePath,
                ffprobePath = ffprobeExecutablePath,
                version = versionInfo,
                isExecutable = true,
                capabilities = listOf(
                    "Video + Audio Muxing (1080p, 1440p, 4K, 8K)",
                    "Audio Extraction & Transcoding (MP3, M4A, FLAC, Opus)",
                    "MP4 / MKV / WebM Container Remuxing",
                    "Subtitle & Metadata Embedding"
                ),
                guidance = "FFmpeg binary is active and verified operational at ${foundFile.absolutePath}."
            )
        } else {
            return FFmpegStatus(
                state = FFmpegState.INVALID_NOT_EXECUTABLE,
                binaryPath = foundFile.absolutePath,
                ffprobePath = null,
                version = null,
                isExecutable = false,
                capabilities = emptyList(),
                guidance = "FFmpeg binary exists at ${foundFile.absolutePath} but is invalid or not executable on this device architecture. Please tap 'Reinstall FFmpeg' to install a compatible binary."
            )
        }
    }

    private fun testRunVersion(binary: File): String? {
        return try {
            if (!binary.canExecute()) {
                binary.setExecutable(true, false)
            }
            val process = ProcessBuilder(binary.absolutePath, "-version")
                .redirectErrorStream(true)
                .start()
            val reader = process.inputStream.bufferedReader()
            val firstLine = reader.readLine()
            val exitCode = process.waitFor()
            if (exitCode == 0 && !firstLine.isNullOrBlank() && firstLine.contains("ffmpeg", ignoreCase = true)) {
                firstLine.trim()
            } else {
                null
            }
        } catch (e: Exception) {
            AppLogger.w("FFmpegDetector", "FFmpeg execution test failed: ${e.message}")
            null
        }
    }
}
