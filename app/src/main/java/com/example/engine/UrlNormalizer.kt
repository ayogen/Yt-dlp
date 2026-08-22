package com.example.engine

import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object UrlNormalizer {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val INSTA_SHARE_REEL = Pattern.compile("https?://(?:www\\.)?instagram\\.com/share/reel/([0-9a-zA-Z_-]+)", Pattern.CASE_INSENSITIVE)
    private val INSTA_SHARE_POST = Pattern.compile("https?://(?:www\\.)?instagram\\.com/share/p/([0-9a-zA-Z_-]+)", Pattern.CASE_INSENSITIVE)

    /**
     * Resolves short links, redirects, and platform share URLs to canonical URLs
     * that yt-dlp extractors natively understand. Supports coroutine cancellation.
     */
    suspend fun resolveCanonicalUrl(inputUrl: String, traceId: String? = null): String {
        val effectiveTraceId = traceId ?: MediaExtractionTracer.currentSessionFlow.value?.traceId
        val opId = if (effectiveTraceId != null) {
            MediaExtractionTracer.startOperation(
                traceId = effectiveTraceId,
                component = "UrlNormalizer",
                stage = "URL_NORMALIZATION",
                name = "resolveCanonicalUrl",
                details = mapOf("inputUrl" to inputUrl)
            )
        } else null

        val trimmed = inputUrl.trim()
        if (trimmed.isBlank()) {
            if (effectiveTraceId != null && opId != null) {
                MediaExtractionTracer.endOperation(
                    traceId = effectiveTraceId,
                    opId = opId,
                    result = trimmed,
                    decision = "BLANK_URL",
                    reason = "Input URL was blank"
                )
            }
            return trimmed
        }

        val normalized = if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            "https://$trimmed"
        } else {
            trimmed
        }

        // 1. Instagram share links (which use standard alphanumeric media shortcodes)
        val instaReelMatcher = INSTA_SHARE_REEL.matcher(normalized)
        if (instaReelMatcher.find()) {
            val id = instaReelMatcher.group(1)
            val rewritten = "https://www.instagram.com/reel/$id/"
            AppLogger.i("UrlNormalizer", "Rewrote Instagram share reel URL to canonical: $rewritten")
            if (effectiveTraceId != null && opId != null) {
                MediaExtractionTracer.endOperation(
                    traceId = effectiveTraceId,
                    opId = opId,
                    result = rewritten,
                    decision = "REWRITE_INSTAGRAM_REEL",
                    reason = "Matched INSTA_SHARE_REEL regex"
                )
            }
            return rewritten
        }

        val instaPostMatcher = INSTA_SHARE_POST.matcher(normalized)
        if (instaPostMatcher.find()) {
            val id = instaPostMatcher.group(1)
            val rewritten = "https://www.instagram.com/p/$id/"
            AppLogger.i("UrlNormalizer", "Rewrote Instagram share post URL to canonical: $rewritten")
            if (effectiveTraceId != null && opId != null) {
                MediaExtractionTracer.endOperation(
                    traceId = effectiveTraceId,
                    opId = opId,
                    result = rewritten,
                    decision = "REWRITE_INSTAGRAM_POST",
                    reason = "Matched INSTA_SHARE_POST regex"
                )
            }
            return rewritten
        }

        // 2. Reddit media URL parameter unwrapping (e.g. https://www.reddit.com/media?url=https%3A%2F%2Fi.redd.it%2F...)
        if (normalized.contains("reddit.com/media") && normalized.contains("url=")) {
            try {
                val queryParam = normalized.substringAfter("url=").substringBefore("&")
                val decoded = java.net.URLDecoder.decode(queryParam, "UTF-8")
                if (decoded.startsWith("http://", ignoreCase = true) || decoded.startsWith("https://", ignoreCase = true)) {
                    AppLogger.i("UrlNormalizer", "Unwrapped Reddit media parameter URL: $decoded")
                    if (effectiveTraceId != null && opId != null) {
                        MediaExtractionTracer.endOperation(
                            traceId = effectiveTraceId,
                            opId = opId,
                            result = decoded,
                            decision = "UNWRAP_REDDIT_MEDIA_PARAM",
                            reason = "Extracted target URL from url= parameter"
                        )
                    }
                    return decoded
                }
            } catch (e: Exception) {
                AppLogger.w("UrlNormalizer", "Failed to unwrap Reddit media URL: ${e.message}")
            }
        }

        // 3. Platform share URLs or redirect domains: follow the HTTP redirect chain to get the real canonical URL
        val lower = normalized.lowercase()
        val isRedirectLink = lower.contains("fb.watch/") ||
                lower.contains("facebook.com/share/") ||
                lower.contains("m.facebook.com/share/") ||
                lower.contains("vm.tiktok.com/") ||
                lower.contains("vt.tiktok.com/") ||
                lower.contains("tiktok.com/t/") ||
                lower.contains("reddit.com/r/") && lower.contains("/s/") ||
                lower.contains("redd.it/") ||
                lower.contains("pin.it/") ||
                lower.contains("youtu.be/") ||
                lower.contains("tinyurl.com/") ||
                lower.contains("bit.ly/") ||
                lower.contains("t.co/")

        if (isRedirectLink) {
            if (effectiveTraceId != null) {
                MediaExtractionTracer.logEvent(
                    traceId = effectiveTraceId,
                    opId = opId,
                    component = "UrlNormalizer",
                    stage = "REDIRECT_START",
                    event = "FOLLOWING_REDIRECT",
                    level = TraceLevel.DEBUG,
                    input = normalized
                )
            }
            val resolved = followRedirects(normalized)
            if (resolved != null && resolved != normalized) {
                AppLogger.i("UrlNormalizer", "Resolved redirect: $normalized -> $resolved")
                if (effectiveTraceId != null) {
                    MediaExtractionTracer.logEvent(
                        traceId = effectiveTraceId,
                        opId = opId,
                        component = "UrlNormalizer",
                        stage = "REDIRECT_RESULT",
                        event = "REDIRECT_RESOLVED",
                        level = TraceLevel.INFO,
                        input = normalized,
                        output = resolved
                    )
                }
                val recursiveResult = resolveCanonicalUrl(resolved, effectiveTraceId)
                if (effectiveTraceId != null && opId != null) {
                    MediaExtractionTracer.endOperation(
                        traceId = effectiveTraceId,
                        opId = opId,
                        result = recursiveResult,
                        decision = "REDIRECT_CHAIN_RESOLVED",
                        reason = "Resolved redirect target and normalized recursively"
                    )
                }
                return recursiveResult
            }
        }

        if (effectiveTraceId != null && opId != null) {
            MediaExtractionTracer.endOperation(
                traceId = effectiveTraceId,
                opId = opId,
                result = normalized,
                decision = "URL_UNCHANGED",
                reason = "URL is already in canonical format"
            )
        }
        return normalized
    }

    private suspend fun followRedirects(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .head()
                .build()

            val response = CancellableNetworkClient.executeCancellable(client, request)
            response.use { resp ->
                val finalUrl = resp.request.url.toString()
                if (finalUrl.isNotBlank() && finalUrl != url) {
                    finalUrl
                } else {
                    resp.header("Location")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            try {
                val getRequest = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .get()
                    .build()
                val response = CancellableNetworkClient.executeCancellable(client, getRequest)
                response.use { resp ->
                    resp.request.url.toString()
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e2: Exception) {
                null
            }
        }
    }
}
