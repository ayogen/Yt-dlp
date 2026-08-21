package com.example.engine

import android.content.Context
import android.os.Build
import com.example.data.model.EngineState
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

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
    private const val PREFS_NAME = "ytdlp_engine_prefs"
    private const val KEY_VERIFIED_VERSION = "verified_version"
    private const val OBSOLETE_BUNDLED_VERSION = "2025.02.19"

    @Volatile
    var isInitialized = false
        private set

    @Volatile
    private var cachedVersion: String? = null

    private val operationMutex = Mutex()

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
        return isInitialized || detect(context).isReady
    }

    /**
     * Initializes the YoutubeDL native Python runtime and packages.
     * This is idempotent, thread-safe, and executed during app startup.
     */
    suspend fun ensureInitialized(context: Context): Result<String> = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            ensureInitializedInternal(context.applicationContext)
        }
    }

    private suspend fun ensureInitializedInternal(appContext: Context): Result<String> {
        return try {
            if (!isInitialized) {
                AppLogger.i(TAG, "Initializing YoutubeDL runtime...")
                YoutubeDL.getInstance().init(appContext)
                isInitialized = true
            }

            var ver = cachedVersion
            if (ver.isNullOrBlank()) {
                val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ver = prefs.getString(KEY_VERIFIED_VERSION, null)
            }
            if (ver.isNullOrBlank()) {
                ver = try {
                    val req = YoutubeDLRequest(emptyList())
                    req.addOption("--version")
                    val resp = YoutubeDL.getInstance().execute(req)
                    resp.out?.trim()?.lines()?.firstOrNull { it.isNotBlank() }?.trim()
                } catch (e: Exception) {
                    null
                } ?: try {
                    YoutubeDL.getInstance().version(appContext)
                } catch (e: Exception) {
                    null
                }
            }

            // If verified version exists and is not the obsolete 2025.02.19 bundle
            if (!ver.isNullOrBlank() && ver != OBSOLETE_BUNDLED_VERSION) {
                cachedVersion = ver
                appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_VERIFIED_VERSION, ver)
                    .apply()
                AppLogger.i(TAG, "yt-dlp runtime initialized and ready: version $ver")
                Result.success(ver)
            } else {
                AppLogger.i(TAG, "yt-dlp runtime initialized (current version check pending)")
                Result.success(ver ?: "")
            }
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            AppLogger.e(TAG, "YoutubeDL.init failed: $msg")
            Result.failure(e)
        }
    }

    fun init(context: Context) {
        val appContext = context.applicationContext
        try {
            if (!isInitialized) {
                YoutubeDL.getInstance().init(appContext)
                isInitialized = true
                val ver = YoutubeDL.getInstance().version(appContext)
                if (!ver.isNullOrBlank() && ver != OBSOLETE_BUNDLED_VERSION) {
                    cachedVersion = ver
                    appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString(KEY_VERIFIED_VERSION, ver)
                        .apply()
                }
                AppLogger.i(TAG, "YoutubeDL synchronous init completed: ${ver ?: "ready"}")
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "YoutubeDL synchronous init exception: ${e.message}")
        }
    }

    fun detect(context: Context): YtDlpStatus {
        val appContext = context.applicationContext
        val primaryAbi = if (Build.SUPPORTED_ABIS.isNotEmpty()) Build.SUPPORTED_ABIS[0] else "unknown"
        val ytdlpFile = File(appContext.noBackupFilesDir, "youtubedl-android/yt-dlp/yt-dlp")
        val ytdlpDir = File(appContext.noBackupFilesDir, "youtubedl-android/yt-dlp")
        val pythonPkg = File(appContext.noBackupFilesDir, "youtubedl-android/packages/python")

        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedVer = cachedVersion ?: prefs.getString(KEY_VERIFIED_VERSION, null)

        val isYtDlpExtracted = ytdlpFile.exists() || ytdlpDir.exists() || isInitialized

        // Valid current runtime present
        return if (savedVer != null && savedVer != OBSOLETE_BUNDLED_VERSION && (isYtDlpExtracted || pythonPkg.exists())) {
            YtDlpStatus(
                state = EngineState.READY,
                version = savedVer,
                binaryPath = if (ytdlpFile.exists()) ytdlpFile.absolutePath else ytdlpDir.absolutePath,
                isExecutable = true,
                latestVersion = savedVer,
                isUpdateAvailable = false,
                guidance = "yt-dlp Python runtime is active and verified ($savedVer).",
                diagnosticDetails = "Version: $savedVer | ABI: $primaryAbi | Runtime: Android Python 3"
            )
        } else if (savedVer == OBSOLETE_BUNDLED_VERSION) {
            // Outdated version needs update to current runtime
            YtDlpStatus(
                state = EngineState.MISSING,
                version = savedVer,
                binaryPath = if (ytdlpFile.exists()) ytdlpFile.absolutePath else ytdlpDir.absolutePath,
                isExecutable = false,
                latestVersion = null,
                isUpdateAvailable = true,
                guidance = "yt-dlp runtime is outdated ($savedVer). Updating to current release...",
                diagnosticDetails = "Outdated version $savedVer | ABI: $primaryAbi"
            )
        } else {
            YtDlpStatus(
                state = EngineState.MISSING,
                version = null,
                binaryPath = null,
                isExecutable = false,
                latestVersion = null,
                isUpdateAvailable = false,
                guidance = "yt-dlp Python runtime is initializing...",
                diagnosticDetails = "Runtime uninitialized | ABI: $primaryAbi"
            )
        }
    }

    suspend fun getVersionInfo(context: Context): YtDlpVersionInfo = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val status = detect(appContext)
        val currentVersion = status.version ?: "Not Installed"

        YtDlpVersionInfo(
            currentVersion = currentVersion,
            latestVersion = currentVersion,
            isUpdateAvailable = false,
            binaryPath = status.binaryPath ?: "${appContext.noBackupFilesDir.absolutePath}/youtubedl-android/yt-dlp/yt-dlp",
            isExecutable = status.isExecutable,
            state = status.state
        )
    }

    /**
     * Downloads and applies the latest yt-dlp update using the youtubedl-android runtime updater,
     * then executes '--version' against the real Python runtime to verify the new version on disk.
     */
    suspend fun updateYoutubeDlp(
        context: Context,
        channel: YoutubeDL.UpdateChannel = YoutubeDL.UpdateChannel.STABLE,
        onProgress: (Float) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            updateYoutubeDlpInternal(context.applicationContext, channel, onProgress)
        }
    }

    private suspend fun updateYoutubeDlpInternal(
        appContext: Context,
        channel: YoutubeDL.UpdateChannel,
        onProgress: (Float) -> Unit
    ): Result<String> {
        return try {
            AppLogger.i(TAG, "Initiating yt-dlp runtime update via channel: $channel...")
            onProgress(15f)

            // Step 1: Ensure underlying runtime is initialized
            try {
                if (!isInitialized) {
                    YoutubeDL.getInstance().init(appContext)
                    isInitialized = true
                }
            } catch (e: Exception) {
                AppLogger.d(TAG, "YoutubeDL.init notice: ${e.message}")
            }
            onProgress(35f)

            // Step 2: Call real YoutubeDL updater to replace yt-dlp binary/zip on disk
            AppLogger.i(TAG, "Executing YoutubeDL.getInstance().updateYoutubeDL($channel)...")
            val updateStatus = YoutubeDL.getInstance().updateYoutubeDL(appContext, channel)
            AppLogger.i(TAG, "YoutubeDL.updateYoutubeDL finished with status: $updateStatus")
            onProgress(75f)

            // Step 3: Directly execute '--version' against the newly updated runtime
            var verifiedVersion: String? = null
            try {
                val req = YoutubeDLRequest(emptyList())
                req.addOption("--version")
                val response = YoutubeDL.getInstance().execute(req)
                val stdout = response.out?.trim()
                val stderr = response.err?.trim()
                AppLogger.i(TAG, "Post-update runtime '--version' exit code: ${response.exitCode}, stdout: '$stdout', stderr: '$stderr'")

                if (!stdout.isNullOrBlank()) {
                    verifiedVersion = stdout.lines().firstOrNull { it.isNotBlank() }?.trim()
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "Direct execute '--version' check after update: ${e.message}")
            }

            // Step 4: Fallback to YoutubeDL.getInstance().version(appContext) if direct execute check was empty
            if (verifiedVersion.isNullOrBlank()) {
                verifiedVersion = try {
                    YoutubeDL.getInstance().version(appContext)
                } catch (e: Exception) {
                    null
                }
            }

            if (!verifiedVersion.isNullOrBlank()) {
                cachedVersion = verifiedVersion
                appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_VERIFIED_VERSION, verifiedVersion)
                    .apply()

                onProgress(100f)
                AppLogger.i(TAG, "yt-dlp successfully updated and verified in runtime: $verifiedVersion (updateStatus: $updateStatus)")
                Result.success(verifiedVersion)
            } else {
                throw Exception("yt-dlp update status was $updateStatus, but the runtime version could not be verified.")
            }
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            AppLogger.e(TAG, "Failed to update yt-dlp engine: $msg")
            Result.failure(Exception("yt-dlp update failed: $msg", e))
        }
    }

    /**
     * Initializes the Android-compatible Python runtime and installs/verifies current yt-dlp execution.
     * On first launch / installation, it invokes updateYoutubeDlp to install the current yt-dlp runtime
     * (the exact same proven mechanism as Reinstall), verifying the actual runtime version via '--version'.
     */
    suspend fun installOrUpdateBinary(
        context: Context,
        onProgress: (Float) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            installOrUpdateBinaryInternal(context.applicationContext, onProgress)
        }
    }

    private suspend fun installOrUpdateBinaryInternal(
        appContext: Context,
        onProgress: (Float) -> Unit
    ): Result<String> {
        return try {
            AppLogger.i(TAG, "Starting first-launch / clean install for yt-dlp runtime...")
            onProgress(10f)

            // Step 1: Initialize YoutubeDL native runtime & Python stdlib
            try {
                if (!isInitialized) {
                    YoutubeDL.getInstance().init(appContext)
                    isInitialized = true
                }
            } catch (e: Exception) {
                AppLogger.d(TAG, "YoutubeDL.init notice: ${e.message}")
            }
            onProgress(30f)

            // Step 2: Run updateYoutubeDlpInternal to download and install the current yt-dlp runtime
            val updateResult = updateYoutubeDlpInternal(appContext, YoutubeDL.UpdateChannel.STABLE) { prog ->
                // Map progress from 30% to 90%
                onProgress(30f + (prog * 0.6f))
            }

            if (updateResult.isSuccess) {
                val installedVer = updateResult.getOrNull()
                if (!installedVer.isNullOrBlank()) {
                    onProgress(100f)
                    AppLogger.i(TAG, "First-launch yt-dlp runtime installation successful: $installedVer")
                    return Result.success(installedVer)
                }
            }

            // If updateYoutubeDlp failed (e.g. offline first launch), verify whether a working runtime is already available
            AppLogger.w(TAG, "yt-dlp online update step returned: ${updateResult.exceptionOrNull()?.message}, checking runtime execution...")
            var verifiedVersion: String? = null
            try {
                val req = YoutubeDLRequest(emptyList())
                req.addOption("--version")
                val response = YoutubeDL.getInstance().execute(req)
                val stdout = response.out?.trim()
                if (!stdout.isNullOrBlank()) {
                    verifiedVersion = stdout.lines().firstOrNull { it.isNotBlank() }?.trim()
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "Direct execute '--version' check: ${e.message}")
            }

            if (verifiedVersion.isNullOrBlank()) {
                verifiedVersion = try {
                    YoutubeDL.getInstance().version(appContext)
                } catch (e: Exception) {
                    null
                }
            }

            if (!verifiedVersion.isNullOrBlank() && verifiedVersion != OBSOLETE_BUNDLED_VERSION) {
                cachedVersion = verifiedVersion
                appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_VERIFIED_VERSION, verifiedVersion)
                    .apply()

                onProgress(100f)
                AppLogger.i(TAG, "yt-dlp verified successfully inside Android Python runtime: $verifiedVersion")
                Result.success(verifiedVersion)
            } else {
                val failureReason = updateResult.exceptionOrNull()?.message ?: "yt-dlp runtime could not be installed on device."
                throw Exception(failureReason)
            }
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            AppLogger.e(TAG, "Failed to install yt-dlp runtime: $msg")
            Result.failure(Exception("yt-dlp setup failed: $msg", e))
        }
    }
}
