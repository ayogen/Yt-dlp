package com.example.routing

import com.example.engine.EmbeddedExtractorEngine

object RoutingPlanner {

    fun plan(url: String, match: PlatformMatch = PlatformClassifier.classify(url)): RoutingDecision {
        val isDirectMedia = EmbeddedExtractorEngine.isDirectMediaUrl(url)
        val allowYtDlp = YtDlpEligibilityPolicy.isEligible(url, match)
        val allowGenericImage = match.descriptor.allowGenericImageFallback

        val clean = url.substringBefore("#").substringBefore("?").lowercase()
        val ext = clean.substringAfterLast(".", "")
        val intent = when {
            isDirectMedia && EmbeddedExtractorEngine.isDirectImageExtension(ext) -> "image"
            isDirectMedia && EmbeddedExtractorEngine.isDirectAudioExtension(ext) -> "audio"
            isDirectMedia -> "video"
            else -> match.intent
        }

        val baseOrder = match.descriptor.strategyOrder
        val strategyOrder = if (isDirectMedia) {
            listOf("DIRECT_MEDIA") + baseOrder.filter { it != "DIRECT_MEDIA" }
        } else {
            baseOrder
        }

        return RoutingDecision(
            platform = match.platformId,
            intent = intent,
            allowYtDlp = allowYtDlp,
            allowGenericImageFallback = allowGenericImage,
            strategyOrder = strategyOrder,
            reason = "Routing decision for platform ${match.platformName} with intent $intent"
        )
    }
}
