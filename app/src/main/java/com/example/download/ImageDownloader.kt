package com.example.download

import android.content.Context
import com.example.data.model.MediaType
import com.example.engine.AppLogger
import com.example.engine.DirectMediaInspector
import com.example.engine.FilenameFormatter
import com.example.engine.HttpCoroutineUtils.executeAsync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URI
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
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

    data class ImageDownloadResult(
        val finalPathOrSafUri: String,
        val fileName: String,
        val totalBytes: Long,
        val mimeType: String
    )

    /**
     * Resolves an appropriate context-aware Referer based on the target image URL
     * or associated webpage URL to bypass CDN 403 Forbidden checks.
     */
    fun resolveReferer(imageUrl: String, pageUrl: String? = null): String {
        if (!pageUrl.isNullOrBlank()) {
            return pageUrl
        }
        val host = runCatching { URI(imageUrl).host?.lowercase() }.getOrNull().orEmpty()
        return when {
            host.contains("instagram.com") || host.contains("cdninstagram.com") || host.contains("fbcdn.net") ->
                "https://www.instagram.com/"
            host.contains("tiktok.com") || host.contains("tiktokcdn.com") || host.contains("byteoversea.com") || host.contains("ibyteimg.com") ->
                "https://www.tiktok.com/"
            host.contains("reddit.com") || host.contains("redditmedia.com") || host.contains("redd.it") ->
                "https://www.reddit.com/"
            host.contains("twitter.com") || host.contains("x.com") || host.contains("twimg.com") ->
                "https://x.com/"
            host.contains("pinterest.com") || host.contains("pinimg.com") ->
                "https://www.pinterest.com/"
            host.contains("facebook.com") ->
                "https://www.facebook.com/"
            host.contains("youtube.com") || host.contains("youtu.be") || host.contains("ytimg.com") || host.contains("ggpht.com") ->
                "https://www.youtube.com/"
            host.contains("threads.net") ->
                "https://www.threads.net/"
            host.isNotBlank() ->
                "https://$host/"
            else ->
                "https://www.google.com/"
        }
    }

    /**
     * Downloads an image or direct media stream directly via HTTP streaming without loading into RAM.
     * Provides realtime progress callbacks, non-busy pause/cancellation handling, and atomic export to SAF.
     */
    suspend fun downloadImage(
        context: Context,
        imageUrl: String,
        suggestedTitle: String,
        customExt: String? = null,
        safTreeUri: String? = null,
        pageUrl: String? = null,
        mediaType: MediaType = MediaType.IMAGE,
        isCancelled: () -> Boolean = { false },
        isPaused: () -> Boolean = { false },
        onProgress: (progress: Float, downloaded: Long, total: Long, speed: Double, eta: Long) -> Unit = { _, _, _, _, _ -> },
        onLog: (String) -> Unit = {}
    ): Result<ImageDownloadResult> = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        var isPausedInterrupted = false
        try {
            onLog("Initiating stream download from: $imageUrl ($mediaType)")

            if (isCancelled()) {
                onLog("Download cancelled before start.")
                return@withContext Result.failure(CancellationException("Download cancelled"))
            }

            if (isPaused()) {
                onLog("Download paused before start.")
                return@withContext Result.failure(Exception("Download paused"))
            }

            val sanitizedTitle = FilenameFormatter.sanitize(suggestedTitle.ifBlank { "media_${System.currentTimeMillis()}" })
            val stagingDir = File(context.cacheDir, "staging_downloads").apply { if (!exists()) mkdirs() }
            val urlHash = imageUrl.hashCode().toUInt().toString(16)
            tempFile = File(stagingDir, "stream_${urlHash}_${sanitizedTitle.take(24)}.tmp")

            val existingBytes = if (tempFile.exists()) tempFile.length() else 0L

            val referer = resolveReferer(imageUrl, pageUrl)
            val requestBuilder = Request.Builder()
                .url(imageUrl)
                .get()
                .header("User-Agent", USER_AGENT)
                .header("Referer", referer)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,video/*,audio/*,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Sec-Fetch-Dest", if (mediaType == MediaType.IMAGE) "image" else "video")
                .header("Sec-Fetch-Mode", "no-cors")
                .header("Sec-Fetch-Site", "cross-site")

            if (existingBytes > 0) {
                requestBuilder.header("Range", "bytes=$existingBytes-")
                onLog("Attempting resume from existing staging bytes: $existingBytes")
            }

            val response = httpClient.executeAsync(requestBuilder.build())
            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                return@withContext Result.failure(Exception("HTTP $code: Failed to download media stream from $imageUrl"))
            }

            val body = response.body ?: run {
                response.close()
                return@withContext Result.failure(Exception("Response body is null"))
            }

            val isPartialContent = response.code == 206
            val appendMode = isPartialContent && existingBytes > 0
            var bytesCopied = if (appendMode) existingBytes else 0L
            val bodyLength = body.contentLength()
            val totalBytes = if (appendMode) {
                if (bodyLength > 0) existingBytes + bodyLength else -1L
            } else {
                bodyLength
            }

            val rawContentType = response.header("Content-Type").orEmpty().lowercase()

            val ext = when {
                !customExt.isNullOrBlank() -> customExt.removePrefix(".")
                rawContentType.contains("jpeg") || rawContentType.contains("jpg") -> "jpg"
                rawContentType.contains("png") -> "png"
                rawContentType.contains("webp") -> "webp"
                rawContentType.contains("gif") -> "gif"
                rawContentType.contains("avif") -> "avif"
                rawContentType.contains("bmp") -> "bmp"
                rawContentType.contains("heic") -> "heic"
                rawContentType.contains("mp4") -> "mp4"
                rawContentType.contains("webm") -> "webm"
                rawContentType.contains("matroska") || rawContentType.contains("mkv") -> "mkv"
                rawContentType.contains("mpeg") || rawContentType.contains("mp3") -> "mp3"
                rawContentType.contains("m4a") || rawContentType.contains("aac") -> "m4a"
                rawContentType.contains("flac") -> "flac"
                rawContentType.contains("ogg") || rawContentType.contains("opus") -> "opus"
                rawContentType.contains("wav") -> "wav"
                imageUrl.contains(".mp4", ignoreCase = true) -> "mp4"
                imageUrl.contains(".webm", ignoreCase = true) -> "webm"
                imageUrl.contains(".mkv", ignoreCase = true) -> "mkv"
                imageUrl.contains(".mp3", ignoreCase = true) -> "mp3"
                imageUrl.contains(".m4a", ignoreCase = true) -> "m4a"
                imageUrl.contains(".flac", ignoreCase = true) -> "flac"
                imageUrl.contains(".opus", ignoreCase = true) -> "opus"
                imageUrl.contains(".wav", ignoreCase = true) -> "wav"
                imageUrl.contains(".png", ignoreCase = true) -> "png"
                imageUrl.contains(".webp", ignoreCase = true) -> "webp"
                imageUrl.contains(".gif", ignoreCase = true) -> "gif"
                mediaType == MediaType.AUDIO -> "mp3"
                mediaType == MediaType.VIDEO -> "mp4"
                else -> "jpg"
            }

            val finalFilename = "$sanitizedTitle.$ext"

            onLog("Streaming media to staging file: ${tempFile.name} (Expected size: ${if (totalBytes > 0) "$totalBytes bytes" else "Unknown"}, appendMode=$appendMode)")

            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(tempFile, appendMode)
            val buffer = ByteArray(128 * 1024)
            var lastProgressTime = System.currentTimeMillis()
            var bytesAtLastInterval = bytesCopied

            try {
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (isCancelled()) {
                        outputStream.close()
                        inputStream.close()
                        response.close()
                        tempFile.delete()
                        onLog("Download cancelled by user.")
                        return@withContext Result.failure(CancellationException("Download cancelled"))
                    }

                    if (isPaused()) {
                        isPausedInterrupted = true
                        onLog("Download paused by user. Gracefully releasing network socket and preserving partial stream.")
                        outputStream.flush()
                        outputStream.close()
                        inputStream.close()
                        response.close()
                        return@withContext Result.failure(Exception("Download paused"))
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
                return@withContext Result.failure(Exception("Downloaded media file is empty"))
            }

            // Final 100% progress
            onProgress(100f, bytesCopied, bytesCopied, 0.0, 0L)
            onLog("Media stream received successfully (${bytesCopied} bytes). Validating header...")

            // Basic magic byte validation
            val validation = validateMediaFile(tempFile, mediaType)
            if (validation.isFailure) {
                tempFile.delete()
                return@withContext Result.failure(validation.exceptionOrNull() ?: Exception("Corrupted media file received"))
            }

            // Export to SAF or local storage directory
            val finalTargetLocation: String = if (!safTreeUri.isNullOrBlank() && StorageUtils.isSafUriWritable(context, safTreeUri)) {
                onLog("Exporting media to SAF directory: $safTreeUri")
                val safResult = StorageUtils.exportFileToSaf(
                    context = context,
                    sourceFile = tempFile,
                    treeUriString = safTreeUri,
                    mediaType = mediaType,
                    customFilename = finalFilename
                )
                if (safResult.isSuccess) {
                    tempFile.delete()
                    safResult.getOrThrow()
                } else {
                    onLog("SAF export warning: ${safResult.exceptionOrNull()?.message}. Moving to app Downloads.")
                    fallbackMoveToDownloads(context, tempFile, finalFilename, mediaType)
                }
            } else {
                fallbackMoveToDownloads(context, tempFile, finalFilename, mediaType)
            }

            onLog("Stream download complete: $finalTargetLocation")
            Result.success(
                ImageDownloadResult(
                    finalPathOrSafUri = finalTargetLocation,
                    fileName = finalFilename,
                    totalBytes = bytesCopied,
                    mimeType = rawContentType.ifBlank {
                        if (mediaType == MediaType.VIDEO) "video/mp4" else if (mediaType == MediaType.AUDIO) "audio/mpeg" else "image/jpeg"
                    }
                )
            )
        } catch (e: Exception) {
            if (e is CancellationException || isCancelled()) {
                tempFile?.delete()
            } else if (!isPausedInterrupted && e.message != "Download paused") {
                tempFile?.delete()
            }
            AppLogger.e("ImageDownloader", "Stream download failed: ${e.message}")
            Result.failure(e)
        }
    }

    private fun fallbackMoveToDownloads(
        context: Context,
        stagingFile: File,
        finalFilename: String,
        mediaType: MediaType = MediaType.IMAGE
    ): String {
        val subfolder = StorageUtils.getSubfolderForMediaType(mediaType, finalFilename.substringAfterLast(".", ""))
        val targetDir = StorageUtils.getFallbackDownloadDirectory(context, subfolder)
        var targetFile = File(targetDir, finalFilename)
        if (targetFile.exists()) {
            val base = finalFilename.substringBeforeLast(".")
            val ext = finalFilename.substringAfterLast(".", "bin")
            targetFile = File(targetDir, "${base}_${System.currentTimeMillis()}.$ext")
        }
        stagingFile.copyTo(targetFile, overwrite = true)
        stagingFile.delete()
        val mimeType = StorageUtils.getMimeTypeForExtension(targetFile.extension, mediaType)
        StorageUtils.scanMediaFile(context, targetFile, mimeType)
        return targetFile.absolutePath
    }

    private fun validateMediaFile(file: File, mediaType: MediaType): Result<Unit> {
        return try {
            val bytes = ByteArray(64)
            val read = file.inputStream().use { it.read(bytes) }
            if (read < 4) return Result.failure(Exception("Media file is too small or truncated"))
            val str = String(bytes, 0, read, Charsets.US_ASCII).lowercase()
            if (str.contains("<html") || str.contains("<!doctype")) {
                Result.failure(Exception("Server returned an HTML webpage instead of a media file"))
            } else {
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
