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
    suspend fun extractPageMedia(url: String): ExtractedMedia? {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return null

        val lower = cleanUrl.lowercase()

        // 1. Social platform direct handlers
        if (lower.contains("instagram.com") || lower.contains("instagr.am")) {
            val igMedia = extractInstagramMedia(cleanUrl)
            if (igMedia != null) return igMedia
        }

        if (lower.contains("facebook.com") || lower.contains("fb.watch")) {
            val fbMedia = extractFacebookMedia(cleanUrl)
            if (fbMedia != null) return fbMedia
        }

        if (lower.contains("pinterest.com") || lower.contains("pin.it")) {
            val pinMedia = extractPinterestMedia(cleanUrl)
            if (pinMedia != null) return pinMedia
        }

        if (lower.contains("reddit.com") || lower.contains("redd.it")) {
            val redditMedia = extractRedditMedia(cleanUrl)
            if (redditMedia != null) return redditMedia
        }

        if (lower.contains("tiktok.com")) {
            val tiktokMedia = extractTikTokMedia(cleanUrl)
            if (tiktokMedia != null) return tiktokMedia
        }

        // 2. Generic webpage OpenGraph & JSON-LD extraction
        return extractGenericPageMedia(cleanUrl)
    }

    suspend fun extractFacebookMedia(url: String): ExtractedMedia? {
        val lower = url.lowercase()
        // If explicitly a Reel or Video URL, let yt-dlp handle it
        if (lower.contains("/reel/") || lower.contains("/videos/") || lower.contains("/watch") || lower.contains("fb.watch")) {
            return null
        }

        try {
            val html = fetchHtml(url) ?: return null

            // Check if page contains video indicators
            val hasVideo = html.contains("og:video") || html.contains("\"video_id\"") || html.contains("playable_url")
            if (hasVideo) {
                // If it has video, do not force Image; let yt-dlp extract video
                return null
            }

            // Extract photo
            val ogImage = extractMetaTag(html, "og:image:secure_url")
                ?: extractMetaTag(html, "og:image")
                ?: extractMetaTag(html, "twitter:image")

            if (!ogImage.isNullOrBlank() && !ogImage.contains("fb_icon_325x325.png") && !ogImage.contains("static.xx.fbcdn.net/rsrc.php")) {
                val title = extractMetaTag(html, "og:title") ?: extractTitle(html) ?: "Facebook Photo"
                val description = extractMetaTag(html, "og:description").orEmpty()
                val width = extractMetaTag(html, "og:image:width")?.toIntOrNull()
                val height = extractMetaTag(html, "og:image:height")?.toIntOrNull()

                return ExtractedMedia.Image(
                    id = "fb_" + UUID.randomUUID().toString().take(8),
                    title = cleanText(title),
                    webpageUrl = url,
                    directDownloadUrl = decodeHtmlEntities(ogImage),
                    thumbnail = decodeHtmlEntities(ogImage),
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

    suspend fun extractInstagramMedia(url: String): ExtractedMedia? {
        val lower = url.lowercase()
        // If explicitly a Reel, TV, or Stories URL, let yt-dlp handle it directly
        if (lower.contains("/reel/") || lower.contains("/reels/") || lower.contains("/tv/") || lower.contains("/stories/")) {
            return null
        }

        try {
            val html = fetchHtml(url) ?: return null

            // Look for carousel items in embedded JSON scripts
            val carouselItems = extractInstagramCarouselItems(html)
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

            val ogImage = extractMetaTag(html, "og:image:secure_url")
                ?: extractMetaTag(html, "og:image")
                ?: extractMetaTag(html, "twitter:image")

            if (!ogImage.isNullOrBlank()) {
                val title = extractMetaTag(html, "og:title") ?: extractTitle(html) ?: "Instagram Photo"
                val description = extractMetaTag(html, "og:description").orEmpty()
                val width = extractMetaTag(html, "og:image:width")?.toIntOrNull()
                val height = extractMetaTag(html, "og:image:height")?.toIntOrNull()

                return ExtractedMedia.Image(
                    id = "ig_" + UUID.randomUUID().toString().take(8),
                    title = cleanText(title),
                    webpageUrl = url,
                    directDownloadUrl = decodeHtmlEntities(ogImage),
                    thumbnail = decodeHtmlEntities(ogImage),
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

    private fun extractInstagramCarouselItems(html: String): List<CarouselItem> {
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

    suspend fun extractPinterestMedia(url: String): ExtractedMedia? {
        try {
            val html = fetchHtml(url) ?: return null
            val ogImage = extractMetaTag(html, "og:image")
                ?: extractMetaTag(html, "twitter:image")
                ?: extractMetaTag(html, "og:image:secure_url")

            if (!ogImage.isNullOrBlank()) {
                // Upscale Pinterest thumbnail url to originals if possible
                val fullImageUrl = ogImage
                    .replace("/236x/", "/originals/")
                    .replace("/474x/", "/originals/")
                    .replace("/736x/", "/originals/")

                val title = extractMetaTag(html, "og:title") ?: extractTitle(html) ?: "Pinterest Image"
                val description = extractMetaTag(html, "og:description").orEmpty()

                return ExtractedMedia.Image(
                    id = "pin_" + UUID.randomUUID().toString().take(8),
                    title = cleanText(title),
                    webpageUrl = url,
                    directDownloadUrl = decodeHtmlEntities(fullImageUrl),
                    thumbnail = decodeHtmlEntities(ogImage),
                    mimeType = "image/jpeg",
                    uploader = "Pinterest",
                    description = cleanText(description)
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.d("PageMetadataExtractor", "Pinterest extraction error: ${e.message}")
        }
        return null
    }

    suspend fun extractRedditMedia(url: String): ExtractedMedia? {
        try {
            val html = fetchHtml(url) ?: return null
            // Check for Reddit gallery
            if (html.contains("gallery_data") || html.contains("media_metadata") || html.contains("shreddit-gallery")) {
                val galleryItems = extractRedditGalleryItems(html)
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

            // Check if page has video
            val hasVideo = html.contains("v.redd.it") ||
                    html.contains("og:video") ||
                    html.contains("post-type=\"video\"") ||
                    html.contains("shreddit-player")
            if (hasVideo) {
                return null // Let yt-dlp handle Reddit video
            }

            // Single image extraction
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
                    } catch (e: Exception) {}
                }
                cleanImg = cleanImg.replace("&amp;", "&")

                val title = extractMetaTag(html, "og:title") ?: extractTitle(html) ?: "Reddit Image"
                return ExtractedMedia.Image(
                    id = "reddit_" + UUID.randomUUID().toString().take(8),
                    title = cleanText(title),
                    webpageUrl = url,
                    directDownloadUrl = cleanImg,
                    thumbnail = cleanImg,
                    mimeType = "image/jpeg",
                    uploader = "Reddit"
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.d("PageMetadataExtractor", "Reddit extraction error: ${e.message}")
        }
        return null
    }

    suspend fun extractTikTokMedia(url: String): ExtractedMedia? {
        val lower = url.lowercase()
        // If explicitly a video endpoint, let yt-dlp handle it
        if (lower.contains("/video/") || lower.contains("/v/")) {
            return null
        }

        try {
            val html = fetchHtml(url) ?: return null

            // If it is a /photo/ URL or contains image slideshow data
            if (lower.contains("/photo/") || html.contains("image_post_info") || html.contains("\"images\":[")) {
                val carouselItems = extractTikTokCarouselItems(html)
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

    private fun extractTikTokCarouselItems(html: String): List<CarouselItem> {
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

    private fun extractRedditGalleryItems(html: String): List<CarouselItem> {
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

    suspend fun extractGenericPageMedia(url: String): ExtractedMedia? {
        try {
            val html = fetchHtml(url) ?: return null

            val ogVideo = extractMetaTag(html, "og:video:secure_url")
                ?: extractMetaTag(html, "og:video")
                ?: extractMetaTag(html, "og:video:url")
                ?: extractMetaTag(html, "twitter:player:stream")

            // If page has clear video, we don't treat it as Image
            if (!ogVideo.isNullOrBlank()) {
                return null
            }

            val ogImage = extractMetaTag(html, "og:image:secure_url")
                ?: extractMetaTag(html, "og:image")
                ?: extractMetaTag(html, "twitter:image")

            if (!ogImage.isNullOrBlank()) {
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
                    directDownloadUrl = decodeHtmlEntities(ogImage),
                    thumbnail = decodeHtmlEntities(ogImage),
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

    private suspend fun fetchHtml(url: String): String? {
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
                    resp.body?.string()
                } else null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.d("PageMetadataExtractor", "Failed to fetch HTML for $url: ${e.message}")
            null
        }
    }

    fun extractMetaTag(html: String, propertyOrName: String): String? {
        val escaped = Pattern.quote(propertyOrName)
        val patterns = listOf(
            Pattern.compile("<meta[^>]+(?:property|name)\\s*=\\s*[\"']$escaped[\"'][^>]+content\\s*=\\s*[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE or Pattern.DOTALL),
            Pattern.compile("<meta[^>]+content\\s*=\\s*[\"'](.*?)[\"'][^>]+(?:property|name)\\s*=\\s*[\"']$escaped[\"']", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
        )

        for (p in patterns) {
            val matcher = p.matcher(html)
            if (matcher.find()) {
                val value = matcher.group(1)?.trim()
                if (!value.isNullOrBlank()) {
                    return decodeHtmlEntities(value)
                }
            }
        }
        return null
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
