package com.example.core.model

import com.example.data.model.MediaMetadata
import com.example.data.model.PlaylistEntry
import com.example.data.model.SubtitleTrack

data class CanonicalMetadata(
    val title: String = "",
    val uploader: String = "",
    val channel: String = "",
    val durationSeconds: Long = 0,
    val viewCount: Long? = null,
    val likeCount: Long? = null,
    val uploadDate: String = "",
    val description: String = "",
    val thumbnail: String = "",
    val isPlaylist: Boolean = false,
    val playlistCount: Int = 0,
    val playlistEntries: List<PlaylistEntry> = emptyList(),
    val subtitles: List<SubtitleTrack> = emptyList(),
    val extractorName: String = "generic",
    val directDownloadUrl: String? = null
)

data class CanonicalMediaResult(
    val sourceUrl: String,
    val canonicalUrl: String,
    val platform: String = "generic",
    val intent: String = "video",
    val primary: MediaCandidate? = null,
    val thumbnail: MediaCandidate? = null,
    val metadata: CanonicalMetadata = CanonicalMetadata(),
    val formats: List<MediaFormat> = emptyList(),
    val warnings: List<String> = emptyList(),
    val provenance: Map<String, String> = emptyMap()
) {
    val isResolved: Boolean
        get() = primary != null

    fun toMediaMetadata(): MediaMetadata {
        val effectiveThumbnail = thumbnail?.url?.ifBlank { null }
            ?: primary?.thumbnail?.ifBlank { null }
            ?: metadata.thumbnail

        val effectiveDirectUrl = if (primary?.role == com.example.core.policy.MediaRole.DIRECT_MEDIA) {
            primary.url
        } else {
            metadata.directDownloadUrl
        }

        return MediaMetadata(
            id = primary?.id ?: Math.abs(canonicalUrl.hashCode()).toString(),
            title = metadata.title.ifBlank { primary?.title ?: "Media" },
            webpageUrl = canonicalUrl,
            uploader = metadata.uploader.ifBlank { primary?.uploader ?: "" },
            channel = metadata.channel.ifBlank { metadata.uploader },
            durationSeconds = if (metadata.durationSeconds > 0) metadata.durationSeconds else (primary?.durationSeconds ?: 0L),
            viewCount = metadata.viewCount,
            likeCount = metadata.likeCount,
            uploadDate = metadata.uploadDate,
            description = metadata.description.ifBlank { primary?.description ?: "" },
            thumbnail = effectiveThumbnail,
            isPlaylist = metadata.isPlaylist,
            playlistCount = metadata.playlistCount,
            playlistEntries = metadata.playlistEntries,
            formats = formats.map { it.toFormatInfo() },
            subtitles = metadata.subtitles,
            extractorName = metadata.extractorName,
            directDownloadUrl = effectiveDirectUrl
        )
    }

    companion object {
        fun fromMediaMetadata(
            metadata: MediaMetadata,
            sourceUrl: String,
            canonicalUrl: String,
            platform: String = "generic",
            intent: String = "video"
        ): CanonicalMediaResult {
            val formats = metadata.formats.map { MediaFormat.fromFormatInfo(it) }
            val canonicalMeta = CanonicalMetadata(
                title = metadata.title,
                uploader = metadata.uploader,
                channel = metadata.channel,
                durationSeconds = metadata.durationSeconds,
                viewCount = metadata.viewCount,
                likeCount = metadata.likeCount,
                uploadDate = metadata.uploadDate,
                description = metadata.description,
                thumbnail = metadata.thumbnail,
                isPlaylist = metadata.isPlaylist,
                playlistCount = metadata.playlistCount,
                playlistEntries = metadata.playlistEntries,
                subtitles = metadata.subtitles,
                extractorName = metadata.extractorName,
                directDownloadUrl = metadata.directDownloadUrl
            )

            return CanonicalMediaResult(
                sourceUrl = sourceUrl,
                canonicalUrl = canonicalUrl,
                platform = platform,
                intent = intent,
                metadata = canonicalMeta,
                formats = formats
            )
        }
    }
}
