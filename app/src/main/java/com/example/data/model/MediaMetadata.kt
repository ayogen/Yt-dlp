package com.example.data.model

data class MediaMetadata(
    val id: String,
    val title: String,
    val webpageUrl: String,
    val uploader: String = "",
    val channel: String = "",
    val durationSeconds: Long = 0,
    val viewCount: Long? = null,
    val likeCount: Long? = null,
    val uploadDate: String = "",
    val description: String = "",
    val thumbnail: String = "",
    val isPlaylist: Boolean = false,
    val playlistCount: Int = 0,
    val playlistEntries: List<PlaylistEntry> = emptyList(),
    val formats: List<FormatInfo> = emptyList(),
    val subtitles: List<SubtitleTrack> = emptyList(),
    val extractorName: String = "generic",
    val directDownloadUrl: String? = null,
    val mediaType: MediaType = MediaType.VIDEO,
    val mimeType: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val fileSize: Long? = null,
    val carouselItems: List<CarouselItem> = emptyList()
) {
    val isImage: Boolean
        get() = mediaType == MediaType.IMAGE

    val isCarousel: Boolean
        get() = mediaType == MediaType.CAROUSEL || carouselItems.isNotEmpty()

    val isAudioOnly: Boolean
        get() = mediaType == MediaType.AUDIO || extractorName.equals("soundcloud", ignoreCase = true)

    val displayFileSize: String
        get() {
            val bytes = fileSize
            return if (bytes != null && bytes > 0) {
                formatBytes(bytes)
            } else {
                "Original HD"
            }
        }

    val durationFormatted: String
        get() {
            if (durationSeconds <= 0) return "--:--"
            val hours = durationSeconds / 3600
            val minutes = (durationSeconds % 3600) / 60
            val seconds = durationSeconds % 60
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%d:%02d", minutes, seconds)
            }
        }
}

data class CarouselItem(
    val id: String,
    val title: String,
    val mediaType: MediaType, // MediaType.IMAGE or MediaType.VIDEO
    val sourceUrl: String,
    val thumbnail: String = sourceUrl,
    val width: Int? = null,
    val height: Int? = null,
    val durationSeconds: Long = 0,
    val mimeType: String? = null,
    val fileSize: Long? = null,
    val isSelected: Boolean = true,
    val errorMessage: String? = null
) {
    val isImage: Boolean get() = mediaType == MediaType.IMAGE
    val isVideo: Boolean get() = mediaType == MediaType.VIDEO
}

sealed class ExtractedMedia {
    data class Image(
        val id: String,
        val title: String,
        val webpageUrl: String,
        val directDownloadUrl: String,
        val thumbnail: String = directDownloadUrl,
        val mimeType: String = "image/jpeg",
        val width: Int? = null,
        val height: Int? = null,
        val fileSize: Long? = null,
        val uploader: String = "",
        val uploadDate: String = "",
        val description: String = ""
    ) : ExtractedMedia()

    data class Video(
        val metadata: MediaMetadata
    ) : ExtractedMedia()

    data class Audio(
        val metadata: MediaMetadata
    ) : ExtractedMedia()

    data class Playlist(
        val metadata: MediaMetadata
    ) : ExtractedMedia()

    data class Carousel(
        val id: String,
        val title: String,
        val webpageUrl: String,
        val uploader: String = "",
        val thumbnail: String = "",
        val items: List<CarouselItem> = emptyList()
    ) : ExtractedMedia()

    data class Unknown(
        val webpageUrl: String,
        val message: String
    ) : ExtractedMedia()

    fun toMediaMetadata(): MediaMetadata {
        return when (this) {
            is Image -> MediaMetadata(
                id = id,
                title = title,
                webpageUrl = webpageUrl,
                uploader = uploader,
                uploadDate = uploadDate,
                description = description,
                thumbnail = thumbnail,
                directDownloadUrl = directDownloadUrl,
                mediaType = MediaType.IMAGE,
                mimeType = mimeType,
                width = width,
                height = height,
                fileSize = fileSize,
                extractorName = "DirectImage"
            )
            is Video -> metadata.copy(mediaType = MediaType.VIDEO)
            is Audio -> metadata.copy(mediaType = MediaType.AUDIO)
            is Playlist -> metadata.copy(mediaType = MediaType.PLAYLIST, isPlaylist = true)
            is Carousel -> MediaMetadata(
                id = id,
                title = title,
                webpageUrl = webpageUrl,
                uploader = uploader,
                thumbnail = thumbnail.ifBlank { items.firstOrNull()?.thumbnail.orEmpty() },
                mediaType = MediaType.CAROUSEL,
                carouselItems = items,
                extractorName = "SocialCarousel"
            )
            is Unknown -> MediaMetadata(
                id = "unknown",
                title = "Unknown Media",
                webpageUrl = webpageUrl,
                mediaType = MediaType.VIDEO,
                description = message
            )
        }
    }

    fun toMediaCollection(): MediaCollection {
        return when (this) {
            is Image -> {
                val singleItem = MediaItem(
                    id = id,
                    title = title,
                    sourceUrl = directDownloadUrl,
                    webpageUrl = webpageUrl,
                    thumbnail = thumbnail,
                    mediaKind = MediaKind.IMAGE,
                    width = width,
                    height = height,
                    mimeType = mimeType,
                    fileSize = fileSize,
                    sizeProvenance = if (fileSize != null && fileSize > 0) SizeProvenance.EXACT else SizeProvenance.UNKNOWN,
                    index = 0
                )
                MediaCollection(
                    id = id,
                    title = title,
                    webpageUrl = webpageUrl,
                    uploader = uploader,
                    thumbnail = thumbnail,
                    mediaKind = MediaKind.IMAGE,
                    items = listOf(singleItem),
                    extractorName = "DirectImage",
                    description = description,
                    uploadDate = uploadDate
                )
            }
            is Carousel -> {
                val mediaItems = items.mapIndexed { idx, item ->
                    val kind = when (item.mediaType) {
                        MediaType.IMAGE -> MediaKind.IMAGE
                        MediaType.AUDIO -> MediaKind.AUDIO
                        else -> MediaKind.VIDEO
                    }
                    val formats = if (kind == MediaKind.VIDEO) {
                        listOf(
                            FormatInfo(
                                formatId = "carousel_video_$idx",
                                ext = "mp4",
                                url = item.sourceUrl,
                                isMuxed = true
                            )
                        )
                    } else emptyList()

                    MediaItem(
                        id = item.id,
                        title = item.title,
                        sourceUrl = item.sourceUrl,
                        webpageUrl = webpageUrl,
                        thumbnail = item.thumbnail.ifBlank { thumbnail },
                        mediaKind = kind,
                        durationSeconds = item.durationSeconds,
                        width = item.width,
                        height = item.height,
                        mimeType = item.mimeType,
                        formats = formats,
                        fileSize = item.fileSize,
                        sizeProvenance = if (item.fileSize != null && item.fileSize > 0) SizeProvenance.EXACT else SizeProvenance.UNKNOWN,
                        isSelected = item.isSelected && item.errorMessage.isNullOrBlank(),
                        index = idx,
                        errorMessage = item.errorMessage
                    )
                }
                MediaCollection(
                    id = id,
                    title = title,
                    webpageUrl = webpageUrl,
                    uploader = uploader,
                    thumbnail = thumbnail.ifBlank { items.firstOrNull()?.thumbnail.orEmpty() },
                    mediaKind = MediaKind.CAROUSEL,
                    items = mediaItems,
                    extractorName = "SocialCarousel"
                )
            }
            is Video -> MediaCollection.fromMediaMetadata(metadata.copy(mediaType = MediaType.VIDEO))
            is Audio -> MediaCollection.fromMediaMetadata(metadata.copy(mediaType = MediaType.AUDIO))
            is Playlist -> MediaCollection.fromMediaMetadata(metadata.copy(mediaType = MediaType.PLAYLIST, isPlaylist = true))
            is Unknown -> MediaCollection(
                id = "unknown",
                title = "Unknown Media",
                webpageUrl = webpageUrl,
                mediaKind = MediaKind.UNKNOWN,
                description = message,
                items = emptyList()
            )
        }
    }
}

data class FormatInfo(
    val formatId: String,
    val ext: String = "mp4",
    val resolution: String = "",
    val width: Int? = null,
    val height: Int? = null,
    val fps: Double? = null,
    val vcodec: String = "none",
    val acodec: String = "none",
    val tbr: Double? = null,
    val vbr: Double? = null,
    val abr: Double? = null,
    val filesize: Long? = null,
    val filesizeApprox: Long? = null,
    val formatNote: String = "",
    val url: String = "",
    val protocol: String = "https",
    val isVideoOnly: Boolean = (vcodec != "none" && vcodec.isNotBlank()) && (acodec == "none" || acodec.isBlank()),
    val isAudioOnly: Boolean = (acodec != "none" && acodec.isNotBlank()) && (vcodec == "none" || vcodec.isBlank()),
    val isMuxed: Boolean = (vcodec != "none" && vcodec.isNotBlank()) && (acodec != "none" && acodec.isNotBlank()),
    val isDirectVideo: Boolean = isMuxed || isVideoOnly
) {
    val effectiveBitrate: Double?
        get() = tbr ?: ((vbr ?: 0.0) + (abr ?: 0.0)).takeIf { it > 0.0 } ?: vbr ?: abr

    val displayResolution: String
        get() = when {
            height != null && height > 0 -> "${height}p"
            resolution.isNotBlank() && resolution != "audio only" -> resolution
            isAudioOnly -> "Audio Only"
            else -> "Default"
        }

    val displayFileSize: String
        get() {
            val bytes = filesize ?: filesizeApprox
            return if (bytes != null && bytes > 0) {
                formatBytes(bytes)
            } else {
                "Unknown size"
            }
        }

    val codecSummary: String
        get() = when {
            isAudioOnly -> "Audio: ${acodec.uppercase()}"
            isVideoOnly -> "Video: ${vcodec.uppercase()} (No Audio)"
            isMuxed -> "${vcodec.uppercase()} + ${acodec.uppercase()}"
            else -> ext.uppercase()
        }
}

data class SubtitleTrack(
    val language: String,
    val name: String,
    val ext: String = "vtt",
    val url: String = "",
    val isAutoGenerated: Boolean = false
)

data class PlaylistEntry(
    val id: String,
    val title: String,
    val url: String,
    val durationSeconds: Long = 0,
    val thumbnail: String = "",
    val uploader: String = "",
    var isSelected: Boolean = true
) {
    val durationFormatted: String
        get() {
            if (durationSeconds <= 0) return ""
            val hours = durationSeconds / 3600
            val minutes = (durationSeconds % 3600) / 60
            val seconds = durationSeconds % 60
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%d:%02d", minutes, seconds)
            }
        }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format("%.2f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}

fun formatSpeed(bytesPerSec: Double): String {
    if (bytesPerSec <= 0) return "0 B/s"
    val exp = (Math.log(bytesPerSec) / Math.log(1024.0)).toInt().coerceIn(0, 4)
    val pre = if (exp > 0) "KMGTPE"[exp - 1].toString() else ""
    return String.format("%.2f %sB/s", bytesPerSec / Math.pow(1024.0, exp.toDouble()), pre)
}

fun formatEta(etaSeconds: Long): String {
    if (etaSeconds <= 0) return "--"
    val hours = etaSeconds / 3600
    val minutes = (etaSeconds % 3600) / 60
    val seconds = etaSeconds % 60
    return if (hours > 0) {
        String.format("%dh %02dm", hours, minutes)
    } else if (minutes > 0) {
        String.format("%dm %02ds", minutes, seconds)
    } else {
        String.format("%ds", seconds)
    }
}
