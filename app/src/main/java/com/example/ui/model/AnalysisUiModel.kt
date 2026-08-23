package com.example.ui.model

import com.example.core.model.CanonicalMediaResult
import com.example.data.model.MediaMetadata
import com.example.data.model.PlaylistEntry
import com.example.data.model.SubtitleTrack

data class AnalysisUiModel(
    val id: String,
    val title: String,
    val webpageUrl: String,
    val uploader: String = "",
    val channel: String = "",
    val durationText: String = "--:--",
    val durationSeconds: Long = 0,
    val thumbnailUrl: String = "",
    val mediaKind: String = "Video",
    val isPlaylist: Boolean = false,
    val playlistCount: Int = 0,
    val playlistEntries: List<PlaylistEntry> = emptyList(),
    val formatOptions: List<FormatOptionUiModel> = emptyList(),
    val subtitles: List<SubtitleTrack> = emptyList(),
    val warnings: List<String> = emptyList(),
    val description: String = "",
    val directDownloadUrl: String? = null,
    val rawMetadata: MediaMetadata? = null
) {
    fun toMediaMetadata(): MediaMetadata {
        return rawMetadata ?: MediaMetadata(
            id = id,
            title = title,
            webpageUrl = webpageUrl,
            uploader = uploader,
            channel = channel,
            durationSeconds = durationSeconds,
            description = description,
            thumbnail = thumbnailUrl,
            isPlaylist = isPlaylist,
            playlistCount = playlistCount,
            playlistEntries = playlistEntries,
            formats = formatOptions.map { it.toFormatInfo() },
            subtitles = subtitles,
            directDownloadUrl = directDownloadUrl
        )
    }

    companion object {
        fun fromMediaMetadata(metadata: MediaMetadata): AnalysisUiModel {
            return AnalysisUiModel(
                id = metadata.id,
                title = metadata.title,
                webpageUrl = metadata.webpageUrl,
                uploader = metadata.uploader,
                channel = metadata.channel,
                durationText = metadata.durationFormatted,
                durationSeconds = metadata.durationSeconds,
                thumbnailUrl = metadata.thumbnail,
                mediaKind = if (metadata.isPlaylist) "Playlist" else "Video",
                isPlaylist = metadata.isPlaylist,
                playlistCount = metadata.playlistCount,
                playlistEntries = metadata.playlistEntries,
                formatOptions = metadata.formats.map { FormatOptionUiModel.fromFormatInfo(it) },
                subtitles = metadata.subtitles,
                description = metadata.description,
                directDownloadUrl = metadata.directDownloadUrl,
                rawMetadata = metadata
            )
        }

        fun fromCanonicalMediaResult(result: CanonicalMediaResult): AnalysisUiModel {
            val metadata = result.toMediaMetadata()
            return AnalysisUiModel(
                id = metadata.id,
                title = metadata.title,
                webpageUrl = result.canonicalUrl,
                uploader = metadata.uploader,
                channel = metadata.channel,
                durationText = metadata.durationFormatted,
                durationSeconds = metadata.durationSeconds,
                thumbnailUrl = metadata.thumbnail,
                mediaKind = if (metadata.isPlaylist) "Playlist" else "Video",
                isPlaylist = metadata.isPlaylist,
                playlistCount = metadata.playlistCount,
                playlistEntries = metadata.playlistEntries,
                formatOptions = result.formats.map { FormatOptionUiModel.fromMediaFormat(it) },
                subtitles = metadata.subtitles,
                warnings = result.warnings,
                description = metadata.description,
                directDownloadUrl = metadata.directDownloadUrl,
                rawMetadata = metadata
            )
        }
    }
}
