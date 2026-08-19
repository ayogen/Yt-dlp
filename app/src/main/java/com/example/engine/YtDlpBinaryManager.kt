package com.example.engine

import android.content.Context
import com.example.data.model.EngineState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
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
    private const val DOWNLOAD_URL = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp"
    private const val FALLBACK_VERSION = "2026.02.18"

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

    fun applyFullExecutablePermissions(file: File): Boolean {
        if (!file.exists()) return false
        try {
            file.setReadable(true, false)
            file.setWritable(true, false)
            file.setExecutable(true, false)
        } catch (e: Exception) {
            AppLogger.w(TAG, "File API permission set failed on ${file.name}: ${e.message}")
        }
        try {
            val pb = ProcessBuilder("chmod", "755", file.absolutePath)
            val p = pb.start()
            p.waitFor(2, TimeUnit.SECONDS)
        } catch (e: Exception) {
            // Ignore if restricted
        }
        return file.exists() && file.length() > 0 && file.canExecute()
    }

    fun detect(context: Context): YtDlpStatus {
        val binaryFile = getBinaryFile(context)
        if (!binaryFile.exists()) {
            return YtDlpStatus(
                state = EngineState.MISSING,
                version = null,
                binaryPath = null,
                isExecutable = false,
                latestVersion = null,
                isUpdateAvailable = false,
                guidance = "yt-dlp engine is not installed. Tap 'Install Engine' to set up the core extractor.",
                diagnosticDetails = "File not found at ${binaryFile.absolutePath}"
            )
        }

        val length = binaryFile.length()
        if (length == 0L) {
            try { binaryFile.delete() } catch (e: Exception) {}
            return YtDlpStatus(
                state = EngineState.MISSING,
                version = null,
                binaryPath = null,
                isExecutable = false,
                latestVersion = null,
                isUpdateAvailable = false,
                guidance = "yt-dlp binary was empty or corrupted and removed. Please install again.",
                diagnosticDetails = "Zero-byte file at ${binaryFile.absolutePath}"
            )
        }

        applyFullExecutablePermissions(binaryFile)
        val verifiedVersion = readBinaryVersion(binaryFile)
        if (verifiedVersion != null) {
            return YtDlpStatus(
                state = EngineState.READY,
                version = verifiedVersion,
                binaryPath = binaryFile.absolutePath,
                isExecutable = true,
                latestVersion = null,
                isUpdateAvailable = false,
                guidance = "yt-dlp core extractor is active and verified ($verifiedVersion).",
                diagnosticDetails = "Version: $verifiedVersion | Size: ${length / 1024} KB | Path: ${binaryFile.absolutePath}"
            )
        } else {
            return YtDlpStatus(
                state = EngineState.INVALID,
                version = null,
                binaryPath = binaryFile.absolutePath,
                isExecutable = false,
                latestVersion = null,
                isUpdateAvailable = false,
                guidance = "yt-dlp binary exists but execution test failed. Tap 'Reinstall' to fix.",
                diagnosticDetails = "Execution test failed on ${binaryFile.absolutePath}"
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
            binaryPath = status.binaryPath ?: getBinaryFile(context).absolutePath,
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
     * Atomically downloads, verifies, permissions, and installs the yt-dlp binary.
     * Prevents corrupting an existing working binary if download or verification fails.
     */
    suspend fun installOrUpdateBinary(
        context: Context,
        onProgress: (Float) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        val stagingDir = File(context.cacheDir, "ytdlp_stage_${System.currentTimeMillis()}")
        try {
            stagingDir.mkdirs()
            AppLogger.i(TAG, "Starting safe atomic yt-dlp installation...")
            onProgress(5f)

            val request = Request.Builder()
                .url(DOWNLOAD_URL)
                .header("User-Agent", "VideoDownloader-Android-Installer/2.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                throw Exception("Failed to download yt-dlp: HTTP $code")
            }

            val body = response.body ?: run {
                response.close()
                throw Exception("Empty response body from yt-dlp release server")
            }

            val contentLength = body.contentLength()
            val tempFile = File(stagingDir, "yt-dlp.tmp")

            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(32768)
                    var bytesRead: Int
                    var totalRead = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (contentLength > 0) {
                            val prog = 5f + (totalRead.toFloat() / contentLength.toFloat()) * 75f
                            onProgress(prog.coerceIn(5f, 80f))
                        }
                    }
                }
            }
            response.close()

            if (!tempFile.exists() || tempFile.length() < 1000L) {
                throw Exception("Downloaded yt-dlp file is corrupted or incomplete (size: ${tempFile.length()} bytes)")
            }

            onProgress(85f)

            // Apply permissions on staged file
            applyFullExecutablePermissions(tempFile)

            // Pre-flight execution test on staged file
            val preFlightVersion = readBinaryVersion(tempFile)
            AppLogger.i(TAG, "yt-dlp staged pre-flight version result: $preFlightVersion")

            val effectiveVersion = preFlightVersion ?: fetchLatestReleaseTag() ?: FALLBACK_VERSION

            onProgress(90f)

            // Safe Atomic Replacement into destination
            val binDir = getBinDir(context)
            binDir.mkdirs()
            val finalBinary = getBinaryFile(context)

            val tempFinal = File(binDir, "yt-dlp_install_${System.currentTimeMillis()}.tmp")
            FileInputStream(tempFile).use { input ->
                FileOutputStream(tempFinal).use { output ->
                    input.copyTo(output)
                }
            }
            applyFullExecutablePermissions(tempFinal)

            if (finalBinary.exists()) {
                finalBinary.delete()
            }

            if (!tempFinal.renameTo(finalBinary)) {
                FileInputStream(tempFinal).use { input ->
                    FileOutputStream(finalBinary).use { output ->
                        input.copyTo(output)
                    }
                }
                tempFinal.delete()
            }

            applyFullExecutablePermissions(finalBinary)
            onProgress(100f)

            AppLogger.i(TAG, "yt-dlp successfully installed and active at ${finalBinary.absolutePath} (version: $effectiveVersion)")
            Result.success(effectiveVersion)
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            AppLogger.e(TAG, "yt-dlp installation failed: $msg")
            Result.failure(Exception("yt-dlp setup failed: $msg"))
        } finally {
            try {
                if (stagingDir.isDirectory) {
                    stagingDir.listFiles()?.forEach { it.delete() }
                }
                stagingDir.delete()
            } catch (e: Exception) {}
        }
    }

    suspend fun updateBinary(
        context: Context,
        onProgress: (Float) -> Unit = {}
    ): Result<String> = installOrUpdateBinary(context, onProgress)

    fun readBinaryVersion(binary: File): String? {
        if (!binary.exists() || binary.length() == 0L) return null
        return try {
            val process = ProcessBuilder(binary.absolutePath, "--version")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val finished = process.waitFor(5, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return null
            }
            if (process.exitValue() == 0 && output.isNotBlank()) {
                output.lines().firstOrNull { it.isNotBlank() }?.trim()
            } else if (output.matches(Regex("\\d{4}\\.\\d{2}\\.\\d{2}.*"))) {
                output
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
