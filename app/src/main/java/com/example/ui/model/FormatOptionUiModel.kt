package com.example.ui.model

import com.example.core.model.MediaFormat
import com.example.data.model.FormatInfo

data class FormatOptionUiModel(
    val formatId: String,
    val displayResolution: String,
    val displaySize: String,
    val codecSummary: String,
    val ext: String = "mp4",
    val isVideoOnly: Boolean = false,
    val isAudioOnly: Boolean = false,
    val isMuxed: Boolean = true,
    val filesize: Long? = null,
    val filesizeApprox: Long? = null,
    val rawFormat: MediaFormat? = null
) {
    fun toFormatInfo(): FormatInfo {
        return rawFormat?.toFormatInfo() ?: FormatInfo(
            formatId = formatId,
            ext = ext,
            resolution = displayResolution,
            filesize = filesize,
            filesizeApprox = filesizeApprox,
            isVideoOnly = isVideoOnly,
            isAudioOnly = isAudioOnly,
            isMuxed = isMuxed
        )
    }

    companion object {
        fun fromMediaFormat(format: MediaFormat): FormatOptionUiModel {
            return FormatOptionUiModel(
                formatId = format.formatId,
                displayResolution = format.displayResolution,
                displaySize = format.displayFileSize,
                codecSummary = format.codecSummary,
                ext = format.ext,
                isVideoOnly = format.isVideoOnly,
                isAudioOnly = format.isAudioOnly,
                isMuxed = format.isMuxed,
                filesize = format.size.bytesOrNull.takeIf { !format.size.isApproximate },
                filesizeApprox = format.size.bytesOrNull.takeIf { format.size.isApproximate },
                rawFormat = format
            )
        }

        fun fromFormatInfo(info: FormatInfo): FormatOptionUiModel {
            return fromMediaFormat(MediaFormat.fromFormatInfo(info))
        }
    }
}
