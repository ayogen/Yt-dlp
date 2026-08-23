package com.example.core.model

import com.example.data.model.formatBytes

enum class SizeProvenance {
    FORMAT_FILESIZE,
    FORMAT_FILESIZE_APPROX,
    VERIFIED_HTTP,
    UNKNOWN_SOURCE
}

sealed class MediaSize {
    data class Exact(val bytes: Long, val source: SizeProvenance = SizeProvenance.FORMAT_FILESIZE) : MediaSize()
    data class Approximate(val bytes: Long, val source: SizeProvenance = SizeProvenance.FORMAT_FILESIZE_APPROX) : MediaSize()
    data class HttpContentLength(val bytes: Long, val source: SizeProvenance = SizeProvenance.VERIFIED_HTTP) : MediaSize()
    data class Unknown(val reason: String = "Not provided") : MediaSize()
    object NotApplicable : MediaSize()

    val bytesOrNull: Long?
        get() = when (this) {
            is Exact -> bytes
            is Approximate -> bytes
            is HttpContentLength -> bytes
            is Unknown, is NotApplicable -> null
        }

    val isApproximate: Boolean
        get() = this is Approximate

    val isKnown: Boolean
        get() = this is Exact || this is Approximate || this is HttpContentLength

    val displayString: String
        get() = when (this) {
            is Exact -> formatBytes(bytes)
            is Approximate -> "~${formatBytes(bytes)}"
            is HttpContentLength -> formatBytes(bytes)
            is Unknown -> "Unknown size"
            is NotApplicable -> "--"
        }

    companion object {
        fun fromBytes(filesize: Long?, filesizeApprox: Long?, httpContentLength: Long? = null): MediaSize {
            return when {
                filesize != null && filesize > 0 -> Exact(filesize)
                filesizeApprox != null && filesizeApprox > 0 -> Approximate(filesizeApprox)
                httpContentLength != null && httpContentLength > 0 -> HttpContentLength(httpContentLength)
                else -> Unknown("No size provided by source")
            }
        }
    }
}
