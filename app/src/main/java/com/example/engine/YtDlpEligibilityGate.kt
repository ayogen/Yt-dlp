package com.example.engine

import com.example.data.model.ExtractedMedia

/**
 * Evaluates whether a URL and any intermediate extraction evidence
 * is semantically eligible to be passed to yt-dlp.
 */
object YtDlpEligibilityGate {

    data class GateDecision(
        val isEligible: Boolean,
        val reason: String
    )

    /**
     * Determines whether yt-dlp should be invoked for the given URL,
     * its initial semantic classification, and any intermediate page metadata extracted.
     */
    fun evaluate(
        url: String,
        classification: SemanticClassification,
        pageMedia: ExtractedMedia?
    ): GateDecision {
        // Rule 1: If page metadata extraction already found an Image or Carousel, do NOT run yt-dlp.
        if (pageMedia is ExtractedMedia.Image) {
            return GateDecision(
                isEligible = false,
                reason = "Page extraction already extracted an Image. yt-dlp execution is prohibited."
            )
        }
        if (pageMedia is ExtractedMedia.Carousel) {
            return GateDecision(
                isEligible = false,
                reason = "Page extraction already extracted a Carousel. yt-dlp execution is prohibited."
            )
        }

        // Rule 2: Explicitly prohibited media intents (Direct image/audio or Platform Image/Carousel)
        if (classification.intent == MediaIntent.PLATFORM_CAROUSEL) {
            return GateDecision(
                isEligible = false,
                reason = "URL is classified as ${classification.intent} (${classification.reason}). yt-dlp execution is prohibited."
            )
        }
        if (classification.intent == MediaIntent.PLATFORM_IMAGE) {
            return GateDecision(
                isEligible = false,
                reason = "URL is classified as ${classification.intent} (${classification.reason}). yt-dlp execution is prohibited."
            )
        }
        if (classification.intent == MediaIntent.DIRECT_MEDIA) {
            return GateDecision(
                isEligible = false,
                reason = "URL is direct media link. Handled by direct downloader, not yt-dlp."
            )
        }

        // Rule 3: Check platform-specific rules
        val lower = url.lowercase()
        if (lower.contains("tiktok.com") && lower.contains("/photo/")) {
            return GateDecision(
                isEligible = false,
                reason = "TikTok /photo/ URL cannot be processed by yt-dlp."
            )
        }
        if (lower.contains("reddit.com") && lower.contains("/gallery/")) {
            return GateDecision(
                isEligible = false,
                reason = "Reddit /gallery/ URL cannot be processed by yt-dlp."
            )
        }
        if (lower.contains("i.redd.it") || lower.contains("preview.redd.it")) {
            return GateDecision(
                isEligible = false,
                reason = "Reddit static image domain cannot be processed by yt-dlp."
            )
        }

        // Rule 4: Generic or Platform Video/Audio endpoints
        if (classification.isYtDlpEligible) {
            return GateDecision(
                isEligible = true,
                reason = "URL is eligible for yt-dlp extraction (${classification.reason})."
            )
        }

        // Default safe fallback: If unknown and not explicitly eligible, do not risk hanging in yt-dlp
        return GateDecision(
            isEligible = false,
            reason = "URL does not meet criteria for yt-dlp extraction (${classification.reason})."
        )
    }
}
