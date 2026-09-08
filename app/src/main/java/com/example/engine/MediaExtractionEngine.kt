package com.example.engine

import android.content.Context
import com.example.data.model.DownloadMode
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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
        geoBypass: Boolean = true,
        mode: DownloadMode = DownloadMode.AUTO
    ): Result<MediaCollection> = withContext(Dispatchers.IO) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("URL cannot be empty"))
        }

        val canonicalUrl = UrlNormalizer.resolveCanonicalUrl(trimmedUrl)
        AppLogger.i("MediaExtractionEngine", "Canonical analysis started ($mode) for: $canonicalUrl")

        try {
            withTimeout(8000L) {
                // Dedicated Instagram handler: inspect metadata & embed pages without mobile login gates
                val isInstagram = canonicalUrl.contains("instagram.com", ignoreCase = true) ||
                        canonicalUrl.contains("instagr.am", ignoreCase = true)
                if (isInstagram) {
                    AppLogger.i("MediaExtractionEngine", "Instagram URL detected: Performing dedicated metadata extraction")
                    val igMedia = PageMetadataExtractor.extractInstagramMedia(canonicalUrl)
                    if (igMedia != null) {
                        AppLogger.i("MediaExtractionEngine", "Instagram metadata extracted successfully (${igMedia.javaClass.simpleName})")
                        return@withTimeout Result.success(igMedia.toMediaCollection())
                    } else {
                        return@withTimeout Result.failure(Exception("Unable to extract media from this Instagram link. The post may be private or unavailable."))
                    }
                }

                when (mode) {
                    DownloadMode.VIDEO -> {
                        // Video Only / Quick yt-dlp:
                        // Skip complex DOM/JSON page scraping and dump-single-json.
                        // If direct video, inspect quickly. Otherwise return sensible defaults for yt-dlp download.
                        val cleanNoQuery = canonicalUrl.substringBefore("?").lowercase()
                        if (cleanNoQuery.endsWith(".mp4") || cleanNoQuery.endsWith(".mkv") ||
                            cleanNoQuery.endsWith(".webm") || cleanNoQuery.endsWith(".mov")
                        ) {
                            val directResult = withTimeoutOrNull(3000L) {
                                DirectMediaInspector.inspectMediaCollection(canonicalUrl)
                            }
                            if (directResult != null && directResult.isSuccess) {
                                return@withTimeout directResult
                            }
                        }

                        val titleFromUrl = canonicalUrl.substringBefore("?").substringAfterLast("/").substringBeforeLast(".")
                            .ifBlank { "video_${System.currentTimeMillis()}" }
                        val cleanTitle = FilenameFormatter.sanitize(titleFromUrl)
                        val defaultFormats = listOf(
                            FormatInfo(
                                formatId = "bestvideo+bestaudio/best",
                                ext = "mp4",
                                vcodec = "best",
                                acodec = "best",
                                url = canonicalUrl,
                                resolution = "Best Video (Muxed)",
                                isMuxed = true
                            ),
                            FormatInfo(
                                formatId = "best",
                                ext = "mp4",
                                resolution = "Single Stream (Best)"
                            ),
                            FormatInfo(
                                formatId = "bestvideo[height<=1080]+bestaudio/best",
                                ext = "mp4",
                                resolution = "1080p (Max)"
                            ),
                            FormatInfo(
                                formatId = "bestvideo[height<=720]+bestaudio/best",
                                ext = "mp4",
                                resolution = "720p (HD)"
                            )
                        )
                        val videoItem = MediaItem(
                            id = "video_" + UUID.randomUUID().toString().take(8),
                            title = cleanTitle,
                            sourceUrl = canonicalUrl,
                            webpageUrl = canonicalUrl,
                            thumbnail = "",
                            mediaKind = MediaKind.VIDEO,
                            formats = defaultFormats,
                            index = 0
                        )
                        val quickCollection = MediaCollection(
                            id = videoItem.id,
                            title = cleanTitle,
                            webpageUrl = canonicalUrl,
                            thumbnail = "",
                            mediaKind = MediaKind.VIDEO,
                            items = listOf(videoItem),
                            extractorName = "QuickYtDlpVideo"
                        )
                        AppLogger.i("MediaExtractionEngine", "VIDEO mode: Returning fast yt-dlp video configuration")
                        Result.success(quickCollection)
                    }

                    DownloadMode.IMAGE -> {
                        // Images Only / Direct Stream:
                        // Completely bypass Python / yt-dlp.
                        // Inspect directly via DirectMediaInspector and PageMetadataExtractor.
                        AppLogger.i("MediaExtractionEngine", "IMAGE mode: Running direct & page inspection, bypassing yt-dlp")
                        val directResult = withTimeoutOrNull(4000L) {
                            DirectMediaInspector.inspectMediaCollection(canonicalUrl)
                        }
                        if (directResult != null && directResult.isSuccess) {
                            val col = directResult.getOrThrow()
                            if (col.mediaKind == MediaKind.IMAGE) {
                                return@withTimeout Result.success(col)
                            }
                        }

                        val pageMedia = withTimeoutOrNull(5000L) {
                            PageMetadataExtractor.extractPageMedia(canonicalUrl)
                                ?: PageMetadataExtractor.extractGenericPageMedia(canonicalUrl)
                        }
                        if (pageMedia != null) {
                            val col = pageMedia.toMediaCollection()
                            val sanitizedItems = col.items.map { it.copy(mediaKind = MediaKind.IMAGE, isSelected = true) }
                            Result.success(
                                col.copy(
                                    mediaKind = if (sanitizedItems.size > 1) MediaKind.CAROUSEL else MediaKind.IMAGE,
                                    items = sanitizedItems
                                )
                            )
                        } else {
                            Result.failure(Exception("No image or photo carousel found at this URL."))
                        }
                    }

                    DownloadMode.AUTO -> {
                        runCanonicalExtractionPipeline(canonicalUrl, cookiesFile, userAgent, proxyUrl, geoBypass)
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            AppLogger.e("MediaExtractionEngine", "Media analysis timed out after 8s: ${e.message}")
            Result.failure(Exception("Media analysis timed out after 8 seconds. The host or media server did not respond in time."))
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
                is ExtractedMedia.Video -> {
                    val directUrl = pageMedia.metadata.directDownloadUrl
                    if (!directUrl.isNullOrBlank() && (directUrl.contains(".mp4") || directUrl.startsWith("http"))) {
                        AppLogger.i("MediaExtractionEngine", "Page metadata completed: Extracted direct Video (${pageMedia.metadata.title})")
                        AppLogger.i("MediaExtractionEngine", "Analysis completed")
                        return Result.success(pageMedia.toMediaCollection())
                    }
                    AppLogger.i("MediaExtractionEngine", "Page metadata completed: Video found without direct stream")
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
        val isMetaUrl = canonicalUrl.contains("instagram.com", ignoreCase = true) ||
                canonicalUrl.contains("instagr.am", ignoreCase = true) ||
                canonicalUrl.contains("facebook.com", ignoreCase = true) ||
                canonicalUrl.contains("fb.watch", ignoreCase = true)
        val customArgsBuilder = StringBuilder()
        if (!userAgent.isNullOrBlank() && !isMetaUrl) {
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

        val ytCollection = ytDlpResult?.getOrNull()
        val hasPlayableMedia = ytCollection != null && ytCollection.items.isNotEmpty() && (
            ytCollection.mediaKind == MediaKind.IMAGE ||
            ytCollection.mediaKind == MediaKind.CAROUSEL ||
            ytCollection.mediaKind == MediaKind.AUDIO ||
            ytCollection.items.any { item ->
                item.isImage || item.isAudio || item.formats.any { fmt ->
                    !fmt.isAudioOnly && (fmt.height != null && fmt.height > 0 || (fmt.vcodec.isNotBlank() && fmt.vcodec != "none"))
                } || (item.sourceUrl.isNotBlank() && (item.formats.isNotEmpty() || ytCollection.items.size > 1))
            }
        )

        if (ytDlpResult != null && ytDlpResult.isSuccess && hasPlayableMedia) {
            AppLogger.i("MediaExtractionEngine", "yt-dlp completed with playable media formats")
            AppLogger.i("MediaExtractionEngine", "Analysis completed")
            return Result.success(ytCollection!!)
        }

        val ytDlpError = when {
            ytDlpResult == null -> "Extraction timed out after 20s"
            ytDlpResult.isFailure -> ytDlpResult.exceptionOrNull()?.message.orEmpty()
            !hasPlayableMedia -> "yt-dlp returned zero playable video formats"
            else -> "yt-dlp extraction inconclusive"
        }
        AppLogger.w("MediaExtractionEngine", "yt-dlp completed/failed: $ytDlpError. Triggering image/DOM fallback.")

        // Stage 4: Comprehensive DOM/JSON-LD, OpenGraph, and Responsive Image Fallback
        val socialFallback = try {
            withTimeoutOrNull(6000L) {
                PageMetadataExtractor.extractSocialVideoOrImageFallback(canonicalUrl)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

        val fallbackMedia = socialFallback ?: pageMedia ?: try {
            withTimeoutOrNull(6000L) {
                PageMetadataExtractor.extractPageMedia(canonicalUrl)
                    ?: PageMetadataExtractor.extractGenericPageMedia(canonicalUrl)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w("MediaExtractionEngine", "PageMetadataExtractor fallback error: ${e.message}")
            null
        }

        if (fallbackMedia != null) {
            val fallbackCollection = fallbackMedia.toMediaCollection()
            // Explicitly ensure images are mapped to MediaKind.IMAGE so DownloadPlanner and DownloadManager route them directly to ImageDownloader
            val sanitizedItems = fallbackCollection.items.map { item ->
                if (fallbackCollection.mediaKind == MediaKind.IMAGE || item.isImage || fallbackMedia is ExtractedMedia.Image) {
                    item.copy(
                        mediaKind = MediaKind.IMAGE,
                        isSelected = true
                    )
                } else {
                    item
                }
            }
            val finalCollection = fallbackCollection.copy(
                mediaKind = if (fallbackMedia is ExtractedMedia.Image) MediaKind.IMAGE else fallbackCollection.mediaKind,
                items = sanitizedItems
            )
            AppLogger.i("MediaExtractionEngine", "Fallback successful: Extracted ${finalCollection.mediaKind} (${finalCollection.title}) with ${finalCollection.items.size} item(s)")
            AppLogger.i("MediaExtractionEngine", "Analysis completed")
            return Result.success(finalCollection)
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
            AppLogger.i("MediaExtractionEngine", "Analysis completed via EmbeddedExtractorEngine")
            return embeddedResult
        }

        // Return a clear, diagnosed error message
        val friendlyMessage = when {
            ytDlpError.contains("timed out", ignoreCase = true) ->
                "Media extraction timed out. The server took too long to respond."
            ytDlpError.contains("Private video", ignoreCase = true) || ytDlpError.contains("requires login", ignoreCase = true) || ytDlpError.contains("account is private", ignoreCase = true) ->
                "This content is private or requires authentication."
            ytDlpError.contains("No video formats found", ignoreCase = true) || ytDlpError.contains("zero playable", ignoreCase = true) ->
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
