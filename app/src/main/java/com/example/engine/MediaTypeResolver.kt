package com.example.engine

import com.example.data.model.ExtractedMedia
import com.example.data.model.MediaMetadata
import com.example.data.model.MediaType

/**
 * Authoritative media type resolver that inspects MIME evidence, file extensions,
 * magic byte classifications, platform endpoints, and intermediate extraction results.
 *
 * Enforces strict boundaries to prevent images or carousels from ever being misclassified
 * as streamable videos or muxed containers.
 */
object MediaTypeResolver {

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "heic", "heif", "bmp", "svg")
    private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "opus", "ogg", "flac", "wav", "aac", "wma", "alac")
    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "mov", "avi", "flv", "m4v", "ts", "m3u8")

    /**
     * Checks if the given URL points directly to an image, audio, or video file.
     */
    fun isDirectMediaUrl(url: String): Boolean {
        val clean = url.substringBefore("?").substringBefore("#").trim().lowercase()
        val ext = clean.substringAfterLast(".", "")
        return ext in IMAGE_EXTENSIONS || ext in AUDIO_EXTENSIONS || ext in VIDEO_EXTENSIONS ||
                clean.contains("i.redd.it") || clean.contains("preview.redd.it")
    }

    /**
     * Determines whether a URL is explicitly an image based on extension or domain.
     */
    fun isExplicitImageUrl(url: String): Boolean {
        val clean = url.substringBefore("?").substringBefore("#").trim().lowercase()
        val ext = clean.substringAfterLast(".", "")
        return ext in IMAGE_EXTENSIONS || clean.contains("i.redd.it") || clean.contains("preview.redd.it")
    }

    /**
     * Determines whether a URL is explicitly an audio stream based on extension.
     */
    fun isExplicitAudioUrl(url: String): Boolean {
        val clean = url.substringBefore("?").substringBefore("#").trim().lowercase()
        val ext = clean.substringAfterLast(".", "")
        return ext in AUDIO_EXTENSIONS
    }

    /**
     * Resolves the definitive MediaType from available evidence.
     */
    fun resolveMediaType(
        url: String,
        mimeType: String? = null,
        extractedMedia: ExtractedMedia? = null,
        metadata: MediaMetadata? = null,
        classification: SemanticClassification? = null
    ): MediaType {
        // Priority 1: Extracted rich media model evidence
        when (extractedMedia) {
            is ExtractedMedia.Carousel -> return MediaType.CAROUSEL
            is ExtractedMedia.Image -> return MediaType.IMAGE
            is ExtractedMedia.Audio -> return MediaType.AUDIO
            is ExtractedMedia.Playlist -> return MediaType.PLAYLIST
            is ExtractedMedia.Video -> return MediaType.VIDEO
            else -> Unit
        }

        // Priority 2: MediaMetadata evidence
        if (metadata != null) {
            if (metadata.carouselItems.isNotEmpty() || metadata.mediaType == MediaType.CAROUSEL) {
                return MediaType.CAROUSEL
            }
            if (metadata.isPlaylist || metadata.mediaType == MediaType.PLAYLIST) {
                return MediaType.PLAYLIST
            }
            if (metadata.mediaType == MediaType.IMAGE || isExplicitImageUrl(metadata.directDownloadUrl ?: metadata.webpageUrl)) {
                return MediaType.IMAGE
            }
            if (metadata.mediaType == MediaType.AUDIO || isExplicitAudioUrl(metadata.directDownloadUrl ?: metadata.webpageUrl) || metadata.isAudioOnly) {
                return MediaType.AUDIO
            }
        }

        // Priority 3: Explicit MIME type evidence
        val cleanMime = mimeType?.substringBefore(";")?.trim()?.lowercase().orEmpty()
        if (cleanMime.startsWith("image/")) return MediaType.IMAGE
        if (cleanMime.startsWith("audio/")) return MediaType.AUDIO
        if (cleanMime.startsWith("video/")) return MediaType.VIDEO

        // Priority 4: URL extension and platform host hints
        val cleanUrl = url.substringBefore("?").substringBefore("#").trim().lowercase()
        val ext = cleanUrl.substringAfterLast(".", "")
        if (ext in IMAGE_EXTENSIONS || cleanUrl.contains("i.redd.it") || cleanUrl.contains("preview.redd.it")) {
            return MediaType.IMAGE
        }
        if (ext in AUDIO_EXTENSIONS) {
            return MediaType.AUDIO
        }
        if (ext in VIDEO_EXTENSIONS) {
            return MediaType.VIDEO
        }

        // Priority 5: Semantic classification intent
        if (classification != null) {
            return when (classification.intent) {
                MediaIntent.PLATFORM_CAROUSEL -> MediaType.CAROUSEL
                MediaIntent.PLATFORM_IMAGE -> MediaType.IMAGE
                MediaIntent.PLATFORM_AUDIO -> MediaType.AUDIO
                MediaIntent.PLATFORM_VIDEO -> MediaType.VIDEO
                else -> MediaType.VIDEO
            }
        }

        return MediaType.VIDEO
    }

    /**
     * Sanitizes MediaMetadata to prevent corrupt state such as images holding video format descriptors.
     */
    fun sanitizeMetadata(metadata: MediaMetadata): MediaMetadata {
        val targetUrl = metadata.directDownloadUrl ?: metadata.webpageUrl
        val resolvedType = resolveMediaType(
            url = targetUrl,
            mimeType = metadata.mimeType,
            metadata = metadata
        )

        return when (resolvedType) {
            MediaType.IMAGE -> {
                metadata.copy(
                    mediaType = MediaType.IMAGE,
                    formats = emptyList(),
                    isPlaylist = false,
                    carouselItems = emptyList(),
                    directDownloadUrl = metadata.directDownloadUrl ?: metadata.webpageUrl,
                    thumbnail = metadata.thumbnail.ifBlank { metadata.directDownloadUrl ?: metadata.webpageUrl }
                )
            }
            MediaType.CAROUSEL -> {
                metadata.copy(
                    mediaType = MediaType.CAROUSEL,
                    formats = emptyList(),
                    isPlaylist = false
                )
            }
            MediaType.AUDIO -> {
                metadata.copy(
                    mediaType = MediaType.AUDIO
                )
            }
            MediaType.PLAYLIST -> {
                metadata.copy(
                    mediaType = MediaType.PLAYLIST,
                    isPlaylist = true
                )
            }
            MediaType.VIDEO -> {
                metadata.copy(
                    mediaType = MediaType.VIDEO
                )
            }
        }
    }
}
