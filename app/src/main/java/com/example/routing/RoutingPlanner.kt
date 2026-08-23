package com.example.routing

object RoutingPlanner {

    fun plan(url: String, match: PlatformMatch = PlatformClassifier.classify(url)): RoutingDecision {
        val allowYtDlp = YtDlpEligibilityPolicy.isEligible(url, match)
        val allowGenericImage = match.descriptor.allowGenericImageFallback

        return RoutingDecision(
            platform = match.platformId,
            intent = match.intent,
            allowYtDlp = allowYtDlp,
            allowGenericImageFallback = allowGenericImage,
            strategyOrder = match.descriptor.strategyOrder,
            reason = "Routing decision for platform ${match.platformName} with intent ${match.intent}"
        )
    }
}
