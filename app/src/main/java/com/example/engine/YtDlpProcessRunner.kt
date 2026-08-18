package com.example.engine

import com.example.data.model.FormatInfo
import com.example.data.model.MediaMetadata
import com.example.data.model.SubtitleTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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

            AppLogger.d("YtDlpProcessRunner", "Executing: ${args.joinToString(" ")}")
            val pb = ProcessBuilder(args)
            val process = pb.start()

            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode != 0 || stdout.isBlank()) {
                val errorMsg = if (stderr.isNotBlank()) stderr else "yt-dlp exited with code $exitCode"
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
        formatSpec: String,
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
                "P|%(progress._percent_str)s|%(progress._downloaded_bytes_str)s|%(progress._total_bytes_str)s|%(progress._speed_str)s|%(progress._eta_str)s",
                "-f", formatSpec,
                "-o", outputTemplate
            )

            if (!ffmpegPath.isNullOrBlank() && File(ffmpegPath).exists()) {
                args.add("--ffmpeg-location")
                args.add(ffmpegPath)
            }

            if (!cookiesPath.isNullOrBlank() && File(cookiesPath).exists()) {
                args.add("--cookies")
                args.add(cookiesPath)
            }

            if (customArgs.isNotBlank()) {
                args.addAll(customArgs.split("\\s+".toRegex()).filter { it.isNotBlank() })
            }

            args.add(url)

            val pb = ProcessBuilder(args)
            pb.redirectErrorStream(true)
            process = pb.start()
            activeProcesses[taskId] = process

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            var downloadedFile: String = outputTemplate

            while (reader.readLine().also { line = it } != null) {
                if (isCancelled()) {
                    process.destroyForcibly()
                    activeProcesses.remove(taskId)
                    return@withContext Result.failure(Exception("Cancelled by user"))
                }

                line?.let { l ->
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
                    }
                }
            }

            val exitCode = process.waitFor()
            activeProcesses.remove(taskId)

            if (exitCode == 0) {
                Result.success(downloadedFile)
            } else {
                Result.failure(Exception("yt-dlp download failed with exit code $exitCode"))
            }
        } catch (e: Exception) {
            activeProcesses.remove(taskId)
            Result.failure(e)
        }
    }

    private fun parseYtDlpJson(json: JSONObject, originalUrl: String): MediaMetadata {
        val id = json.optString("id", System.currentTimeMillis().toString())
        val title = json.optString("title", "Untitled Media")
        val uploader = json.optString("uploader", json.optString("channel", "Unknown Uploader"))
        val duration = json.optLong("duration", 0L)
        val viewCount = if (json.has("view_count")) json.optLong("view_count") else null
        val likeCount = if (json.has("like_count")) json.optLong("like_count") else null
        val uploadDate = json.optString("upload_date", "")
        val description = json.optString("description", "")
        val thumbnail = json.optString("thumbnail", "")

        val formatsList = mutableListOf<FormatInfo>()
        val formatsArray = json.optJSONArray("formats")
        if (formatsArray != null) {
            for (i in 0 until formatsArray.length()) {
                val f = formatsArray.optJSONObject(i) ?: continue
                formatsList.add(
                    FormatInfo(
                        formatId = f.optString("format_id", "$i"),
                        ext = f.optString("ext", "mp4"),
                        resolution = f.optString("resolution", "${f.optInt("height", 0)}p"),
                        width = if (f.has("width")) f.optInt("width") else null,
                        height = if (f.has("height")) f.optInt("height") else null,
                        fps = if (f.has("fps")) f.optDouble("fps") else null,
                        vcodec = f.optString("vcodec", "none"),
                        acodec = f.optString("acodec", "none"),
                        tbr = if (f.has("tbr")) f.optDouble("tbr") else null,
                        vbr = if (f.has("vbr")) f.optDouble("vbr") else null,
                        abr = if (f.has("abr")) f.optDouble("abr") else null,
                        filesize = if (f.has("filesize")) f.optLong("filesize") else null,
                        filesizeApprox = if (f.has("filesize_approx")) f.optLong("filesize_approx") else null,
                        formatNote = f.optString("format_note", ""),
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
