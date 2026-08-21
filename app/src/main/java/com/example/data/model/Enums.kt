package com.example.data.model

enum class DownloadStatus(val displayName: String) {
    QUEUED("Queued"),
    ANALYZING("Analyzing"),
    DOWNLOADING("Downloading"),
    PAUSED("Paused"),
    PROCESSING("Processing"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    CANCELLED("Cancelled")
}

enum class MediaType(val displayName: String) {
    VIDEO("Video"),
    AUDIO("Audio"),
    PLAYLIST("Playlist"),
    IMAGE("Image"),
    CAROUSEL("Carousel")
}

enum class OutputContainer(val ext: String) {
    AUTO("auto"),
    MP4("mp4"),
    MKV("mkv"),
    WEBM("webm"),
    JPG("jpg"),
    PNG("png"),
    WEBP("webp"),
    GIF("gif");

    companion object {
        fun fromExt(ext: String): OutputContainer {
            return entries.firstOrNull { it.ext.equals(ext, ignoreCase = true) } ?: MP4
        }
    }
}

enum class AudioFormat(val ext: String, val displayName: String) {
    BEST("best", "Best Quality"),
    MP3("mp3", "MP3 (.mp3)"),
    M4A("m4a", "M4A / AAC (.m4a)"),
    OPUS("opus", "Opus (.opus)"),
    WAV("wav", "WAV Lossless (.wav)"),
    FLAC("flac", "FLAC Lossless (.flac)");

    companion object {
        fun fromExt(ext: String): AudioFormat {
            return entries.firstOrNull { it.ext.equals(ext, ignoreCase = true) } ?: MP3
        }
    }
}

enum class VideoQualityPreset(val label: String, val height: Int?) {
    BEST("Best Available", null),
    RES_4K("4K (2160p)", 2160),
    RES_1440P("2K (1440p)", 1440),
    RES_1080P("Full HD (1080p)", 1080),
    RES_720P("HD (720p)", 720),
    RES_480P("SD (480p)", 480),
    RES_360P("Low (360p)", 360),
    CUSTOM("Custom Format", null)
}

enum class AudioQualityPreset(val label: String, val bitrateKbps: Int?) {
    BEST("Best Original", null),
    KBPS_320("320 kbps (Ultra)", 320),
    KBPS_256("256 kbps (High)", 256),
    KBPS_192("192 kbps (Medium)", 192),
    KBPS_128("128 kbps (Standard)", 128),
    CUSTOM("Custom Bitrate", null)
}

enum class LogLevel {
    DEBUG, INFO, WARNING, ERROR
}

enum class EngineState(val displayName: String) {
    MISSING("Missing"),
    INSTALLING("Installing"),
    READY("Ready"),
    INVALID("Invalid / Not Executable"),
    UPDATING("Updating"),
    ERROR("Error")
}

enum class HistorySortOrder(val displayName: String) {
    NEWEST("Newest First"),
    OLDEST("Oldest First"),
    SIZE_DESC("Largest File"),
    NAME_ASC("Name (A-Z)")
}
