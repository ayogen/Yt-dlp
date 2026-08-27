package com.example.ui.home

import com.example.data.model.AudioFormat
import com.example.data.model.FormatInfo
import com.example.data.model.MediaCollection
import com.example.data.model.MediaItem
import com.example.data.model.MediaKind
import com.example.data.model.MediaMetadata
import com.example.data.model.MediaType
import com.example.data.model.OutputContainer
import com.example.data.model.SizeProvenance

/**
 * UI representation of a single media item within an analysis bottom sheet.
 */
data class MediaItemUiModel(
    val id: String,
    val title: String,
    val sourceUrl: String,
    val thumbnail: String,
    val mediaKind: MediaKind,
    val durationSeconds: Long = 0L,
    val durationFormatted: String,
    val dimensionsFormatted: String,
    val displayFileSize: String,
    val sizeProvenance: SizeProvenance,
    val formats: List<FormatInfo>,
    val isSelected: Boolean,
    val index: Int,
    val errorMessage: String? = null
) {
    val isSuccess: Boolean get() = errorMessage.isNullOrBlank()
    val isFailed: Boolean get() = !isSuccess
    val isImage: Boolean get() = mediaKind == MediaKind.IMAGE
    val isVideo: Boolean get() = mediaKind == MediaKind.VIDEO
    val isAudio: Boolean get() = mediaKind == MediaKind.AUDIO
}

/**
 * Canonical presentation model for media analysis bottom sheet.
 */
data class AnalysisUiModel(
    val id: String,
    val title: String,
    val webpageUrl: String,
    val uploader: String,
    val channel: String,
    val thumbnail: String,
    val mediaKind: MediaKind,
    val items: List<MediaItemUiModel>,
    val primaryFormats: List<FormatInfo>,
    val defaultFormat: FormatInfo?,
    val extractorName: String,
    val isSingleItem: Boolean,
    val isMultiItem: Boolean,
    val isPlaylist: Boolean,
    val isCarousel: Boolean,
    val isMixedCollection: Boolean,
    val totalCount: Int,
    val selectedCount: Int,
    val failedCount: Int
) {
    val isImage: Boolean get() = mediaKind == MediaKind.IMAGE || (isSingleItem && items.firstOrNull()?.isImage == true)
    val isVideo: Boolean get() = mediaKind == MediaKind.VIDEO || (isSingleItem && items.firstOrNull()?.isVideo == true)
    val isAudio: Boolean get() = mediaKind == MediaKind.AUDIO || (isSingleItem && items.firstOrNull()?.isAudio == true)
    val formats: List<FormatInfo> get() = primaryFormats
    val durationSeconds: Long get() = items.firstOrNull()?.durationSeconds ?: 0L
    val durationFormatted: String get() = items.firstOrNull()?.durationFormatted.orEmpty()
    val displayFileSize: String get() = items.firstOrNull()?.displayFileSize ?: "Unknown size"
    val sizeProvenance: SizeProvenance get() = items.firstOrNull()?.sizeProvenance ?: SizeProvenance.UNKNOWN
    val primaryMediaType: MediaType get() = when {
        isCarousel -> MediaType.CAROUSEL
        isPlaylist -> MediaType.PLAYLIST
        isImage -> MediaType.IMAGE
        isAudio -> MediaType.AUDIO
        else -> MediaType.VIDEO
    }
}

/**
 * Mapper between domain media collections/metadata and AnalysisUiModel.
 */
object MediaUiMapper {

    /**
     * Map canonical MediaCollection to AnalysisUiModel.
     */
    fun mapCollectionToUiModel(collection: MediaCollection): AnalysisUiModel {
        val itemsUi = collection.items.map { item ->
            val dimensions = if (item.width != null && item.height != null) {
                "${item.width}x${item.height}"
            } else if (item.isImage) {
                "Original HD"
            } else {
                ""
            }

            MediaItemUiModel(
                id = item.id,
                title = item.title,
                sourceUrl = item.sourceUrl,
                thumbnail = item.thumbnail.ifBlank { collection.thumbnail },
                mediaKind = item.mediaKind,
                durationSeconds = item.durationSeconds,
                durationFormatted = item.durationFormatted,
                dimensionsFormatted = dimensions,
                displayFileSize = item.displayFileSize,
                sizeProvenance = item.sizeProvenance,
                formats = item.formats,
                isSelected = item.isSelected && item.isSuccess,
                index = item.index,
                errorMessage = item.errorMessage
            )
        }

        val primaryFormats = deduplicateVideoFormats(
            collection.items.flatMap { it.formats }.ifEmpty {
                collection.toMediaMetadata().formats
            }
        )

        val defaultFormat = primaryFormats.firstOrNull { it.height != null && it.height <= 1080 }
            ?: primaryFormats.firstOrNull()

        return AnalysisUiModel(
            id = collection.id,
            title = collection.title,
            webpageUrl = collection.webpageUrl,
            uploader = collection.uploader,
            channel = collection.channel,
            thumbnail = collection.thumbnail.ifBlank { itemsUi.firstOrNull()?.thumbnail.orEmpty() },
            mediaKind = collection.mediaKind,
            items = itemsUi,
            primaryFormats = primaryFormats,
            defaultFormat = defaultFormat,
            extractorName = collection.extractorName,
            isSingleItem = collection.isSingleItem,
            isMultiItem = collection.isMultiItem,
            isPlaylist = collection.isPlaylist,
            isCarousel = collection.isCarousel,
            isMixedCollection = collection.isMixedCollection,
            totalCount = collection.items.size,
            selectedCount = itemsUi.count { it.isSelected },
            failedCount = itemsUi.count { it.isFailed }
        )
    }

    /**
     * Compatibility mapping for legacy MediaMetadata.
     */
    fun mapMetadataToUiModel(metadata: MediaMetadata): AnalysisUiModel {
        val canonical = MediaCollection.fromMediaMetadata(metadata)
        return mapCollectionToUiModel(canonical)
    }
}
