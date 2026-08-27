package com.example.data.model

/**
 * Provenance of size calculation.
 */
enum class SizeProvenance {
    EXACT,
    APPROXIMATE,
    DERIVED,
    UNKNOWN
}

/**
 * Kind of media item in a collection.
 */
enum class MediaKind {
    IMAGE,
    VIDEO,
    AUDIO,
    CAROUSEL,
    PLAYLIST,
    UNKNOWN
}

/**
 * Canonical representation of a single media item within a collection.
 */
data class MediaItem(
    val id: String,
    val title: String,
    val sourceUrl: String,
    val webpageUrl: String = sourceUrl,
    val thumbnail: String = "",
    val mediaKind: MediaKind = MediaKind.VIDEO,
    val durationSeconds: Long = 0,
    val width: Int? = null,
    val height: Int? = null,
    val mimeType: String? = null,
    val formats: List<FormatInfo> = emptyList(),
    val subtitles: List<SubtitleTrack> = emptyList(),
    val fileSize: Long? = null,
    val sizeProvenance: SizeProvenance = if (fileSize != null && fileSize > 0) SizeProvenance.EXACT else SizeProvenance.UNKNOWN,
    val isSelected: Boolean = true,
    val index: Int = 0,
    val errorMessage: String? = null
) {
    val isSuccess: Boolean get() = errorMessage.isNullOrBlank()
    val isFailed: Boolean get() = !isSuccess

    val isImage: Boolean get() = mediaKind == MediaKind.IMAGE
    val isVideo: Boolean get() = mediaKind == MediaKind.VIDEO
    val isAudio: Boolean get() = mediaKind == MediaKind.AUDIO

    val displayFileSize: String
        get() {
            val bytes = fileSize
            return when (sizeProvenance) {
                SizeProvenance.EXACT -> if (bytes != null && bytes > 0) formatBytes(bytes) else "Unknown size"
                SizeProvenance.APPROXIMATE -> if (bytes != null && bytes > 0) "~${formatBytes(bytes)}" else "~Approximate"
                SizeProvenance.DERIVED -> if (bytes != null && bytes > 0) "≈${formatBytes(bytes)}" else "≈Estimated"
                SizeProvenance.UNKNOWN -> if (bytes != null && bytes > 0) formatBytes(bytes) else "Unknown size"
            }
        }

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

/**
 * Canonical presentation and domain source representing a single media item or a multi-item collection.
 */
data class MediaCollection(
    val id: String,
    val title: String,
    val webpageUrl: String,
    val uploader: String = "",
    val channel: String = "",
    val thumbnail: String = "",
    val mediaKind: MediaKind = MediaKind.VIDEO,
    val items: List<MediaItem> = emptyList(),
    val extractorName: String = "generic",
    val description: String = "",
    val uploadDate: String = "",
    val totalCount: Int = items.size
) {
    val isSingleItem: Boolean get() = items.size == 1
    val isMultiItem: Boolean get() = items.size > 1
    val isPlaylist: Boolean get() = mediaKind == MediaKind.PLAYLIST
    val isCarousel: Boolean get() = mediaKind == MediaKind.CAROUSEL
    val isMixedCollection: Boolean get() = items.map { it.mediaKind }.distinct().size > 1

    val selectedItems: List<MediaItem> get() = items.filter { it.isSelected && it.isSuccess }
    val validItems: List<MediaItem> get() = items.filter { it.isSuccess }
    val failedItems: List<MediaItem> get() = items.filter { it.isFailed }

    companion object {
        /**
         * Backward compatibility adapter from legacy MediaMetadata to canonical MediaCollection.
         */
        fun fromMediaMetadata(meta: MediaMetadata): MediaCollection {
            return when {
                meta.isCarousel && meta.carouselItems.isNotEmpty() -> {
                    val items = meta.carouselItems.mapIndexed { idx, cItem ->
                        MediaItem(
                            id = cItem.id,
                            title = cItem.title,
                            sourceUrl = cItem.sourceUrl,
                            webpageUrl = meta.webpageUrl,
                            thumbnail = cItem.thumbnail.ifBlank { meta.thumbnail },
                            mediaKind = when (cItem.mediaType) {
                                MediaType.IMAGE -> MediaKind.IMAGE
                                MediaType.AUDIO -> MediaKind.AUDIO
                                else -> MediaKind.VIDEO
                            },
                            durationSeconds = cItem.durationSeconds,
                            width = cItem.width,
                            height = cItem.height,
                            mimeType = cItem.mimeType,
                            fileSize = cItem.fileSize,
                            sizeProvenance = if (cItem.fileSize != null && cItem.fileSize > 0) SizeProvenance.EXACT else SizeProvenance.UNKNOWN,
                            isSelected = cItem.isSelected && cItem.errorMessage.isNullOrBlank(),
                            index = idx,
                            errorMessage = cItem.errorMessage
                        )
                    }
                    MediaCollection(
                        id = meta.id,
                        title = meta.title,
                        webpageUrl = meta.webpageUrl,
                        uploader = meta.uploader,
                        channel = meta.channel,
                        thumbnail = meta.thumbnail.ifBlank { items.firstOrNull()?.thumbnail.orEmpty() },
                        mediaKind = MediaKind.CAROUSEL,
                        items = items,
                        extractorName = meta.extractorName,
                        description = meta.description,
                        uploadDate = meta.uploadDate
                    )
                }
                meta.isPlaylist && meta.playlistEntries.isNotEmpty() -> {
                    val items = meta.playlistEntries.mapIndexed { idx, pEntry ->
                        MediaItem(
                            id = pEntry.id,
                            title = pEntry.title,
                            sourceUrl = pEntry.url,
                            webpageUrl = pEntry.url,
                            thumbnail = pEntry.thumbnail.ifBlank { meta.thumbnail },
                            mediaKind = MediaKind.VIDEO,
                            durationSeconds = pEntry.durationSeconds,
                            isSelected = pEntry.isSelected,
                            index = idx
                        )
                    }
                    MediaCollection(
                        id = meta.id,
                        title = meta.title,
                        webpageUrl = meta.webpageUrl,
                        uploader = meta.uploader,
                        channel = meta.channel,
                        thumbnail = meta.thumbnail,
                        mediaKind = MediaKind.PLAYLIST,
                        items = items,
                        extractorName = meta.extractorName,
                        description = meta.description,
                        uploadDate = meta.uploadDate
                    )
                }
                meta.isImage -> {
                    val singleItem = MediaItem(
                        id = meta.id,
                        title = meta.title,
                        sourceUrl = meta.directDownloadUrl ?: meta.webpageUrl,
                        webpageUrl = meta.webpageUrl,
                        thumbnail = meta.thumbnail.ifBlank { meta.directDownloadUrl ?: meta.webpageUrl },
                        mediaKind = MediaKind.IMAGE,
                        width = meta.width,
                        height = meta.height,
                        mimeType = meta.mimeType,
                        fileSize = meta.fileSize,
                        sizeProvenance = if (meta.fileSize != null && meta.fileSize > 0) SizeProvenance.EXACT else SizeProvenance.UNKNOWN,
                        isSelected = true,
                        index = 0
                    )
                    MediaCollection(
                        id = meta.id,
                        title = meta.title,
                        webpageUrl = meta.webpageUrl,
                        uploader = meta.uploader,
                        channel = meta.channel,
                        thumbnail = singleItem.thumbnail,
                        mediaKind = MediaKind.IMAGE,
                        items = listOf(singleItem),
                        extractorName = meta.extractorName,
                        description = meta.description,
                        uploadDate = meta.uploadDate
                    )
                }
                meta.isAudioOnly || meta.mediaType == MediaType.AUDIO -> {
                    val singleItem = MediaItem(
                        id = meta.id,
                        title = meta.title,
                        sourceUrl = meta.directDownloadUrl ?: meta.webpageUrl,
                        webpageUrl = meta.webpageUrl,
                        thumbnail = meta.thumbnail,
                        mediaKind = MediaKind.AUDIO,
                        durationSeconds = meta.durationSeconds,
                        formats = meta.formats,
                        subtitles = meta.subtitles,
                        fileSize = meta.fileSize ?: meta.formats.firstOrNull()?.filesize,
                        sizeProvenance = if (meta.fileSize != null) SizeProvenance.EXACT else if (meta.formats.firstOrNull()?.filesizeApprox != null) SizeProvenance.APPROXIMATE else SizeProvenance.UNKNOWN,
                        isSelected = true,
                        index = 0
                    )
                    MediaCollection(
                        id = meta.id,
                        title = meta.title,
                        webpageUrl = meta.webpageUrl,
                        uploader = meta.uploader,
                        channel = meta.channel,
                        thumbnail = meta.thumbnail,
                        mediaKind = MediaKind.AUDIO,
                        items = listOf(singleItem),
                        extractorName = meta.extractorName,
                        description = meta.description,
                        uploadDate = meta.uploadDate
                    )
                }
                else -> {
                    // Standard Single Video
                    val singleItem = MediaItem(
                        id = meta.id,
                        title = meta.title,
                        sourceUrl = meta.directDownloadUrl ?: meta.webpageUrl,
                        webpageUrl = meta.webpageUrl,
                        thumbnail = meta.thumbnail,
                        mediaKind = MediaKind.VIDEO,
                        durationSeconds = meta.durationSeconds,
                        width = meta.width,
                        height = meta.height,
                        formats = meta.formats,
                        subtitles = meta.subtitles,
                        fileSize = meta.fileSize ?: meta.formats.firstOrNull()?.filesize,
                        sizeProvenance = when {
                            meta.fileSize != null -> SizeProvenance.EXACT
                            meta.formats.any { it.filesize != null } -> SizeProvenance.EXACT
                            meta.formats.any { it.filesizeApprox != null } -> SizeProvenance.APPROXIMATE
                            else -> SizeProvenance.UNKNOWN
                        },
                        isSelected = true,
                        index = 0
                    )
                    MediaCollection(
                        id = meta.id,
                        title = meta.title,
                        webpageUrl = meta.webpageUrl,
                        uploader = meta.uploader,
                        channel = meta.channel,
                        thumbnail = meta.thumbnail,
                        mediaKind = MediaKind.VIDEO,
                        items = listOf(singleItem),
                        extractorName = meta.extractorName,
                        description = meta.description,
                        uploadDate = meta.uploadDate
                    )
                }
            }
        }

        /**
         * Adapter from canonical MediaCollection back to legacy MediaMetadata.
         */
        fun toLegacyMediaMetadata(collection: MediaCollection): MediaMetadata = collection.toMediaMetadata()
    }

    /**
     * Backward compatibility adapter from canonical MediaCollection back to legacy MediaMetadata.
     */
    fun toMediaMetadata(): MediaMetadata {
        return when {
            isCarousel -> {
                MediaMetadata(
                    id = id,
                    title = title,
                    webpageUrl = webpageUrl,
                    uploader = uploader,
                    channel = channel,
                    thumbnail = thumbnail,
                    mediaType = MediaType.CAROUSEL,
                    carouselItems = items.map { item ->
                        CarouselItem(
                            id = item.id,
                            title = item.title,
                            mediaType = when (item.mediaKind) {
                                MediaKind.IMAGE -> MediaType.IMAGE
                                MediaKind.AUDIO -> MediaType.AUDIO
                                else -> MediaType.VIDEO
                            },
                            sourceUrl = item.sourceUrl,
                            thumbnail = item.thumbnail,
                            width = item.width,
                            height = item.height,
                            durationSeconds = item.durationSeconds,
                            mimeType = item.mimeType,
                            fileSize = item.fileSize,
                            isSelected = item.isSelected && item.isSuccess,
                            errorMessage = item.errorMessage
                        )
                    },
                    extractorName = extractorName,
                    description = description,
                    uploadDate = uploadDate
                )
            }
            isPlaylist -> {
                MediaMetadata(
                    id = id,
                    title = title,
                    webpageUrl = webpageUrl,
                    uploader = uploader,
                    channel = channel,
                    thumbnail = thumbnail,
                    isPlaylist = true,
                    playlistCount = items.size,
                    mediaType = MediaType.PLAYLIST,
                    playlistEntries = items.map { item ->
                        PlaylistEntry(
                            id = item.id,
                            title = item.title,
                            url = item.sourceUrl,
                            durationSeconds = item.durationSeconds,
                            thumbnail = item.thumbnail,
                            uploader = uploader,
                            isSelected = item.isSelected
                        )
                    },
                    extractorName = extractorName,
                    description = description,
                    uploadDate = uploadDate
                )
            }
            items.firstOrNull()?.isImage == true || mediaKind == MediaKind.IMAGE -> {
                val item = items.firstOrNull()
                MediaMetadata(
                    id = item?.id ?: id,
                    title = item?.title ?: title,
                    webpageUrl = item?.webpageUrl ?: webpageUrl,
                    uploader = uploader,
                    channel = channel,
                    thumbnail = item?.thumbnail ?: thumbnail,
                    directDownloadUrl = item?.sourceUrl ?: webpageUrl,
                    mediaType = MediaType.IMAGE,
                    mimeType = item?.mimeType,
                    width = item?.width,
                    height = item?.height,
                    fileSize = item?.fileSize,
                    extractorName = extractorName,
                    description = description,
                    uploadDate = uploadDate
                )
            }
            items.firstOrNull()?.isAudio == true || mediaKind == MediaKind.AUDIO -> {
                val item = items.firstOrNull()
                MediaMetadata(
                    id = item?.id ?: id,
                    title = item?.title ?: title,
                    webpageUrl = item?.webpageUrl ?: webpageUrl,
                    uploader = uploader,
                    channel = channel,
                    thumbnail = item?.thumbnail ?: thumbnail,
                    directDownloadUrl = item?.sourceUrl ?: webpageUrl,
                    mediaType = MediaType.AUDIO,
                    durationSeconds = item?.durationSeconds ?: 0,
                    formats = item?.formats.orEmpty(),
                    subtitles = item?.subtitles.orEmpty(),
                    fileSize = item?.fileSize,
                    extractorName = extractorName,
                    description = description,
                    uploadDate = uploadDate
                )
            }
            else -> {
                val item = items.firstOrNull()
                MediaMetadata(
                    id = item?.id ?: id,
                    title = item?.title ?: title,
                    webpageUrl = item?.webpageUrl ?: webpageUrl,
                    uploader = uploader,
                    channel = channel,
                    thumbnail = item?.thumbnail ?: thumbnail,
                    directDownloadUrl = item?.sourceUrl ?: webpageUrl,
                    mediaType = MediaType.VIDEO,
                    durationSeconds = item?.durationSeconds ?: 0,
                    width = item?.width,
                    height = item?.height,
                    formats = item?.formats.orEmpty(),
                    subtitles = item?.subtitles.orEmpty(),
                    fileSize = item?.fileSize,
                    extractorName = extractorName,
                    description = description,
                    uploadDate = uploadDate
                )
            }
        }
    }
}
