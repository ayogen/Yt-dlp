package com.example.data.model

data class DownloadProfile(
    val id: String,
    val name: String,
    val description: String = "",
    val mediaType: MediaType = MediaType.VIDEO,
    val videoQuality: String = "1080p", // "best", "2160p", "1440p", "1080p", "720p", "480p", "360p"
    val container: String = "mp4", // "mp4", "mp3", "m4a", "mkv", "webm"
    val audioBitrate: Int? = 320,
    val embedSubs: Boolean = false,
    val embedThumbnail: Boolean = true,
    val isPreset: Boolean = false
) {
    companion object {
        val DEFAULT_PROFILES = listOf(
            DownloadProfile(
                id = "preset_yt_video",
                name = "YouTube Video",
                description = "1080p High Definition MP4 video",
                mediaType = MediaType.VIDEO,
                videoQuality = "1080p",
                container = "mp4",
                embedThumbnail = true,
                isPreset = true
            ),
            DownloadProfile(
                id = "preset_music",
                name = "Music & Podcasts",
                description = "Best Audio extracted as MP3 (320 kbps)",
                mediaType = MediaType.AUDIO,
                videoQuality = "best",
                container = "mp3",
                audioBitrate = 320,
                embedThumbnail = true,
                isPreset = true
            ),
            DownloadProfile(
                id = "preset_social",
                name = "Social Media",
                description = "Best available MP4 for TikTok/Reels/Shorts",
                mediaType = MediaType.VIDEO,
                videoQuality = "best",
                container = "mp4",
                embedThumbnail = true,
                isPreset = true
            ),
            DownloadProfile(
                id = "preset_hd_720",
                name = "Fast HD (720p)",
                description = "Compact 720p MP4 for fast downloading",
                mediaType = MediaType.VIDEO,
                videoQuality = "720p",
                container = "mp4",
                embedThumbnail = true,
                isPreset = true
            )
        )
    }
}
