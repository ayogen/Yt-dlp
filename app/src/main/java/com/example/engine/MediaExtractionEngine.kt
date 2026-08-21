package com.example.engine

import android.content.Context
import com.example.data.model.ExtractedMedia
import com.example.data.model.FormatInfo
import com.example.data.model.MediaMetadata
import com.example.data.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class MediaExtractionEngine(private val context: Context) {

    /**
     * Unified media extraction method.
     * Evaluates the URL using a layered multi-tier strategy:
     * 1. Direct HTTP/MIME/Magic Bytes Inspection
     * 2. Platform-specific Social / Page Metadata Extraction (Facebook Photos, Instagram Posts/Carousels, Pinterest, Reddit Galleries)
     * 3. yt-dlp Engine CLI extraction
     * 4. Embedded extractor & OpenGraph fallback
     */
    suspend fun extractMedia(
        url: String,
        cookiesFile: File? = null,
        userAgent: String? = null,
        proxyUrl: String? = null,
        geoBypass: Boolean = true
    ): Result<MediaMetadata> = withContext(Dispatchers.IO) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("URL cannot be empty"))
        }

        val canonicalUrl = UrlNormalizer.resolveCanonicalUrl(trimmedUrl)
        AppLogger.i("MediaExtractionEngine", "Starting extraction for: $canonicalUrl")

        // 1. Direct Media Link Inspection (fast HEAD / range header inspection)
        val directInspection = DirectMediaInspector.inspectUrl(canonicalUrl)
        if (directInspection.isDirectMedia) {
            AppLogger.i("MediaExtractionEngine", "Detected direct media stream: ${directInspection.mimeType} (${directInspection.mediaType})")
            val titleFromUrl = canonicalUrl.substringBefore("?").substringAfterLast("/").substringBeforeLast(".")
                .ifBlank { "media_${System.currentTimeMillis()}" }

            val cleanTitle = FilenameFormatter.sanitize(titleFromUrl)

            val metadata = when (directInspection.mediaType) {
                MediaType.IMAGE -> {
                    MediaMetadata(
                        id = "direct_img_" + UUID.randomUUID().toString().take(8),
                        title = cleanTitle,
                        webpageUrl = canonicalUrl,
                        directDownloadUrl = canonicalUrl,
                        thumbnail = canonicalUrl,
                        mediaType = MediaType.IMAGE,
                        mimeType = directInspection.mimeType,
                        width = directInspection.width,
                        height = directInspection.height,
                        fileSize = directInspection.contentLength,
                        extractorName = "DirectImage"
                    )
                }
                MediaType.AUDIO -> {
                    val format = FormatInfo(
                        formatId = "direct_audio",
                        ext = directInspection.suggestedExt,
                        acodec = directInspection.mimeType.substringAfter("audio/"),
                        url = canonicalUrl,
                        filesize = directInspection.contentLength,
                        isAudioOnly = true
                    )
                    MediaMetadata(
                        id = "direct_audio_" + UUID.randomUUID().toString().take(8),
                        title = cleanTitle,
                        webpageUrl = canonicalUrl,
                        directDownloadUrl = canonicalUrl,
                        mediaType = MediaType.AUDIO,
                        formats = listOf(format),
                        extractorName = "DirectAudio"
                    )
                }
                else -> {
                    val format = FormatInfo(
                        formatId = "direct_video",
                        ext = directInspection.suggestedExt,
                        vcodec = "h264",
                        acodec = "aac",
                        url = canonicalUrl,
                        filesize = directInspection.contentLength,
                        isMuxed = true
                    )
                    MediaMetadata(
                        id = "direct_video_" + UUID.randomUUID().toString().take(8),
                        title = cleanTitle,
                        webpageUrl = canonicalUrl,
                        directDownloadUrl = canonicalUrl,
                        mediaType = MediaType.VIDEO,
                        formats = listOf(format),
                        extractorName = "DirectVideo"
                    )
                }
            }
            return@withContext Result.success(metadata)
        }

        // 2. Social Media Photo/Carousel/Post Detection before yt-dlp (e.g. Facebook photo, Instagram carousel)
        val pageMedia = PageMetadataExtractor.extractPageMedia(canonicalUrl)
        if (pageMedia != null) {
            when (pageMedia) {
                is ExtractedMedia.Image, is ExtractedMedia.Carousel -> {
                    AppLogger.i("MediaExtractionEngine", "Extracted page media directly via DOM/OpenGraph: ${pageMedia.javaClass.simpleName}")
                    return@withContext Result.success(pageMedia.toMediaMetadata())
                }
                else -> {
                    // If video or other, allow yt-dlp to attempt full multi-format extraction first
                }
            }
        }

        // 3. yt-dlp Engine Extraction
        val ytDlpResult = YtDlpProcessRunner.extractMetadataCli(
            context = context,
            url = canonicalUrl,
            cookiesFile = cookiesFile,
            userAgent = userAgent,
            proxyUrl = proxyUrl,
            geoBypass = geoBypass
        )

        if (ytDlpResult.isSuccess) {
            val meta = ytDlpResult.getOrThrow()
            // Refine mediaType if audio only or playlist
            val refinedMeta = when {
                meta.isPlaylist -> meta.copy(mediaType = MediaType.PLAYLIST)
                meta.formats.isNotEmpty() && meta.formats.all { it.isAudioOnly } || meta.extractorName.equals("soundcloud", ignoreCase = true) ->
                    meta.copy(mediaType = MediaType.AUDIO)
                else -> meta.copy(mediaType = MediaType.VIDEO)
            }
            AppLogger.i("MediaExtractionEngine", "yt-dlp extraction succeeded for ${meta.title} (${refinedMeta.mediaType})")
            return@withContext Result.success(refinedMeta)
        }

        val ytDlpError = ytDlpResult.exceptionOrNull()?.message.orEmpty()
        AppLogger.w("MediaExtractionEngine", "yt-dlp extraction failed: $ytDlpError")

        // 4. Fallback: If yt-dlp failed, re-check page metadata or embedded extractor
        if (pageMedia != null) {
            return@withContext Result.success(pageMedia.toMediaMetadata())
        }

        val pageMediaFallback = PageMetadataExtractor.extractGenericPageMedia(canonicalUrl)
        if (pageMediaFallback != null) {
            return@withContext Result.success(pageMediaFallback.toMediaMetadata())
        }

        val embeddedResult = EmbeddedExtractorEngine.extractDirectStream(canonicalUrl)
        if (embeddedResult.isSuccess) {
            return@withContext embeddedResult
        }

        // Return a clear, diagnosed error message
        val friendlyMessage = when {
            ytDlpError.contains("Private video", ignoreCase = true) || ytDlpError.contains("requires login", ignoreCase = true) || ytDlpError.contains("account is private", ignoreCase = true) ->
                "This content is private or requires authentication."
            ytDlpError.contains("No video formats found", ignoreCase = true) ->
                "No downloadable media streams were found at this URL."
            ytDlpError.contains("Unsupported URL", ignoreCase = true) ->
                "Unsupported media URL or webpage format."
            ytDlpError.contains("Video unavailable", ignoreCase = true) ->
                "The requested media is unavailable or has been removed."
            else ->
                ytDlpError.ifBlank { "Unable to extract media from this URL." }
        }

        Result.failure(Exception(friendlyMessage))
    }
}
