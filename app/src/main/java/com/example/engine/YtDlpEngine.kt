package com.example.engine

import android.content.Context
import com.example.data.model.AppSettings
import com.example.data.model.DownloadTaskEntity
import com.example.data.model.MediaMetadata
import com.example.data.model.MediaType
import com.example.download.StorageUtils
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

    private val extractionEngine = MediaExtractionEngine(context)

    suspend fun analyzeUrl(url: String, settings: AppSettings): Result<MediaMetadata> = withContext(Dispatchers.IO) {
        val resolvedUrl = UrlNormalizer.resolveCanonicalUrl(url)
        AppLogger.i("YtDlpEngine", "Starting universal media analysis for: $resolvedUrl (original: $url)")

        // Ensure yt-dlp runtime is initialized in background
        YtDlpBinaryManager.ensureInitialized(context)

        val cookiesFile = if (settings.cookiesFilePath.isNotBlank()) File(settings.cookiesFilePath) else null
        val userAgent = if (settings.customUserAgent.isNotBlank()) settings.customUserAgent else null
        val proxyUrl = if (settings.proxyUrl.isNotBlank()) settings.proxyUrl else null

        extractionEngine.extractMedia(
            url = resolvedUrl,
            cookiesFile = cookiesFile,
            userAgent = userAgent,
            proxyUrl = proxyUrl,
            geoBypass = settings.geoBypass
        )
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

        // Ensure runtimes are initialized
        YtDlpBinaryManager.ensureInitialized(context)
        FFmpegBinaryManager.ensureInitialized(context)

        val isYtDlpReady = YtDlpBinaryManager.isReady(context)
        val ffmpegStatus = FFmpegDetector.detect(context)

        // Determine target file extension
        val resolvedExt = if (task.mediaType == MediaType.AUDIO) {
            task.targetContainer.ifBlank { "mp3" }
        } else {
            task.targetContainer.ifBlank { "mp4" }
        }

        // Format specification
        val formatArg = buildFormatArgument(task)

        // Check if FFmpeg is strictly required
        val requiresFFmpeg = task.mediaType == MediaType.AUDIO ||
                formatArg.contains("+") ||
                task.formatId in listOf("1080p", "1440p", "2160p", "720p", "480p", "360p", "best") ||
                task.embedSubs ||
                task.embedThumbnail

        if (requiresFFmpeg && !ffmpegStatus.isAvailable) {
            val msg = "FFmpeg is required for ${task.formatDescription} to merge streams or convert audio, but is not available on this device. Please install FFmpeg from Settings."
            AppLogger.e("YtDlpEngine", msg, taskId)
            return@withContext Result.failure(IllegalStateException(msg))
        }

        val filename = FilenameFormatter.format(
            template = settings.filenameTemplate,
            title = task.title,
            uploader = "MediaCreator",
            id = task.id,
            ext = resolvedExt
        )

        // If SAF is configured, download to staging first, then export to SAF subfolder
        val isSafConfigured = settings.downloadLocationUri.isNotBlank() && StorageUtils.isSafUriWritable(context, settings.downloadLocationUri)
        val stagingDir = if (isSafConfigured) {
            StorageUtils.getStagingDirectory(context)
        } else {
            val subfolder = StorageUtils.getSubfolderForMediaType(task.mediaType, resolvedExt)
            StorageUtils.getFallbackDownloadDirectory(context, subfolder)
        }

        val stagingFile = File(stagingDir, filename)

        // Helper to validate and finalize downloaded file
        fun finalizeDownload(completedFile: File): Result<String> {
            val validation = StorageUtils.validateMediaFile(completedFile, task.mediaType, resolvedExt)
            if (validation.isFailure) {
                val err = validation.exceptionOrNull() ?: Exception("Media validation failed")
                AppLogger.e("YtDlpEngine", "Downloaded file validation failed: ${err.message}", taskId)
                try { completedFile.delete() } catch (e: Exception) {}
                return Result.failure(err)
            }

            return if (isSafConfigured) {
                StorageUtils.exportFileToSaf(
                    context = context,
                    sourceFile = completedFile,
                    treeUriString = settings.downloadLocationUri,
                    mediaType = task.mediaType,
                    customFilename = filename
                )
            } else {
                StorageUtils.scanMediaFile(context, completedFile)
                Result.success(completedFile.absolutePath)
            }
        }

        // If yt-dlp runtime is ready, use yt-dlp
        if (isYtDlpReady) {
            val outputTemplate = stagingFile.absolutePath

            var cliResult = YtDlpProcessRunner.runDownloadCli(
                taskId = taskId,
                binaryPath = "",
                url = task.url,
                mediaType = task.mediaType,
                formatSpec = formatArg,
                targetContainer = resolvedExt,
                audioBitrate = task.audioBitrate,
                embedSubs = task.embedSubs,
                embedThumbnail = task.embedThumbnail,
                outputTemplate = outputTemplate,
                ffmpegPath = if (ffmpegStatus.isAvailable) ffmpegStatus.binaryPath else null,
                cookiesPath = settings.cookiesFilePath.ifBlank { null },
                customArgs = settings.customYtDlpArgs,
                onProgress = onProgress,
                isCancelled = isCancelled
            )

            // If failed and thumbnail embedding was enabled, retry once without thumbnail embedding
            // to avoid failing an entire media download due to post-processing thumbnail errors or temp file issues
            if (cliResult.isFailure && task.embedThumbnail && !isCancelled()) {
                val failureMsg = cliResult.exceptionOrNull()?.message ?: ""
                AppLogger.w("YtDlpEngine", "Initial download attempt with thumbnail embedding failed ($failureMsg). Retrying cleanly without thumbnail embedding...", taskId)
                
                cliResult = YtDlpProcessRunner.runDownloadCli(
                    taskId = taskId,
                    binaryPath = "",
                    url = task.url,
                    mediaType = task.mediaType,
                    formatSpec = formatArg,
                    targetContainer = resolvedExt,
                    audioBitrate = task.audioBitrate,
                    embedSubs = task.embedSubs,
                    embedThumbnail = false,
                    outputTemplate = outputTemplate,
                    ffmpegPath = if (ffmpegStatus.isAvailable) ffmpegStatus.binaryPath else null,
                    cookiesPath = settings.cookiesFilePath.ifBlank { null },
                    customArgs = settings.customYtDlpArgs,
                    onProgress = onProgress,
                    isCancelled = isCancelled
                )
            }

            if (cliResult.isSuccess) {
                val producedPath = cliResult.getOrNull() ?: stagingFile.absolutePath
                val producedFile = File(producedPath)
                return@withContext finalizeDownload(producedFile)
            } else {
                val cliError = cliResult.exceptionOrNull() ?: Exception("yt-dlp execution failed")
                AppLogger.e("YtDlpEngine", "yt-dlp download failed: ${cliError.message}", taskId)
                return@withContext Result.failure(cliError)
            }
        }

        // If yt-dlp is not ready, check if direct HTTP media stream URL
        if (EmbeddedExtractorEngine.isDirectMediaUrl(task.url)) {
            val streamResult = EmbeddedExtractorEngine.downloadDirectStream(
                taskId = taskId,
                url = task.url,
                destinationFile = stagingFile,
                targetTotalBytes = task.totalBytes,
                onProgress = onProgress,
                isCancelled = isCancelled,
                isPaused = isPaused
            )

            if (streamResult.isSuccess) {
                val producedPath = streamResult.getOrNull() ?: stagingFile.absolutePath
                val producedFile = File(producedPath)
                return@withContext finalizeDownload(producedFile)
            } else {
                return@withContext streamResult
            }
        }

        return@withContext Result.failure(
            IllegalStateException("yt-dlp engine is not initialized. Please complete engine setup in Settings.")
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
                    title = "FFmpeg Processing Required",
                    reason = "FFmpeg is required to merge video and audio streams or convert audio formats.",
                    suggestedAction = "Go to Settings > FFmpeg Status and tap 'Install FFmpeg' to set up.",
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

    private fun buildFormatArgument(task: DownloadTaskEntity): String {
        return if (task.mediaType == MediaType.AUDIO) {
            "bestaudio/best"
        } else {
            when (task.formatId) {
                "2160p" -> "bestvideo[height<=2160]+bestaudio/best[height<=2160]/best"
                "1440p" -> "bestvideo[height<=1440]+bestaudio/best[height<=1440]/best"
                "1080p" -> "bestvideo[height<=1080]+bestaudio/best[height<=1080]/best"
                "720p" -> "bestvideo[height<=720]+bestaudio/best[height<=720]/best"
                "480p" -> "bestvideo[height<=480]+bestaudio/best[height<=480]/best"
                "360p" -> "bestvideo[height<=360]+bestaudio/best[height<=360]/best"
                "best" -> "bestvideo+bestaudio/best"
                else -> {
                    if (task.formatId.isNotBlank()) {
                        if (task.formatId.contains("+") || task.formatId.contains("/")) {
                            task.formatId
                        } else {
                            "${task.formatId}+bestaudio/${task.formatId}/best"
                        }
                    } else {
                        "bestvideo+bestaudio/best"
                    }
                }
            }
        }
    }
}
