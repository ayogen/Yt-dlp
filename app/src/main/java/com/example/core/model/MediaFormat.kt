package com.example.core.model

import com.example.data.model.FormatInfo

data class MediaFormat(
    val formatId: String,
    val ext: String = "mp4",
    val resolution: String = "",
    val width: Int? = null,
    val height: Int? = null,
    val fps: Double? = null,
    val vcodec: String = "none",
    val acodec: String = "none",
    val tbr: Double? = null,
    val vbr: Double? = null,
    val abr: Double? = null,
    val size: MediaSize = MediaSize.Unknown(),
    val formatNote: String = "",
    val url: String = "",
    val protocol: String = "https",
    val isVideoOnly: Boolean = (vcodec != "none" && vcodec.isNotBlank()) && (acodec == "none" || acodec.isBlank()),
    val isAudioOnly: Boolean = (acodec != "none" && acodec.isNotBlank()) && (vcodec == "none" || vcodec.isBlank()),
    val isMuxed: Boolean = (vcodec != "none" && vcodec.isNotBlank()) && (acodec != "none" && acodec.isNotBlank())
) {
    val displayResolution: String
        get() = when {
            height != null && height > 0 -> "${height}p"
            resolution.isNotBlank() && resolution != "audio only" -> resolution
            isAudioOnly -> "Audio Only"
            else -> "Default"
        }

    val displayFileSize: String
        get() = size.displayString

    val codecSummary: String
        get() = when {
            isAudioOnly -> "Audio: ${acodec.uppercase()}"
            isVideoOnly -> "Video: ${vcodec.uppercase()} (No Audio)"
            isMuxed -> "${vcodec.uppercase()} + ${acodec.uppercase()}"
            else -> ext.uppercase()
        }

    fun toFormatInfo(): FormatInfo {
        val exactBytes = (size as? MediaSize.Exact)?.bytes
        val approxBytes = (size as? MediaSize.Approximate)?.bytes
        val httpBytes = (size as? MediaSize.HttpContentLength)?.bytes
        return FormatInfo(
            formatId = formatId,
            ext = ext,
            resolution = resolution,
            width = width,
            height = height,
            fps = fps,
            vcodec = vcodec,
            acodec = acodec,
            tbr = tbr,
            vbr = vbr,
            abr = abr,
            filesize = exactBytes ?: httpBytes,
            filesizeApprox = approxBytes,
            formatNote = formatNote,
            url = url,
            protocol = protocol,
            isVideoOnly = isVideoOnly,
            isAudioOnly = isAudioOnly,
            isMuxed = isMuxed
        )
    }

    companion object {
        fun fromFormatInfo(info: FormatInfo): MediaFormat {
            val size = MediaSize.fromBytes(info.filesize, info.filesizeApprox)
            return MediaFormat(
                formatId = info.formatId,
                ext = info.ext,
                resolution = info.resolution,
                width = info.width,
                height = info.height,
                fps = info.fps,
                vcodec = info.vcodec,
                acodec = info.acodec,
                tbr = info.tbr,
                vbr = info.vbr,
                abr = info.abr,
                size = size,
                formatNote = info.formatNote,
                url = info.url,
                protocol = info.protocol,
                isVideoOnly = info.isVideoOnly,
                isAudioOnly = info.isAudioOnly,
                isMuxed = info.isMuxed
            )
        }
    }
}
