package com.example.engine

import android.content.Context
import com.example.data.model.ExtractedMedia
import com.example.data.model.FormatInfo
import com.example.data.model.MediaCollection
import com.example.data.model.MediaItem
import com.example.data.model.MediaKind
import com.example.data.model.MediaMetadata
import com.example.data.model.MediaType
import com.example.data.model.SizeProvenance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID

class MediaExtractionEngine(private val context: Context) {

    /**
     * Canonical media extraction method directly returning Result<MediaCollection>
     * with bounded timeouts and structured fallback:
     * 1. Direct HTTP/MIME/Magic Bytes Inspection (5s timeout)
     * 2. Platform-specific Social / Page Metadata Extraction (6s timeout)
     * 3. yt-dlp Engine CLI extraction (20s timeout)
     * 4. Embedded extractor & OpenGraph fallback (5s timeout)
     */
    suspend fun extractMediaCollection(
        url: String,
        cookiesFile: File? = null,
        userAgent: String? = null,
        proxyUrl: String? = null,
        geoBypass: Boolean = true
    ): Result<MediaCollection> = withContext(Dispatchers.IO) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("URL cannot be empty"))
        }

        val canonicalUrl = UrlNormalizer.resolveCanonicalUrl(trimmedUrl)
        AppLogger.i("MediaExtractionEngine", "Canonical analysis started")

        try {
            val overallResult = withTimeoutOrNull(50000L) {
                runCanonicalExtractionPipeline(canonicalUrl, cookiesFile, userAgent, proxyUrl, geoBypass)
            }
            if (overallResult != null) {
                return@withContext overallResult
            }
            AppLogger.e("MediaExtractionEngine", "Analysis failed: Global analysis timeout exceeded (50s)")
            Result.failure(Exception("Media analysis timed out after 50 seconds. The host or media server did not respond in time."))
        } catch (e: CancellationException) {
            AppLogger.i("MediaExtractionEngine", "Analysis cancelled")
            throw e
        } catch (e: Throwable) {
            val msg = e.message ?: "Unexpected extraction failure"
            AppLogger.e("MediaExtractionEngine", "Analysis failed: $msg")
            Result.failure(Exception(msg))
        }
    }

    /**
     * Backward compatibility extractor returning legacy MediaMetadata.
     */
    suspend fun extractMedia(
        url: String,
        cookiesFile: File? = null,
        userAgent: String? = null,
        proxyUrl: String? = null,
        geoBypass: Boolean = true
    ): Result<MediaMetadata> = withContext(Dispatchers.IO) {
        val colResult = extractMediaCollection(url, cookiesFile, userAgent, proxyUrl, geoBypass)
        if (colResult.isSuccess) {
            val col = colResult.getOrThrow()
            Result.success(MediaCollection.toLegacyMediaMetadata(col))
        } else {
            Result.failure(colResult.exceptionOrNull() ?: Exception("Media analysis failed"))
        }
    }

    private suspend fun runCanonicalExtractionPipeline(
        canonicalUrl: String,
        cookiesFile: File?,
        userAgent: String?,
        proxyUrl: String?,
        geoBypass: Boolean
    ): Result<MediaCollection> {
        // Stage 1: Direct Media Link Inspection (fast HEAD / range inspection)
        AppLogger.i("MediaExtractionEngine", "Direct inspection started")
        val directResult = try {
            withTimeoutOrNull(5000L) {
                DirectMediaInspector.inspectMediaCollection(canonicalUrl)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w("MediaExtractionEngine", "Direct inspection error: ${e.message}")
            null
        }

        if (directResult == null) {
            AppLogger.w("MediaExtractionEngine", "Direct inspection timed out or inconclusive")
        } else if (directResult.isSuccess) {
            val collection = directResult.getOrThrow()
            AppLogger.i("MediaExtractionEngine", "Direct inspection completed: ${collection.mediaKind} (${collection.title})")
            AppLogger.i("MediaExtractionEngine", "Analysis completed")
            return Result.success(collection)
        } else {
            AppLogger.i("MediaExtractionEngine", "Direct inspection completed (not direct media)")
        }

        // Stage 2: Page Metadata Extraction (DOM / OpenGraph / Carousel / Photo detection)
        AppLogger.i("MediaExtractionEngine", "Page metadata extraction started")
        val pageMedia = try {
            withTimeoutOrNull(6000L) {
                PageMetadataExtractor.extractPageMedia(canonicalUrl)
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
                    return Result.success(pageMedia.toMediaCollection())
                }
                else -> {
                    AppLogger.i("MediaExtractionEngine", "Page metadata completed")
                }
            }
        } else {
            AppLogger.i("MediaExtractionEngine", "Page metadata completed")
        }

        // Stage 3: yt-dlp Engine CLI extraction
        AppLogger.i("MediaExtractionEngine", "yt-dlp fallback started")
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

        val ytDlpResult = try {
            withTimeoutOrNull(20000L) {
                YtDlpProcessRunner.extractMediaCollectionCli(
                    binaryPath = binaryPath,
                    url = canonicalUrl,
                    cookiesPath = cookiesFile?.absolutePath,
                    customArgs = customArgsBuilder.toString().trim()
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e("MediaExtractionEngine", "yt-dlp execution exception: ${e.message}")
            Result.failure(e)
        }

        if (ytDlpResult != null && ytDlpResult.isSuccess) {
            val col = ytDlpResult.getOrThrow()
            AppLogger.i("MediaExtractionEngine", "yt-dlp completed")
            AppLogger.i("MediaExtractionEngine", "Analysis completed")
            return Result.success(col)
        }

        val ytDlpError = if (ytDlpResult == null) "Extraction timed out after 20s" else ytDlpResult.exceptionOrNull()?.message.orEmpty()
        AppLogger.w("MediaExtractionEngine", "yt-dlp completed/failed: $ytDlpError")

        // Stage 4: Embedded extractor & Generic OpenGraph fallback
        if (pageMedia != null) {
            AppLogger.i("MediaExtractionEngine", "Analysis completed")
            return Result.success(pageMedia.toMediaCollection())
        }

        val pageMediaFallback = try {
            withTimeoutOrNull(5000L) {
                PageMetadataExtractor.extractGenericPageMedia(canonicalUrl)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

        if (pageMediaFallback != null) {
            AppLogger.i("MediaExtractionEngine", "Analysis completed")
            return Result.success(pageMediaFallback.toMediaCollection())
        }

        val embeddedResult = try {
            withTimeoutOrNull(5000L) {
                EmbeddedExtractorEngine.analyzeMediaCollection(canonicalUrl)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

        if (embeddedResult != null && embeddedResult.isSuccess) {
            AppLogger.i("MediaExtractionEngine", "Analysis completed")
            return embeddedResult
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
        return Result.failure(Exception(friendlyMessage))
    }
}
