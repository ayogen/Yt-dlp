package com.example.routing

import com.example.engine.EmbeddedExtractorEngine

object YtDlpEligibilityPolicy {

    fun isEligible(url: String, match: PlatformMatch): Boolean {
        // Direct media files (.mp4, .jpg, .mp3, etc.) do not require yt-dlp
        if (EmbeddedExtractorEngine.isDirectMediaUrl(url)) {
            return false
        }

        return match.isYtDlpEligible
    }
}
