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

        val clean = url.substringBefore("#").substringBefore("?").lowercase()
        val ext = clean.substringAfterLast(".", "")
        var isImage = EmbeddedExtractorEngine.isDirectImageExtension(ext)
        var isAudio = EmbeddedExtractorEngine.isDirectAudioExtension(ext)

        try {
            var contentLength: Long? = null
            var mimeType: String? = null

            val headRequest = Request.Builder().url(url).head().build()
            client.newCall(headRequest).execute().use { response ->
                if (response.isSuccessful) {
                    contentLength = response.header("Content-Length")?.toLongOrNull()
                    mimeType = response.header("Content-Type")

                    val ct = mimeType?.lowercase() ?: ""
                    if (ct.contains("text/html") || ct.contains("application/xhtml+xml")) {
                        // Not a direct media file despite extension/URL; delegate to other strategies
                        return@withContext ExtractionEvidence()
                    }

                    if (ct.startsWith("image/")) {
                        isImage = true
                        isAudio = false
                    } else if (ct.startsWith("audio/")) {
                        isAudio = true
                        isImage = false
                    }
                }
            }

            val candidate = CandidateNormalizer.fromDirectMedia(
                url = url,
                pageUrl = url,
                mimeType = mimeType,
                contentLength = contentLength,
                isAudio = isAudio,
                isImage = isImage
            )

            ExtractionEvidence(candidates = listOf(candidate))
        } catch (e: Exception) {
            // Even if HEAD fails (e.g. server does not support HEAD), if extension is direct media create candidate
            val candidate = CandidateNormalizer.fromDirectMedia(
                url = url,
                pageUrl = url,
                mimeType = null,
                contentLength = null,
                isAudio = isAudio,
                isImage = isImage
            )
            ExtractionEvidence(
                candidates = listOf(candidate),
                warnings = listOf("Direct media head request notice: ${e.message}")
            )
        }
    }
}
