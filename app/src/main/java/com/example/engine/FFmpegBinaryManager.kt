package com.example.engine

import android.content.Context
import android.os.Build
import com.yausername.ffmpeg.FFmpeg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object FFmpegBinaryManager {
    private const val TAG = "FFmpegBinaryManager"

    @Volatile
    var isInitialized = false
        private set

    fun getBinDir(context: Context): File {
        return FFmpegDetector.getPreferredBinDir(context)
    }

    fun getFFmpegFile(context: Context): File {
        return FFmpegDetector.getPreferredFFmpegFile(context)
    }

    fun getFFprobeFile(context: Context): File {
        return FFmpegDetector.getPreferredFFprobeFile(context)
    }

    fun isReady(context: Context): Boolean {
        return FFmpegDetector.detect(context).isAvailable
    }

    /**
     * Initializes the Android-compatible native FFmpeg/FFprobe binaries for the current device ABI.
     */
    suspend fun installOrUpdateFFmpeg(
        context: Context,
        onProgress: (Float) -> Unit = {}
    ): Result<FFmpegStatus> = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val supportedAbis = Build.SUPPORTED_ABIS
        val primaryAbi = if (supportedAbis.isNotEmpty()) supportedAbis[0] else "arm64-v8a"
        AppLogger.i(TAG, "Starting native FFmpeg initialization for ABI: $primaryAbi (Supported: ${supportedAbis.joinToString(", ")})")
        onProgress(15f)

        try {
            // Step 1: Initialize native FFmpeg binaries in app's execution environment
            FFmpeg.getInstance().init(appContext)
            onProgress(80f)

            isInitialized = true
            onProgress(100f)

            val status = FFmpegDetector.detect(appContext)
            AppLogger.i(TAG, "Native FFmpeg initialized and verified successfully: ${status.version ?: "Active"}")
            Result.success(status)
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            AppLogger.e(TAG, "FFmpeg native initialization failed: $msg")
            Result.failure(Exception("FFmpeg native initialization failed: $msg"))
        }
    }
}
