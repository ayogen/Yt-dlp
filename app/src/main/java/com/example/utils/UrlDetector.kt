package com.example.utils

object UrlDetector {
    private val KNOWN_MEDIA_DOMAINS = listOf(
        "youtube.com", "youtu.be", "tiktok.com", "instagram.com", "instagr.am",
        "facebook.com", "fb.watch", "twitter.com", "x.com", "vimeo.com",
        "soundcloud.com", "reddit.com", "bilibili.com", "twitch.tv",
        "dailymotion.com", "threads.net", "pin.it", "pinterest.com"
    )

    private val DIRECT_EXTENSIONS = listOf(
        ".mp4", ".m3u8", ".mp3", ".m4a", ".webm", ".flv", ".mov", ".wav", ".aac", ".ogg", ".ts",
        ".jpg", ".jpeg", ".png", ".webp", ".gif", ".avif", ".bmp", ".heic", ".heif", ".svg"
    )

    fun isPotentialMediaUrl(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val trimmed = text.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            return false
        }
        val lower = trimmed.lowercase()
        if (KNOWN_MEDIA_DOMAINS.any { lower.contains(it) }) {
            return true
        }
        if (DIRECT_EXTENSIONS.any { lower.contains(it) }) {
            return true
        }
        // General http(s) link without spaces
        return !trimmed.contains(" ") && trimmed.length > 10
    }

    fun extractFirstUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val words = text.split("\\s+".toRegex())
        for (w in words) {
            val candidate = w.trim()
            if (isPotentialMediaUrl(candidate)) {
                return candidate
            }
        }
        return null
    }
}
