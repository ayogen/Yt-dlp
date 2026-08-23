package com.example.extraction

import android.content.Context
import com.example.data.model.AppSettings
import com.example.engine.YtDlpBinaryManager
import com.example.engine.YtDlpProcessRunner
import com.example.routing.RoutingDecision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class YtDlpStrategy(private val context: Context? = null) : ExtractionStrategy {

    override val name: String = "YTDLP"

    override suspend fun canHandle(url: String, decision: RoutingDecision): Boolean {
        return decision.allowYtDlp
    }

    override suspend fun extract(
        url: String,
        decision: RoutingDecision,
        settings: AppSettings?
    ): ExtractionEvidence = withContext(Dispatchers.IO) {
        if (!decision.allowYtDlp) {
            return@withContext ExtractionEvidence()
        }

        try {
            if (context != null) {
                YtDlpBinaryManager.ensureInitialized(context)
            }

            val cliResult = YtDlpProcessRunner.extractMetadataCli(
                binaryPath = "",
                url = url,
                cookiesPath = settings?.cookiesFilePath?.ifBlank { null },
                customArgs = settings?.customYtDlpArgs ?: ""
            )

            if (cliResult.isSuccess) {
                val metadata = cliResult.getOrThrow()
                // Convert MediaMetadata to DTO / candidates
                val formatsDto = metadata.formats.map { f ->
                    com.example.extraction.model.YtDlpFormatDto(
                        formatId = f.formatId,
                        ext = f.ext,
                        resolution = f.resolution,
                        width = f.width,
                        height = f.height,
                        fps = f.fps,
                        vcodec = f.vcodec,
                        acodec = f.acodec,
                        tbr = f.tbr,
                        vbr = f.vbr,
                        abr = f.abr,
                        filesize = f.filesize,
                        filesizeApprox = f.filesizeApprox,
                        formatNote = f.formatNote,
                        url = f.url,
                        protocol = f.protocol
                    )
                }

                val entriesDto = metadata.playlistEntries.map { e ->
                    com.example.extraction.model.YtDlpPlaylistEntryDto(
                        id = e.id,
                        title = e.title,
                        url = e.url,
                        duration = e.durationSeconds,
                        thumbnail = e.thumbnail,
                        uploader = e.uploader
                    )
                }

                val infoDto = com.example.extraction.model.YtDlpInfoDto(
                    id = metadata.id,
                    title = metadata.title,
                    webpageUrl = metadata.webpageUrl,
                    uploader = metadata.uploader,
                    channel = metadata.channel,
                    duration = metadata.durationSeconds,
                    viewCount = metadata.viewCount,
                    likeCount = metadata.likeCount,
                    uploadDate = metadata.uploadDate,
                    description = metadata.description,
                    thumbnail = metadata.thumbnail,
                    type = if (metadata.isPlaylist) "playlist" else null,
                    extractor = metadata.extractorName,
                    formats = formatsDto,
                    entries = entriesDto
                )

                val candidates = CandidateNormalizer.fromYtDlpInfo(infoDto, url, decision.intent)
                ExtractionEvidence(candidates = candidates)
            } else {
                val err = cliResult.exceptionOrNull()?.message ?: "yt-dlp extraction failed"
                ExtractionEvidence(
                    warnings = listOf("yt-dlp extraction failed: $err"),
                    failedStrategies = listOf(name)
                )
            }
        } catch (e: Exception) {
            ExtractionEvidence(
                warnings = listOf("yt-dlp extraction exception: ${e.message}"),
                failedStrategies = listOf(name)
            )
        }
    }
}
