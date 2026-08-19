package com.example.engine

import com.example.data.model.FormatInfo
import com.example.data.model.MediaMetadata
import com.example.data.model.MediaType
import com.example.data.model.SubtitleTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

object YtDlpProcessRunner {
    private val activeProcesses = ConcurrentHashMap<String, Process>()

    fun cancelTaskProcess(taskId: String) {
        activeProcesses[taskId]?.let { process ->
            try {
                process.destroyForcibly()
                AppLogger.i("YtDlpProcessRunner", "Process forcibly terminated for task $taskId", taskId)
            } catch (e: Exception) {
                AppLogger.w("YtDlpProcessRunner", "Error killing process for task $taskId: ${e.message}", taskId)
            } finally {
                activeProcesses.remove(taskId)
            }
        }
    }

    suspend fun extractMetadataCli(
        binaryPath: String,
        url: String,
        cookiesPath: String? = null,
        customArgs: String = ""
    ): Result<MediaMetadata> = withContext(Dispatchers.IO) {
        try {
            val args = mutableListOf(binaryPath, "--dump-single-json", "--no-warnings", "--flat-playlist")
            if (!cookiesPath.isNullOrBlank() && File(cookiesPath).exists()) {
                args.add("--cookies")
                args.add(cookiesPath)
            }
            if (customArgs.isNotBlank()) {
                args.addAll(customArgs.split("\\s+".toRegex()).filter { it.isNotBlank() })
            }
            args.add(url)

            AppLogger.d("YtDlpProcessRunner", "Executing metadata command: ${args.joinToString(" ")}")
            val pb = ProcessBuilder(args)
            val process = pb.start()

            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode != 0 || stdout.isBlank()) {
                val errorMsg = if (stderr.isNotBlank()) stderr.trim() else "yt-dlp exited with code $exitCode"
                AppLogger.e("YtDlpProcessRunner", "Metadata extraction failed: $errorMsg")
                return@withContext Result.failure(Exception(errorMsg))
            }

            val json = JSONObject(stdout)
            val metadata = parseYtDlpJson(json, url)
            Result.success(metadata)
        } catch (e: Exception) {
            AppLogger.e("YtDlpProcessRunner", "CLI Execution error: ${e.message}")
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
        var process: Process? = null
        try {
            val args = mutableListOf(
                binaryPath,
                "--newline",
                "--progress-template",
                "P|%(progress._percent_str)s|%(progress._downloaded_bytes_str)s|%(progress._total_bytes_str)s|%(progress._speed_str)s|%(progress._eta_str)s"
            )

            // Audio extraction vs Video muxing configuration
            if (mediaType == MediaType.AUDIO) {
                args.add("-f")
                args.add("bestaudio/best")
                args.add("-x")
                val cleanAudioFormat = when (targetContainer.lowercase()) {
                    "mp3" -> "mp3"
                    "m4a", "aac" -> "m4a"
                    "flac" -> "flac"
                    "opus", "ogg" -> "opus"
                    "wav" -> "wav"
                    else -> "mp3"
                }
                args.add("--audio-format")
                args.add(cleanAudioFormat)
                args.add("--audio-quality")
                args.add("${audioBitrate ?: 320}k")
            } else {
                args.add("-f")
                args.add(formatSpec)
                val cleanVideoContainer = when (targetContainer.lowercase()) {
                    "mp4" -> "mp4"
                    "mkv" -> "mkv"
                    "webm" -> "webm"
                    else -> "mp4"
                }
                args.add("--merge-output-format")
                args.add(cleanVideoContainer)
            }

            // Output path
            args.add("-o")
            args.add(outputTemplate)

            // Real FFmpeg location
            if (!ffmpegPath.isNullOrBlank()) {
                val ffmpegFile = File(ffmpegPath)
                if (ffmpegFile.exists()) {
                    val locationArg = ffmpegFile.parentFile?.absolutePath ?: ffmpegPath
                    args.add("--ffmpeg-location")
                    args.add(locationArg)
                }
            }

            // Subtitle & Thumbnail embedding
            if (embedSubs) {
                args.add("--embed-subs")
                args.add("--write-subs")
                args.add("--sub-langs")
                args.add("all")
            }
            if (embedThumbnail) {
                args.add("--embed-thumbnail")
            }

            // Cookies
            if (!cookiesPath.isNullOrBlank() && File(cookiesPath).exists()) {
                args.add("--cookies")
                args.add(cookiesPath)
            }

            // Custom user arguments
            if (customArgs.isNotBlank()) {
                args.addAll(customArgs.split("\\s+".toRegex()).filter { it.isNotBlank() })
            }

            args.add(url)

            AppLogger.i("YtDlpProcessRunner", "Starting yt-dlp download: ${args.joinToString(" ")}", taskId)

            val pb = ProcessBuilder(args)
            pb.redirectErrorStream(true)
            process = pb.start()
            activeProcesses[taskId] = process

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            var downloadedFile: String = outputTemplate
            val lastOutputLines = mutableListOf<String>()

            while (reader.readLine().also { line = it } != null) {
                if (isCancelled()) {
                    process.destroyForcibly()
                    activeProcesses.remove(taskId)
                    return@withContext Result.failure(Exception("Download cancelled by user"))
                }

                line?.let { l ->
                    lastOutputLines.add(l)
                    if (lastOutputLines.size > 20) lastOutputLines.removeAt(0)

                    if (l.startsWith("P|")) {
                        val parts = l.split("|")
                        if (parts.size >= 6) {
                            val percentStr = parts[1].replace("%", "").trim()
                            val prog = percentStr.toFloatOrNull() ?: 0f
                            val downBytes = parseBytes(parts[2])
                            val totalBytes = parseBytes(parts[3])
                            val speed = parseSpeed(parts[4])
                            val eta = parseEtaSeconds(parts[5])
                            onProgress(prog, downBytes, totalBytes, speed, eta)
                        }
                    } else if (l.contains("[download] Destination:")) {
                        downloadedFile = l.substringAfter("[download] Destination:").trim()
                    } else if (l.contains("[Merger] Merging formats into")) {
                        val merged = l.substringAfter("[Merger] Merging formats into").replace("\"", "").trim()
                        if (merged.isNotBlank()) downloadedFile = merged
                    } else if (l.contains("[ExtractAudio] Destination:")) {
                        val audioDest = l.substringAfter("[ExtractAudio] Destination:").trim()
                        if (audioDest.isNotBlank()) downloadedFile = audioDest
                    }
                }
            }

            val exitCode = process.waitFor()
            activeProcesses.remove(taskId)

            if (exitCode == 0) {
                // If downloaded file doesn't exist directly at tracked path, search parent directory for matching prefix
                val targetFile = File(downloadedFile)
                if (targetFile.exists() && targetFile.length() > 0) {
                    Result.success(targetFile.absolutePath)
                } else {
                    val fallbackFile = File(outputTemplate)
                    if (fallbackFile.exists() && fallbackFile.length() > 0) {
                        Result.success(fallbackFile.absolutePath)
                    } else {
                        // Check directory for any produced file matching the base name
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
            } else {
                val errorLog = lastOutputLines.joinToString("\n")
                AppLogger.e("YtDlpProcessRunner", "yt-dlp failed (code $exitCode):\n$errorLog", taskId)
                Result.failure(Exception("yt-dlp download failed with exit code $exitCode:\n$errorLog"))
            }
        } catch (e: Exception) {
            activeProcesses.remove(taskId)
            AppLogger.e("YtDlpProcessRunner", "Process runner exception: ${e.message}", taskId)
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

    private fun parseBytes(str: String): Long {
        val s = str.trim()
        val num = s.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: return 0L
        return when {
            s.endsWith("GiB", true) || s.endsWith("GB", true) -> (num * 1024 * 1024 * 1024).toLong()
            s.endsWith("MiB", true) || s.endsWith("MB", true) -> (num * 1024 * 1024).toLong()
            s.endsWith("KiB", true) || s.endsWith("KB", true) -> (num * 1024).toLong()
            else -> num.toLong()
        }
    }

    private fun parseSpeed(str: String): Double {
        val s = str.trim().removeSuffix("/s")
        val num = s.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: return 0.0
        return when {
            s.endsWith("GiB", true) || s.endsWith("GB", true) -> num * 1024 * 1024 * 1024
            s.endsWith("MiB", true) || s.endsWith("MB", true) -> num * 1024 * 1024
            s.endsWith("KiB", true) || s.endsWith("KB", true) -> num * 1024
            else -> num
        }
    }

    private fun parseEtaSeconds(str: String): Long {
        val parts = str.trim().split(":")
        return when (parts.size) {
            3 -> (parts[0].toLongOrNull() ?: 0L) * 3600 + (parts[1].toLongOrNull() ?: 0L) * 60 + (parts[2].toLongOrNull() ?: 0L)
            2 -> (parts[0].toLongOrNull() ?: 0L) * 60 + (parts[1].toLongOrNull() ?: 0L)
            1 -> parts[0].toLongOrNull() ?: 0L
            else -> 0L
        }
    }
}
