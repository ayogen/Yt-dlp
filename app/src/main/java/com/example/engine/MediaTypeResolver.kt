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
        classification: SemanticClassification? = null,
        traceId: String? = null
    ): MediaType {
        val effectiveTraceId = traceId ?: MediaExtractionTracer.currentSessionFlow.value?.traceId
        val opId = if (effectiveTraceId != null) {
            MediaExtractionTracer.startOperation(
                traceId = effectiveTraceId,
                component = "MediaTypeResolver",
                stage = "MEDIA_TYPE_RESOLUTION",
                name = "resolveMediaType",
                details = mapOf(
                    "url" to url,
                    "mimeType" to (mimeType ?: "null"),
                    "extractedMedia" to (extractedMedia?.javaClass?.simpleName ?: "null")
                )
            )
        } else null

        // Priority 1: Extracted rich media model evidence
        when (extractedMedia) {
            is ExtractedMedia.Carousel -> {
                recordResolution(effectiveTraceId, opId, MediaType.CAROUSEL, mimeType, "PRIORITY_1_EXTRACTED_CAROUSEL")
                return MediaType.CAROUSEL
            }
            is ExtractedMedia.Image -> {
                recordResolution(effectiveTraceId, opId, MediaType.IMAGE, mimeType, "PRIORITY_1_EXTRACTED_IMAGE")
                return MediaType.IMAGE
            }
            is ExtractedMedia.Audio -> {
                recordResolution(effectiveTraceId, opId, MediaType.AUDIO, mimeType, "PRIORITY_1_EXTRACTED_AUDIO")
                return MediaType.AUDIO
            }
            is ExtractedMedia.Playlist -> {
                recordResolution(effectiveTraceId, opId, MediaType.PLAYLIST, mimeType, "PRIORITY_1_EXTRACTED_PLAYLIST")
                return MediaType.PLAYLIST
            }
            is ExtractedMedia.Video -> {
                recordResolution(effectiveTraceId, opId, MediaType.VIDEO, mimeType, "PRIORITY_1_EXTRACTED_VIDEO")
                return MediaType.VIDEO
            }
            else -> Unit
        }

        // Priority 2: MediaMetadata evidence
        if (metadata != null) {
            if (metadata.carouselItems.isNotEmpty() || metadata.mediaType == MediaType.CAROUSEL) {
                recordResolution(effectiveTraceId, opId, MediaType.CAROUSEL, mimeType, "PRIORITY_2_METADATA_CAROUSEL")
                return MediaType.CAROUSEL
            }
            if (metadata.isPlaylist || metadata.mediaType == MediaType.PLAYLIST) {
                recordResolution(effectiveTraceId, opId, MediaType.PLAYLIST, mimeType, "PRIORITY_2_METADATA_PLAYLIST")
                return MediaType.PLAYLIST
            }
            if (metadata.mediaType == MediaType.IMAGE || isExplicitImageUrl(metadata.directDownloadUrl ?: metadata.webpageUrl)) {
                recordResolution(effectiveTraceId, opId, MediaType.IMAGE, mimeType, "PRIORITY_2_METADATA_IMAGE")
                return MediaType.IMAGE
            }
            if (metadata.mediaType == MediaType.AUDIO || isExplicitAudioUrl(metadata.directDownloadUrl ?: metadata.webpageUrl) || metadata.isAudioOnly) {
                recordResolution(effectiveTraceId, opId, MediaType.AUDIO, mimeType, "PRIORITY_2_METADATA_AUDIO")
                return MediaType.AUDIO
            }
        }

        // Priority 3: Explicit MIME type evidence
        val cleanMime = mimeType?.substringBefore(";")?.trim()?.lowercase().orEmpty()
        if (cleanMime.startsWith("image/")) {
            recordResolution(effectiveTraceId, opId, MediaType.IMAGE, cleanMime, "PRIORITY_3_MIME_IMAGE")
            return MediaType.IMAGE
        }
        if (cleanMime.startsWith("audio/")) {
            recordResolution(effectiveTraceId, opId, MediaType.AUDIO, cleanMime, "PRIORITY_3_MIME_AUDIO")
            return MediaType.AUDIO
        }
        if (cleanMime.startsWith("video/")) {
            recordResolution(effectiveTraceId, opId, MediaType.VIDEO, cleanMime, "PRIORITY_3_MIME_VIDEO")
            return MediaType.VIDEO
        }

        // Priority 4: URL extension and platform host hints
        val cleanUrl = url.substringBefore("?").substringBefore("#").trim().lowercase()
        val ext = cleanUrl.substringAfterLast(".", "")
        if (ext in IMAGE_EXTENSIONS || cleanUrl.contains("i.redd.it") || cleanUrl.contains("preview.redd.it")) {
            recordResolution(effectiveTraceId, opId, MediaType.IMAGE, cleanMime, "PRIORITY_4_EXTENSION_IMAGE")
            return MediaType.IMAGE
        }
        if (ext in AUDIO_EXTENSIONS) {
            recordResolution(effectiveTraceId, opId, MediaType.AUDIO, cleanMime, "PRIORITY_4_EXTENSION_AUDIO")
            return MediaType.AUDIO
        }
        if (ext in VIDEO_EXTENSIONS) {
            recordResolution(effectiveTraceId, opId, MediaType.VIDEO, cleanMime, "PRIORITY_4_EXTENSION_VIDEO")
            return MediaType.VIDEO
        }

        // Priority 5: Semantic classification intent
        if (classification != null) {
            val resType = when (classification.intent) {
                MediaIntent.PLATFORM_CAROUSEL -> MediaType.CAROUSEL
                MediaIntent.PLATFORM_IMAGE -> MediaType.IMAGE
                MediaIntent.PLATFORM_AUDIO -> MediaType.AUDIO
                MediaIntent.PLATFORM_VIDEO -> MediaType.VIDEO
                else -> MediaType.VIDEO
            }
            recordResolution(effectiveTraceId, opId, resType, cleanMime, "PRIORITY_5_SEMANTIC_INTENT")
            return resType
        }

        recordResolution(effectiveTraceId, opId, MediaType.VIDEO, cleanMime, "PRIORITY_DEFAULT_VIDEO")
        return MediaType.VIDEO
    }

    private fun recordResolution(traceId: String?, opId: String?, type: MediaType, mime: String?, reason: String) {
        if (traceId != null) {
            val session = MediaExtractionTracer.getSession(traceId)
            session?.resolvedMediaType = type
            if (!mime.isNullOrBlank()) session?.resolvedMime = mime

            if (opId != null) {
                MediaExtractionTracer.endOperation(
                    traceId = traceId,
                    opId = opId,
                    result = "resolvedType=${type.name}",
                    decision = type.name,
                    reason = reason,
                    details = mapOf(
                        "resolvedMediaType" to type.name,
                        "mimeType" to (mime ?: ""),
                        "reason" to reason
                    )
                )
            }
        }
    }

    /**
     * Sanitizes MediaMetadata to prevent corrupt state such as images holding video format descriptors.
     */
    fun sanitizeMetadata(metadata: MediaMetadata, traceId: String? = null): MediaMetadata {
        val targetUrl = metadata.directDownloadUrl ?: metadata.webpageUrl
        val resolvedType = resolveMediaType(
            url = targetUrl,
            mimeType = metadata.mimeType,
            metadata = metadata,
            traceId = traceId
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
