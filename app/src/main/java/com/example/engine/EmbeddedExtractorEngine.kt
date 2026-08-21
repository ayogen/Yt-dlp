package com.example.engine

import com.example.data.model.FormatInfo
import com.example.data.model.MediaMetadata
import com.example.data.model.PlaylistEntry
import com.example.data.model.SubtitleTrack
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

    suspend fun analyzeUrl(url: String): Result<MediaMetadata> = withContext(Dispatchers.IO) {
        try {
            AppLogger.i("EmbeddedExtractor", "Analyzing direct URL: $url")
            val validatedUrl = validateAndNormalizeUrl(url)
                ?: return@withContext Result.failure(IllegalArgumentException("Invalid URL format. Please provide a valid HTTP/HTTPS address."))

            val uri = URL(validatedUrl)
            val host = uri.host.lowercase()

            // Check if URL is a direct media stream
            val isDirectMedia = isDirectMediaUrl(validatedUrl)
            if (isDirectMedia) {
                return@withContext extractDirectMediaMetadata(validatedUrl)
            }

            // Extract webpage HTML to obtain OpenGraph and meta tags without fake numbers
            val htmlContent = fetchWebpage(validatedUrl)
            val metadata = parseWebpageMetadata(validatedUrl, host, htmlContent)
            AppLogger.i("EmbeddedExtractor", "Extracted web page metadata for: ${metadata.title}")
            Result.success(metadata)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.e("EmbeddedExtractor", "Extraction failed: ${e.message}")
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
            client.newCall(headRequest).execute().use { response ->
                if (response.isSuccessful) {
                    contentLength = response.header("Content-Length")?.toLongOrNull()
                }
            }
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

    private fun fetchWebpage(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }
            return response.body?.string() ?: ""
        }
    }

    private fun parseWebpageMetadata(url: String, host: String, html: String): MediaMetadata {
        val ogTitle = extractTag(html, "property=\"og:title\" content=\"([^\"]+)\"")
            ?: extractTag(html, "<title>([^<]+)</title>")
            ?: "Media ($host)"

        val ogImage = extractTag(html, "property=\"og:image\" content=\"([^\"]+)\"")
            ?: extractTag(html, "name=\"twitter:image\" content=\"([^\"]+)\"")
            ?: ""

        val ogDesc = extractTag(html, "property=\"og:description\" content=\"([^\"]+)\"")
            ?: extractTag(html, "name=\"description\" content=\"([^\"]+)\"")
            ?: ""

        val ogSiteName = extractTag(html, "property=\"og:site_name\" content=\"([^\"]+)\"")
            ?: host.removePrefix("www.").substringBefore(".")

        val isPlaylist = url.contains("list=", ignoreCase = true) || url.contains("/playlist", ignoreCase = true)

        val formats = listOf(
            FormatInfo(
                formatId = "bestvideo+bestaudio/best",
                ext = "mp4",
                resolution = "Best Available",
                formatNote = "Best Video + Audio (FFmpeg Merged)",
                filesize = null,
                filesizeApprox = null,
                url = url
            ),
            FormatInfo(
                formatId = "1080p",
                ext = "mp4",
                resolution = "1080p",
                height = 1080,
                formatNote = "1080p Full HD",
                filesize = null,
                filesizeApprox = null,
                url = url
            ),
            FormatInfo(
                formatId = "720p",
                ext = "mp4",
                resolution = "720p",
                height = 720,
                formatNote = "720p HD",
                filesize = null,
                filesizeApprox = null,
                url = url
            ),
            FormatInfo(
                formatId = "bestaudio",
                ext = "m4a",
                resolution = "audio only",
                formatNote = "Best Audio Stream",
                filesize = null,
                filesizeApprox = null,
                url = url
            )
        )

        return MediaMetadata(
            id = Math.abs(url.hashCode()).toString(),
            title = cleanHtmlEntities(ogTitle),
            webpageUrl = url,
            uploader = ogSiteName.capitalizeFirstLetter(),
            channel = ogSiteName,
            durationSeconds = 0L,
            viewCount = null,
            likeCount = null,
            uploadDate = "",
            description = cleanHtmlEntities(ogDesc),
            thumbnail = ogImage,
            isPlaylist = isPlaylist,
            playlistCount = 0,
            playlistEntries = emptyList(),
            formats = formats,
            subtitles = emptyList(),
            extractorName = host
        )
    }

    suspend fun downloadDirectStream(
        taskId: String,
        url: String,
        destinationFile: File,
        targetTotalBytes: Long,
        onProgress: (progress: Float, downloaded: Long, total: Long, speed: Double, eta: Long) -> Unit,
        isCancelled: () -> Boolean,
        isPaused: () -> Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!isDirectMediaUrl(url)) {
            return@withContext Result.failure(
                IllegalStateException("Direct stream download only supports direct media URLs (e.g. .mp4, .mp3, .m4a). For video platforms, yt-dlp and FFmpeg are required.")
            )
        }

        try {
            destinationFile.parentFile?.mkdirs()
            val existingLength = if (destinationFile.exists()) destinationFile.length() else 0L

            AppLogger.i("EmbeddedExtractor", "Starting direct media stream download for task $taskId. Existing bytes: $existingLength", taskId)

            val requestBuilder = Request.Builder().url(url)
            if (existingLength > 0) {
                requestBuilder.header("Range", "bytes=$existingLength-")
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful && response.code != 206) {
                return@withContext Result.failure(Exception("HTTP error ${response.code}: ${response.message}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Empty response body from media server"))
            val contentLength = body.contentLength()
            val totalBytes = if (contentLength > 0) existingLength + contentLength else targetTotalBytes

            var currentDownloaded = existingLength
            var lastUpdateTime = System.currentTimeMillis()
            var bytesSinceLastUpdate = 0L

            val randomAccess = RandomAccessFile(destinationFile, "rw")
            randomAccess.seek(existingLength)

            try {
                val stream = body.byteStream()
                val buffer = ByteArray(32768)
                var bytesRead: Int

                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    if (isCancelled()) {
                        randomAccess.close()
                        return@withContext Result.failure(Exception("Download cancelled by user"))
                    }

                    while (isPaused()) {
                        if (isCancelled()) {
                            randomAccess.close()
                            return@withContext Result.failure(Exception("Download cancelled by user"))
                        }
                        kotlinx.coroutines.delay(250)
                    }

                    randomAccess.write(buffer, 0, bytesRead)
                    currentDownloaded += bytesRead
                    bytesSinceLastUpdate += bytesRead

                    val now = System.currentTimeMillis()
                    val elapsed = (now - lastUpdateTime) / 1000.0
                    if (elapsed >= 0.25) {
                        val speed = bytesSinceLastUpdate / elapsed
                        val remainingBytes = if (totalBytes > 0) (totalBytes - currentDownloaded).coerceAtLeast(0L) else 0L
                        val eta = if (speed > 0) (remainingBytes / speed).toLong() else 0L
                        val progress = if (totalBytes > 0) {
                            ((currentDownloaded.toDouble() / totalBytes.toDouble()) * 100.0).toFloat().coerceIn(0f, 100f)
                        } else {
                            0f
                        }

                        onProgress(progress, currentDownloaded, totalBytes, speed, eta)
                        lastUpdateTime = now
                        bytesSinceLastUpdate = 0L
                    }
                }
            } finally {
                try { randomAccess.close() } catch (e: Exception) {}
            }

            onProgress(100f, currentDownloaded, currentDownloaded, 0.0, 0L)
            AppLogger.i("EmbeddedExtractor", "Direct stream completed for task $taskId -> ${destinationFile.absolutePath}", taskId)
            Result.success(destinationFile.absolutePath)
        } catch (e: Exception) {
            AppLogger.e("EmbeddedExtractor", "Direct stream download failed for task $taskId: ${e.message}", taskId)
            Result.failure(e)
        }
    }

    private fun extractTag(html: String, regexPattern: String): String? {
        val pattern = Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(html)
        return if (matcher.find()) {
            matcher.group(1)?.trim()
        } else {
            null
        }
    }

    private fun cleanHtmlEntities(text: String): String {
        return text.replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .trim()
    }

    private fun String.capitalizeFirstLetter(): String {
        return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
