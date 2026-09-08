package com.example.data.model

enum class DownloadMode(val label: String, val description: String) {
    AUTO(
        label = "Auto Detect",
        description = "Standard multi-stage extraction pipeline"
    ),
    VIDEO(
        label = "Video Only",
        description = "Quick yt-dlp direct video stream"
    ),
    IMAGE(
        label = "Images Only",
        description = "Direct stream & images only"
    )
}
