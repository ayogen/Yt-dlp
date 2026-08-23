package com.example.core.policy

sealed class ExtractionError(val message: String, val cause: Throwable? = null) {
    class UnsupportedUrl(url: String) : ExtractionError("Unsupported or malformed URL: $url")
    class NetworkTimeout(url: String, cause: Throwable? = null) : ExtractionError("Network request timed out for $url", cause)
    class YtDlpFailure(message: String, cause: Throwable? = null) : ExtractionError("yt-dlp extraction failed: $message", cause)
    class ResourceUnresolved(reason: String) : ExtractionError("Primary resource could not be resolved: $reason")
    class GenericError(message: String, cause: Throwable? = null) : ExtractionError(message, cause)
}
