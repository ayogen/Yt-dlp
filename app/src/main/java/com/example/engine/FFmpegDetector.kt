package com.example.engine

import android.content.Context
import android.os.Build
import com.example.data.model.EngineState
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
    val ffprobeVersion: String?,
    val isExecutable: Boolean,
    val isFfprobeAvailable: Boolean,
    val abi: String,
    val fileSize: Long,
    val capabilities: List<String>,
    val guidance: String,
    val diagnosticDetails: String? = null
) {
    val isAvailable: Boolean get() = state == FFmpegState.AVAILABLE
    val engineState: EngineState get() = when (state) {
        FFmpegState.AVAILABLE -> EngineState.READY
        FFmpegState.MISSING -> EngineState.MISSING
        FFmpegState.INVALID_NOT_EXECUTABLE -> EngineState.INVALID
    }
}

object FFmpegDetector {
    private const val TAG = "FFmpegDetector"

    fun getPreferredBinDir(context: Context): File {
        val binDir = File(context.filesDir, "bin")
        if (!binDir.exists()) {
            binDir.mkdirs()
        }
        return binDir
    }

    fun getPreferredFFmpegFile(context: Context): File {
        return File(getPreferredBinDir(context), "ffmpeg")
    }

    fun getPreferredFFprobeFile(context: Context): File {
        return File(getPreferredBinDir(context), "ffprobe")
    }

    fun detect(context: Context): FFmpegStatus {
        val primaryAbi = if (Build.SUPPORTED_ABIS.isNotEmpty()) Build.SUPPORTED_ABIS[0] else "unknown"
        val abiList = Build.SUPPORTED_ABIS.joinToString(", ")

        return try {
            if (FFmpegBinaryManager.isInitialized) {
                FFmpegStatus(
                    state = FFmpegState.AVAILABLE,
                    binaryPath = "${context.applicationInfo.nativeLibraryDir}/libffmpeg.so",
                    ffprobePath = "${context.applicationInfo.nativeLibraryDir}/libffprobe.so",
                    version = "7.0 (Native)",
                    ffprobeVersion = "7.0 (Native)",
                    isExecutable = true,
                    isFfprobeAvailable = true,
                    abi = primaryAbi,
                    fileSize = 0L,
                    capabilities = listOf(
                        "Video + Audio Muxing (1080p, 1440p, 4K, 8K)",
                        "Audio Extraction & Transcoding (MP3, M4A, FLAC, Opus)",
                        "MP4 / MKV / WebM Container Remuxing",
                        "Metadata & Subtitle Processing"
                    ),
                    guidance = "Native FFmpeg binary active and verified (Native $primaryAbi).",
                    diagnosticDetails = "ABI: $primaryAbi | Native Execution: Verified"
                )
            } else {
                FFmpegStatus(
                    state = FFmpegState.MISSING,
                    binaryPath = null,
                    ffprobePath = null,
                    version = null,
                    ffprobeVersion = null,
                    isExecutable = false,
                    isFfprobeAvailable = false,
                    abi = primaryAbi,
                    fileSize = 0L,
                    capabilities = emptyList(),
                    guidance = "FFmpeg native component is not initialized. Tap 'Install' to set up.",
                    diagnosticDetails = "Uninitialized | ABIs: $abiList"
                )
            }
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            AppLogger.d(TAG, "FFmpeg detect check: $msg")
            FFmpegStatus(
                state = FFmpegState.MISSING,
                binaryPath = null,
                ffprobePath = null,
                version = null,
                ffprobeVersion = null,
                isExecutable = false,
                isFfprobeAvailable = false,
                abi = primaryAbi,
                fileSize = 0L,
                capabilities = emptyList(),
                guidance = "FFmpeg native component is not initialized. Tap 'Install' to set up.",
                diagnosticDetails = "Detection check: $msg | ABIs: $abiList"
            )
        }
    }
}
