package com.example.data.model

data class AppSettings(
    val maxConcurrentDownloads: Int = 3,
    val defaultVideoQuality: VideoQualityPreset = VideoQualityPreset.BEST,
    val defaultAudioQuality: AudioQualityPreset = AudioQualityPreset.BEST,
    val defaultContainer: OutputContainer = OutputContainer.MP4,
    val defaultAudioFormat: AudioFormat = AudioFormat.MP3,
    val downloadLocationUri: String = "",
    val downloadLocationDisplayName: String = "Default App Storage",
    val resumeDownloads: Boolean = true,
    val retryCount: Int = 3,
    val customYtDlpArgs: String = "",
    val verboseLogging: Boolean = false,
    val cookiesFilePath: String = "",
    val sanitizeFilenames: Boolean = true,
    val organizeByUploader: Boolean = false,
    val embedSubtitles: Boolean = false,
    val embedThumbnail: Boolean = true,
    val filenameTemplate: String = "%(title)s.%(ext)s",
    val autoStartDownloads: Boolean = true,
    val confirmDelete: Boolean = true,
    val darkTheme: Boolean = true
)

object FilenameTemplatePresets {
    val PRESETS = listOf(
        "%(title)s.%(ext)s" to "Title Only (Default)",
        "%(uploader)s - %(title)s.%(ext)s" to "Uploader - Title",
        "%(upload_date)s - %(title)s.%(ext)s" to "Date - Title",
        "%(title)s [%(id)s].%(ext)s" to "Title [ID]",
        "%(uploader)s/%(title)s.%(ext)s" to "Uploader Folder / Title"
    )
}
