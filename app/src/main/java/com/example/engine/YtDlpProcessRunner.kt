package com.example.engine

import com.example.data.model.FormatInfo
import com.example.data.model.MediaMetadata
import com.example.data.model.MediaType
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
        customArgs: String = ""
    ): Result<MediaMetadata> = withContext(Dispatchers.IO) {
        try {
            val request = YoutubeDLRequest(url)
            request.addOption("--dump-single-json")
            request.addOption("--no-warnings")
            request.addOption("--flat-playlist")

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

            AppLogger.d("YtDlpProcessRunner", "Executing metadata request for $url")
            val response = YoutubeDL.getInstance().execute(request)
            val stdout = response.out

            if (stdout.isNullOrBlank()) {
                AppLogger.e("YtDlpProcessRunner", "Metadata extraction failed: empty output")
                return@withContext Result.failure(Exception("yt-dlp returned empty metadata output"))
            }

            val json = JSONObject(stdout)
            val metadata = parseYtDlpJson(json, url)
            Result.success(metadata)
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            AppLogger.e("YtDlpProcessRunner", "CLI Execution error: $msg")
            Result.failure(e)
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

            val response = YoutubeDL.getInstance().execute(request, taskId) { progress, etaInSeconds, line ->
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

                onProgress(progress, 0L, 0L, 0.0, etaInSeconds)
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

    private fun parseYtDlpJson(json: JSONObject, originalUrl: String): MediaMetadata {
        val id = json.optString("id", Math.abs(originalUrl.hashCode()).toString())
        val title = json.optString("title", "Untitled Media")
        val uploader = json.optString("uploader", json.optString("channel", "Unknown Uploader"))
        val duration = json.optLong("duration", 0L)
        val viewCount = if (json.has("view_count") && !json.isNull("view_count")) json.optLong("view_count") else null
        val likeCount = if (json.has("like_count") && !json.isNull("like_count")) json.optLong("like_count") else null
        val uploadDate = json.optString("upload_date", "")
        val description = json.optString("description", "")
        val thumbnail = json.optString("thumbnail", "")

        val formatsList = mutableListOf<FormatInfo>()
        val formatsArray = json.optJSONArray("formats")
        if (formatsArray != null) {
            for (i in 0 until formatsArray.length()) {
                val f = formatsArray.optJSONObject(i) ?: continue
                val formatId = f.optString("format_id", "$i")
                val ext = f.optString("ext", "mp4")
                val width = if (f.has("width") && !f.isNull("width")) f.optInt("width") else null
                val height = if (f.has("height") && !f.isNull("height")) f.optInt("height") else null
                val fps = if (f.has("fps") && !f.isNull("fps")) f.optDouble("fps") else null
                val vcodec = f.optString("vcodec", "none")
                val acodec = f.optString("acodec", "none")
                val tbr = if (f.has("tbr") && !f.isNull("tbr")) f.optDouble("tbr") else null
                val vbr = if (f.has("vbr") && !f.isNull("vbr")) f.optDouble("vbr") else null
                val abr = if (f.has("abr") && !f.isNull("abr")) f.optDouble("abr") else null
                val filesize = if (f.has("filesize") && !f.isNull("filesize") && f.optLong("filesize") > 0) f.optLong("filesize") else null
                val filesizeApprox = if (f.has("filesize_approx") && !f.isNull("filesize_approx") && f.optLong("filesize_approx") > 0) f.optLong("filesize_approx") else null
                val formatNote = f.optString("format_note", "")
                val resolution = f.optString("resolution", if (height != null && height > 0) "${height}p" else "")

                formatsList.add(
                    FormatInfo(
                        formatId = formatId,
                        ext = ext,
                        resolution = resolution,
                        width = width,
                        height = height,
                        fps = fps,
                        vcodec = vcodec,
                        acodec = acodec,
                        tbr = tbr,
                        vbr = vbr,
                        abr = abr,
                        filesize = filesize,
                        filesizeApprox = filesizeApprox,
                        formatNote = formatNote,
                        url = f.optString("url", "")
                    )
                )
            }
        }

        val subtitlesList = mutableListOf<SubtitleTrack>()
        val subsObj = json.optJSONObject("subtitles")
        if (subsObj != null) {
            val keys = subsObj.keys()
            while (keys.hasNext()) {
                val lang = keys.next()
                subtitlesList.add(
                    SubtitleTrack(
                        language = lang,
                        name = lang.uppercase(),
                        isAutoGenerated = false
                    )
                )
            }
        }

        return MediaMetadata(
            id = id,
            title = title,
            webpageUrl = originalUrl,
            uploader = uploader,
            durationSeconds = duration,
            viewCount = viewCount,
            likeCount = likeCount,
            uploadDate = uploadDate,
            description = description,
            thumbnail = thumbnail,
            formats = formatsList,
            subtitles = subtitlesList,
            extractorName = json.optString("extractor", "yt-dlp")
        )
    }
}
