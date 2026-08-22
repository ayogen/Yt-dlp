package com.example.engine

import com.example.data.model.CarouselItem
import com.example.data.model.ExtractedMedia
import com.example.data.model.MediaType
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object PageMetadataExtractor {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /**
     * Attempts to extract rich media (Image, Carousel, Video, or Audio) from a web page using
     * HTML meta tags, OpenGraph, JSON-LD, and platform-specific social DOM structures.
     * Supports coroutine cancellation.
     */
    suspend fun extractPageMedia(url: String, traceId: String? = null): ExtractedMedia? {
        val effectiveTraceId = traceId ?: MediaExtractionTracer.currentSessionFlow.value?.traceId
        val opId = if (effectiveTraceId != null) {
            MediaExtractionTracer.startOperation(
                traceId = effectiveTraceId,
                component = "PageMetadataExtractor",
                stage = "PAGE_METADATA_EXTRACTION",
                name = "extractPageMedia",
                details = mapOf("url" to url)
            )
        } else null

        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) {
            if (effectiveTraceId != null && opId != null) {
                MediaExtractionTracer.endOperation(
                    traceId = effectiveTraceId,
                    opId = opId,
                    result = "null",
                    decision = "BLANK_URL",
                    reason = "URL is blank"
                )
            }
            return null
        }

        val lower = cleanUrl.lowercase()

        // 1. Social platform direct handlers
        if (lower.contains("instagram.com") || lower.contains("instagr.am")) {
            val igMedia = extractInstagramMedia(cleanUrl, effectiveTraceId)
            if (igMedia != null) {
                recordCompleted(effectiveTraceId, opId, igMedia, "INSTAGRAM_HANDLER")
                return igMedia
            }
        }

        if (lower.contains("facebook.com") || lower.contains("fb.watch")) {
            val fbMedia = extractFacebookMedia(cleanUrl, effectiveTraceId)
            if (fbMedia != null) {
                recordCompleted(effectiveTraceId, opId, fbMedia, "FACEBOOK_HANDLER")
                return fbMedia
            }
        }

        if (lower.contains("pinterest.com") || lower.contains("pin.it")) {
            val pinMedia = extractPinterestMedia(cleanUrl, effectiveTraceId)
            if (pinMedia != null) {
                recordCompleted(effectiveTraceId, opId, pinMedia, "PINTEREST_HANDLER")
                return pinMedia
            }
        }

        if (lower.contains("reddit.com") || lower.contains("redd.it")) {
            val redditMedia = extractRedditMedia(cleanUrl, effectiveTraceId)
            if (redditMedia != null) {
                recordCompleted(effectiveTraceId, opId, redditMedia, "REDDIT_HANDLER")
                return redditMedia
            }
        }

        if (lower.contains("tiktok.com")) {
            val tiktokMedia = extractTikTokMedia(cleanUrl, effectiveTraceId)
            if (tiktokMedia != null) {
                recordCompleted(effectiveTraceId, opId, tiktokMedia, "TIKTOK_HANDLER")
                return tiktokMedia
            }
        }

        // 2. Generic webpage OpenGraph & JSON-LD extraction
        val generic = extractGenericPageMedia(cleanUrl, effectiveTraceId)
        if (generic != null) {
            recordCompleted(effectiveTraceId, opId, generic, "GENERIC_OPENGRAPH_HANDLER")
            return generic
        }

        if (effectiveTraceId != null && opId != null) {
            MediaExtractionTracer.endOperation(
                traceId = effectiveTraceId,
                opId = opId,
                result = "null",
                decision = "NO_PAGE_MEDIA_FOUND",
                reason = "No supported social or OpenGraph media tags found"
            )
        }
        return null
    }

    private fun recordCompleted(traceId: String?, opId: String?, media: ExtractedMedia, handler: String) {
        if (traceId != null && opId != null) {
            val mediaMeta = media.toMediaMetadata()
            val mediaTypeName = when (media) {
                is ExtractedMedia.Image -> "IMAGE"
                is ExtractedMedia.Carousel -> "CAROUSEL (${media.items.size} items)"
                is ExtractedMedia.Video -> "VIDEO"
                is ExtractedMedia.Audio -> "AUDIO"
                is ExtractedMedia.Playlist -> "PLAYLIST"
                is ExtractedMedia.Unknown -> "UNKNOWN"
            }
            MediaExtractionTracer.endOperation(
                traceId = traceId,
                opId = opId,
                result = "$handler extracted $mediaTypeName: ${mediaMeta.title}",
                decision = "PAGE_MEDIA_EXTRACTED",
                reason = "Extracted media via $handler",
                details = mapOf("mediaType" to mediaTypeName, "title" to mediaMeta.title, "webpageUrl" to mediaMeta.webpageUrl)
            )
        }
    }

    suspend fun extractFacebookMedia(url: String, traceId: String? = null): ExtractedMedia? {
        val lower = url.lowercase()
        // If explicitly a Reel or Video URL, let yt-dlp handle it
        if (lower.contains("/reel/") || lower.contains("/videos/") || lower.contains("/watch") || lower.contains("fb.watch")) {
            if (traceId != null) {
                MediaExtractionTracer.logEvent(
                    traceId = traceId,
                    component = "PageMetadataExtractor",
                    stage = "FACEBOOK_EXTRACTION",
                    event = "SKIPPED_FOR_YTDLP",
                    level = TraceLevel.DEBUG,
                    reason = "Facebook video/reel endpoint delegated to yt-dlp"
                )
            }
            return null
        }

        try {
            val html = fetchHtml(url, traceId) ?: return null

            // Check if page contains video indicators
            val hasVideo = html.contains("og:video") || html.contains("\"video_id\"") || html.contains("playable_url")
            if (hasVideo) {
                if (traceId != null) {
                    MediaExtractionTracer.logEvent(
                        traceId = traceId,
                        component = "PageMetadataExtractor",
                        stage = "FACEBOOK_EXTRACTION",
                        event = "VIDEO_INDICATOR_FOUND",
                        level = TraceLevel.DEBUG,
                        reason = "Facebook page contains video indicators, delegating to yt-dlp"
                    )
                }
                return null
            }

            // Extract photo
            val rawOgImage = extractMetaTag(html, "og:image:secure_url")
                ?: extractMetaTag(html, "og:image")
                ?: extractMetaTag(html, "twitter:image")

            val validatedUrl = validateAndLogCandidate(
                traceId = traceId,
                source = "FACEBOOK_OG_IMAGE",
                attribute = "content",
                candidate = rawOgImage,
                mediaType = MediaType.IMAGE,
                confidence = 0.95f
            )

            if (validatedUrl != null) {
                val title = extractMetaTag(html, "og:title") ?: extractTitle(html) ?: "Facebook Photo"
                val description = extractMetaTag(html, "og:description").orEmpty()
                val width = extractMetaTag(html, "og:image:width")?.toIntOrNull()
                val height = extractMetaTag(html, "og:image:height")?.toIntOrNull()

                return ExtractedMedia.Image(
                    id = "fb_" + UUID.randomUUID().toString().take(8),
                    title = cleanText(title),
                    webpageUrl = url,
                    directDownloadUrl = validatedUrl,
                    thumbnail = validatedUrl,
                    mimeType = "image/jpeg",
                    width = width,
                    height = height,
                    uploader = "Facebook",
                    description = cleanText(description)
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.d("PageMetadataExtractor", "Facebook page extraction failed: ${e.message}")
        }
        return null
    }

    suspend fun extractInstagramMedia(url: String, traceId: String? = null): ExtractedMedia? {
        val lower = url.lowercase()
        // If explicitly a Reel, TV, or Stories URL, let yt-dlp handle it directly
        if (lower.contains("/reel/") || lower.contains("/reels/") || lower.contains("/tv/") || lower.contains("/stories/")) {
            return null
        }

        try {
            val html = fetchHtml(url, traceId) ?: return null

            // Look for carousel items in embedded JSON scripts
            val carouselItems = extractInstagramCarouselItems(html, traceId)
            if (carouselItems.isNotEmpty()) {
                val title = extractMetaTag(html, "og:title") ?: extractTitle(html) ?: "Instagram Post"
                return ExtractedMedia.Carousel(
                    id = "ig_carousel_" + UUID.randomUUID().toString().take(8),
                    title = cleanText(title),
                    webpageUrl = url,
                    uploader = "Instagram",
                    thumbnail = carouselItems.firstOrNull()?.thumbnail.orEmpty(),
                    items = carouselItems
                )
            }

            // Check if single image post without video
            val hasVideo = extractMetaTag(html, "og:video") != null || html.contains("\"is_video\":true")
            if (hasVideo) {
                return null // Let yt-dlp handle single video
            }

            val rawOgImage = extractMetaTag(html, "og:image:secure_url")
                ?: extractMetaTag(html, "og:image")
                ?: extractMetaTag(html, "twitter:image")

            val validatedUrl = validateAndLogCandidate(
                traceId = traceId,
                source = "INSTAGRAM_OG_IMAGE",
                attribute = "content",
                candidate = rawOgImage,
                mediaType = MediaType.IMAGE,
                confidence = 0.95f
            )

            if (validatedUrl != null) {
                val title = extractMetaTag(html, "og:title") ?: extractTitle(html) ?: "Instagram Photo"
                val description = extractMetaTag(html, "og:description").orEmpty()
                val width = extractMetaTag(html, "og:image:width")?.toIntOrNull()
                val height = extractMetaTag(html, "og:image:height")?.toIntOrNull()

                return ExtractedMedia.Image(
                    id = "ig_" + UUID.randomUUID().toString().take(8),
                    title = cleanText(title),
                    webpageUrl = url,
                    directDownloadUrl = validatedUrl,
                    thumbnail = validatedUrl,
                    mimeType = "image/jpeg",
                    width = width,
                    height = height,
                    uploader = "Instagram",
                    description = cleanText(description)
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.d("PageMetadataExtractor", "Instagram extraction error: ${e.message}")
        }
        return null
    }

    private fun extractInstagramCarouselItems(html: String, traceId: String? = null): List<CarouselItem> {
        val items = mutableListOf<CarouselItem>()
        try {
            // Find JSON blocks containing edge_sidecar_to_children or carousel_media
            val scriptPattern = Pattern.compile("<script[^>]*>(.*?)</script>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
            val matcher = scriptPattern.matcher(html)
            while (matcher.find()) {
                val scriptContent = matcher.group(1) ?: continue
                if (scriptContent.contains("edge_sidecar_to_children") || scriptContent.contains("carousel_media")) {
                    val edgesPattern = Pattern.compile("\"edge_sidecar_to_children\"\\s*:\\s*\\{\\s*\"edges\"\\s*:\\s*(\\[.*?\\])\\s*\\}", Pattern.DOTALL)
                    val edgeMatcher = edgesPattern.matcher(scriptContent)
                    if (edgeMatcher.find()) {
                        val jsonArr = JSONArray(edgeMatcher.group(1))
                        for (i in 0 until jsonArr.length()) {
                            val node = jsonArr.getJSONObject(i).optJSONObject("node") ?: continue
                            val isVideo = node.optBoolean("is_video", false)
                            val displayUrl = node.optString("display_url", "")
                            val videoUrl = node.optString("video_url", "")
                            val id = node.optString("id", UUID.randomUUID().toString().take(8))
                            val dim = node.optJSONObject("dimensions")
                            val w = dim?.optInt("width")
                            val h = dim?.optInt("height")

                            if (isVideo && videoUrl.isNotBlank()) {
                                if (traceId != null) {
                                    MediaExtractionTracer.logCandidate(
                                        traceId = traceId,
                                        source = "INSTAGRAM_CAROUSEL_JSON",
                                        subSource = "edge_sidecar_to_children.video_url",
                                        rawValue = videoUrl,
                                        accepted = true,
                                        mediaType = MediaType.VIDEO,
                                        confidence = 0.95f
                                    )
                                }
                                items.add(
                                    CarouselItem(
                                        id = id,
                                        title = "Video #${i + 1}",
                                        mediaType = MediaType.VIDEO,
                                        sourceUrl = videoUrl,
                                        thumbnail = displayUrl.ifBlank { videoUrl },
                                        width = w,
                                        height = h,
                                        mimeType = "video/mp4"
                                    )
                                )
                            } else if (displayUrl.isNotBlank()) {
                                if (traceId != null) {
                                    MediaExtractionTracer.logCandidate(
                                        traceId = traceId,
                                        source = "INSTAGRAM_CAROUSEL_JSON",
                                        subSource = "edge_sidecar_to_children.display_url",
                                        rawValue = displayUrl,
                                        accepted = true,
                                        mediaType = MediaType.IMAGE,
                                        confidence = 0.95f
                                    )
                                }
                                items.add(
                                    CarouselItem(
                                        id = id,
                                        title = "Photo #${i + 1}",
                                        mediaType = MediaType.IMAGE,
                                        sourceUrl = displayUrl,
                                        thumbnail = displayUrl,
                                        width = w,
                                        height = h,
                                        mimeType = "image/jpeg"
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.d("PageMetadataExtractor", "Error parsing Instagram carousel: ${e.message}")
        }
        return items
    }

    suspend fun extractPinterestMedia(url: String, traceId: String? = null): ExtractedMedia? {
        try {
            val html = fetchHtml(url, traceId) ?: return null

            // 1. Check for video indicators on Pinterest (video pins, story pins, HLS streams)
            val hasVideo = html.contains("og:video") ||
                    html.contains("twitter:player") ||
                    html.contains("\"video_list\"") ||
                    html.contains("\"v_hls_url\"") ||
                    html.contains("\"video_url\"") ||
                    html.contains("\"is_video\":true")
            if (hasVideo) {
                if (traceId != null) {
                    MediaExtractionTracer.logEvent(
                        traceId = traceId,
                        component = "PageMetadataExtractor",
                        stage = "PINTEREST_EXTRACTION",
                        event = "PINTEREST_VIDEO_DETECTED",
                        level = TraceLevel.DEBUG,
                        reason = "Pinterest pin contains video indicators; delegating to yt-dlp"
                    )
                }
                return null // Allow yt-dlp to extract the video/HLS stream
            }

            // 2. Extract image candidate
            val rawCandidate = extractMetaTag(html, "og:image")
                ?: extractMetaTag(html, "twitter:image")
                ?: extractMetaTag(html, "og:image:secure_url")

            val validatedCandidate = validateAndLogCandidate(
                traceId = traceId,
                source = "PINTEREST_OG_IMAGE",
                attribute = "content",
                candidate = rawCandidate,
                mediaType = MediaType.IMAGE,
                confidence = 0.95f
            ) ?: return null

            // Upscale Pinterest CDN thumbnail to originals if safe
            val upscaled = if (validatedCandidate.contains("pinimg.com") || validatedCandidate.contains("pinterest.com")) {
                validatedCandidate
                    .replace("/236x/", "/originals/")
                    .replace("/474x/", "/originals/")
                    .replace("/736x/", "/originals/")
            } else {
                validatedCandidate
            }

            val directUrl = if (isValidMediaCandidateUrl(upscaled)) upscaled else validatedCandidate
            val title = extractMetaTag(html, "og:title") ?: extractTitle(html) ?: "Pinterest Image"
            val description = extractMetaTag(html, "og:description").orEmpty()

            return ExtractedMedia.Image(
                id = "pin_" + UUID.randomUUID().toString().take(8),
                title = cleanText(title),
                webpageUrl = url,
                directDownloadUrl = directUrl,
                thumbnail = validatedCandidate,
                mimeType = "image/jpeg",
                uploader = "Pinterest",
                description = cleanText(description)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.d("PageMetadataExtractor", "Pinterest extraction error: ${e.message}")
        }
        return null
    }

    suspend fun extractRedditMedia(url: String, traceId: String? = null): ExtractedMedia? {
        try {
            val html = fetchHtml(url, traceId) ?: return null

            // 1. Check for Reddit gallery / multi-image posts
            if (html.contains("gallery_data") || html.contains("media_metadata") || html.contains("shreddit-gallery")) {
                val galleryItems = extractRedditGalleryItems(html, traceId)
                if (galleryItems.isNotEmpty()) {
                    val title = extractMetaTag(html, "og:title") ?: extractTitle(html) ?: "Reddit Gallery"
                    return ExtractedMedia.Carousel(
                        id = "reddit_gallery_" + UUID.randomUUID().toString().take(8),
                        title = cleanText(title),
                        webpageUrl = url,
                        uploader = "Reddit",
                        thumbnail = galleryItems.firstOrNull()?.thumbnail.orEmpty(),
                        items = galleryItems
                    )
                }
            }

            // 2. Check for Reddit video indicators
            val hasVideo = html.contains("v.redd.it") ||
                    html.contains("og:video") ||
                    html.contains("post-type=\"video\"") ||
                    html.contains("shreddit-player")
            if (hasVideo) {
                if (traceId != null) {
                    MediaExtractionTracer.logEvent(
                        traceId = traceId,
                        component = "PageMetadataExtractor",
                        stage = "REDDIT_EXTRACTION",
                        event = "REDDIT_VIDEO_DETECTED",
                        level = TraceLevel.DEBUG,
                        reason = "Reddit page has v.redd.it or shreddit-player video component; delegating to yt-dlp"
                    )
                }
                return null // Allow yt-dlp to extract the video/audio stream
            }

            // 3. Single image extraction
            var rawImage = extractMetaTag(html, "og:image")
                ?: extractMetaTag(html, "twitter:image")

            if (rawImage.isNullOrBlank()) {
                val imgPattern = Pattern.compile("content-href=[\"'](https?://i\\.redd\\.it/[^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
                val m = imgPattern.matcher(html)
                if (m.find()) {
                    rawImage = m.group(1)
                }
            }

            if (!rawImage.isNullOrBlank()) {
                var cleanImg = decodeHtmlEntities(rawImage)
                if (cleanImg.contains("reddit.com/media") && cleanImg.contains("url=")) {
                    try {
                        val param = cleanImg.substringAfter("url=").substringBefore("&")
                        val decoded = URLDecoder.decode(param, "UTF-8")
                        if (decoded.startsWith("http")) cleanImg = decoded
                    } catch (_: Exception) {}
                }
                cleanImg = cleanImg.replace("&amp;", "&")

                val validatedUrl = validateAndLogCandidate(
                    traceId = traceId,
                    source = "REDDIT_IMAGE_TAG",
                    candidate = cleanImg,
                    mediaType = MediaType.IMAGE,
                    confidence = 0.95f
                )

                if (validatedUrl != null) {
                    val title = extractMetaTag(html, "og:title") ?: extractTitle(html) ?: "Reddit Image"
                    return ExtractedMedia.Image(
                        id = "reddit_" + UUID.randomUUID().toString().take(8),
                        title = cleanText(title),
                        webpageUrl = url,
                        directDownloadUrl = validatedUrl,
                        thumbnail = validatedUrl,
                        mimeType = "image/jpeg",
                        uploader = "Reddit"
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.d("PageMetadataExtractor", "Reddit extraction error: ${e.message}")
        }
        return null
    }

    suspend fun extractTikTokMedia(url: String, traceId: String? = null): ExtractedMedia? {
        val lower = url.lowercase()
        // If explicitly a video endpoint, let yt-dlp handle it
        if (lower.contains("/video/") || lower.contains("/v/")) {
            return null
        }

        try {
            val html = fetchHtml(url, traceId) ?: return null

            // If it is a /photo/ URL or contains image slideshow data
            if (lower.contains("/photo/") || html.contains("image_post_info") || html.contains("\"images\":[")) {
                val carouselItems = extractTikTokCarouselItems(html, traceId)
                val title = extractMetaTag(html, "og:title") ?: extractTitle(html) ?: "TikTok Photo"
                if (carouselItems.isNotEmpty()) {
                    return ExtractedMedia.Carousel(
                        id = "tiktok_carousel_" + UUID.randomUUID().toString().take(8),
                        title = cleanText(title),
                        webpageUrl = url,
                        uploader = "TikTok",
                        thumbnail = carouselItems.firstOrNull()?.thumbnail.orEmpty(),
                        items = carouselItems
                    )
                }

                // Fallback to single image from og:image
                val ogImage = extractMetaTag(html, "og:image")
                    ?: extractMetaTag(html, "twitter:image")
                if (!ogImage.isNullOrBlank()) {
                    val cleanImg = decodeHtmlEntities(ogImage).replace("&amp;", "&")
                    if (traceId != null) {
                        MediaExtractionTracer.logCandidate(
                            traceId = traceId,
                            source = "TIKTOK_PHOTO_OG_IMAGE",
                            rawValue = cleanImg,
                            accepted = true,
                            mediaType = MediaType.IMAGE,
                            confidence = 0.95f
                        )
                    }
                    return ExtractedMedia.Image(
                        id = "tiktok_" + UUID.randomUUID().toString().take(8),
                        title = cleanText(title),
                        webpageUrl = url,
                        directDownloadUrl = cleanImg,
                        thumbnail = cleanImg,
                        mimeType = "image/jpeg",
                        uploader = "TikTok"
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.d("PageMetadataExtractor", "TikTok extraction error: ${e.message}")
        }
        return null
    }

    private fun extractTikTokCarouselItems(html: String, traceId: String? = null): List<CarouselItem> {
        val items = mutableListOf<CarouselItem>()
        try {
            val pattern = Pattern.compile("\"displayImage\"\\s*:\\s*\\{\\s*\"urlList\"\\s*:\\s*(\\[.*?\\])", Pattern.DOTALL)
            val matcher = pattern.matcher(html)
            var idx = 1
            while (matcher.find()) {
                val urlsJson = matcher.group(1) ?: continue
                val jsonArr = JSONArray(urlsJson)
                val firstUrl = jsonArr.optString(0, "")
                if (firstUrl.isNotBlank()) {
                    val cleanUrl = firstUrl.replace("&amp;", "&")
                    if (traceId != null) {
                        MediaExtractionTracer.logCandidate(
                            traceId = traceId,
                            source = "TIKTOK_CAROUSEL_JSON",
                            subSource = "displayImage.urlList",
                            rawValue = cleanUrl,
                            accepted = true,
                            mediaType = MediaType.IMAGE,
                            confidence = 0.95f
                        )
                    }
                    items.add(
                        CarouselItem(
                            id = "tiktok_img_$idx",
                            title = "Photo #$idx",
                            mediaType = MediaType.IMAGE,
                            sourceUrl = cleanUrl,
                            thumbnail = cleanUrl,
                            mimeType = "image/jpeg"
                        )
                    )
                    idx++
                }
            }
        } catch (e: Exception) {
            AppLogger.d("PageMetadataExtractor", "Error parsing TikTok carousel: ${e.message}")
        }
        return items
    }

    private fun extractRedditGalleryItems(html: String, traceId: String? = null): List<CarouselItem> {
        val items = mutableListOf<CarouselItem>()
        try {
            val jsonPattern = Pattern.compile("media_metadata\"\\s*:\\s*(\\{.*?\\})\\s*,", Pattern.DOTALL)
            val matcher = jsonPattern.matcher(html)
            if (matcher.find()) {
                val json = JSONObject(matcher.group(1))
                val keys = json.keys()
                var index = 1
                while (keys.hasNext()) {
                    val key = keys.next()
                    val mediaObj = json.getJSONObject(key)
                    val status = mediaObj.optString("status")
                    if (status == "valid") {
                        val s = mediaObj.optJSONObject("s")
                        val imgUrl = s?.optString("u")?.replace("&amp;", "&") ?: continue
                        val w = s.optInt("x")
                        val h = s.optInt("y")
                        if (traceId != null) {
                            MediaExtractionTracer.logCandidate(
                                traceId = traceId,
                                source = "REDDIT_GALLERY_JSON",
                                subSource = "media_metadata.$key.s.u",
                                rawValue = imgUrl,
                                accepted = true,
                                mediaType = MediaType.IMAGE,
                                confidence = 0.95f
                            )
                        }
                        items.add(
                            CarouselItem(
                                id = key,
                                title = "Image #$index",
                                mediaType = MediaType.IMAGE,
                                sourceUrl = imgUrl,
                                thumbnail = imgUrl,
                                width = if (w > 0) w else null,
                                height = if (h > 0) h else null,
                                mimeType = "image/jpeg"
                            )
                        )
                        index++
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.d("PageMetadataExtractor", "Error parsing Reddit gallery: ${e.message}")
        }
        return items
    }

    suspend fun extractGenericPageMedia(url: String, traceId: String? = null): ExtractedMedia? {
        try {
            val html = fetchHtml(url, traceId) ?: return null

            val ogVideo = extractMetaTag(html, "og:video:secure_url")
                ?: extractMetaTag(html, "og:video")
                ?: extractMetaTag(html, "og:video:url")
                ?: extractMetaTag(html, "twitter:player:stream")

            // If page has clear video, we don't treat it as Image
            if (!ogVideo.isNullOrBlank()) {
                if (traceId != null) {
                    MediaExtractionTracer.logEvent(
                        traceId = traceId,
                        component = "PageMetadataExtractor",
                        stage = "GENERIC_OPENGRAPH",
                        event = "OPENGRAPH_VIDEO_PRESENT",
                        level = TraceLevel.DEBUG,
                        reason = "Page contains og:video or twitter:player, leaving for video extraction"
                    )
                }
                return null
            }

            val rawOgImage = extractMetaTag(html, "og:image:secure_url")
                ?: extractMetaTag(html, "og:image")
                ?: extractMetaTag(html, "twitter:image")

            val validatedUrl = validateAndLogCandidate(
                traceId = traceId,
                source = "GENERIC_OG_IMAGE",
                attribute = "content",
                candidate = rawOgImage,
                mediaType = MediaType.IMAGE,
                confidence = 0.85f
            )

            if (validatedUrl != null) {
                val title = extractMetaTag(html, "og:title")
                    ?: extractMetaTag(html, "twitter:title")
                    ?: extractTitle(html)
                    ?: "Web Image"
                val description = extractMetaTag(html, "og:description") ?: extractMetaTag(html, "twitter:description").orEmpty()
                val siteName = extractMetaTag(html, "og:site_name").orEmpty()
                val width = extractMetaTag(html, "og:image:width")?.toIntOrNull()
                val height = extractMetaTag(html, "og:image:height")?.toIntOrNull()

                return ExtractedMedia.Image(
                    id = "web_" + UUID.randomUUID().toString().take(8),
                    title = cleanText(title),
                    webpageUrl = url,
                    directDownloadUrl = validatedUrl,
                    thumbnail = validatedUrl,
                    mimeType = "image/jpeg",
                    width = width,
                    height = height,
                    uploader = siteName,
                    description = cleanText(description)
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.d("PageMetadataExtractor", "Generic page extraction error: ${e.message}")
        }
        return null
    }

    private suspend fun fetchHtml(url: String, traceId: String? = null): String? {
        val opId = if (traceId != null) {
            MediaExtractionTracer.startOperation(
                traceId = traceId,
                component = "PageMetadataExtractor",
                stage = "FETCH_HTML",
                name = "fetchHtml",
                details = mapOf("url" to url)
            )
        } else null

        return try {
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val response = CancellableNetworkClient.executeCancellable(httpClient, request)
            response.use { resp ->
                if (resp.isSuccessful) {
                    val bodyString = resp.body?.string()
                    if (traceId != null && !bodyString.isNullOrBlank()) {
                        MediaExtractionTracer.recordHtmlCapture(traceId, bodyString, url)
                        if (opId != null) {
                            MediaExtractionTracer.endOperation(
                                traceId = traceId,
                                opId = opId,
                                result = "HTTP ${resp.code} (${bodyString.length} chars)",
                                decision = "HTML_FETCH_SUCCESS",
                                reason = "HTTP ${resp.code} response"
                            )
                        }
                    }
                    bodyString
                } else {
                    if (traceId != null && opId != null) {
                        MediaExtractionTracer.endOperation(
                            traceId = traceId,
                            opId = opId,
                            result = "HTTP ${resp.code}",
                            decision = "HTML_FETCH_UNSUCCESSFUL",
                            reason = "HTTP response status ${resp.code}"
                        )
                    }
                    null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.d("PageMetadataExtractor", "Failed to fetch HTML for $url: ${e.message}")
            if (traceId != null && opId != null) {
                MediaExtractionTracer.endOperation(
                    traceId = traceId,
                    opId = opId,
                    error = e,
                    decision = "HTML_FETCH_ERROR",
                    reason = e.message
                )
            }
            null
        }
    }

    fun extractMetaTag(html: String, propertyOrName: String): String? {
        val metaTagPattern = Pattern.compile("<meta\\s+([^>]+)>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
        val matcher = metaTagPattern.matcher(html)
        val targetName = propertyOrName.lowercase()

        while (matcher.find()) {
            val tagAttributes = matcher.group(1) ?: continue
            val nameMatch = Pattern.compile("(?:name|property)\\s*=\\s*[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE).matcher(tagAttributes)
            if (nameMatch.find()) {
                val attrName = nameMatch.group(1)?.trim()?.lowercase()
                if (attrName == targetName) {
                    val contentMatch = Pattern.compile("(?:content|value)\\s*=\\s*[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(tagAttributes)
                    if (contentMatch.find()) {
                        val contentValue = contentMatch.group(1)?.trim()
                        if (!contentValue.isNullOrBlank()) {
                            return decodeHtmlEntities(contentValue)
                        }
                    }
                }
            }
        }
        return null
    }

    /**
     * Validates that a string is a well-formed, absolute HTTP or HTTPS URL
     * and not an HTML attribute token, viewport config, CSS, JS snippet, or placeholder.
     */
    fun isValidMediaCandidateUrl(candidate: String?): Boolean {
        if (candidate.isNullOrBlank()) return false
        val trimmed = candidate.trim()
        if (trimmed.length < 8) return false
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            return false
        }
        // Reject common non-URL HTML attribute tokens, viewport declarations, script snippets
        if (trimmed.contains("width=") || trimmed.contains("device-width") || trimmed.contains("initial-scale") ||
            trimmed.contains("viewport") || trimmed.contains("charset=") || trimmed.contains("<") ||
            trimmed.contains(">") || trimmed.contains("javascript:") || trimmed.contains("data:") ||
            trimmed.contains(" ") || trimmed.contains("\n") || trimmed.contains("\r") ||
            trimmed.contains("\t") || trimmed.contains("\"") || trimmed.contains("'") ||
            trimmed.contains("{") || trimmed.contains("}") || trimmed.contains(";")
        ) {
            return false
        }

        return try {
            val uri = java.net.URI(trimmed)
            val host = uri.host
            !host.isNullOrBlank() && host.contains(".")
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Validates and logs media candidate provenance to Deep Trace.
     * Returns the validated URL string if accepted, or null if rejected.
     */
    fun validateAndLogCandidate(
        traceId: String?,
        source: String,
        subSource: String? = null,
        attribute: String? = null,
        candidate: String?,
        mediaType: MediaType = MediaType.IMAGE,
        confidence: Float = 0.9f
    ): String? {
        if (candidate.isNullOrBlank()) return null
        val clean = decodeHtmlEntities(candidate.trim())
        val isValid = isValidMediaCandidateUrl(clean)
        val isGenericIcon = clean.contains("fb_icon_325x325.png") ||
                clean.contains("static.xx.fbcdn.net/rsrc.php") ||
                clean.contains("1x1.png") ||
                clean.contains("pixel.gif") ||
                clean.contains("favicon.ico") ||
                clean.contains("default_avatar") ||
                clean.contains("reddit_snoo")

        val isAccepted = isValid && !isGenericIcon
        val rejectionReason = when {
            !isValid -> {
                when {
                    !clean.startsWith("http://", ignoreCase = true) && !clean.startsWith("https://", ignoreCase = true) ->
                        "Rejected: Non-HTTP(S) URL scheme or HTML attribute fragment ('${clean.take(40)}')"
                    clean.contains("device-width") || clean.contains("viewport") ->
                        "Rejected: Viewport/HTML metadata token, not a media URL"
                    clean.contains(" ") || clean.contains("<") || clean.contains(";") ->
                        "Rejected: Contains invalid URL characters/HTML markup"
                    else -> "Rejected: Malformed URL candidate"
                }
            }
            isGenericIcon -> "Rejected: Generic site placeholder / tracking icon"
            else -> null
        }

        if (traceId != null) {
            MediaExtractionTracer.logCandidate(
                traceId = traceId,
                source = source,
                subSource = subSource,
                attribute = attribute,
                rawValue = clean,
                accepted = isAccepted,
                rejected = !isAccepted,
                rejectionReason = rejectionReason,
                mediaType = mediaType,
                confidence = if (isAccepted) confidence else 0.0f
            )
        }

        return if (isAccepted) clean else null
    }

    private fun extractTitle(html: String): String? {
        val pattern = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            val title = matcher.group(1)?.trim()
            if (!title.isNullOrBlank()) {
                return decodeHtmlEntities(title)
            }
        }
        return null
    }

    private fun cleanText(text: String): String {
        return text.replace("\n", " ").replace("\r", " ").replace("\\s+".toRegex(), " ").trim()
    }

    private fun decodeHtmlEntities(str: String): String {
        return str
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("\\u0026", "&")
    }
}
