package com.example.download

import com.example.core.model.CanonicalMediaResult
import com.example.data.model.DownloadStatus
import com.example.data.model.DownloadTaskEntity
import com.example.data.model.FormatInfo
import com.example.data.model.MediaMetadata
import com.example.data.model.MediaType
import com.example.data.model.OutputContainer
import java.util.UUID

object DownloadCoordinator {

    fun createDownloadTask(
        metadata: MediaMetadata,
        selectedFormat: FormatInfo?,
        mediaType: MediaType,
        targetContainer: OutputContainer,
        audioBitrate: Int?,
        embedSubs: Boolean,
        embedThumbnail: Boolean,
        qualityLabel: String = "Best"
    ): DownloadTaskEntity {
        val formatDesc = if (mediaType == MediaType.AUDIO) {
            "Audio (${targetContainer.ext.uppercase()} - ${audioBitrate ?: 320}kbps)"
        } else {
            "${selectedFormat?.displayResolution ?: qualityLabel} (${targetContainer.ext.uppercase()})"
        }

        return DownloadTaskEntity(
            id = UUID.randomUUID().toString(),
            url = metadata.webpageUrl,
            title = metadata.title,
            thumbnail = metadata.thumbnail,
            status = DownloadStatus.QUEUED,
            formatId = selectedFormat?.formatId ?: "best",
            formatDescription = formatDesc,
            qualityLabel = qualityLabel,
            totalBytes = selectedFormat?.filesize ?: selectedFormat?.filesizeApprox ?: 0L,
            mediaType = mediaType,
            audioBitrate = audioBitrate,
            targetContainer = targetContainer.ext,
            embedSubs = embedSubs,
            embedThumbnail = embedThumbnail
        )
    }

    fun createPlaylistDownloadTasks(
        metadata: MediaMetadata,
        selectedIndices: Set<Int>,
        selectedFormat: FormatInfo?,
        mediaType: MediaType,
        targetContainer: OutputContainer,
        audioBitrate: Int?,
        embedSubs: Boolean,
        embedThumbnail: Boolean,
        qualityLabel: String = "Best"
    ): List<DownloadTaskEntity> {
        val totalSelected = selectedIndices.size
        var idx = 1
        val tasks = mutableListOf<DownloadTaskEntity>()

        selectedIndices.sorted().forEach { itemIndex ->
            val entry = metadata.playlistEntries.getOrNull(itemIndex)
            if (entry != null) {
                val task = DownloadTaskEntity(
                    id = UUID.randomUUID().toString(),
                    url = entry.url,
                    title = entry.title,
                    thumbnail = entry.thumbnail.ifBlank { metadata.thumbnail },
                    status = DownloadStatus.QUEUED,
                    formatId = selectedFormat?.formatId ?: "best",
                    formatDescription = selectedFormat?.displayResolution ?: "Best",
                    qualityLabel = qualityLabel,
                    mediaType = mediaType,
                    isPlaylist = true,
                    playlistIndex = idx,
                    playlistTotal = totalSelected,
                    audioBitrate = audioBitrate,
                    targetContainer = targetContainer.ext,
                    embedSubs = embedSubs,
                    embedThumbnail = embedThumbnail
                )
                tasks.add(task)
                idx++
            }
        }
        return tasks
    }
}
