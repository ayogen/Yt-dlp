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
        pageMedia: ExtractedMedia?,
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
                    "intent" to classification.intent.name,
                    "pageMedia" to (pageMedia?.javaClass?.simpleName ?: "null")
                )
            )
        } else null

        // Rule 1: If page metadata extraction already found an Image or Carousel, do NOT run yt-dlp.
        if (pageMedia is ExtractedMedia.Image) {
            val decision = GateDecision(
                isEligible = false,
                reason = "Page extraction already extracted an Image. yt-dlp execution is prohibited."
            )
            recordDecision(effectiveTraceId, opId, decision, "RULE_1_PAGE_IMAGE")
            return decision
        }
        if (pageMedia is ExtractedMedia.Carousel) {
            val decision = GateDecision(
                isEligible = false,
                reason = "Page extraction already extracted a Carousel. yt-dlp execution is prohibited."
            )
            recordDecision(effectiveTraceId, opId, decision, "RULE_1_PAGE_CAROUSEL")
            return decision
        }

        // Rule 2: Explicitly prohibited media intents (Direct image/audio or Platform Image/Carousel)
        if (classification.intent == MediaIntent.PLATFORM_CAROUSEL) {
            val decision = GateDecision(
                isEligible = false,
                reason = "URL is classified as ${classification.intent} (${classification.reason}). yt-dlp execution is prohibited."
            )
            recordDecision(effectiveTraceId, opId, decision, "RULE_2_PLATFORM_CAROUSEL")
            return decision
        }
        if (classification.intent == MediaIntent.PLATFORM_IMAGE) {
            val decision = GateDecision(
                isEligible = false,
                reason = "URL is classified as ${classification.intent} (${classification.reason}). yt-dlp execution is prohibited."
            )
            recordDecision(effectiveTraceId, opId, decision, "RULE_2_PLATFORM_IMAGE")
            return decision
        }
        if (classification.intent == MediaIntent.DIRECT_MEDIA) {
            val decision = GateDecision(
                isEligible = false,
                reason = "URL is direct media link. Handled by direct downloader, not yt-dlp."
            )
            recordDecision(effectiveTraceId, opId, decision, "RULE_2_DIRECT_MEDIA")
            return decision
        }

        // Rule 3: Check platform-specific rules
        val lower = url.lowercase()
        if (lower.contains("tiktok.com") && lower.contains("/photo/")) {
            val decision = GateDecision(
                isEligible = false,
                reason = "TikTok /photo/ URL cannot be processed by yt-dlp."
            )
            recordDecision(effectiveTraceId, opId, decision, "RULE_3_TIKTOK_PHOTO")
            return decision
        }
        if (lower.contains("reddit.com") && lower.contains("/gallery/")) {
            val decision = GateDecision(
                isEligible = false,
                reason = "Reddit /gallery/ URL cannot be processed by yt-dlp."
            )
            recordDecision(effectiveTraceId, opId, decision, "RULE_3_REDDIT_GALLERY")
            return decision
        }
        if (lower.contains("i.redd.it") || lower.contains("preview.redd.it")) {
            val decision = GateDecision(
                isEligible = false,
                reason = "Reddit static image domain cannot be processed by yt-dlp."
            )
            recordDecision(effectiveTraceId, opId, decision, "RULE_3_REDDIT_STATIC_IMAGE")
            return decision
        }

        // Rule 4: Generic or Platform Video/Audio endpoints
        if (classification.isYtDlpEligible) {
            val decision = GateDecision(
                isEligible = true,
                reason = "URL is eligible for yt-dlp extraction (${classification.reason})."
            )
            recordDecision(effectiveTraceId, opId, decision, "RULE_4_ELIGIBLE")
            return decision
        }

        // Default safe fallback: If unknown and not explicitly eligible, do not risk hanging in yt-dlp
        val decision = GateDecision(
            isEligible = false,
            reason = "URL does not meet criteria for yt-dlp extraction (${classification.reason})."
        )
        recordDecision(effectiveTraceId, opId, decision, "RULE_5_DEFAULT_FALLBACK")
        return decision
    }

    private fun recordDecision(traceId: String?, opId: String?, decision: GateDecision, ruleMatched: String) {
        if (traceId != null) {
            val session = MediaExtractionTracer.getSession(traceId)
            session?.ytdlpEligible = decision.isEligible
            session?.ytdlpEligibilityReason = decision.reason

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
