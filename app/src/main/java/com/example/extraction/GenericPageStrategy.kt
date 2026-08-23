package com.example.extraction

import com.example.core.model.MediaCandidate
import com.example.data.model.AppSettings
import com.example.engine.EmbeddedExtractorEngine
import com.example.routing.RoutingDecision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GenericPageStrategy : ExtractionStrategy {

    override val name: String = "GENERIC_PAGE"

    override suspend fun canHandle(url: String, decision: RoutingDecision): Boolean {
        return true
    }

    override suspend fun extract(
        url: String,
        decision: RoutingDecision,
        settings: AppSettings?
    ): ExtractionEvidence = withContext(Dispatchers.IO) {
        try {
            val result = EmbeddedExtractorEngine.analyzeUrl(url)
            if (result.isSuccess) {
                val metadata = result.getOrThrow()
                val isImageIntent = decision.intent == "image" || decision.intent == "photo"

                val candidates = mutableListOf<MediaCandidate>()

                if (metadata.directDownloadUrl?.isNotBlank() == true || metadata.formats.isNotEmpty()) {
                    val streamUrl = metadata.directDownloadUrl ?: metadata.formats.firstOrNull()?.url ?: ""
                    if (streamUrl.isNotBlank()) {
                        val streamCandidate = CandidateNormalizer.fromEmbeddedMedia(
                            mediaUrl = streamUrl,
                            pageUrl = url,
                            title = metadata.title,
                            uploader = metadata.uploader,
                            mediaType = metadata.mediaType,
                            formats = metadata.formats
                        )
                        candidates.add(streamCandidate)
                    }
                }

                if (metadata.thumbnail.isNotBlank()) {
                    val ogCandidate = CandidateNormalizer.fromOpenGraphImage(
                        imageUrl = metadata.thumbnail,
                        pageUrl = url,
                        title = metadata.title,
                        uploader = metadata.uploader,
                        isExplicitImageIntent = isImageIntent && decision.allowGenericImageFallback
                    )
                    candidates.add(ogCandidate)
                }

                ExtractionEvidence(candidates = candidates)

            } else {
                val err = result.exceptionOrNull()?.message ?: "Generic page extraction failed"
                ExtractionEvidence(
                    warnings = listOf("Generic page metadata failed: $err"),
                    failedStrategies = listOf(name)
                )
            }
        } catch (e: Exception) {
            ExtractionEvidence(
                warnings = listOf("Generic page exception: ${e.message}"),
                failedStrategies = listOf(name)
            )
        }
    }
}
