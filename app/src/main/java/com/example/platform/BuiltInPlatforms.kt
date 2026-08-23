package com.example.platform

object BuiltInPlatforms {

    private fun domainMatch(host: String, domain: String): Boolean {
        val cleanHost = host.lowercase().removePrefix("www.").removePrefix("m.")
        val cleanDomain = domain.lowercase()
        return cleanHost == cleanDomain || cleanHost.endsWith(".$cleanDomain")
    }

    val KICK = PlatformDescriptor(
        id = "kick",
        name = "Kick",
        hostMatcher = { host -> domainMatch(host, "kick.com") },
        defaultIntent = "video",
        pathClassifier = { path, _ ->
            when {
                path.contains("/video/") || path.contains("/videos/") -> "video"
                path.endsWith(".jpg") || path.endsWith(".png") || path.endsWith(".webp") -> "image"
                else -> "video"
            }
        },
        allowYtDlp = true,
        allowGenericImageFallback = false,
        strategyOrder = listOf("YTDLP", "EMBEDDED")
    )

    val DAILYMOTION = PlatformDescriptor(
        id = "dailymotion",
        name = "Dailymotion",
        hostMatcher = { host -> domainMatch(host, "dailymotion.com") || domainMatch(host, "dai.ly") },
        defaultIntent = "video",
        allowYtDlp = true,
        allowGenericImageFallback = false,
        strategyOrder = listOf("YTDLP", "EMBEDDED")
    )

    val YOUTUBE = PlatformDescriptor(
        id = "youtube",
        name = "YouTube",
        hostMatcher = { host -> domainMatch(host, "youtube.com") || domainMatch(host, "youtu.be") },
        defaultIntent = "video",
        pathClassifier = { path, query ->
            when {
                path.contains("/playlist") || query?.contains("list=") == true -> "playlist"
                path.contains("/shorts/") -> "video"
                else -> "video"
            }
        },
        allowYtDlp = true,
        allowGenericImageFallback = false,
        strategyOrder = listOf("YTDLP", "EMBEDDED")
    )

    val TIKTOK = PlatformDescriptor(
        id = "tiktok",
        name = "TikTok",
        hostMatcher = { host -> domainMatch(host, "tiktok.com") },
        defaultIntent = "video",
        pathClassifier = { path, _ ->
            if (path.contains("/photo/")) "carousel" else "video"
        },
        allowYtDlp = true,
        allowGenericImageFallback = false,
        strategyOrder = listOf("YTDLP", "EMBEDDED")
    )

    val REDDIT = PlatformDescriptor(
        id = "reddit",
        name = "Reddit",
        hostMatcher = { host -> domainMatch(host, "reddit.com") || domainMatch(host, "redd.it") },
        defaultIntent = "video",
        pathClassifier = { path, _ ->
            when {
                path.contains("/gallery/") -> "carousel"
                path.endsWith(".jpg") || path.endsWith(".png") || path.endsWith(".gif") -> "image"
                else -> "video"
            }
        },
        allowYtDlp = true,
        allowGenericImageFallback = false,
        strategyOrder = listOf("YTDLP", "EMBEDDED")
    )

    val INSTAGRAM = PlatformDescriptor(
        id = "instagram",
        name = "Instagram",
        hostMatcher = { host -> domainMatch(host, "instagram.com") },
        defaultIntent = "video",
        pathClassifier = { path, _ ->
            when {
                path.contains("/reel/") || path.contains("/reels/") -> "video"
                path.contains("/p/") -> "video"
                path.contains("/stories/") -> "video"
                else -> "video"
            }
        },
        allowYtDlp = true,
        allowGenericImageFallback = false,
        strategyOrder = listOf("YTDLP", "EMBEDDED")
    )

    val FACEBOOK = PlatformDescriptor(
        id = "facebook",
        name = "Facebook",
        hostMatcher = { host -> domainMatch(host, "facebook.com") || domainMatch(host, "fb.watch") || domainMatch(host, "fb.com") },
        defaultIntent = "video",
        pathClassifier = { path, _ ->
            if (path.contains("/photo") || path.contains("/photos")) "image" else "video"
        },
        allowYtDlp = true,
        allowGenericImageFallback = false,
        strategyOrder = listOf("YTDLP", "EMBEDDED")
    )

    val TWITTER = PlatformDescriptor(
        id = "twitter",
        name = "X / Twitter",
        hostMatcher = { host -> domainMatch(host, "twitter.com") || domainMatch(host, "x.com") },
        defaultIntent = "video",
        pathClassifier = { path, _ ->
            if (path.contains("/photo/")) "image" else "video"
        },
        allowYtDlp = true,
        allowGenericImageFallback = false,
        strategyOrder = listOf("YTDLP", "EMBEDDED")
    )

    val VIMEO = PlatformDescriptor(
        id = "vimeo",
        name = "Vimeo",
        hostMatcher = { host -> domainMatch(host, "vimeo.com") },
        defaultIntent = "video",
        allowYtDlp = true,
        allowGenericImageFallback = false,
        strategyOrder = listOf("YTDLP", "EMBEDDED")
    )

    val SOUNDCLOUD = PlatformDescriptor(
        id = "soundcloud",
        name = "SoundCloud",
        hostMatcher = { host -> domainMatch(host, "soundcloud.com") },
        defaultIntent = "audio",
        allowYtDlp = true,
        allowGenericImageFallback = false,
        strategyOrder = listOf("YTDLP", "EMBEDDED")
    )

    val GENERIC = PlatformDescriptor(
        id = "generic",
        name = "Generic Web",
        hostMatcher = { true },
        defaultIntent = "generic",
        allowYtDlp = true,
        allowGenericImageFallback = true,
        strategyOrder = listOf("DIRECT_MEDIA", "YTDLP", "EMBEDDED", "GENERIC_PAGE")
    )

    val ALL_PLATFORMS = listOf(
        KICK,
        DAILYMOTION,
        YOUTUBE,
        TIKTOK,
        REDDIT,
        INSTAGRAM,
        FACEBOOK,
        TWITTER,
        VIMEO,
        SOUNDCLOUD,
        GENERIC
    )
}
