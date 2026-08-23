package com.example.extraction

import com.example.data.model.AppSettings
import com.example.engine.EmbeddedExtractorEngine
import com.example.routing.RoutingDecision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class DirectMediaStrategy(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) : ExtractionStrategy {

    override val name: String = "DIRECT_MEDIA"

    override suspend fun canHandle(url: String, decision: RoutingDecision): Boolean {
        return EmbeddedExtractorEngine.isDirectMediaUrl(url)
    }

    override suspend fun extract(
        url: String,
        decision: RoutingDecision,
        settings: AppSettings?
    ): ExtractionEvidence = withContext(Dispatchers.IO) {
        if (!canHandle(url, decision)) {
            return@withContext ExtractionEvidence()
        }

        try {
            var contentLength: Long? = null
            var mimeType: String? = null

            val headRequest = Request.Builder().url(url).head().build()
            client.newCall(headRequest).execute().use { response ->
                if (response.isSuccessful) {
                    contentLength = response.header("Content-Length")?.toLongOrNull()
                    mimeType = response.header("Content-Type")
                }
            }

            val isAudio = url.substringBefore("?").lowercase().let {
                it.endsWith(".mp3") || it.endsWith(".m4a") || it.endsWith(".opus") || it.endsWith(".wav") || it.endsWith(".flac")
            }

            val candidate = CandidateNormalizer.fromDirectMedia(
                url = url,
                pageUrl = url,
                mimeType = mimeType,
                contentLength = contentLength,
                isAudio = isAudio
            )

            ExtractionEvidence(candidates = listOf(candidate))
        } catch (e: Exception) {
            ExtractionEvidence(
                warnings = listOf("Direct media head request failed: ${e.message}"),
                failedStrategies = listOf(name)
            )
        }
    }
}
