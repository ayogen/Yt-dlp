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
            if (e is CancellationException) throw e
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

        val videoUrl = extractTag(html, "property=\"og:video\" content=\"([^\"]+)\"")
            ?: extractTag(html, "property=\"og:video:url\" content=\"([^\"]+)\"")
            ?: extractTag(html, "property=\"og:video:secure_url\" content=\"([^\"]+)\"")
            ?: url

        return MediaMetadata(
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
                    resolution = "Web Stream",
                    vcodec = "h264",
                    acodec = "aac",
                    url = videoUrl
                )
            ),
            extractorName = "GenericWebExtractor",
            directDownloadUrl = videoUrl
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
}
