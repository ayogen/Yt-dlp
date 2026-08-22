package com.example.engine

import com.example.data.model.FormatInfo
import com.example.data.model.MediaMetadata
import com.example.data.model.PlaylistEntry
import com.example.data.model.SubtitleTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object EmbeddedExtractorEngine {
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun analyzeUrl(url: String, traceId: String? = null): Result<MediaMetadata> = withContext(Dispatchers.IO) {
        val effectiveTraceId = traceId ?: MediaExtractionTracer.currentSessionFlow.value?.traceId
        val opId = if (effectiveTraceId != null) {
            MediaExtractionTracer.startOperation(
                traceId = effectiveTraceId,
                component = "EmbeddedExtractorEngine",
                stage = "EMBEDDED_EXTRACTOR",
                name = "analyzeUrl",
                details = mapOf("url" to url)
            )
        } else null

        try {
            AppLogger.i("EmbeddedExtractor", "Analyzing direct URL: $url")
            val validatedUrl = validateAndNormalizeUrl(url)
                ?: run {
                    if (effectiveTraceId != null && opId != null) {
                        MediaExtractionTracer.endOperation(
                            traceId = effectiveTraceId,
                            opId = opId,
                            error = IllegalArgumentException("Invalid URL format"),
                            decision = "INVALID_URL",
                            reason = "Invalid URL format"
                        )
                    }
                    return@withContext Result.failure(IllegalArgumentException("Invalid URL format. Please provide a valid HTTP/HTTPS address."))
                }

            val uri = URL(validatedUrl)
            val host = uri.host.lowercase()

            // Check if URL is a direct media stream
            val isDirectMedia = isDirectMediaUrl(validatedUrl)
            if (isDirectMedia) {
                val directRes = extractDirectMediaMetadata(validatedUrl)
                if (effectiveTraceId != null && opId != null) {
                    MediaExtractionTracer.endOperation(
                        traceId = effectiveTraceId,
                        opId = opId,
                        result = directRes.getOrNull()?.title,
                        decision = "DIRECT_STREAM_EXTRACTED",
                        reason = "Extracted direct media stream metadata"
                    )
                }
                return@withContext directRes
            }

            // Extract webpage HTML to obtain OpenGraph and meta tags only if a real stream exists
            val htmlContent = fetchWebpage(validatedUrl)
            val metadataResult = parseWebpageMetadata(validatedUrl, host, htmlContent)
            if (metadataResult.isSuccess) {
                val metadata = metadataResult.getOrThrow()
                AppLogger.i("EmbeddedExtractor", "Extracted web page metadata for: ${metadata.title}")
                if (effectiveTraceId != null && opId != null) {
                    MediaExtractionTracer.endOperation(
                        traceId = effectiveTraceId,
                        opId = opId,
                        result = metadata.title,
                        decision = "WEBPAGE_METADATA_EXTRACTED",
                        reason = "Extracted playable video stream from webpage"
                    )
                }
                Result.success(metadata)
            } else {
                if (effectiveTraceId != null && opId != null) {
                    MediaExtractionTracer.endOperation(
                        traceId = effectiveTraceId,
                        opId = opId,
                        decision = "NO_STREAM_FOUND",
                        reason = metadataResult.exceptionOrNull()?.message ?: "No stream found"
                    )
                }
                metadataResult
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e("EmbeddedExtractor", "Extraction failed: ${e.message}")
            if (effectiveTraceId != null && opId != null) {
                MediaExtractionTracer.endOperation(
                    traceId = effectiveTraceId,
                    opId = opId,
                    error = e,
                    decision = "EXTRACTION_FAILED",
                    reason = e.message
                )
            }
            Result.failure(e)
        }
    }

    private fun validateAndNormalizeUrl(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null
        val normalized = if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            "https://$trimmed"
        } else {
            trimmed
        }

        return try {
            val u = URL(normalized)
            if (u.host.isNullOrBlank()) null else normalized
        } catch (e: Exception) {
            null
        }
    }

    fun isDirectMediaUrl(url: String): Boolean {
        val clean = url.substringBefore("?").lowercase()
        return clean.endsWith(".mp4") || clean.endsWith(".mkv") || clean.endsWith(".webm") ||
                clean.endsWith(".mp3") || clean.endsWith(".m4a") || clean.endsWith(".opus") ||
                clean.endsWith(".wav") || clean.endsWith(".flac") || clean.endsWith(".m3u8") ||
                clean.endsWith(".ts")
    }

    private suspend fun extractDirectMediaMetadata(url: String): Result<MediaMetadata> {
        val fileName = url.substringBefore("?").substringAfterLast("/").ifBlank { "Direct Media Stream" }
        val ext = fileName.substringAfterLast(".", "mp4")
        val isAudio = ext in listOf("mp3", "m4a", "opus", "wav", "flac")

        var contentLength: Long? = null
        try {
            val headRequest = Request.Builder().url(url).head().build()
            val response = CancellableNetworkClient.executeCancellable(client, headRequest)
            response.use { resp ->
                if (resp.isSuccessful) {
                    contentLength = resp.header("Content-Length")?.toLongOrNull()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w("EmbeddedExtractor", "Could not fetch Content-Length: ${e.message}")
        }

        val formats = if (isAudio) {
            listOf(
                FormatInfo(
                    formatId = "audio-direct",
                    ext = ext,
                    acodec = ext,
                    abr = null,
                    filesize = contentLength,
                    formatNote = "Direct Audio Stream",
                    url = url
                )
            )
        } else {
            listOf(
                FormatInfo(
                    formatId = "best",
                    ext = ext,
                    resolution = "Direct Stream",
                    vcodec = "h264",
                    acodec = "aac",
                    filesize = contentLength,
                    formatNote = "Original Direct Stream",
                    url = url
                )
            )
        }

        return Result.success(
            MediaMetadata(
                id = Math.abs(url.hashCode()).toString(),
                title = fileName.substringBeforeLast("."),
                webpageUrl = url,
                uploader = "Direct Web Source",
                durationSeconds = 0L,
                viewCount = null,
                likeCount = null,
                uploadDate = "",
                description = "Direct media stream from $url",
                thumbnail = "",
                formats = formats,
                extractorName = "DirectStreamExtractor",
                directDownloadUrl = url
            )
        )
    }

    private suspend fun fetchWebpage(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        val response = CancellableNetworkClient.executeCancellable(client, request)
        response.use { resp ->
            if (!resp.isSuccessful) {
                throw Exception("HTTP ${resp.code}: ${resp.message}")
            }
            return resp.body?.string() ?: ""
        }
    }

    private fun parseWebpageMetadata(url: String, host: String, html: String): Result<MediaMetadata> {
        val videoUrl = extractTag(html, "property=\"og:video:secure_url\" content=\"([^\"]+)\"")
            ?: extractTag(html, "property=\"og:video\" content=\"([^\"]+)\"")
            ?: extractTag(html, "property=\"og:video:url\" content=\"([^\"]+)\"")
            ?: extractTag(html, "name=\"twitter:player:stream\" content=\"([^\"]+)\"")

        // Only succeed if there is an actual stream URL that is distinct from the HTML webpage
        if (videoUrl.isNullOrBlank() || videoUrl.equals(url, ignoreCase = true)) {
            return Result.failure(Exception("No downloadable media streams found on this webpage"))
        }

        val ogTitle = extractTag(html, "property=\"og:title\" content=\"([^\"]+)\"")
            ?: extractTag(html, "<title>([^<]+)</title>")
            ?: "Media ($host)"

        val ogImage = extractTag(html, "property=\"og:image\" content=\"([^\"]+)\"")
            ?: extractTag(html, "name=\"twitter:image\" content=\"([^\"]+)\"")
            ?: ""

        val ogDesc = extractTag(html, "property=\"og:description\" content=\"([^\"]+)\"")
            ?: extractTag(html, "name=\"description\" content=\"([^\"]+)\"")
            ?: ""

        return Result.success(
            MediaMetadata(
                id = Math.abs(url.hashCode()).toString(),
                title = sanitizeTitle(ogTitle),
                webpageUrl = url,
                uploader = host,
                durationSeconds = 0L,
                viewCount = null,
                likeCount = null,
                uploadDate = "",
                description = ogDesc,
                thumbnail = ogImage,
                formats = listOf(
                    FormatInfo(
                        formatId = "embedded-best",
                        ext = "mp4",
                        resolution = "Direct Stream",
                        vcodec = "h264",
                        acodec = "aac",
                        url = videoUrl
                    )
                ),
                extractorName = "GenericWebExtractor",
                directDownloadUrl = videoUrl
            )
        )
    }

    private fun extractTag(html: String, regex: String): String? {
        val matcher = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(html)
        return if (matcher.find()) matcher.group(1)?.trim() else null
    }

    private fun sanitizeTitle(title: String): String {
        return title.replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
    }

    suspend fun downloadDirectStream(
        taskId: String,
        url: String,
        destinationFile: File,
        targetTotalBytes: Long? = null,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long, speedBytesPerSec: Double, etaSeconds: Long) -> Unit,
        isCancelled: () -> Boolean,
        isPaused: () -> Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (isCancelled()) {
                return@withContext Result.failure(CancellationException("Download cancelled"))
            }

            destinationFile.parentFile?.mkdirs()
            var existingLength = 0L
            if (destinationFile.exists()) {
                existingLength = destinationFile.length()
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

            if (existingLength > 0L) {
                requestBuilder.header("Range", "bytes=$existingLength-")
            }

            val response = CancellableNetworkClient.executeCancellable(client, requestBuilder.build())
            if (!response.isSuccessful && response.code != 206) {
                response.close()
                return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }

            val isPartial = response.code == 206
            val responseBody = response.body ?: return@withContext Result.failure(Exception("Empty response body"))
            val streamLength = responseBody.contentLength()
            val totalBytes = if (isPartial) {
                existingLength + if (streamLength > 0) streamLength else (targetTotalBytes ?: 0L)
            } else {
                if (streamLength > 0) streamLength else (targetTotalBytes ?: 0L)
            }

            val raf = RandomAccessFile(destinationFile, "rw")
            if (isPartial) {
                raf.seek(existingLength)
            } else {
                raf.setLength(0)
                existingLength = 0L
            }

            var downloadedBytes = existingLength
            val buffer = ByteArray(64 * 1024)
            var lastTime = System.currentTimeMillis()
            var bytesSinceLastTime = 0L
            var currentSpeed = 0.0

            responseBody.byteStream().use { input ->
                raf.use { output ->
                    while (true) {
                        if (isCancelled()) {
                            return@withContext Result.failure(CancellationException("Download cancelled"))
                        }
                        while (isPaused()) {
                            if (isCancelled()) {
                                return@withContext Result.failure(CancellationException("Download cancelled"))
                            }
                            kotlinx.coroutines.delay(200)
                        }

                        val read = input.read(buffer)
                        if (read == -1) break

                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        bytesSinceLastTime += read

                        val now = System.currentTimeMillis()
                        val diff = now - lastTime
                        if (diff >= 500) {
                            currentSpeed = (bytesSinceLastTime * 1000.0) / diff
                            lastTime = now
                            bytesSinceLastTime = 0L

                            val progress = if (totalBytes > 0) {
                                (downloadedBytes.toFloat() / totalBytes.toFloat()) * 100f
                            } else 0f

                            val remainingBytes = if (totalBytes > downloadedBytes) totalBytes - downloadedBytes else 0L
                            val eta = if (currentSpeed > 0) (remainingBytes / currentSpeed).toLong() else 0L

                            onProgress(progress, downloadedBytes, totalBytes, currentSpeed, eta)
                        }
                    }
                }
            }

            val finalProgress = if (totalBytes > 0) 100f else 0f
            onProgress(finalProgress, downloadedBytes, totalBytes, 0.0, 0L)
            Result.success(destinationFile.absolutePath)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }
}
