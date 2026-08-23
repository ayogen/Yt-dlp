package com.example.extraction.model

data class YtDlpFormatDto(
    val formatId: String,
    val ext: String = "mp4",
    val resolution: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val fps: Double? = null,
    val vcodec: String? = null,
    val acodec: String? = null,
    val tbr: Double? = null,
    val vbr: Double? = null,
    val abr: Double? = null,
    val filesize: Long? = null,
    val filesizeApprox: Long? = null,
    val formatNote: String? = null,
    val url: String = "",
    val protocol: String? = null
)
