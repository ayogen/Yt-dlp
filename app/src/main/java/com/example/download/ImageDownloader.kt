package com.example.download

import android.content.Context
import com.example.data.model.MediaType
import com.example.engine.AppLogger
import com.example.engine.DirectMediaInspector
import com.example.engine.FilenameFormatter
import com.example.engine.HttpCoroutineUtils.executeAsync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object ImageDownloader {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    data class ImageDownloadResult(
        val finalPathOrSafUri: String,
        val fileName: String,
        val totalBytes: Long,
        val mimeType: String
    )

    /**
     * Downloads an image directly via HTTP streaming without loading the entire image into RAM.
     * Provides realtime progress callbacks, pause/cancellation checks, and atomic export to SAF.
     */
    suspend fun downloadImage(
        context: Context,
        imageUrl: String,
        suggestedTitle: String,
        customExt: String? = null,
        safTreeUri: String? = null,
        isCancelled: () -> Boolean = { false },
        isPaused: () -> Boolean = { false },
        onProgress: (progress: Float, downloaded: Long, total: Long, speed: Double, eta: Long) -> Unit = { _, _, _, _, _ -> },
        onLog: (String) -> Unit = {}
    ): Result<ImageDownloadResult> = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        try {
            onLog("Initiating image download from: $imageUrl")

            val request = Request.Builder()
                .url(imageUrl)
                .get()
                .header("User-Agent", USER_AGENT)
                .header("Accept", "image/*,*/*;q=0.8")
                .build()

            val response = httpClient.executeAsync(request)
            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                return@withContext Result.failure(Exception("HTTP $code: Failed to download image stream"))
            }

            val body = response.body ?: run {
                response.close()
                return@withContext Result.failure(Exception("Response body is null"))
            }

            val rawContentType = response.header("Content-Type").orEmpty().lowercase()
            val totalBytes = body.contentLength()

            val ext = when {
                !customExt.isNullOrBlank() -> customExt.removePrefix(".")
                rawContentType.contains("jpeg") || rawContentType.contains("jpg") -> "jpg"
                rawContentType.contains("png") -> "png"
                rawContentType.contains("webp") -> "webp"
                rawContentType.contains("gif") -> "gif"
                rawContentType.contains("avif") -> "avif"
                rawContentType.contains("bmp") -> "bmp"
                rawContentType.contains("heic") -> "heic"
                imageUrl.contains(".png", ignoreCase = true) -> "png"
                imageUrl.contains(".webp", ignoreCase = true) -> "webp"
                imageUrl.contains(".gif", ignoreCase = true) -> "gif"
                else -> "jpg"
            }

            val sanitizedTitle = FilenameFormatter.sanitize(suggestedTitle.ifBlank { "image_${System.currentTimeMillis()}" })
            val finalFilename = "$sanitizedTitle.$ext"

            val stagingDir = File(context.cacheDir, "staging_downloads").apply { if (!exists()) mkdirs() }
            tempFile = File(stagingDir, "img_${System.currentTimeMillis()}_${sanitizedTitle.take(20)}.$ext.tmp")

            onLog("Saving image to staging file: ${tempFile.name} (Expected size: ${if (totalBytes > 0) "$totalBytes bytes" else "Unknown"})")

            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(tempFile)
            val buffer = ByteArray(32 * 1024)
            var bytesCopied = 0L
            var lastProgressTime = System.currentTimeMillis()
            var bytesAtLastInterval = 0L

            try {
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (isCancelled()) {
                        outputStream.close()
                        inputStream.close()
                        tempFile.delete()
                        onLog("Download cancelled by user.")
                        return@withContext Result.failure(Exception("Download cancelled"))
                    }

                    while (isPaused()) {
                        delay(200)
                        if (isCancelled()) {
                            outputStream.close()
                            inputStream.close()
                            tempFile.delete()
                            return@withContext Result.failure(Exception("Download cancelled"))
                        }
                    }

                    outputStream.write(buffer, 0, bytesRead)
                    bytesCopied += bytesRead

                    val now = System.currentTimeMillis()
                    val timeDelta = now - lastProgressTime
                    if (timeDelta >= 250) {
                        val bytesInInterval = bytesCopied - bytesAtLastInterval
                        val speed = if (timeDelta > 0) (bytesInInterval.toDouble() / (timeDelta.toDouble() / 1000.0)) else 0.0
                        val progress = if (totalBytes > 0) ((bytesCopied.toFloat() / totalBytes.toFloat()) * 100f).coerceIn(0f, 100f) else 50f
                        val remainingBytes = if (totalBytes > bytesCopied) totalBytes - bytesCopied else 0L
                        val eta = if (speed > 0) (remainingBytes / speed).toLong() else 0L

                        onProgress(progress, bytesCopied, totalBytes, speed, eta)
                        lastProgressTime = now
                        bytesAtLastInterval = bytesCopied
                    }
                }
                outputStream.flush()
            } finally {
                try { outputStream.close() } catch (_: Exception) {}
                try { inputStream.close() } catch (_: Exception) {}
                try { response.close() } catch (_: Exception) {}
            }

            if (!tempFile.exists() || tempFile.length() <= 0) {
                return@withContext Result.failure(Exception("Downloaded image file is empty"))
            }

            // Final 100% progress
            onProgress(100f, bytesCopied, bytesCopied, 0.0, 0L)
            onLog("Image stream received successfully (${bytesCopied} bytes). Validating header...")

            // Basic validation
            val validation = validateImageFile(tempFile)
            if (validation.isFailure) {
                tempFile.delete()
                return@withContext Result.failure(validation.exceptionOrNull() ?: Exception("Corrupted image file received"))
            }

            // Export to SAF or local storage directory
            val finalTargetLocation: String = if (!safTreeUri.isNullOrBlank() && StorageUtils.isSafUriWritable(context, safTreeUri)) {
                onLog("Exporting image to SAF directory: $safTreeUri")
                val safResult = StorageUtils.exportFileToSaf(
                    context = context,
                    sourceFile = tempFile,
                    treeUriString = safTreeUri,
                    mediaType = MediaType.IMAGE,
                    customFilename = finalFilename
                )
                if (safResult.isSuccess) {
                    tempFile.delete()
                    safResult.getOrThrow()
                } else {
                    onLog("SAF export warning: ${safResult.exceptionOrNull()?.message}. Moving to app Downloads.")
                    fallbackMoveToDownloads(context, tempFile, finalFilename)
                }
            } else {
                fallbackMoveToDownloads(context, tempFile, finalFilename)
            }

            onLog("Image download complete: $finalTargetLocation")
            Result.success(
                ImageDownloadResult(
                    finalPathOrSafUri = finalTargetLocation,
                    fileName = finalFilename,
                    totalBytes = bytesCopied,
                    mimeType = rawContentType.ifBlank { "image/jpeg" }
                )
            )
        } catch (e: Exception) {
            tempFile?.delete()
            AppLogger.e("ImageDownloader", "Image download failed: ${e.message}")
            Result.failure(e)
        }
    }

    private fun fallbackMoveToDownloads(context: Context, stagingFile: File, finalFilename: String): String {
        val imagesDir = StorageUtils.getFallbackDownloadDirectory(context, StorageUtils.SUBDIR_IMAGES)
        var targetFile = File(imagesDir, finalFilename)
        if (targetFile.exists()) {
            val base = finalFilename.substringBeforeLast(".")
            val ext = finalFilename.substringAfterLast(".", "jpg")
            targetFile = File(imagesDir, "${base}_${System.currentTimeMillis()}.$ext")
        }
        stagingFile.copyTo(targetFile, overwrite = true)
        stagingFile.delete()
        StorageUtils.scanMediaFile(context, targetFile, "image/*")
        return targetFile.absolutePath
    }

    private fun validateImageFile(file: File): Result<Unit> {
        return try {
            val bytes = ByteArray(64)
            val read = file.inputStream().use { it.read(bytes) }
            if (read < 4) return Result.failure(Exception("Image file is too small or truncated"))
            val classified = DirectMediaInspector.classifyMagicBytes(bytes, read)
            if (classified != null && classified.first == MediaType.IMAGE) {
                Result.success(Unit)
            } else {
                // If magic bytes were generic, check text/html check
                val str = String(bytes, 0, read, Charsets.US_ASCII).lowercase()
                if (str.contains("<html") || str.contains("<!doctype")) {
                    Result.failure(Exception("Server returned an HTML webpage instead of an image"))
                } else {
                    Result.success(Unit)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
