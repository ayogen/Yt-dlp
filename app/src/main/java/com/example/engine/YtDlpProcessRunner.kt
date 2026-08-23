package com.example.engine

import com.example.data.model.FormatInfo
import com.example.data.model.MediaMetadata
import com.example.data.model.MediaType
import com.example.data.model.PlaylistEntry
import com.example.data.model.SubtitleTrack
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

object YtDlpProcessRunner {
    fun cancelTaskProcess(taskId: String) {
        try {
            YoutubeDL.getInstance().destroyProcessById(taskId)
            AppLogger.i("YtDlpProcessRunner", "Process terminated for task $taskId", taskId)
        } catch (e: Exception) {
            AppLogger.w("YtDlpProcessRunner", "Error killing process for task $taskId: ${e.message}", taskId)
        }
    }

    suspend fun extractMetadataCli(
        binaryPath: String,
        url: String,
        cookiesPath: String? = null,
        customArgs: String = "",
        traceId: String? = null
    ): Result<MediaMetadata> {
        return extractInfoDto(binaryPath, url, cookiesPath, customArgs, traceId).map { dto ->
            com.example.extraction.YtDlpMetadataMapper.mapToMediaMetadata(dto, url)
        }
    }

    suspend fun extractInfoDto(
        binaryPath: String,
        url: String,
        cookiesPath: String? = null,
        customArgs: String = "",
        traceId: String? = null
    ): Result<com.example.extraction.model.YtDlpInfoDto> {
        val effectiveTraceId = traceId ?: MediaExtractionTracer.currentSessionFlow.value?.traceId
        val opId = if (effectiveTraceId != null) {
            MediaExtractionTracer.startOperation(
                traceId = effectiveTraceId,
                component = "YtDlpProcessRunner",
                stage = "YTDLP_CLI_EXTRACTION",
                name = "extractInfoDto",
                details = mapOf(
                    "url" to url,
                    "cookiesConfigured" to (!cookiesPath.isNullOrBlank()).toString(),
                    "customArgs" to AppLogger.sanitize(customArgs)
                )
            )
        } else null

        val processId = "meta_${System.currentTimeMillis()}_${(1000..9999).random()}"
        return try {
            val request = YoutubeDLRequest(url)
            request.addOption("--dump-single-json")
            request.addOption("--no-warnings")
            request.addOption("--flat-playlist")
            request.addOption("--socket-timeout", "15")

            if (!cookiesPath.isNullOrBlank() && File(cookiesPath).exists()) {
                request.addOption("--cookies", cookiesPath)
            }
            if (customArgs.isNotBlank()) {
                val parts = customArgs.split("\\s+".toRegex()).filter { it.isNotBlank() }
                var i = 0
                while (i < parts.size) {
                    val opt = parts[i]
                    if (opt.startsWith("-") && i + 1 < parts.size && !parts[i + 1].startsWith("-")) {
                        request.addOption(opt, parts[i + 1])
                        i += 2
                    } else {
                        request.addOption(opt)
                        i++
                    }
                }
            }

            AppLogger.d("YtDlpProcessRunner", "Executing metadata request for $url [processId=$processId]")
            if (effectiveTraceId != null) {
                MediaExtractionTracer.logEvent(
                    traceId = effectiveTraceId,
                    opId = opId,
                    component = "YtDlpProcessRunner",
                    stage = "YTDLP_CLI_EXTRACTION",
                    event = "PROCESS_START",
                    level = TraceLevel.DEBUG,
                    input = "processId=$processId",
                    details = mapOf("processId" to processId)
                )
            }

            val response = executeRequestCancellable(request, processId)
            val stdout = response.out

            if (stdout.isNullOrBlank()) {
                AppLogger.e("YtDlpProcessRunner", "Metadata extraction failed: empty output")
                if (effectiveTraceId != null && opId != null) {
                    MediaExtractionTracer.endOperation(
                        traceId = effectiveTraceId,
                        opId = opId,
                        error = Exception("yt-dlp returned empty metadata output"),
                        decision = "EMPTY_OUTPUT",
                        reason = "Empty stdout from yt-dlp"
                    )
                }
                return Result.failure(Exception("yt-dlp returned empty metadata output"))
            }

            if (effectiveTraceId != null) {
                MediaExtractionTracer.recordYtDlpDump(effectiveTraceId, stdout)
            }

            val json = JSONObject(stdout)

            // TEMPORARY DIAGNOSTIC INSTRUMENTATION: Raw yt-dlp filesize exposure
            val rootFilesize = if (json.has("filesize") && !json.isNull("filesize")) json.opt("filesize").toString() else "NULL/ABSENT"
            val rootFilesizeApprox = if (json.has("filesize_approx") && !json.isNull("filesize_approx")) json.opt("filesize_approx").toString() else "NULL/ABSENT"
            val rootDuration = if (json.has("duration") && !json.isNull("duration")) json.opt("duration").toString() else "NULL/ABSENT"
            AppLogger.i("YtDlpProcessRunner", "RAW_YTDLP_ROOT_SIZE | url=$url | filesize=$rootFilesize | filesize_approx=$rootFilesizeApprox | duration=$rootDuration")

            val formatsArray = json.optJSONArray("formats")
            if (formatsArray != null) {
                for (i in 0 until formatsArray.length()) {
                    val fmt = formatsArray.optJSONObject(i) ?: continue
                    val fId = if (fmt.has("format_id") && !fmt.isNull("format_id")) fmt.opt("format_id").toString() else "NULL/ABSENT"
                    val fFormat = if (fmt.has("format") && !fmt.isNull("format")) fmt.opt("format").toString() else "NULL/ABSENT"
                    val fExt = if (fmt.has("ext") && !fmt.isNull("ext")) fmt.opt("ext").toString() else "NULL/ABSENT"
                    val fWidth = if (fmt.has("width") && !fmt.isNull("width")) fmt.opt("width").toString() else "NULL/ABSENT"
                    val fHeight = if (fmt.has("height") && !fmt.isNull("height")) fmt.opt("height").toString() else "NULL/ABSENT"
                    val fFps = if (fmt.has("fps") && !fmt.isNull("fps")) fmt.opt("fps").toString() else "NULL/ABSENT"
                    val fFilesize = if (fmt.has("filesize") && !fmt.isNull("filesize")) fmt.opt("filesize").toString() else "NULL/ABSENT"
                    val fFilesizeApprox = if (fmt.has("filesize_approx") && !fmt.isNull("filesize_approx")) fmt.opt("filesize_approx").toString() else "NULL/ABSENT"
                    val fTbr = if (fmt.has("tbr") && !fmt.isNull("tbr")) fmt.opt("tbr").toString() else "NULL/ABSENT"
                    val fDuration = if (fmt.has("duration") && !fmt.isNull("duration")) fmt.opt("duration").toString() else "NULL/ABSENT"
                    val fProtocol = if (fmt.has("protocol") && !fmt.isNull("protocol")) fmt.opt("protocol").toString() else "NULL/ABSENT"

                    AppLogger.i(
                        "YtDlpProcessRunner",
                        "RAW_YTDLP_FORMAT_SIZE | format_id=$fId | format=$fFormat | ext=$fExt | width=$fWidth | height=$fHeight | fps=$fFps | filesize=$fFilesize | filesize_approx=$fFilesizeApprox | tbr=$fTbr | duration=$fDuration | protocol=$fProtocol"
                    )
                }
            } else {
                AppLogger.i("YtDlpProcessRunner", "RAW_YTDLP_FORMAT_SIZE | formats=NULL/ABSENT")
            }

            val isPlaylistDetected = json.optString("_type") == "playlist" || (json.has("entries") && !json.isNull("entries"))
            val dto = com.example.extraction.YtDlpJsonParser.parse(json, url)

            if (isPlaylistDetected && dto.entries.isEmpty()) {
                AppLogger.e("YtDlpProcessRunner", "Playlist contains no accessible videos or is private")
                if (effectiveTraceId != null && opId != null) {
                    MediaExtractionTracer.endOperation(
                        traceId = effectiveTraceId,
                        opId = opId,
                        error = Exception("Playlist contains no accessible videos or is private"),
                        decision = "EMPTY_PLAYLIST",
                        reason = "Playlist contains no accessible videos or is private"
                    )
                }
                return Result.failure(Exception("Playlist contains no accessible videos or is private"))
            }

            if (effectiveTraceId != null && opId != null) {
                MediaExtractionTracer.endOperation(
                    traceId = effectiveTraceId,
                    opId = opId,
                    result = "${dto.title} (formats=${dto.formats.size}, entries=${dto.entries.size})",
                    decision = "METADATA_PARSED",
                    reason = "Successfully extracted JSON from yt-dlp",
                    details = mapOf(
                        "title" to dto.title,
                        "extractor" to (dto.extractor ?: "generic"),
                        "formatsCount" to dto.formats.size.toString(),
                        "isPlaylist" to dto.isPlaylist.toString(),
                        "entriesCount" to dto.entries.size.toString()
                    )
                )
            }

            Result.success(dto)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                try {
                    YoutubeDL.getInstance().destroyProcessById(processId)
                } catch (_: Exception) {}
                if (effectiveTraceId != null && opId != null) {
                    MediaExtractionTracer.endOperation(
                        traceId = effectiveTraceId,
                        opId = opId,
                        error = e,
                        decision = "CANCELLED",
                        reason = "Extraction cancelled by coroutine"
                    )
                }
                throw e
            }
            val msg = e.message ?: e.javaClass.simpleName
            AppLogger.e("YtDlpProcessRunner", "CLI Execution error: $msg")
            if (effectiveTraceId != null && opId != null) {
                MediaExtractionTracer.endOperation(
                    traceId = effectiveTraceId,
                    opId = opId,
                    error = e,
                    decision = "EXECUTION_ERROR",
                    reason = msg
                )
            }
            Result.failure(e)
        }
    }

    /**
     * Executes a YoutubeDLRequest in a background thread while registering an invokeOnCancellation
     * handler that immediately invokes YoutubeDL.destroyProcessById(processId) if the coroutine is cancelled
     * or timed out while execute() is blocking.
     */
    private suspend fun executeRequestCancellable(
        request: YoutubeDLRequest,
        processId: String
    ): com.yausername.youtubedl_android.YoutubeDLResponse = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation {
            try {
                AppLogger.i("YtDlpProcessRunner", "Coroutine cancelled: destroying yt-dlp process $processId")
                YoutubeDL.getInstance().destroyProcessById(processId)
            } catch (e: Exception) {
                AppLogger.w("YtDlpProcessRunner", "Error in invokeOnCancellation destroyProcessById($processId): ${e.message}")
            }
        }

        // Run the blocking execute on IO dispatcher
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            Thread(r, "yt-dlp-exec-$processId").apply { isDaemon = true }
        }

        executor.execute {
            try {
                val resp = YoutubeDL.getInstance().execute(request, processId)
                if (continuation.isActive) {
                    continuation.resume(resp) {}
                }
            } catch (e: Throwable) {
                if (continuation.isActive) {
                    continuation.resumeWith(Result.failure(e))
                }
            } finally {
                executor.shutdown()
            }
        }
    }

    suspend fun runDownloadCli(
        taskId: String,
        binaryPath: String,
        url: String,
        mediaType: MediaType,
        formatSpec: String,
        targetContainer: String,
        audioBitrate: Int?,
        embedSubs: Boolean,
        embedThumbnail: Boolean,
        outputTemplate: String,
        ffmpegPath: String?,
        cookiesPath: String?,
        customArgs: String,
        onProgress: (progress: Float, downloaded: Long, total: Long, speed: Double, eta: Long) -> Unit,
        isCancelled: () -> Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = YoutubeDLRequest(url)
            request.addOption("--newline")
            request.addOption("--no-playlist")

            // Audio extraction vs Video muxing configuration
            if (mediaType == MediaType.AUDIO) {
                request.addOption("-f", "bestaudio/best")
                request.addOption("-x")
                val cleanAudioFormat = when (targetContainer.lowercase()) {
                    "mp3" -> "mp3"
                    "m4a", "aac" -> "m4a"
                    "flac" -> "flac"
                    "opus", "ogg" -> "opus"
                    "wav" -> "wav"
                    else -> "mp3"
                }
                request.addOption("--audio-format", cleanAudioFormat)
                request.addOption("--audio-quality", "${audioBitrate ?: 320}k")
            } else {
                request.addOption("-f", formatSpec)
                val cleanVideoContainer = when (targetContainer.lowercase()) {
                    "mp4" -> "mp4"
                    "mkv" -> "mkv"
                    "webm" -> "webm"
                    else -> "mp4"
                }
                request.addOption("--merge-output-format", cleanVideoContainer)
            }

            // Output path
            request.addOption("-o", outputTemplate)

            // Subtitle & Thumbnail embedding
            if (embedSubs) {
                request.addOption("--embed-subs")
                request.addOption("--write-subs")
                request.addOption("--sub-langs", "all")
            }
            if (embedThumbnail) {
                request.addOption("--embed-thumbnail")
            }

            // Cookies
            if (!cookiesPath.isNullOrBlank() && File(cookiesPath).exists()) {
                request.addOption("--cookies", cookiesPath)
            }

            // Custom user arguments
            if (customArgs.isNotBlank()) {
                val parts = customArgs.split("\\s+".toRegex()).filter { it.isNotBlank() }
                var i = 0
                while (i < parts.size) {
                    val opt = parts[i]
                    if (opt.startsWith("-") && i + 1 < parts.size && !parts[i + 1].startsWith("-")) {
                        request.addOption(opt, parts[i + 1])
                        i += 2
                    } else {
                        request.addOption(opt)
                        i++
                    }
                }
            }

            AppLogger.i("YtDlpProcessRunner", "Starting yt-dlp download for $url", taskId)

            var downloadedFile: String = outputTemplate
            var lastSpeed = 0.0
            var lastDownloaded = 0L
            var lastTotal = 0L
            var lastEta = 0L

            val response = try {
                YoutubeDL.getInstance().execute(request, taskId) { progress, etaInSeconds, line ->
                    if (isCancelled()) {
                        YoutubeDL.getInstance().destroyProcessById(taskId)
                    }

                    if (line.contains("[download] Destination:")) {
                        downloadedFile = line.substringAfter("[download] Destination:").trim()
                    } else if (line.contains("[Merger] Merging formats into")) {
                        val merged = line.substringAfter("[Merger] Merging formats into").replace("\"", "").trim()
                        if (merged.isNotBlank()) downloadedFile = merged
                    } else if (line.contains("[ExtractAudio] Destination:")) {
                        val audioDest = line.substringAfter("[ExtractAudio] Destination:").trim()
                        if (audioDest.isNotBlank()) downloadedFile = audioDest
                    }

                    val parsedSpeed = YtDlpOutputParser.parseSpeed(line)
                    if (parsedSpeed > 0.0) {
                        lastSpeed = parsedSpeed
                    }

                    val (dBytes, tBytes) = YtDlpOutputParser.parseSize(line, progress)
                    if (tBytes > 0L) {
                        lastTotal = tBytes
                    }
                    if (dBytes > 0L) {
                        lastDownloaded = dBytes
                    } else if (progress > 0f && lastTotal > 0L) {
                        lastDownloaded = ((progress / 100.0) * lastTotal).toLong()
                    }

                    val parsedEta = if (etaInSeconds > 0) etaInSeconds else YtDlpOutputParser.parseEta(line)
                    if (parsedEta > 0) {
                        lastEta = parsedEta
                    }

                    onProgress(progress, lastDownloaded, lastTotal, lastSpeed, lastEta)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    try {
                        YoutubeDL.getInstance().destroyProcessById(taskId)
                    } catch (_: Exception) {}
                    throw e
                }
                throw e
            }

            // Check if downloaded file exists
            val targetFile = File(downloadedFile)
            if (targetFile.exists() && targetFile.length() > 0) {
                Result.success(targetFile.absolutePath)
            } else {
                val fallbackFile = File(outputTemplate)
                if (fallbackFile.exists() && fallbackFile.length() > 0) {
                    Result.success(fallbackFile.absolutePath)
                } else {
                    val parentDir = fallbackFile.parentFile
                    val baseName = fallbackFile.nameWithoutExtension
                    val matched = parentDir?.listFiles()?.firstOrNull { it.name.startsWith(baseName) && it.length() > 0 }
                    if (matched != null) {
                        Result.success(matched.absolutePath)
                    } else {
                        Result.failure(Exception("yt-dlp finished but output media file was not found on disk."))
                    }
                }
            }
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            AppLogger.e("YtDlpProcessRunner", "Process runner exception: $msg", taskId)
            Result.failure(e)
        }
    }

    private fun cleanString(json: JSONObject, key: String, fallback: String = ""): String {
        if (json.isNull(key) || !json.has(key)) return fallback
        val v = json.optString(key, fallback).trim()
        return if (v.isEmpty() || v == "null") fallback else v
    }

    private fun cleanLong(json: JSONObject, key: String): Long? {
        if (!json.has(key) || json.isNull(key)) return null
        return when (val v = json.opt(key)) {
            is Number -> {
                val l = v.toLong()
                if (l > 0) l else null
            }
            is String -> {
                v.trim().toDoubleOrNull()?.toLong()?.takeIf { it > 0 }
            }
            else -> null
        }
    }

    private fun cleanDouble(json: JSONObject, key: String): Double? {
        if (!json.has(key) || json.isNull(key)) return null
        return when (val v = json.opt(key)) {
            is Number -> {
                val d = v.toDouble()
                if (d > 0.0) d else null
            }
            is String -> {
                v.trim().toDoubleOrNull()?.takeIf { it > 0.0 }
            }
            else -> null
        }
    }

    private fun cleanInt(json: JSONObject, key: String): Int? {
        if (!json.has(key) || json.isNull(key)) return null
        return when (val v = json.opt(key)) {
            is Number -> {
                val i = v.toInt()
                if (i > 0) i else null
            }
            is String -> {
                v.trim().toDoubleOrNull()?.toInt()?.takeIf { it > 0 }
            }
            else -> null
        }
    }

    internal fun parseYtDlpJson(json: JSONObject, originalUrl: String): MediaMetadata {
        val dto = com.example.extraction.YtDlpJsonParser.parse(json, originalUrl)
        return com.example.extraction.YtDlpMetadataMapper.mapToMediaMetadata(dto, originalUrl)
    }
}

object YtDlpOutputParser {
    private val speedRegex = """(?:at|@)\s+~?([0-9.]+)\s*([KMGTkmgt]i?B)/s""".toRegex()
    private val sizeRegex = """([0-9.]+)\s*([KMGTkmgt]i?B)\s+of\s+~?([0-9.]+)\s*([KMGTkmgt]i?B)""".toRegex()
    private val totalSizeRegex = """of\s+~?([0-9.]+)\s*([KMGTkmgt]i?B)""".toRegex()
    private val etaRegex = """ETA\s+(\d{1,2}:\d{2}(?::\d{2})?)""".toRegex()

    fun parseSpeed(line: String): Double {
        val match = speedRegex.find(line) ?: return 0.0
        val value = match.groupValues[1].toDoubleOrNull() ?: return 0.0
        val unit = match.groupValues[2].uppercase()
        val multiplier = when {
            unit.startsWith("G") -> 1024.0 * 1024.0 * 1024.0
            unit.startsWith("M") -> 1024.0 * 1024.0
            unit.startsWith("K") -> 1024.0
            else -> 1.0
        }
        return value * multiplier
    }

    fun parseSize(line: String, currentProgress: Float): Pair<Long, Long> {
        val match = sizeRegex.find(line)
        if (match != null) {
            val downloadedVal = match.groupValues[1].toDoubleOrNull() ?: 0.0
            val downloadedUnit = match.groupValues[2].uppercase()
            val totalVal = match.groupValues[3].toDoubleOrNull() ?: 0.0
            val totalUnit = match.groupValues[4].uppercase()

            val downloadedBytes = toBytes(downloadedVal, downloadedUnit)
            val totalBytes = toBytes(totalVal, totalUnit)
            return Pair(downloadedBytes, totalBytes)
        }

        val totalMatch = totalSizeRegex.find(line)
        if (totalMatch != null) {
            val totalVal = totalMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            val totalUnit = totalMatch.groupValues[2].uppercase()
            val totalBytes = toBytes(totalVal, totalUnit)
            val downloadedBytes = if (currentProgress > 0f) {
                ((currentProgress / 100.0) * totalBytes).toLong()
            } else {
                0L
            }
            return Pair(downloadedBytes, totalBytes)
        }

        return Pair(0L, 0L)
    }

    fun parseEta(line: String): Long {
        val match = etaRegex.find(line) ?: return 0L
        val parts = match.groupValues[1].split(":").mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0]
            else -> 0L
        }
    }

    private fun toBytes(value: Double, unit: String): Long {
        val multiplier = when {
            unit.startsWith("G") -> 1024.0 * 1024.0 * 1024.0
            unit.startsWith("M") -> 1024.0 * 1024.0
            unit.startsWith("K") -> 1024.0
            else -> 1.0
        }
        return (value * multiplier).toLong()
    }
}
