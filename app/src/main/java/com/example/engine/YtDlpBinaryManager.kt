package com.example.engine

import android.content.Context
import android.os.Build
import com.example.data.model.EngineState
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class YtDlpVersionInfo(
    val currentVersion: String,
    val latestVersion: String,
    val isUpdateAvailable: Boolean,
    val binaryPath: String,
    val isExecutable: Boolean,
    val state: EngineState = if (isExecutable) EngineState.READY else EngineState.MISSING
)

data class YtDlpStatus(
    val state: EngineState,
    val version: String?,
    val binaryPath: String?,
    val isExecutable: Boolean,
    val latestVersion: String?,
    val isUpdateAvailable: Boolean,
    val guidance: String,
    val diagnosticDetails: String? = null
) {
    val isReady: Boolean get() = state == EngineState.READY
}

object YtDlpBinaryManager {
    private const val TAG = "YtDlpBinaryManager"
    private const val GITHUB_API_URL = "https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest"
    private const val FALLBACK_VERSION = "2025.02.19"

    @Volatile
    private var isInitialized = false

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun getBinDir(context: Context): File {
        val binDir = File(context.filesDir, "bin")
        if (!binDir.exists()) {
            binDir.mkdirs()
        }
        return binDir
    }

    fun getBinaryFile(context: Context): File {
        return File(getBinDir(context), "yt-dlp")
    }

    fun isReady(context: Context): Boolean {
        return detect(context).isReady
    }

    fun detect(context: Context): YtDlpStatus {
        val primaryAbi = if (Build.SUPPORTED_ABIS.isNotEmpty()) Build.SUPPORTED_ABIS[0] else "unknown"
        return try {
            val ver = YoutubeDL.getInstance().version(context)
            if (!ver.isNullOrBlank()) {
                isInitialized = true
                YtDlpStatus(
                    state = EngineState.READY,
                    version = ver,
                    binaryPath = "${context.filesDir.absolutePath}/packages/yt-dlp",
                    isExecutable = true,
                    latestVersion = null,
                    isUpdateAvailable = false,
                    guidance = "yt-dlp Python runtime is active and verified ($ver).",
                    diagnosticDetails = "Version: $ver | ABI: $primaryAbi | Runtime: Android Python 3"
                )
            } else {
                YtDlpStatus(
                    state = EngineState.MISSING,
                    version = null,
                    binaryPath = null,
                    isExecutable = false,
                    latestVersion = null,
                    isUpdateAvailable = false,
                    guidance = "yt-dlp Python runtime is not initialized. Tap 'Install Engine' to set up.",
                    diagnosticDetails = "Runtime uninitialized | ABI: $primaryAbi"
                )
            }
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            AppLogger.d(TAG, "yt-dlp detect status: $msg")
            YtDlpStatus(
                state = EngineState.MISSING,
                version = null,
                binaryPath = null,
                isExecutable = false,
                latestVersion = null,
                isUpdateAvailable = false,
                guidance = "yt-dlp engine is not initialized. Tap 'Install Engine' to set up.",
                diagnosticDetails = "Detection check: $msg | ABI: $primaryAbi"
            )
        }
    }

    suspend fun getVersionInfo(context: Context): YtDlpVersionInfo = withContext(Dispatchers.IO) {
        val status = detect(context)
        val currentVersion = status.version ?: FALLBACK_VERSION
        val latest = fetchLatestReleaseTag() ?: currentVersion
        val isUpdate = status.state == EngineState.READY && latest != currentVersion && latest.isNotBlank()

        YtDlpVersionInfo(
            currentVersion = currentVersion,
            latestVersion = latest,
            isUpdateAvailable = isUpdate,
            binaryPath = status.binaryPath ?: "${context.filesDir.absolutePath}/packages/yt-dlp",
            isExecutable = status.isExecutable,
            state = status.state
        )
    }

    suspend fun fetchLatestReleaseTag(): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_API_URL)
                .header("User-Agent", "VideoDownloader-Android/2.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@use null
                    val json = JSONObject(body)
                    return@use json.optString("tag_name", "").ifBlank { null }
                }
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Could not check GitHub releases: ${e.message}")
        }
        null
    }

    /**
     * Initializes the Android-compatible Python runtime and extracts/prepares yt-dlp.
     */
    suspend fun installOrUpdateBinary(
        context: Context,
        onProgress: (Float) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            AppLogger.i(TAG, "Initializing Android Python runtime for yt-dlp...")
            onProgress(15f)

            // Step 1: Initialize YoutubeDL native runtime & Python stdlib
            try {
                YoutubeDL.getInstance().init(context)
            } catch (e: Exception) {
                AppLogger.d(TAG, "YoutubeDL.init note: ${e.message}")
            }
            onProgress(65f)

            // Step 2: Verify real execution through embedded Python runtime
            val verifiedVersion = YoutubeDL.getInstance().version(context)
            onProgress(90f)

            if (!verifiedVersion.isNullOrBlank()) {
                isInitialized = true
                onProgress(100f)
                AppLogger.i(TAG, "yt-dlp verified successfully inside Android Python runtime: $verifiedVersion")
                Result.success(verifiedVersion)
            } else {
                throw Exception("yt-dlp initialization completed but version verification returned empty output.")
            }
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            AppLogger.e(TAG, "yt-dlp runtime setup failed: $msg")
            Result.failure(Exception("yt-dlp setup failed: $msg"))
        }
    }

    suspend fun updateBinary(
        context: Context,
        onProgress: (Float) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            AppLogger.i(TAG, "Updating yt-dlp via official channel...")
            onProgress(20f)
            val status = YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
            onProgress(70f)
            val verifiedVersion = YoutubeDL.getInstance().version(context) ?: FALLBACK_VERSION
            onProgress(100f)
            AppLogger.i(TAG, "yt-dlp update status: $status (Version: $verifiedVersion)")
            Result.success(verifiedVersion)
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            AppLogger.e(TAG, "yt-dlp update failed: $msg")
            Result.failure(Exception("yt-dlp update failed: $msg"))
        }
    }
}
