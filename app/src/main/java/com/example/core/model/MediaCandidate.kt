package com.example.core.model

import com.example.core.policy.Confidence
import com.example.core.policy.MediaRole
import com.example.data.model.MediaType

enum class CandidateSource {
    YTDLP,
    DIRECT_HTTP,
    PLATFORM,
    OPENGRAPH,
    EMBEDDED
}

data class MediaCandidate(
    val id: String,
    val source: CandidateSource,
    val role: MediaRole,
    val mediaType: MediaType,
    val url: String,
    val pageUrl: String,
    val mimeType: String? = null,
    val title: String = "",
    val uploader: String = "",
    val durationSeconds: Long = 0,
    val thumbnail: String = "",
    val description: String = "",
    val formats: List<MediaFormat> = emptyList(),
    val size: MediaSize = MediaSize.Unknown(),
    val confidence: Confidence = Confidence(),
    val isDownloadable: Boolean = role.isPrimary,
    val provenance: Map<String, String> = emptyMap()
)
