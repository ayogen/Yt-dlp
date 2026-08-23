package com.example.extraction.model

data class YtDlpSubtitleDto(
    val language: String,
    val ext: String = "vtt",
    val url: String = "",
    val name: String? = null
)

data class YtDlpPlaylistEntryDto(
    val id: String,
    val title: String,
    val url: String,
    val duration: Long = 0,
    val thumbnail: String = "",
    val uploader: String = ""
)

data class YtDlpInfoDto(
    val id: String,
    val title: String,
    val webpageUrl: String? = null,
    val uploader: String = "",
    val channel: String = "",
    val duration: Long = 0,
    val viewCount: Long? = null,
    val likeCount: Long? = null,
    val uploadDate: String = "",
    val description: String = "",
    val thumbnail: String = "",
    val type: String? = null,
    val extractor: String? = null,
    val extractorKey: String? = null,
    val filesize: Long? = null,
    val filesizeApprox: Long? = null,
    val formats: List<YtDlpFormatDto> = emptyList(),
    val subtitles: List<YtDlpSubtitleDto> = emptyList(),
    val entries: List<YtDlpPlaylistEntryDto> = emptyList()
) {
    val isPlaylist: Boolean
        get() = type == "playlist" || entries.isNotEmpty()
}
