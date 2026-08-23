package com.example.extraction

import android.content.Context
import com.example.core.model.CanonicalMediaResult
import com.example.core.model.MediaCandidate
import com.example.data.model.AppSettings
import com.example.engine.AppLogger
import com.example.engine.UrlNormalizer
import com.example.routing.PlatformClassifier
import com.example.routing.RoutingPlanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExtractionCoordinator(
    private val context: Context? = null,
    private val strategies: List<ExtractionStrategy> = listOf(
        DirectMediaStrategy(),
        YtDlpStrategy(context),
        GenericPageStrategy()
    )
) {

    suspend fun extract(url: String, settings: AppSettings? = null): Result<CanonicalMediaResult> = withContext(Dispatchers.IO) {
        val canonicalUrl = UrlNormalizer.resolveCanonicalUrl(url)
        AppLogger.i("ExtractionCoordinator", "Starting coordinated extraction for: $canonicalUrl (original: $url)")

        val platformMatch = PlatformClassifier.classify(canonicalUrl)
        val decision = RoutingPlanner.plan(canonicalUrl, platformMatch)
        AppLogger.i("ExtractionCoordinator", "Routing decision: platform=${decision.platform}, intent=${decision.intent}, ytdlp=${decision.allowYtDlp}")

        val allCandidates = mutableListOf<MediaCandidate>()
        val allWarnings = mutableListOf<String>()
        val failedStrategies = mutableListOf<String>()
        var accumulatedMeta: com.example.core.model.CanonicalMetadata? = null

        // Execute strategies according to strategyOrder
        for (strategyKey in decision.strategyOrder) {
            val strategy = strategies.firstOrNull { it.name.equals(strategyKey, ignoreCase = true) }
            if (strategy != null && strategy.canHandle(canonicalUrl, decision)) {
                AppLogger.d("ExtractionCoordinator", "Executing strategy: ${strategy.name}")
                val evidence = strategy.extract(canonicalUrl, decision, settings)
                allCandidates.addAll(evidence.candidates)
                allWarnings.addAll(evidence.warnings)
                failedStrategies.addAll(evidence.failedStrategies)
                if (evidence.metadata != null && accumulatedMeta == null) {
                    accumulatedMeta = evidence.metadata
                }

                // If we found a verified primary video/audio or direct stream, we have strong primary evidence
                val hasUsablePrimary = evidence.candidates.any {
                    it.role.isPrimary && (it.formats.isNotEmpty() || it.mediaType == com.example.data.model.MediaType.IMAGE ||
                            it.mediaType == com.example.data.model.MediaType.PLAYLIST ||
                            it.source == com.example.core.model.CandidateSource.DIRECT_HTTP)
                }
                if (hasUsablePrimary && (strategy.name == "YTDLP" || strategy.name == "DIRECT_MEDIA")) {
                    AppLogger.i("ExtractionCoordinator", "Found authoritative primary candidate via ${strategy.name}")
                    break
                }
            }
        }

        // Resolve primary resource deterministically
        val resolution = PrimaryResourceResolver.resolve(
            candidates = allCandidates,
            sourceUrl = url,
            canonicalUrl = canonicalUrl,
            platform = decision.platform,
            intent = decision.intent
        )

        if (!resolution.isResolved && allCandidates.isEmpty() && failedStrategies.isNotEmpty()) {
            val errorMsg = allWarnings.firstOrNull() ?: "Extraction failed across all strategies: ${failedStrategies.joinToString(", ")}"
            return@withContext Result.failure(IllegalStateException(errorMsg))
        }

        val canonicalResult = PrimaryResourceResolver.toCanonicalMediaResult(
            decision = resolution,
            sourceUrl = url,
            canonicalUrl = canonicalUrl,
            platform = decision.platform,
            intent = decision.intent,
            fallbackMetadata = accumulatedMeta ?: com.example.core.model.CanonicalMetadata()
        )


        if (!resolution.isResolved) {
            AppLogger.w("ExtractionCoordinator", "Primary resource unresolved: ${resolution.reason}")
            // Return failure if no primary candidate could be resolved for video intent
            return@withContext Result.failure(IllegalStateException(resolution.reason))
        }

        AppLogger.i("ExtractionCoordinator", "Successfully resolved primary media: ${canonicalResult.primary?.title}")
        Result.success(canonicalResult)
    }
}
