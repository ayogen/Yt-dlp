package com.example.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class YtDlpVersionInfo(
    val currentVersion: String,
    val latestVersion: String,
    val isUpdateAvailable: Boolean,
    val binaryPath: String,
    val isExecutable: Boolean
)

object YtDlpBinaryManager {
    private const val GITHUB_API_URL = "https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest"
    private const val FALLBACK_VERSION = "2026.02.18"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun getBinaryFile(context: Context): File {
        val binDir = File(context.filesDir, "bin")
        if (!binDir.exists()) {
            binDir.mkdirs()
        }
        return File(binDir, "yt-dlp")
    }

    suspend fun getVersionInfo(context: Context): YtDlpVersionInfo = withContext(Dispatchers.IO) {
        val binaryFile = getBinaryFile(context)
        val currentVersion = if (binaryFile.exists()) {
            // Check version if binary exists or default
            readBinaryVersion(binaryFile) ?: FALLBACK_VERSION
        } else {
            FALLBACK_VERSION
        }

        val latest = fetchLatestReleaseTag() ?: currentVersion
        val isUpdate = latest != currentVersion && latest.isNotBlank()

        YtDlpVersionInfo(
            currentVersion = currentVersion,
            latestVersion = latest,
            isUpdateAvailable = isUpdate,
            binaryPath = binaryFile.absolutePath,
            isExecutable = binaryFile.exists() && binaryFile.canExecute()
        )
    }

    private suspend fun fetchLatestReleaseTag(): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_API_URL)
                .header("User-Agent", "YtDlpDownloader-Android")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@use null
                    val json = JSONObject(body)
                    return@use json.optString("tag_name", "").ifBlank { null }
                }
            }
        } catch (e: Exception) {
            AppLogger.w("YtDlpBinaryManager", "Could not check GitHub releases: ${e.message}")
        }
        null
    }

    suspend fun updateBinary(
        context: Context,
        onProgress: (Float) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            AppLogger.i("YtDlpBinaryManager", "Starting yt-dlp binary update check...")
            val binaryFile = getBinaryFile(context)

            // Download official yt-dlp standalone binary
            val downloadUrl = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp"
            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "YtDlpDownloader-Android")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Failed to download yt-dlp: HTTP ${response.code}"))
                }

                val body = response.body ?: return@withContext Result.failure(Exception("Empty response body"))
                val contentLength = body.contentLength()
                val tempFile = File(binaryFile.parentFile, "yt-dlp.tmp")

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalRead = 0L
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (contentLength > 0) {
                                val prog = (totalRead.toFloat() / contentLength.toFloat()) * 100f
                                onProgress(prog)
                            }
                        }
                    }
                }

                if (binaryFile.exists()) binaryFile.delete()
                tempFile.renameTo(binaryFile)
                binaryFile.setExecutable(true, false)
                binaryFile.setReadable(true, false)
            }

            val newVersion = fetchLatestReleaseTag() ?: FALLBACK_VERSION
            AppLogger.i("YtDlpBinaryManager", "yt-dlp updated successfully to version $newVersion")
            Result.success(newVersion)
        } catch (e: Exception) {
            AppLogger.e("YtDlpBinaryManager", "Failed to update yt-dlp: ${e.message}")
            Result.failure(e)
        }
    }

    private fun readBinaryVersion(binary: File): String? {
        return try {
            val process = ProcessBuilder(binary.absolutePath, "--version")
                .redirectErrorStream(true)
                .start()
            val version = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            if (version.isNotBlank() && version.matches(Regex("\\d{4}\\.\\d{2}\\.\\d{2}.*"))) {
                version
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
