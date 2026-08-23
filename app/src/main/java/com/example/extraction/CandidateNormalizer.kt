package com.example.extraction

import com.example.core.model.CandidateSource
import com.example.core.model.MediaCandidate
import com.example.core.model.MediaFormat
import com.example.core.model.MediaSize
import com.example.core.policy.Confidence
import com.example.core.policy.ConfidenceTier
import com.example.core.policy.MediaRole
import com.example.data.model.MediaMetadata
import com.example.data.model.MediaType
import com.example.extraction.model.YtDlpInfoDto

object CandidateNormalizer {

    fun fromYtDlpInfo(
        dto: YtDlpInfoDto,
        pageUrl: String,
        targetIntent: String = "video"
    ): List<MediaCandidate> {
        val candidates = mutableListOf<MediaCandidate>()
        val formats = dto.formats.map { MetadataNormalizer.normalizeFormat(it) }

        val isAudioIntent = targetIntent.equals("audio", ignoreCase = true)
        val role = when {
            dto.isPlaylist -> MediaRole.PRIMARY_VIDEO
            isAudioIntent -> MediaRole.PRIMARY_AUDIO
            else -> MediaRole.PRIMARY_VIDEO
        }
        val mediaType = when {
            dto.isPlaylist -> MediaType.VIDEO
            isAudioIntent -> MediaType.AUDIO
            else -> MediaType.VIDEO
        }

        // Primary candidate from yt-dlp
        candidates.add(
            MediaCandidate(
                id = dto.id,
                source = CandidateSource.YTDLP,
                role = role,
                mediaType = mediaType,
                url = dto.webpageUrl ?: pageUrl,
                pageUrl = pageUrl,
                title = dto.title,
                uploader = dto.uploader,
                durationSeconds = dto.duration,
                thumbnail = dto.thumbnail,
                description = dto.description,
                formats = formats,
                confidence = Confidence(ConfidenceTier.VERIFIED, 100, "Extracted via yt-dlp"),
                isDownloadable = true,
                provenance = mapOf("extractor" to (dto.extractor ?: "yt-dlp"))
            )
        )

        // Thumbnail candidate (if present)
        if (dto.thumbnail.isNotBlank()) {
            candidates.add(
                MediaCandidate(
                    id = "${dto.id}_thumb",
                    source = CandidateSource.YTDLP,
                    role = MediaRole.THUMBNAIL,
                    mediaType = MediaType.IMAGE,
                    url = dto.thumbnail,
                    pageUrl = pageUrl,
                    title = "${dto.title} Thumbnail",
                    uploader = dto.uploader,
                    confidence = Confidence(ConfidenceTier.HIGH, 80, "Thumbnail from yt-dlp"),
                    isDownloadable = false,
                    provenance = mapOf("role" to "thumbnail")
                )
            )
        }

        return candidates
    }

    fun fromDirectMedia(
        url: String,
        pageUrl: String,
        mimeType: String? = null,
        contentLength: Long? = null,
        isAudio: Boolean = false
    ): MediaCandidate {
        val ext = url.substringBefore("?").substringAfterLast(".", if (isAudio) "mp3" else "mp4")
        val size = MetadataNormalizer.resolveMediaSize(null, null, contentLength)
        val mediaType = if (isAudio) MediaType.AUDIO else MediaType.VIDEO
        val role = MediaRole.DIRECT_MEDIA

        val format = MediaFormat(
            formatId = if (isAudio) "direct-audio" else "direct-video",
            ext = ext,
            resolution = if (isAudio) "Audio Only" else "Direct Stream",
            size = size,
            url = url,
            isAudioOnly = isAudio,
            isVideoOnly = !isAudio
        )

        return MediaCandidate(
            id = Math.abs(url.hashCode()).toString(),
            source = CandidateSource.DIRECT_HTTP,
            role = role,
            mediaType = mediaType,
            url = url,
            pageUrl = pageUrl,
            mimeType = mimeType,
            title = url.substringBefore("?").substringAfterLast("/").ifBlank { "Direct Media" },
            formats = listOf(format),
            size = size,
            confidence = Confidence(ConfidenceTier.VERIFIED, 100, "Direct HTTP inspection verified"),
            isDownloadable = true,
            provenance = mapOf("source" to "direct_http")
        )
    }

    fun fromOpenGraphImage(
        imageUrl: String,
        pageUrl: String,
        title: String = "",
        uploader: String = "",
        isExplicitImageIntent: Boolean = false
    ): MediaCandidate {
        val role = if (isExplicitImageIntent) MediaRole.DIRECT_MEDIA else MediaRole.THUMBNAIL
        val isDownloadable = isExplicitImageIntent

        return MediaCandidate(
            id = Math.abs(imageUrl.hashCode()).toString(),
            source = CandidateSource.OPENGRAPH,
            role = role,
            mediaType = MediaType.IMAGE,
            url = imageUrl,
            pageUrl = pageUrl,
            title = title.ifBlank { "Image" },
            uploader = uploader,
            confidence = Confidence(
                tier = if (isExplicitImageIntent) ConfidenceTier.HIGH else ConfidenceTier.MEDIUM,
                score = if (isExplicitImageIntent) 75 else 40,
                reason = if (isExplicitImageIntent) "OpenGraph image as explicit image intent" else "OpenGraph thumbnail"
            ),
            isDownloadable = isDownloadable,
            provenance = mapOf("og:image" to imageUrl)
        )
    }

    fun fromEmbeddedMedia(
        mediaUrl: String,
        pageUrl: String,
        title: String = "",
        uploader: String = "",
        mediaType: MediaType = MediaType.VIDEO,
        formats: List<com.example.data.model.FormatInfo> = emptyList()
    ): MediaCandidate {
        val normalizedFormats = if (formats.isNotEmpty()) {
            formats.map { MetadataNormalizer.normalizeFormat(it) }
        } else {
            listOf(
                MediaFormat(
                    formatId = "embedded-best",
                    ext = mediaUrl.substringBefore("?").substringAfterLast(".", if (mediaType == MediaType.AUDIO) "mp3" else "mp4"),
                    resolution = "Direct Stream",
                    url = mediaUrl,
                    isAudioOnly = mediaType == MediaType.AUDIO,
                    isVideoOnly = mediaType == MediaType.VIDEO
                )
            )
        }

        return MediaCandidate(
            id = Math.abs(mediaUrl.hashCode()).toString(),
            source = CandidateSource.EMBEDDED,
            role = if (mediaType == MediaType.AUDIO) MediaRole.PRIMARY_AUDIO else MediaRole.PRIMARY_VIDEO,
            mediaType = mediaType,
            url = mediaUrl,
            pageUrl = pageUrl,
            title = title.ifBlank { "Embedded Media" },
            uploader = uploader,
            formats = normalizedFormats,
            confidence = Confidence(ConfidenceTier.HIGH, 85, "Embedded playable media stream"),
            isDownloadable = true,
            provenance = mapOf("source" to "embedded_stream")
        )
    }
}

