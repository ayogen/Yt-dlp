package com.example.routing

object YtDlpEligibilityPolicy {

    fun isEligible(url: String, match: PlatformMatch): Boolean {
        // Direct media files (.mp4, .mp3, etc.) do not require yt-dlp first
        val clean = url.substringBefore("?").lowercase()
        val isDirectMedia = clean.endsWith(".mp4") || clean.endsWith(".mp3") ||
                clean.endsWith(".m4a") || clean.endsWith(".opus") || clean.endsWith(".wav") ||
                clean.endsWith(".flac") || clean.endsWith(".m3u8")

        if (isDirectMedia && match.platformId == "generic") {
            return false
        }

        return match.isYtDlpEligible
    }
}
