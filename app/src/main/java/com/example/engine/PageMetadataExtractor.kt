package com.example.engine

import com.example.data.model.CarouselItem
import com.example.data.model.ExtractedMedia
import com.example.data.model.MediaType
import com.example.engine.HttpCoroutineUtils.executeAsync
import com.example.engine.HttpCoroutineUtils.fetchStringAsync
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
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

    private const val CRAWLER_USER_AGENT =
        "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)"

    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private const val REDDIT_USER_AGENT =
        "android:com.example.universalmediasaver:v1.0 (by /u/mediabot)"

    /**
     * Attempts to extract rich media (Image, Carousel, Video, or Audio) from a web page using
     * HTML meta tags, OpenGraph, JSON-LD, and platform-specific social DOM / API structures.
     */
    suspend fun extractPageMedia(url: String): ExtractedMedia? {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return null

        AppLogger.i("PageMetadataExtractor", "START url=$cleanUrl")
        val lower = cleanUrl.lowercase()

        // 1. TikTok Photo / Carousel handler
        if (lower.contains("tiktok.com")) {
            val tiktokMedia = extractTikTokMedia(cleanUrl)
            if (tiktokMedia != null) {
                AppLogger.i("PageMetadataExtractor", "Extracted TikTok media: ${tiktokMedia.javaClass.simpleName}")
                return tiktokMedia
            }
        }

        // 2. Instagram handler
        if (lower.contains("instagram.com") || lower.contains("instagr.am")) {
            val igMedia = extractInstagramMedia(cleanUrl)
            if (igMedia != null) {
                AppLogger.i("PageMetadataExtractor", "Extracted Instagram media: ${igMedia.javaClass.simpleName}")
                return igMedia
            }
        }

        // 3. Reddit handler
        if (lower.contains("reddit.com") || lower.contains("redd.it")) {
            val redditMedia = extractRedditMedia(cleanUrl)
            if (redditMedia != null) {
                AppLogger.i("PageMetadataExtractor", "Extracted Reddit media: ${redditMedia.javaClass.simpleName}")
                return redditMedia
            }
        }

        // 4. Pinterest handler
        if (lower.contains("pinterest.com") || lower.contains("pin.it")) {
            val pinMedia = extractPinterestMedia(cleanUrl)
            if (pinMedia != null) {
                AppLogger.i("PageMetadataExtractor", "Extracted Pinterest media: ${pinMedia.javaClass.simpleName}")
                return pinMedia
            }
        }

        // 5. Facebook handler
        if (lower.contains("facebook.com") || lower.contains("fb.watch")) {
            val fbMedia = extractFacebookMedia(cleanUrl)
            if (fbMedia != null) {
                AppLogger.i("PageMetadataExtractor", "Extracted Facebook media: ${fbMedia.javaClass.simpleName}")
                return fbMedia
            }
        }

        // 6. Generic webpage OpenGraph & JSON-LD extraction
        val generic = extractGenericPageMedia(cleanUrl)
        if (generic != null) {
            AppLogger.i("PageMetadataExtractor", "Extracted Generic media: ${generic.javaClass.simpleName}")
        }
        return generic
    }

    /**
     * Extracts rich media directly as a canonical MediaCollection.
     */
    suspend fun extractPageMediaCollection(url: String): Result<com.example.data.model.MediaCollection> {
        val extracted = extractPageMedia(url)
        return if (extracted != null) {
            Result.success(extracted.toMediaCollection())
        } else {
            Result.failure(Exception("No rich media extracted from webpage"))
        }
    }

    /**
     * Dedicated TikTok extractor:
     * - /video/ -> returns null to allow yt-dlp to handle full video extraction.
     * - /photo/ -> extracts single photo or multi-photo carousel cleanly.
     */
    suspend fun extractTikTokMedia(url: String): ExtractedMedia? {
        val lower = url.lowercase()
        // If explicitly a video endpoint, let yt-dlp handle it directly
        if (lower.contains("/video/") || lower.contains("/v/")) {
            AppLogger.d("PageMetadataExtractor", "TikTok URL is a video endpoint; skipping to yt-dlp")
            return null
        }

        val isExplicitPhoto = lower.contains("/photo/")
        if (isExplicitPhoto) {
            AppLogger.i("PageMetadataExtractor", "URL classified as TikTok PHOTO: $url")
        }

        try {
            AppLogger.d("PageMetadataExtractor", "Fetching TikTok HTML...")
            val html = fetchHtml(url, userAgent = CRAWLER_USER_AGENT)
                ?: fetchHtml(url, userAgent = BROWSER_USER_AGENT)
                ?: return null

            // 1. Check for TikTok Photo Slides / Carousel JSON in scripts
            val carouselItems = extractTikTokCarouselItems(html)
            if (carouselItems.isNotEmpty()) {
                val title = extractMetaTag(html, "og:title") ?: extractTitle(html) ?: "TikTok Photo Album"
                val uploader = extractMetaTag(html, "og:author") ?: "TikTok Creator"
                AppLogger.i("PageMetadataExtractor", "RETURN Carousel with ${carouselItems.size} items for TikTok")
                return ExtractedMedia.Carousel(
                    id = "tiktok_carousel_" + UUID.randomUUID().toString().take(8),
                    title = cleanText(title),
                    webpageUrl = url,
                    uploader = uploader,
                    thumbnail = carouselItems.firstOrNull()?.thumbnail.orEmpty(),
                    items = carouselItems
                )
            }

            // 2. If single image post
            val ogImage = extractMetaTag(html, "og:image:secure_url")
                ?: extractMetaTag(html, "og:image")
                ?: extractMetaTag(html, "twitter:image")

            if (!ogImage.isNullOrBlank() && !ogImage.contains("tiktok-logo") && !ogImage.contains("avatar")) {
                val title = extractMetaTag(html, "og:title") ?: extractTitle(html) ?: "TikTok Photo"
                val description = extractMetaTag(html, "og:description").orEmpty()
                val width = extractMetaTag(html, "og:image:width")?.toIntOrNull()
                val height = extractMetaTag(html, "og:image:height")?.toIntOrNull()

                AppLogger.i("PageMetadataExtractor", "RETURN Image for TikTok: $title")
                return ExtractedMedia.Image(
                    id = "tiktok_img_" + UUID.randomUUID().toString().take(8),
                    title = cleanText(title),
                    webpageUrl = url,
                    directDownloadUrl = decodeHtmlEntities(ogImage),
                    thumbnail = decodeHtmlEntities(ogImage),
                    mimeType = "image/jpeg",
                    width = width,
                    height = height,
                    uploader = "TikTok",
                    description = cleanText(description)
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w("PageMetadataExtractor", "TikTok extraction error: ${e.message}")
        }
        return null
    }

    private fun extractTikTokCarouselItems(html: String): List<CarouselItem> {
        val items = mutableListOf<CarouselItem>()
        try {
            // Find embedded JSON scripts (UNIVERSAL_DATA_FOR_REHYDRATION or SIGI_STATE or __NEXT_DATA__)
            val scriptPattern = Pattern.compile("<script[^>]*id=[\"'](?:__UNIVERSAL_DATA_FOR_REHYDRATION__|SIGI_STATE|__NEXT_DATA__)[\"'][^>]*>(.*?)</script>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
            val matcher = scriptPattern.matcher(html)
            while (matcher.find()) {
                val scriptContent = matcher.group(1)?.trim() ?: continue
                try {
                    val root = JSONObject(scriptContent)
                    // Universal data path: __DEFAULT_SCOPE__ -> webapp.video-detail -> itemInfo -> itemStruct
                    val defaultScope = root.optJSONObject("__DEFAULT_SCOPE__")
                    val videoDetail = defaultScope?.optJSONObject("webapp.video-detail")
                    val itemStruct = videoDetail?.optJSONObject("itemInfo")?.optJSONObject("itemStruct")
                        ?: root.optJSONObject("ItemModule")?.let { itemModule ->
                            val firstKey = itemModule.keys().asSequence().firstOrNull()
                            if (firstKey != null) itemModule.optJSONObject(firstKey) else null
                        }

                    val imagePost = itemStruct?.optJSONObject("imagePost")
                    val imagesArr = imagePost?.optJSONArray("images")
                    if (imagesArr != null && imagesArr.length() > 0) {
                        for (i in 0 until imagesArr.length()) {
                            val imgObj = imagesArr.optJSONObject(i)
                            val imageURLObj = imgObj?.optJSONObject("imageURL")
                            val urlList = imageURLObj?.optJSONArray("urlList")
                            val imgUrl = if (urlList != null && urlList.length() > 0) urlList.optString(0, "") else ""
                            if (imgUrl.isNotBlank()) {
                                items.add(
                                    CarouselItem(
                                        id = "tt_slide_${i + 1}",
                                        title = "Photo #${i + 1}",
                                        mediaType = MediaType.IMAGE,
                                        sourceUrl = imgUrl,
                                        thumbnail = imgUrl,
                                        mimeType = "image/jpeg"
                                    )
                                )
                            } else if (imgObj != null) {
                                items.add(
                                    CarouselItem(
                                        id = "tt_slide_${i + 1}",
                                        title = "Photo #${i + 1}",
                                        mediaType = MediaType.IMAGE,
                                        sourceUrl = "",
                                        thumbnail = "",
                                        mimeType = "image/jpeg",
                                        isSelected = false,
                                        errorMessage = "TikTok photo stream unavailable for photo #${i + 1}"
                                    )
                                )
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // Secondary search: regex for imagePost url lists if DOM parsing didn't match
            if (items.isEmpty()) {
                val imgUrlPattern = Pattern.compile("\"urlList\"\\s*:\\s*\\[\"(https?:\\\\/\\\\/[^\"]+)\"", Pattern.CASE_INSENSITIVE)
                val imgMatcher = imgUrlPattern.matcher(html)
                var index = 1
                val seen = mutableSetOf<String>()
                while (imgMatcher.find()) {
                    val rawUrl = imgMatcher.group(1)?.replace("\\/", "/") ?: continue
                    if (seen.add(rawUrl) && !rawUrl.contains("avatar") && !rawUrl.contains("logo")) {
                        items.add(
                            CarouselItem(
                                id = "tt_slide_$index",
                                title = "Photo #$index",
                                mediaType = MediaType.IMAGE,
                                sourceUrl = rawUrl,
                                thumbnail = rawUrl,
                                mimeType = "image/jpeg"
                            )
                        )
                        index++
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.d("PageMetadataExtractor", "Error parsing TikTok carousel items: ${e.message}")
        }
        return items
    }

    /**
     * Reddit extractor:
     * - Video posts -> returns null (routes directly to yt-dlp for muxed 1080p/720p audio+video).
     * - Reddit Galleries -> returns ExtractedMedia.Carousel.
     * - Single image posts -> returns ExtractedMedia.Image.
     */
    suspend fun extractRedditMedia(url: String): ExtractedMedia? {
        try {
            val cleanUrl = url.substringBefore("?")
            AppLogger.i("PageMetadataExtractor", "Attempting Reddit structured extraction for $cleanUrl")

            // 1. Try Reddit JSON API endpoint for precise media distinction
            val jsonUrl = if (cleanUrl.endsWith("/")) "${cleanUrl.dropLast(1)}.json?raw_json=1" else "$cleanUrl.json?raw_json=1"
            val jsonString = fetchHtml(jsonUrl, userAgent = REDDIT_USER_AGENT)

            if (!jsonString.isNullOrBlank() && jsonString.startsWith("[")) {
                val jsonArr = JSONArray(jsonString)
                if (jsonArr.length() > 0) {
                    val dataObj = jsonArr.getJSONObject(0).optJSONObject("data")
                    val children = dataObj?.optJSONArray("children")
                    val post = children?.optJSONObject(0)?.optJSONObject("data")

                    if (post != null) {
                        val isVideo = post.optBoolean("is_video", false)
                        val postHint = post.optString("post_hint", "")
                        val domain = post.optString("domain", "")
                        val postTitle = post.optString("title", "Reddit Post")
                        val author = post.optString("author", "Reddit User")

                        // If video, let yt-dlp handle it cleanly
                        if (isVideo || domain == "v.redd.it" || postHint == "hosted:video" || postHint == "rich:video") {
                            AppLogger.i("PageMetadataExtractor", "Reddit post is VIDEO; routing to yt-dlp")
                            return null
                        }

                        // Check for gallery
                        val isGallery = post.optBoolean("is_gallery", false)
                        val galleryData = post.optJSONObject("gallery_data")
                        val mediaMetadata = post.optJSONObject("media_metadata")

                        if (isGallery && galleryData != null && mediaMetadata != null) {
                            val itemsArr = galleryData.optJSONArray("items")
                            val carouselItems = mutableListOf<CarouselItem>()
                            if (itemsArr != null) {
                                for (i in 0 until itemsArr.length()) {
                                    val itemObj = itemsArr.optJSONObject(i) ?: continue
                                    val mediaId = itemObj.optString("media_id", "reddit_item_${i + 1}")
                                    val meta = mediaMetadata.optJSONObject(mediaId)
                                    val s = meta?.optJSONObject("s")
                                    val imgUrl = s?.optString("u")?.replace("&amp;", "&")
                                        ?: s?.optString("gif")?.replace("&amp;", "&")
                                        ?: meta?.optString("u")?.replace("&amp;", "&")
                                    if (!imgUrl.isNullOrBlank()) {
                                        val w = s?.optInt("x") ?: 0
                                        val h = s?.optInt("y") ?: 0
                                        carouselItems.add(
                                            CarouselItem(
                                                id = mediaId,
                                                title = "Image #${i + 1}",
                                                mediaType = MediaType.IMAGE,
                                                sourceUrl = imgUrl,
                                                thumbnail = imgUrl,
                                                width = if (w > 0) w else null,
                                                height = if (h > 0) h else null,
                                                mimeType = "image/jpeg"
                                            )
                                        )
                                    } else {
                                        carouselItems.add(
                                            CarouselItem(
                                                id = mediaId,
                                                title = "Image #${i + 1}",
                                                mediaType = MediaType.IMAGE,
                                                sourceUrl = "",
                                                thumbnail = "",
                                                mimeType = "image/jpeg",
                                                isSelected = false,
                                                errorMessage = "Reddit image metadata unavailable for gallery item #${i + 1} ($mediaId)"
                                            )
                                        )
                                    }
                                }
                            }
                            if (carouselItems.isNotEmpty()) {
                                AppLogger.i("PageMetadataExtractor", "RETURN Carousel for Reddit Gallery with ${carouselItems.size} items")
                                return ExtractedMedia.Carousel(
                                    id = "reddit_gal_" + UUID.randomUUID().toString().take(8),
                                    title = cleanText(postTitle),
                                    webpageUrl = url,
                                    uploader = author,
                                    thumbnail = carouselItems.firstOrNull()?.thumbnail.orEmpty(),
                                    items = carouselItems
                                )
                            }
                        }

                        // Check for single image post
                        val destUrl = post.optString("url_overridden_by_dest", post.optString("url", ""))
                        if (postHint == "image" || destUrl.contains("i.redd.it") || destUrl.contains("preview.redd.it") ||
                            destUrl.endsWith(".jpg") || destUrl.endsWith(".png") || destUrl.endsWith(".jpeg") || destUrl.endsWith(".webp")
                        ) {
                            AppLogger.i("PageMetadataExtractor", "RETURN Image for Reddit: $postTitle")
                            return ExtractedMedia.Image(
                                id = "reddit_img_" + UUID.randomUUID().toString().take(8),
                                title = cleanText(postTitle),
                                webpageUrl = url,
                                directDownloadUrl = destUrl,
                                thumbnail = destUrl,
                                mimeType = "image/jpeg",
                                uploader = author
                            )
                        }
                    }
                }
            }

            // 2. HTML Fallback for Reddit
            val html = fetchHtml(url, userAgent = CRAWLER_USER_AGENT) ?: return null

            // Check if page og:video is present
            val ogVideo = extractMetaTag(html, "og:video") ?: extractMetaTag(html, "og:video:secure_url")
            if (!ogVideo.isNullOrBlank()) {
                AppLogger.i("PageMetadataExtractor", "Reddit page has og:video; routing to yt-dlp")
                return null
            }

            val ogImage = extractMetaTag(html, "og:image")
            if (!ogImage.isNullOrBlank() && (ogImage.contains("preview.redd.it") || ogImage.contains("i.redd.it") || ogImage.contains("external-preview.redd.it"))) {
                val title = extractMetaTag(html, "og:title") ?: extractTitle(html) ?: "Reddit Image"
                AppLogger.i("PageMetadataExtractor", "RETURN Image for Reddit via OpenGraph")
                return ExtractedMedia.Image(
                    id = "reddit_" + UUID.randomUUID().toString().take(8),
                    title = cleanText(title),
                    webpageUrl = url,
                    directDownloadUrl = decodeHtmlEntities(ogImage),
                    thumbnail = decodeHtmlEntities(ogImage),
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

    suspend fun extractInstagramMedia(url: String): ExtractedMedia? {
        val lower = url.lowercase()
        // If explicitly a Reel, TV, or Stories URL, let yt-dlp handle it directly
        if (lower.contains("/reel/") || lower.contains("/reels/") || lower.contains("/tv/") || lower.contains("/stories/")) {
            AppLogger.d("PageMetadataExtractor", "Instagram URL is a Reel/Video; routing directly to yt-dlp")
            return null
        }

        try {
            val html = fetchHtml(url, userAgent = CRAWLER_USER_AGENT)
                ?: fetchHtml(url, userAgent = BROWSER_USER_AGENT)
                ?: return null

            // Look for carousel items in embedded JSON scripts
            val carouselItems = extractInstagramCarouselItems(html)
            if (carouselItems.isNotEmpty()) {
                val title = extractMetaTag(html, "og:title") ?: extractTitle(html) ?: "Instagram Post"
                AppLogger.i("PageMetadataExtractor", "RETURN Carousel for Instagram (${carouselItems.size} items)")
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
                AppLogger.d("PageMetadataExtractor", "Instagram post contains video; routing to yt-dlp")
                return null // Let yt-dlp handle video
            }

            val ogImage = extractMetaTag(html, "og:image:secure_url")
                ?: extractMetaTag(html, "og:image")
                ?: extractMetaTag(html, "twitter:image")

            if (!ogImage.isNullOrBlank() && !ogImage.contains("instagram_icon") && !ogImage.contains("null")) {
                val title = extractMetaTag(html, "og:title") ?: extractTitle(html) ?: "Instagram Photo"
                val description = extractMetaTag(html, "og:description").orEmpty()
                val width = extractMetaTag(html, "og:image:width")?.toIntOrNull()
                val height = extractMetaTag(html, "og:image:height")?.toIntOrNull()

                AppLogger.i("PageMetadataExtractor", "RETURN Image for Instagram: $title")
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
            val edgesPattern = Pattern.compile("\"edge_sidecar_to_children\"\\s*:\\s*\\{\\s*\"edges\"\\s*:\\s*(\\[.*?\\])\\s*\\}", Pattern.DOTALL)
            val edgeMatcher = edgesPattern.matcher(html)
            if (edgeMatcher.find()) {
                val jsonArr = JSONArray(edgeMatcher.group(1))
                for (i in 0 until jsonArr.length()) {
                    val nodeObj = jsonArr.optJSONObject(i)
                    val node = nodeObj?.optJSONObject("node") ?: continue
                    val isVideo = node.optBoolean("is_video", false)
                    val displayUrl = node.optString("display_url", "")
                    val videoUrl = node.optString("video_url", "")
                    val id = node.optString("id", "ig_item_${i + 1}").ifBlank { "ig_item_${i + 1}" }
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
                    } else if (!isVideo && displayUrl.isNotBlank()) {
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
                    } else {
                        items.add(
                            CarouselItem(
                                id = id,
                                title = if (isVideo) "Video #${i + 1}" else "Photo #${i + 1}",
                                mediaType = if (isVideo) MediaType.VIDEO else MediaType.IMAGE,
                                sourceUrl = displayUrl.ifBlank { "" },
                                thumbnail = displayUrl,
                                width = w,
                                height = h,
                                mimeType = if (isVideo) "video/mp4" else "image/jpeg",
                                isSelected = false,
                                errorMessage = "Instagram media stream unavailable for slide #${i + 1}"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.d("PageMetadataExtractor", "Error parsing Instagram carousel: ${e.message}")
        }
        return items
    }

    suspend fun extractFacebookMedia(url: String): ExtractedMedia? {
        val lower = url.lowercase()
        // If explicitly a Reel or Video URL, let yt-dlp handle it
        if (lower.contains("/reel/") || lower.contains("/videos/") || lower.contains("/watch") || lower.contains("fb.watch")) {
            return null
        }

        try {
            val html = fetchHtml(url, userAgent = CRAWLER_USER_AGENT) ?: return null

            // Check if page contains video indicators
            val hasVideo = html.contains("og:video") || html.contains("\"video_id\"") || html.contains("playable_url")
            if (hasVideo) {
                return null // Let yt-dlp extract video
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

    suspend fun extractPinterestMedia(url: String): ExtractedMedia? {
        try {
            val html = fetchHtml(url, userAgent = CRAWLER_USER_AGENT)
                ?: fetchHtml(url, userAgent = BROWSER_USER_AGENT)
                ?: return null

            val ogImage = extractMetaTag(html, "og:image")
                ?: extractMetaTag(html, "twitter:image")
                ?: extractMetaTag(html, "og:image:secure_url")

            if (!ogImage.isNullOrBlank()) {
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

    suspend fun extractGenericPageMedia(url: String): ExtractedMedia? {
        try {
            val html = fetchHtml(url, userAgent = CRAWLER_USER_AGENT)
                ?: fetchHtml(url, userAgent = BROWSER_USER_AGENT)
                ?: return null

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

    private suspend fun fetchHtml(url: String, userAgent: String = BROWSER_USER_AGENT): String? {
        return try {
            AppLogger.d("PageMetadataExtractor", "HTTP request START for $url")
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,application/json,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val result = httpClient.fetchStringAsync(request)
            if (result != null) {
                AppLogger.d("PageMetadataExtractor", "HTTP request COMPLETED for $url")
            } else {
                AppLogger.d("PageMetadataExtractor", "HTTP request returned empty/null for $url")
            }
            result
        } catch (e: CancellationException) {
            AppLogger.d("PageMetadataExtractor", "HTTP request CANCELLED for $url")
            throw e
        } catch (e: Exception) {
            AppLogger.d("PageMetadataExtractor", "HTTP request FAILED for $url: ${e.message}")
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
