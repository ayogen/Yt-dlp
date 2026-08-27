package com.example.download

import com.example.data.model.FormatInfo
import com.example.data.model.MediaCollection
import com.example.data.model.MediaItem
import com.example.data.model.MediaKind
import com.example.data.model.MediaType
import com.example.data.model.OutputContainer
import java.util.UUID

/**
 * Options for generating download plans from a canonical MediaCollection.
 */
data class DownloadPlanningOptions(
    val selectedFormat: FormatInfo? = null,
    val targetMediaType: MediaType = MediaType.VIDEO,
    val targetContainer: OutputContainer = OutputContainer.MP4,
    val audioBitrate: Int? = null,
    val embedSubtitles: Boolean = false,
    val embedThumbnail: Boolean = true,
    val selectedIndices: Set<Int> = emptySet(),
    val qualityLabel: String = "Best"
)

/**
 * A single planned download request ready for coordinator execution.
 */
data class PlannedDownloadRequest(
    val id: String = UUID.randomUUID().toString(),
    val sourceUrl: String,
    val title: String,
    val thumbnail: String,
    val formatId: String,
    val formatDescription: String,
    val qualityLabel: String,
    val totalBytes: Long,
    val mediaType: MediaType,
    val targetContainer: String,
    val audioBitrate: Int? = null,
    val embedSubs: Boolean,
    val embedThumbnail: Boolean,
    val isPlaylist: Boolean,
    val playlistIndex: Int,
    val playlistTotal: Int
)

/**
 * Complete plan for downloading items from a collection.
 */
data class DownloadPlan(
    val requests: List<PlannedDownloadRequest>
)

/**
 * Domain planner translating MediaCollection and planning options into concrete DownloadPlan.
 */
object DownloadPlanner {

    fun planDownloads(collection: MediaCollection, options: DownloadPlanningOptions): DownloadPlan {
        val requests = mutableListOf<PlannedDownloadRequest>()

        val effectiveSelectedIndices = if (options.selectedIndices.isNotEmpty()) {
            options.selectedIndices
        } else if (collection.isMultiItem) {
            collection.items.filter { it.isSelected && it.isSuccess }.map { it.index }.toSet()
        } else {
            setOf(0)
        }

        val totalSelected = effectiveSelectedIndices.size
        var taskSequence = 1

        effectiveSelectedIndices.sorted().forEach { itemIndex ->
            val item = collection.items.getOrNull(itemIndex)
            if (item != null && item.isSuccess) {
                val isImage = item.isImage
                val isAudio = item.isAudio || options.targetMediaType == MediaType.AUDIO
                val itemMediaType = when {
                    isImage -> MediaType.IMAGE
                    isAudio -> MediaType.AUDIO
                    else -> MediaType.VIDEO
                }

                val itemExt = when {
                    isImage -> options.targetContainer.ext.ifBlank { "jpg" }
                    isAudio -> options.targetContainer.ext.ifBlank { "mp3" }
                    else -> options.targetContainer.ext.ifBlank { "mp4" }
                }

                val formatDesc = when {
                    isImage -> "Original Image (${itemExt.uppercase()})"
                    isAudio -> "Audio (${itemExt.uppercase()} - ${options.audioBitrate ?: 320}kbps)"
                    else -> "${options.selectedFormat?.displayResolution ?: options.qualityLabel} (${itemExt.uppercase()})"
                }

                val formatId = when {
                    isImage -> "image_direct"
                    isAudio -> "bestaudio/best"
                    else -> options.selectedFormat?.formatId ?: "best"
                }

                val isMulti = collection.isMultiItem
                val calculatedBytes = item.fileSize 
                    ?: (if (!isMulti) (options.selectedFormat?.filesize ?: options.selectedFormat?.filesizeApprox) else null)
                    ?: 0L

                requests.add(
                    PlannedDownloadRequest(
                        sourceUrl = item.sourceUrl,
                        title = if (collection.isMultiItem && !item.title.startsWith(collection.title)) {
                            "${collection.title} - ${item.title}"
                        } else {
                            item.title.ifBlank { collection.title }
                        },
                        thumbnail = item.thumbnail.ifBlank { collection.thumbnail },
                        formatId = formatId,
                        formatDescription = formatDesc,
                        qualityLabel = when {
                            isImage -> "Original"
                            isAudio -> "Audio ${options.audioBitrate ?: 320}k"
                            else -> options.qualityLabel
                        },
                        totalBytes = calculatedBytes,
                        mediaType = itemMediaType,
                        targetContainer = itemExt,
                        audioBitrate = if (itemMediaType == MediaType.AUDIO) (options.audioBitrate ?: 320) else null,
                        embedSubs = if (isImage) false else options.embedSubtitles,
                        embedThumbnail = options.embedThumbnail,
                        isPlaylist = isMulti,
                        playlistIndex = if (isMulti) taskSequence else 0,
                        playlistTotal = if (isMulti) totalSelected else 0
                    )
                )
                taskSequence++
            }
        }

        return DownloadPlan(requests)
    }
}
