package com.example.engine

import android.content.Context
import com.example.data.model.ExtractedMedia
import com.example.data.model.FormatInfo
import com.example.data.model.MediaMetadata
import com.example.data.model.MediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID

class MediaExtractionEngine(private val context: Context) {

    /**
     * Unified media extraction method with bounded timeouts and structured fallback:
     * 1. Direct HTTP/MIME/Magic Bytes Inspection (5s timeout)
     * 2. Platform-specific Social / Page Metadata Extraction (6s timeout)
     * 3. yt-dlp Engine CLI extraction (20s timeout)
     * 4. Embedded extractor & OpenGraph fallback (5s timeout)
     */
    suspend fun extractMedia(
        url: String,
        cookiesFile: File? = null,
        userAgent: String? = null,
        proxyUrl: String? = null,
        geoBypass: Boolean = true,
        traceId: String? = null
    ): Result<MediaMetadata> = withContext(Dispatchers.IO) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("URL cannot be empty"))
        }

        val effectiveTraceId = traceId ?: MediaExtractionTracer.currentSessionFlow.value?.traceId
            ?: MediaExtractionTracer.startSession(trimmedUrl).traceId

        val canonicalUrl = UrlNormalizer.resolveCanonicalUrl(trimmedUrl, effectiveTraceId)
        AppLogger.i("MediaExtractionEngine", "Analysis started: $canonicalUrl")

        // Step 0: Semantic Media Classification
        val classification = SemanticClassifier.classify(canonicalUrl, effectiveTraceId)
        AppLogger.i(
            "MediaExtractionEngine",
            "[SemanticClassifier] platform=${classification.platform}, intent=${classification.intent}, ytDlpEligible=${classification.isYtDlpEligible}, reason=${classification.reason}"
        )

        try {
            // Stage 1: Direct Media Link Inspection (fast HEAD / range inspection)
            AppLogger.i("MediaExtractionEngine", "Direct inspection started")
            val directInspection = try {
                withTimeoutOrNull(5000L) {
                    DirectMediaInspector.inspectUrl(canonicalUrl, effectiveTraceId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w("MediaExtractionEngine", "Direct inspection error: ${e.message}")
                null
            }

            if (directInspection == null) {
                AppLogger.w("MediaExtractionEngine", "Direct inspection timed out")
            } else if (directInspection.isDirectMedia) {
                AppLogger.i("MediaExtractionEngine", "Direct inspection completed: ${directInspection.mimeType} (${directInspection.mediaType})")
                val titleFromUrl = canonicalUrl.substringBefore("?").substringAfterLast("/").substringBeforeLast(".")
                    .ifBlank { "media_${System.currentTimeMillis()}" }

                val cleanTitle = FilenameFormatter.sanitize(titleFromUrl)

                val resolvedType = MediaTypeResolver.resolveMediaType(
                    url = canonicalUrl,
                    mimeType = directInspection.mimeType,
                    classification = classification,
                    traceId = effectiveTraceId
                )

                val metadata = when (resolvedType) {
                    MediaType.IMAGE -> {
                        MediaMetadata(
                            id = "direct_img_" + UUID.randomUUID().toString().take(8),
                            title = cleanTitle,
                            webpageUrl = canonicalUrl,
                            directDownloadUrl = canonicalUrl,
                            thumbnail = canonicalUrl,
                            mediaType = MediaType.IMAGE,
                            mimeType = directInspection.mimeType,
                            width = directInspection.width,
                            height = directInspection.height,
                            fileSize = directInspection.contentLength,
                            extractorName = "DirectImage"
                        )
                    }
                    MediaType.AUDIO -> {
                        val format = FormatInfo(
                            formatId = "direct_audio",
                            ext = directInspection.suggestedExt,
                            acodec = directInspection.mimeType.substringAfter("audio/"),
                            url = canonicalUrl,
                            filesize = directInspection.contentLength,
                            isAudioOnly = true
                        )
                        MediaMetadata(
                            id = "direct_audio_" + UUID.randomUUID().toString().take(8),
                            title = cleanTitle,
                            webpageUrl = canonicalUrl,
                            directDownloadUrl = canonicalUrl,
                            mediaType = MediaType.AUDIO,
                            formats = listOf(format),
                            extractorName = "DirectAudio"
                        )
                    }
                    else -> {
                        val format = FormatInfo(
                            formatId = "direct_video",
                            ext = directInspection.suggestedExt,
                            vcodec = "h264",
                            acodec = "aac",
                            url = canonicalUrl,
                            filesize = directInspection.contentLength,
                            isMuxed = true
                        )
                        MediaMetadata(
                            id = "direct_video_" + UUID.randomUUID().toString().take(8),
                            title = cleanTitle,
                            webpageUrl = canonicalUrl,
                            directDownloadUrl = canonicalUrl,
                            mediaType = MediaType.VIDEO,
                            formats = listOf(format),
                            extractorName = "DirectVideo"
                        )
                    }
                }
                AppLogger.i("MediaExtractionEngine", "Analysis completed")
                val sanitized = MediaTypeResolver.sanitizeMetadata(metadata, effectiveTraceId)
                return@withContext Result.success(sanitized)
            } else {
                AppLogger.i("MediaExtractionEngine", "Direct inspection completed")
            }

            // Stage 2: Page Metadata Extraction (DOM / OpenGraph / Carousel / Photo detection)
            AppLogger.i("MediaExtractionEngine", "Page metadata extraction started")
            val pageMedia = try {
                withTimeoutOrNull(6000L) {
                    PageMetadataExtractor.extractPageMedia(canonicalUrl, effectiveTraceId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w("MediaExtractionEngine", "Page metadata extraction error: ${e.message}")
                null
            }

            if (pageMedia != null) {
                when (pageMedia) {
                    is ExtractedMedia.Image, is ExtractedMedia.Carousel -> {
                        AppLogger.i("MediaExtractionEngine", "Page metadata completed: Extracted ${pageMedia.javaClass.simpleName}")
                        AppLogger.i("MediaExtractionEngine", "Analysis completed")
                        val meta = MediaTypeResolver.sanitizeMetadata(pageMedia.toMediaMetadata(), effectiveTraceId)
                        return@withContext Result.success(meta)
                    }
                    else -> {
                        AppLogger.i("MediaExtractionEngine", "Page metadata completed")
                    }
                }
            } else {
                AppLogger.i("MediaExtractionEngine", "Page metadata completed")
            }

            // Stage 3: yt-dlp Engine CLI extraction (Evaluated against Eligibility Gate)
            val gateDecision = YtDlpEligibilityGate.evaluate(canonicalUrl, classification, pageMedia, effectiveTraceId)
            AppLogger.i(
                "MediaExtractionEngine",
                "[YtDlpEligibilityGate] eligible=${gateDecision.isEligible}, reason=${gateDecision.reason}"
            )

            val ytDlpResult = if (gateDecision.isEligible) {
                AppLogger.i("MediaExtractionEngine", "yt-dlp extraction started")
                val binary = YtDlpBinaryManager.getBinaryFile(context)
                val binaryPath = binary?.absolutePath ?: "yt-dlp"
                val customArgsBuilder = StringBuilder()
                if (!userAgent.isNullOrBlank()) {
                    customArgsBuilder.append("--user-agent \"$userAgent\" ")
                }
                if (!proxyUrl.isNullOrBlank()) {
                    customArgsBuilder.append("--proxy $proxyUrl ")
                }
                if (geoBypass) {
                    customArgsBuilder.append("--geo-bypass ")
                }

                try {
                    withTimeoutOrNull(20000L) {
                        YtDlpProcessRunner.extractMetadataCli(
                            binaryPath = binaryPath,
                            url = canonicalUrl,
                            cookiesPath = cookiesFile?.absolutePath,
                            customArgs = customArgsBuilder.toString().trim(),
                            traceId = effectiveTraceId
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.e("MediaExtractionEngine", "yt-dlp execution exception: ${e.message}")
                    Result.failure(e)
                }
            } else {
                AppLogger.i("MediaExtractionEngine", "yt-dlp extraction skipped: ${gateDecision.reason}")
                null
            }

            if (ytDlpResult != null && ytDlpResult.isSuccess) {
                val meta = ytDlpResult.getOrThrow()
                val refinedMeta = MediaTypeResolver.sanitizeMetadata(meta, effectiveTraceId)
                AppLogger.i("MediaExtractionEngine", "yt-dlp completed")
                AppLogger.i("MediaExtractionEngine", "Analysis completed")
                return@withContext Result.success(refinedMeta)
            }

            val ytDlpError = if (gateDecision.isEligible) {
                if (ytDlpResult == null) "Extraction timed out after 20s" else ytDlpResult.exceptionOrNull()?.message.orEmpty()
            } else {
                "Skipped: ${gateDecision.reason}"
            }
            AppLogger.w("MediaExtractionEngine", "yt-dlp completed/failed: $ytDlpError")

            // Stage 4: Embedded extractor & Generic OpenGraph fallback
            if (pageMedia != null) {
                AppLogger.i("MediaExtractionEngine", "Analysis completed")
                val meta = MediaTypeResolver.sanitizeMetadata(pageMedia.toMediaMetadata(), effectiveTraceId)
                return@withContext Result.success(meta)
            }

            val pageMediaFallback = try {
                withTimeoutOrNull(5000L) {
                    PageMetadataExtractor.extractGenericPageMedia(canonicalUrl, effectiveTraceId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }

            if (pageMediaFallback != null) {
                AppLogger.i("MediaExtractionEngine", "Analysis completed")
                val meta = MediaTypeResolver.sanitizeMetadata(pageMediaFallback.toMediaMetadata(), effectiveTraceId)
                return@withContext Result.success(meta)
            }

            val embeddedResult = try {
                withTimeoutOrNull(5000L) {
                    EmbeddedExtractorEngine.analyzeUrl(canonicalUrl, effectiveTraceId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }

            if (embeddedResult != null && embeddedResult.isSuccess) {
                AppLogger.i("MediaExtractionEngine", "Analysis completed")
                val meta = MediaTypeResolver.sanitizeMetadata(embeddedResult.getOrThrow(), effectiveTraceId)
                return@withContext Result.success(meta)
            }

            // Return a clear, diagnosed error message
            val friendlyMessage = when {
                ytDlpError.contains("timed out", ignoreCase = true) ->
                    "Media extraction timed out. The server took too long to respond."
                ytDlpError.contains("Private video", ignoreCase = true) || ytDlpError.contains("requires login", ignoreCase = true) || ytDlpError.contains("account is private", ignoreCase = true) ->
                    "This content is private or requires authentication."
                ytDlpError.contains("No video formats found", ignoreCase = true) ->
                    "No downloadable media streams were found at this URL."
                ytDlpError.contains("Unsupported URL", ignoreCase = true) ->
                    "Unsupported media URL or webpage format."
                ytDlpError.contains("Video unavailable", ignoreCase = true) ->
                    "The requested media is unavailable or has been removed."
                else ->
                    ytDlpError.ifBlank { "Unable to extract media from this URL." }
            }

            AppLogger.e("MediaExtractionEngine", "Analysis failed: $friendlyMessage")
            Result.failure(Exception(friendlyMessage))
        } catch (e: CancellationException) {
            AppLogger.i("MediaExtractionEngine", "Analysis cancelled")
            throw e
        } catch (e: Throwable) {
            val msg = e.message ?: "Unexpected extraction failure"
            AppLogger.e("MediaExtractionEngine", "Analysis failed: $msg")
            Result.failure(Exception(msg))
        }
    }
}
