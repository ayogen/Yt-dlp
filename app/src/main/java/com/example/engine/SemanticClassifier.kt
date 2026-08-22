package com.example.engine

/**
 * High-level semantic classification of media URLs before extraction engine selection.
 */
enum class MediaIntent {
    DIRECT_MEDIA,       // Direct file links (.jpg, .mp4, .mp3, etc.)
    PLATFORM_VIDEO,     // YouTube, TikTok /video/, Reddit v.redd.it, Instagram /reel/
    PLATFORM_AUDIO,     // SoundCloud, audio-only platforms
    PLATFORM_IMAGE,     // Single image post endpoints
    PLATFORM_CAROUSEL,  // TikTok /photo/, Reddit /gallery/, Instagram /p/ sidecar
    GENERIC_WEBPAGE,    // HTML webpages with unknown media composition
    UNKNOWN             // Unrecognized URL patterns
}

/**
 * Result of semantic URL classification.
 */
data class SemanticClassification(
    val intent: MediaIntent,
    val platform: String,
    val isYtDlpEligible: Boolean,
    val reason: String
)

/**
 * Central semantic classifier and router.
 * Evaluates URLs prior to engine dispatch to prevent non-video/photo/gallery
 * endpoints from being misrouted to yt-dlp.
 */
object SemanticClassifier {

    fun classify(url: String, traceId: String? = null): SemanticClassification {
        val effectiveTraceId = traceId ?: MediaExtractionTracer.currentSessionFlow.value?.traceId
        val opId = if (effectiveTraceId != null) {
            MediaExtractionTracer.startOperation(
                traceId = effectiveTraceId,
                component = "SemanticClassifier",
                stage = "SEMANTIC_CLASSIFICATION",
                name = "classify",
                details = mapOf("url" to url)
            )
        } else null

        val trimmed = url.trim()
        if (trimmed.isBlank()) {
            val res = SemanticClassification(
                intent = MediaIntent.UNKNOWN,
                platform = "unknown",
                isYtDlpEligible = false,
                reason = "URL is blank"
            )
            recordResult(effectiveTraceId, opId, res)
            return res
        }

        val clean = trimmed.substringBefore("?").substringBefore("#")
        val lower = clean.lowercase()

        // 1. Direct media extension detection
        if (hasDirectMediaExtension(lower)) {
            val isAudio = lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".flac") ||
                    lower.endsWith(".opus") || lower.endsWith(".wav") || lower.endsWith(".ogg")
            val isImage = lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
                    lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".avif")
            val res = SemanticClassification(
                intent = MediaIntent.DIRECT_MEDIA,
                platform = "direct",
                isYtDlpEligible = false, // Direct media handled by DirectMediaInspector / ImageDownloader
                reason = if (isImage) "Direct image file extension" else if (isAudio) "Direct audio file extension" else "Direct video file extension"
            )
            recordResult(effectiveTraceId, opId, res)
            return res
        }

        // 2. TikTok
        if (lower.contains("tiktok.com")) {
            val res = when {
                lower.contains("/photo/") -> SemanticClassification(
                    intent = MediaIntent.PLATFORM_CAROUSEL,
                    platform = "tiktok",
                    isYtDlpEligible = false,
                    reason = "TikTok /photo/ endpoint represents static images/slideshow (NOT eligible for yt-dlp)"
                )
                lower.contains("/video/") || lower.contains("/v/") -> SemanticClassification(
                    intent = MediaIntent.PLATFORM_VIDEO,
                    platform = "tiktok",
                    isYtDlpEligible = true,
                    reason = "TikTok /video/ endpoint is a streamable video (yt-dlp eligible)"
                )
                else -> SemanticClassification(
                    intent = MediaIntent.GENERIC_WEBPAGE,
                    platform = "tiktok",
                    isYtDlpEligible = true, // Unresolved short link or generic TikTok page
                    reason = "TikTok general page"
                )
            }
            recordResult(effectiveTraceId, opId, res)
            return res
        }

        // 3. Reddit
        if (lower.contains("reddit.com") || lower.contains("redd.it")) {
            val res = when {
                lower.contains("v.redd.it") -> SemanticClassification(
                    intent = MediaIntent.PLATFORM_VIDEO,
                    platform = "reddit",
                    isYtDlpEligible = true,
                    reason = "Reddit v.redd.it direct video host (yt-dlp eligible)"
                )
                lower.contains("i.redd.it") || lower.contains("preview.redd.it") -> SemanticClassification(
                    intent = MediaIntent.PLATFORM_IMAGE,
                    platform = "reddit",
                    isYtDlpEligible = false,
                    reason = "Reddit i.redd.it static image host (NOT eligible for yt-dlp)"
                )
                lower.contains("/gallery/") -> SemanticClassification(
                    intent = MediaIntent.PLATFORM_CAROUSEL,
                    platform = "reddit",
                    isYtDlpEligible = false,
                    reason = "Reddit /gallery/ multi-image post (NOT eligible for yt-dlp)"
                )
                else -> SemanticClassification(
                    intent = MediaIntent.GENERIC_WEBPAGE,
                    platform = "reddit",
                    isYtDlpEligible = false, // Post pages must be inspected via PageMetadataExtractor first
                    reason = "Reddit comments/post page requires page metadata inspection before engine selection"
                )
            }
            recordResult(effectiveTraceId, opId, res)
            return res
        }

        // 4. Instagram
        if (lower.contains("instagram.com") || lower.contains("instagr.am")) {
            val res = when {
                lower.contains("/reel/") || lower.contains("/reels/") || lower.contains("/tv/") -> SemanticClassification(
                    intent = MediaIntent.PLATFORM_VIDEO,
                    platform = "instagram",
                    isYtDlpEligible = true,
                    reason = "Instagram Reel/TV video endpoint (yt-dlp eligible)"
                )
                lower.contains("/p/") -> SemanticClassification(
                    intent = MediaIntent.GENERIC_WEBPAGE,
                    platform = "instagram",
                    isYtDlpEligible = false, // Instagram /p/ can be photo, carousel, or video. Inspect via PageMetadata first!
                    reason = "Instagram /p/ post may be image, carousel, or video (inspect page metadata first)"
                )
                else -> SemanticClassification(
                    intent = MediaIntent.GENERIC_WEBPAGE,
                    platform = "instagram",
                    isYtDlpEligible = false,
                    reason = "Instagram generic endpoint"
                )
            }
            recordResult(effectiveTraceId, opId, res)
            return res
        }

        // 5. YouTube
        if (lower.contains("youtube.com") || lower.contains("youtu.be")) {
            val res = SemanticClassification(
                intent = MediaIntent.PLATFORM_VIDEO,
                platform = "youtube",
                isYtDlpEligible = true,
                reason = "YouTube video/shorts/audio stream (yt-dlp eligible)"
            )
            recordResult(effectiveTraceId, opId, res)
            return res
        }

        // 6. Facebook
        if (lower.contains("facebook.com") || lower.contains("fb.watch")) {
            val res = when {
                lower.contains("/watch") || lower.contains("/reel") || lower.contains("/videos") -> SemanticClassification(
                    intent = MediaIntent.PLATFORM_VIDEO,
                    platform = "facebook",
                    isYtDlpEligible = true,
                    reason = "Facebook video/watch/reel endpoint (yt-dlp eligible)"
                )
                lower.contains("/photo") || lower.contains("/photos") -> SemanticClassification(
                    intent = MediaIntent.PLATFORM_IMAGE,
                    platform = "facebook",
                    isYtDlpEligible = false,
                    reason = "Facebook photo post (NOT eligible for yt-dlp)"
                )
                else -> SemanticClassification(
                    intent = MediaIntent.GENERIC_WEBPAGE,
                    platform = "facebook",
                    isYtDlpEligible = true,
                    reason = "Facebook generic post/link"
                )
            }
            recordResult(effectiveTraceId, opId, res)
            return res
        }

        // 7. X / Twitter
        if (lower.contains("twitter.com") || lower.contains("x.com")) {
            val res = SemanticClassification(
                intent = MediaIntent.GENERIC_WEBPAGE,
                platform = "twitter",
                isYtDlpEligible = true,
                reason = "Twitter/X post (may contain video, audio, or image)"
            )
            recordResult(effectiveTraceId, opId, res)
            return res
        }

        // 8. Dedicated Audio Platforms (SoundCloud, Bandcamp, Mixcloud)
        if (lower.contains("soundcloud.com") || lower.contains("bandcamp.com") || lower.contains("mixcloud.com")) {
            val res = SemanticClassification(
                intent = MediaIntent.PLATFORM_AUDIO,
                platform = "audio_platform",
                isYtDlpEligible = true,
                reason = "Dedicated audio streaming platform (yt-dlp eligible)"
            )
            recordResult(effectiveTraceId, opId, res)
            return res
        }

        // 9. Generic Webpages / Other yt-dlp supported domains
        val res = SemanticClassification(
            intent = MediaIntent.GENERIC_WEBPAGE,
            platform = "generic",
            isYtDlpEligible = true, // Allowed as generic fallback if page inspection yields no non-video media
            reason = "Generic webpage (eligible for yt-dlp only after page metadata inspection)"
        )
        recordResult(effectiveTraceId, opId, res)
        return res
    }

    private fun recordResult(traceId: String?, opId: String?, res: SemanticClassification) {
        if (traceId != null) {
            val session = MediaExtractionTracer.getSession(traceId)
            session?.platform = res.platform
            session?.intent = res.intent.name
            session?.confidence = 1.0f

            if (opId != null) {
                MediaExtractionTracer.endOperation(
                    traceId = traceId,
                    opId = opId,
                    result = "platform=${res.platform} intent=${res.intent}",
                    decision = res.intent.name,
                    reason = res.reason,
                    details = mapOf(
                        "platform" to res.platform,
                        "intent" to res.intent.name,
                        "isYtDlpEligible" to res.isYtDlpEligible.toString(),
                        "reason" to res.reason
                    )
                )
            }
        }
    }

    private fun hasDirectMediaExtension(lowerUrl: String): Boolean {
        return lowerUrl.endsWith(".jpg") || lowerUrl.endsWith(".jpeg") || lowerUrl.endsWith(".png") ||
                lowerUrl.endsWith(".webp") || lowerUrl.endsWith(".gif") || lowerUrl.endsWith(".avif") ||
                lowerUrl.endsWith(".mp4") || lowerUrl.endsWith(".webm") || lowerUrl.endsWith(".mkv") ||
                lowerUrl.endsWith(".mov") || lowerUrl.endsWith(".flv") || lowerUrl.endsWith(".avi") ||
                lowerUrl.endsWith(".mp3") || lowerUrl.endsWith(".m4a") || lowerUrl.endsWith(".flac") ||
                lowerUrl.endsWith(".opus") || lowerUrl.endsWith(".wav") || lowerUrl.endsWith(".ogg")
    }
}
