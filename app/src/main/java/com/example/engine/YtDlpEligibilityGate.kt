package com.example.engine

import com.example.data.model.ExtractedMedia

/**
 * Evaluates whether a URL and any intermediate extraction evidence
 * is semantically eligible to be passed to yt-dlp.
 *
 * Core Principle:
 * Answers "Can yt-dlp reasonably attempt this URL?" based on platform capabilities,
 * intent, and URL patterns — rather than "Did earlier metadata extraction find media?".
 */
object YtDlpEligibilityGate {

    data class GateDecision(
        val isEligible: Boolean,
        val reason: String
    )

    /**
     * Determines whether yt-dlp should be invoked for the given URL,
     * its semantic classification, and any intermediate page metadata extracted.
     */
    fun evaluate(
        url: String,
        classification: SemanticClassification,
        pageMedia: ExtractedMedia? = null,
        traceId: String? = null
    ): GateDecision {
        val effectiveTraceId = traceId ?: MediaExtractionTracer.currentSessionFlow.value?.traceId
        val opId = if (effectiveTraceId != null) {
            MediaExtractionTracer.startOperation(
                traceId = effectiveTraceId,
                component = "YtDlpEligibilityGate",
                stage = "YTDLP_ELIGIBILITY_CHECK",
                name = "evaluate",
                details = mapOf(
                    "url" to url,
                    "platform" to classification.platform,
                    "intent" to classification.intent.name,
                    "pageMedia" to (pageMedia?.javaClass?.simpleName ?: "null")
                )
            )
        } else null

        // Rule 1: Prohibited direct media links (already downloadable as standalone files)
        if (classification.intent == MediaIntent.DIRECT_MEDIA) {
            val decision = GateDecision(
                isEligible = false,
                reason = "URL is direct media link; handled by direct downloader rather than yt-dlp."
            )
            recordDecision(effectiveTraceId, opId, decision, "RULE_1_DIRECT_MEDIA")
            return decision
        }

        // Rule 2: Explicitly prohibited static image-only endpoints (e.g. i.redd.it, Facebook photo)
        if (classification.intent == MediaIntent.PLATFORM_IMAGE) {
            val decision = GateDecision(
                isEligible = false,
                reason = "URL is classified as single static image endpoint (${classification.reason}). yt-dlp is not needed."
            )
            recordDecision(effectiveTraceId, opId, decision, "RULE_2_PLATFORM_IMAGE")
            return decision
        }

        // Rule 3: Explicitly prohibited static carousels (e.g. TikTok /photo/, Reddit /gallery/)
        if (classification.intent == MediaIntent.PLATFORM_CAROUSEL) {
            val decision = GateDecision(
                isEligible = false,
                reason = "URL is classified as static image carousel (${classification.reason}); handled by page metadata extractor."
            )
            recordDecision(effectiveTraceId, opId, decision, "RULE_3_PLATFORM_CAROUSEL")
            return decision
        }

        // Rule 4: Domain-level static media checks
        val lower = url.lowercase()
        if (lower.contains("i.redd.it") || lower.contains("preview.redd.it")) {
            val decision = GateDecision(
                isEligible = false,
                reason = "Reddit static image domain (handled by direct image downloader)."
            )
            recordDecision(effectiveTraceId, opId, decision, "RULE_4_REDDIT_STATIC_IMAGE")
            return decision
        }
        if (lower.contains("tiktok.com") && lower.contains("/photo/")) {
            val decision = GateDecision(
                isEligible = false,
                reason = "TikTok /photo/ slideshow endpoint is not processed via yt-dlp."
            )
            recordDecision(effectiveTraceId, opId, decision, "RULE_4_TIKTOK_PHOTO")
            return decision
        }
        if (lower.contains("reddit.com") && lower.contains("/gallery/")) {
            val decision = GateDecision(
                isEligible = false,
                reason = "Reddit /gallery/ multi-image post is not processed via yt-dlp."
            )
            recordDecision(effectiveTraceId, opId, decision, "RULE_4_REDDIT_GALLERY")
            return decision
        }

        // Rule 5: If the URL is classified as a Platform Page, Video, or Audio (Reddit, Pinterest, TikTok, Instagram, Twitter, YouTube, etc.)
        if (classification.isYtDlpEligible ||
            classification.intent == MediaIntent.PLATFORM_PAGE ||
            classification.intent == MediaIntent.PLATFORM_VIDEO ||
            classification.intent == MediaIntent.PLATFORM_AUDIO ||
            classification.intent == MediaIntent.MEDIA_CONTAINER
        ) {
            val decision = GateDecision(
                isEligible = true,
                reason = "Platform is supported for yt-dlp extraction (${classification.reason})."
            )
            recordDecision(effectiveTraceId, opId, decision, "RULE_5_PLATFORM_ELIGIBLE")
            return decision
        }

        // Rule 6: Generic webpage with no known media signatures
        val decision = GateDecision(
            isEligible = false,
            reason = "URL does not meet criteria for yt-dlp extraction (${classification.reason})."
        )
        recordDecision(effectiveTraceId, opId, decision, "RULE_6_GENERIC_INELIGIBLE")
        return decision
    }

    private fun recordDecision(traceId: String?, opId: String?, decision: GateDecision, ruleMatched: String) {
        if (traceId != null) {
            val session = MediaExtractionTracer.getSession(traceId)
            session?.ytdlpEligible = decision.isEligible
            session?.ytdlpEligibilityReason = decision.reason

            MediaExtractionTracer.logRoutingDecision(
                traceId = traceId,
                stage = "YTDLP_ELIGIBILITY_GATE",
                decision = if (decision.isEligible) "ELIGIBLE" else "INELIGIBLE",
                reason = decision.reason,
                details = mapOf(
                    "ruleMatched" to ruleMatched,
                    "isEligible" to decision.isEligible.toString()
                )
            )

            if (opId != null) {
                MediaExtractionTracer.endOperation(
                    traceId = traceId,
                    opId = opId,
                    result = "isEligible=${decision.isEligible}",
                    decision = if (decision.isEligible) "ELIGIBLE" else "INELIGIBLE",
                    reason = decision.reason,
                    details = mapOf(
                        "isEligible" to decision.isEligible.toString(),
                        "ruleMatched" to ruleMatched,
                        "reason" to decision.reason
                    )
                )
            }
        }
    }
}
