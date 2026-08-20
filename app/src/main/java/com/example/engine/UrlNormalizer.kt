package com.example.engine

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
     * that yt-dlp extractors natively understand.
     */
    fun resolveCanonicalUrl(inputUrl: String): String {
        val trimmed = inputUrl.trim()
        if (trimmed.isBlank()) return trimmed

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
            return rewritten
        }

        val instaPostMatcher = INSTA_SHARE_POST.matcher(normalized)
        if (instaPostMatcher.find()) {
            val id = instaPostMatcher.group(1)
            val rewritten = "https://www.instagram.com/p/$id/"
            AppLogger.i("UrlNormalizer", "Rewrote Instagram share post URL to canonical: $rewritten")
            return rewritten
        }

        // 2. Facebook share URLs or redirect domains: follow the HTTP redirect chain to get the real canonical URL
        val lower = normalized.lowercase()
        val isRedirectLink = lower.contains("fb.watch/") ||
                lower.contains("facebook.com/share/") ||
                lower.contains("m.facebook.com/share/") ||
                lower.contains("vm.tiktok.com/") ||
                lower.contains("vt.tiktok.com/") ||
                lower.contains("youtu.be/") ||
                lower.contains("tinyurl.com/") ||
                lower.contains("bit.ly/") ||
                lower.contains("t.co/")

        if (isRedirectLink) {
            val resolved = followRedirects(normalized)
            if (resolved != null && resolved != normalized) {
                AppLogger.i("UrlNormalizer", "Resolved redirect: $normalized -> $resolved")
                return resolveCanonicalUrl(resolved)
            }
        }

        return normalized
    }

    private fun followRedirects(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .head()
                .build()

            client.newCall(request).execute().use { response ->
                val finalUrl = response.request.url.toString()
                if (finalUrl.isNotBlank() && finalUrl != url) {
                    finalUrl
                } else {
                    response.header("Location")
                }
            }
        } catch (e: Exception) {
            try {
                val getRequest = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .get()
                    .build()
                client.newCall(getRequest).execute().use { response ->
                    response.request.url.toString()
                }
            } catch (e2: Exception) {
                null
            }
        }
    }
}
