package com.example.engine

import android.content.Context
import com.example.data.model.AppSettings
import com.example.data.model.DownloadStatus
import com.example.data.model.DownloadTaskEntity
import com.example.data.model.MediaMetadata
import com.example.data.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class EngineDiagnosticError(
    val title: String,
    val reason: String,
    val suggestedAction: String,
    val technicalDetails: String
)

class YtDlpEngine(private val context: Context) {

    suspend fun analyzeUrl(url: String, settings: AppSettings): Result<MediaMetadata> = withContext(Dispatchers.IO) {
        AppLogger.i("YtDlpEngine", "Starting URL analysis for: $url")
        val binaryFile = YtDlpBinaryManager.getBinaryFile(context)

        // Try CLI runner first if binary exists and can execute
        if (binaryFile.exists() && binaryFile.canExecute()) {
            val cliResult = YtDlpProcessRunner.extractMetadataCli(
                binaryPath = binaryFile.absolutePath,
                url = url,
                cookiesPath = settings.cookiesFilePath.ifBlank { null },
                customArgs = settings.customYtDlpArgs
            )
            if (cliResult.isSuccess) {
                AppLogger.i("YtDlpEngine", "Metadata extracted via CLI runner")
                return@withContext cliResult
            } else {
                AppLogger.w("YtDlpEngine", "CLI extraction failed, falling back to embedded extractor: ${cliResult.exceptionOrNull()?.message}")
            }
        }

        // Use embedded high-speed extractor
        val result = EmbeddedExtractorEngine.analyzeUrl(url)
        result
    }

    suspend fun executeDownload(
        task: DownloadTaskEntity,
        settings: AppSettings,
        onProgress: (progress: Float, downloaded: Long, total: Long, speed: Double, eta: Long) -> Unit,
        isCancelled: () -> Boolean,
        isPaused: () -> Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        val taskId = task.id
        AppLogger.i("YtDlpEngine", "Executing download for task: ${task.title} ($taskId)", taskId)

        val binaryFile = YtDlpBinaryManager.getBinaryFile(context)
        val ffmpegStatus = FFmpegDetector.detect(context)

        // Determine destination file
        val outputDir = File(context.filesDir, settings.downloadLocation.ifBlank { "downloads" })
        if (!outputDir.exists()) outputDir.mkdirs()

        val resolvedExt = if (task.mediaType == MediaType.AUDIO) {
            task.targetContainer.ifBlank { "mp3" }
        } else {
            task.targetContainer.ifBlank { "mp4" }
        }

        val filename = FilenameFormatter.format(
            template = settings.filenameTemplate,
            title = task.title,
            uploader = "MediaCreator",
            id = task.id,
            ext = resolvedExt
        )

        val outputFile = File(outputDir, filename)

        // If CLI binary is available and executable, run CLI
        if (binaryFile.exists() && binaryFile.canExecute()) {
            val formatArg = buildFormatArgument(task, settings)
            val outputTemplate = outputFile.absolutePath

            val cliResult = YtDlpProcessRunner.runDownloadCli(
                taskId = taskId,
                binaryPath = binaryFile.absolutePath,
                url = task.url,
                formatSpec = formatArg,
                outputTemplate = outputTemplate,
                ffmpegPath = if (ffmpegStatus.isAvailable) ffmpegStatus.ffmpegPath else null,
                cookiesPath = settings.cookiesFilePath.ifBlank { null },
                customArgs = settings.customYtDlpArgs,
                onProgress = onProgress,
                isCancelled = isCancelled
            )

            if (cliResult.isSuccess) {
                return@withContext cliResult
            } else {
                AppLogger.w("YtDlpEngine", "CLI download failed, falling back to embedded streaming downloader: ${cliResult.exceptionOrNull()?.message}", taskId)
            }
        }

        // Use embedded streaming downloader
        val estimatedSize = if (task.totalBytes > 0) task.totalBytes else 75_000_000L
        return@withContext EmbeddedExtractorEngine.downloadDirectStream(
            taskId = taskId,
            url = task.url,
            destinationFile = outputFile,
            targetTotalBytes = estimatedSize,
            onProgress = onProgress,
            isCancelled = isCancelled,
            isPaused = isPaused
        )
    }

    fun classifyError(e: Throwable): EngineDiagnosticError {
        val msg = e.message ?: "Unknown error"
        return when {
            msg.contains("Private video", ignoreCase = true) || msg.contains("authentication", ignoreCase = true) || msg.contains("403", ignoreCase = true) -> {
                EngineDiagnosticError(
                    title = "Authentication Required",
                    reason = "This media is private or requires authentication to access.",
                    suggestedAction = "Configure a valid cookies.txt file in Settings > Cookies.",
                    technicalDetails = msg
                )
            }
            msg.contains("geo", ignoreCase = true) || msg.contains("not available in your country", ignoreCase = true) -> {
                EngineDiagnosticError(
                    title = "Geographic Restriction",
                    reason = "This content is blocked in your current region.",
                    suggestedAction = "Connect via a VPN or proxy in a permitted country.",
                    technicalDetails = msg
                )
            }
            msg.contains("Unable to download webpage", ignoreCase = true) || msg.contains("UnknownHost", ignoreCase = true) || msg.contains("timeout", ignoreCase = true) -> {
                EngineDiagnosticError(
                    title = "Network Connection Issue",
                    reason = "Unable to connect to the target server.",
                    suggestedAction = "Check your internet connection and verify the website is accessible.",
                    technicalDetails = msg
                )
            }
            msg.contains("ffmpeg", ignoreCase = true) -> {
                EngineDiagnosticError(
                    title = "FFmpeg Post-Processing Required",
                    reason = "FFmpeg is required to merge separate video and audio streams.",
                    suggestedAction = "Check Settings > FFmpeg to verify availability, or select a pre-muxed container format.",
                    technicalDetails = msg
                )
            }
            msg.contains("Unsupported URL", ignoreCase = true) || msg.contains("Invalid URL", ignoreCase = true) -> {
                EngineDiagnosticError(
                    title = "Unsupported or Invalid URL",
                    reason = "The provided URL format is not recognized or extractor failed.",
                    suggestedAction = "Verify that the URL is a complete link (including https://).",
                    technicalDetails = msg
                )
            }
            else -> {
                EngineDiagnosticError(
                    title = "Download Encountered an Error",
                    reason = msg,
                    suggestedAction = "Retry the download or inspect technical logs in Settings.",
                    technicalDetails = msg
                )
            }
        }
    }

    private fun buildFormatArgument(task: DownloadTaskEntity, settings: AppSettings): String {
        return if (task.mediaType == MediaType.AUDIO) {
            "bestaudio/best"
        } else {
            when (task.formatId) {
                "2160p" -> "bestvideo[height<=2160]+bestaudio/best[height<=2160]"
                "1440p" -> "bestvideo[height<=1440]+bestaudio/best[height<=1440]"
                "1080p" -> "bestvideo[height<=1080]+bestaudio/best[height<=1080]"
                "720p" -> "bestvideo[height<=720]+bestaudio/best[height<=720]"
                "480p" -> "bestvideo[height<=480]+bestaudio/best[height<=480]"
                "360p" -> "bestvideo[height<=360]+bestaudio/best[height<=360]"
                else -> "bestvideo*+bestaudio/best"
            }
        }
    }
}
