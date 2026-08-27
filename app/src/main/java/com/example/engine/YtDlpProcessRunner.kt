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
        customArgs: String = ""
    ): Result<MediaMetadata> = withContext(Dispatchers.IO) {
        val processId = "meta_${System.currentTimeMillis()}_${(1000..9999).random()}"
        try {
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

            AppLogger.d("YtDlpProcessRunner", "Executing metadata request for $url")
            val response = YoutubeDL.getInstance().execute(request, processId)
            val stdout = response.out

            if (stdout.isNullOrBlank()) {
                AppLogger.e("YtDlpProcessRunner", "Metadata extraction failed: empty output")
                return@withContext Result.failure(Exception("yt-dlp returned empty metadata output"))
            }

            val json = JSONObject(stdout)
            val isPlaylistDetected = json.optString("_type") == "playlist" || (json.has("entries") && !json.isNull("entries"))
            val metadata = parseYtDlpJson(json, url)

            if (isPlaylistDetected && metadata.playlistEntries.isEmpty()) {
                AppLogger.e("YtDlpProcessRunner", "Playlist contains no accessible videos or is private")
                return@withContext Result.failure(Exception("Playlist contains no accessible videos or is private"))
            }

            Result.success(metadata)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                try {
                    YoutubeDL.getInstance().destroyProcessById(processId)
                } catch (_: Exception) {}
                throw e
            }
            val msg = e.message ?: e.javaClass.simpleName
            AppLogger.e("YtDlpProcessRunner", "CLI Execution error: $msg")
            Result.failure(e)
        }
    }

    suspend fun extractMediaCollectionCli(
        binaryPath: String,
        url: String,
        cookiesPath: String? = null,
        customArgs: String = ""
    ): Result<com.example.data.model.MediaCollection> = withContext(Dispatchers.IO) {
        val processId = "meta_col_${System.currentTimeMillis()}_${(1000..9999).random()}"
        try {
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

            AppLogger.d("YtDlpProcessRunner", "Executing metadata request for $url (canonical collection)")
            val response = YoutubeDL.getInstance().execute(request, processId)
            val stdout = response.out

            if (stdout.isNullOrBlank()) {
                AppLogger.e("YtDlpProcessRunner", "Metadata extraction failed: empty output")
                return@withContext Result.failure(Exception("yt-dlp returned empty metadata output"))
            }

            val json = JSONObject(stdout)
            val isPlaylistDetected = json.optString("_type") == "playlist" || (json.has("entries") && !json.isNull("entries"))
            val collection = parseYtDlpMediaCollection(json, url)

            if (isPlaylistDetected && collection.items.isEmpty()) {
                AppLogger.e("YtDlpProcessRunner", "Playlist contains no accessible videos or is private")
                return@withContext Result.failure(Exception("Playlist contains no accessible videos or is private"))
            }

            Result.success(collection)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                try {
                    YoutubeDL.getInstance().destroyProcessById(processId)
                } catch (_: Exception) {}
                throw e
            }
            val msg = e.message ?: e.javaClass.simpleName
            AppLogger.e("YtDlpProcessRunner", "CLI Execution error: $msg")
            Result.failure(e)
        }
    }

    suspend fun extractInfoDto(
        binaryPath: String,
        url: String,
        cookiesPath: String? = null,
        customArgs: String = ""
    ): Result<com.example.extraction.model.YtDlpInfoDto> = withContext(Dispatchers.IO) {
        val processId = "meta_dto_${System.currentTimeMillis()}_${(1000..9999).random()}"
        try {
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

            AppLogger.d("YtDlpProcessRunner", "Executing DTO request for $url")
            val response = YoutubeDL.getInstance().execute(request, processId)
            val stdout = response.out

            if (stdout.isNullOrBlank()) {
                AppLogger.e("YtDlpProcessRunner", "Metadata extraction failed: empty output")
                return@withContext Result.failure(Exception("yt-dlp returned empty metadata output"))
            }

            val json = JSONObject(stdout)
            val infoDto = com.example.extraction.YtDlpJsonParser.parse(json, url)
            Result.success(infoDto)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                try {
                    YoutubeDL.getInstance().destroyProcessById(processId)
                } catch (_: Exception) {}
                throw e
            }
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
        val type = cleanString(json, "_type", "")
        val hasEntries = json.has("entries") && !json.isNull("entries")
        val isPlaylist = type.equals("playlist", ignoreCase = true) || hasEntries

        val id = cleanString(json, "id", Math.abs(originalUrl.hashCode()).toString())
        val title = cleanString(json, "title", if (isPlaylist) "Untitled Playlist" else "Untitled Media")
        val uploader = cleanString(json, "uploader", cleanString(json, "channel", cleanString(json, "playlist_uploader", "Unknown Uploader")))
        val duration = cleanLong(json, "duration") ?: 0L
        val viewCount = cleanLong(json, "view_count")
        val likeCount = cleanLong(json, "like_count")
        val uploadDate = cleanString(json, "upload_date", "")
        val description = cleanString(json, "description", "")
        var rootThumbnail = cleanString(json, "thumbnail", "")

        val playlistEntries = mutableListOf<PlaylistEntry>()

        if (isPlaylist) {
            val entriesArray = json.optJSONArray("entries")
            if (entriesArray != null) {
                val isYouTube = originalUrl.contains("youtube.com", ignoreCase = true) ||
                        originalUrl.contains("youtu.be", ignoreCase = true) ||
                        cleanString(json, "extractor", "").contains("youtube", ignoreCase = true) ||
                        cleanString(json, "extractor_key", "").contains("youtube", ignoreCase = true)

                for (i in 0 until entriesArray.length()) {
                    val entryObj = entriesArray.optJSONObject(i) ?: continue
                    val entryId = cleanString(entryObj, "id", "")
                    var entryUrl = cleanString(entryObj, "url", cleanString(entryObj, "webpage_url", ""))
                    val entryTitle = cleanString(entryObj, "title", if (entryId.isNotBlank()) "Video $entryId" else "")
                    val entryDuration = cleanLong(entryObj, "duration") ?: 0L
                    val entryThumbnail = cleanString(entryObj, "thumbnail", "")
                    val entryUploader = cleanString(entryObj, "uploader", cleanString(entryObj, "channel", uploader))

                    if (entryUrl.isBlank() && entryId.isNotBlank()) {
                        if (isYouTube) {
                            entryUrl = "https://www.youtube.com/watch?v=$entryId"
                        } else if (entryId.startsWith("http://", ignoreCase = true) || entryId.startsWith("https://", ignoreCase = true)) {
                            entryUrl = entryId
                        }
                    }

                    if (entryUrl.isNotBlank()) {
                        val validTitle = if (entryTitle.isNotBlank()) entryTitle else "Video ${playlistEntries.size + 1}"
                        playlistEntries.add(
                            PlaylistEntry(
                                id = entryId.ifBlank { "entry_${playlistEntries.size + 1}" },
                                title = validTitle,
                                url = entryUrl,
                                durationSeconds = entryDuration,
                                thumbnail = entryThumbnail,
                                uploader = entryUploader,
                                isSelected = true
                            )
                        )
                    }
                }
            }

            if (rootThumbnail.isBlank() && playlistEntries.isNotEmpty()) {
                val firstThumb = playlistEntries.firstOrNull { it.thumbnail.isNotBlank() }?.thumbnail
                if (!firstThumb.isNullOrBlank()) {
                    rootThumbnail = firstThumb
                }
            }
        }

        val formatsList = mutableListOf<FormatInfo>()
        val formatsArray = json.optJSONArray("formats")
        if (formatsArray != null) {
            for (i in 0 until formatsArray.length()) {
                val f = formatsArray.optJSONObject(i) ?: continue
                val formatId = cleanString(f, "format_id", "$i")
                val ext = cleanString(f, "ext", "mp4")
                val width = cleanInt(f, "width")
                val height = cleanInt(f, "height")
                val fps = cleanDouble(f, "fps")
                val vcodec = cleanString(f, "vcodec", "none")
                val acodec = cleanString(f, "acodec", "none")
                val tbr = cleanDouble(f, "tbr")
                val vbr = cleanDouble(f, "vbr")
                val abr = cleanDouble(f, "abr")
                val filesize = cleanLong(f, "filesize")
                val filesizeApprox = cleanLong(f, "filesize_approx")
                val formatNote = cleanString(f, "format_note", "")
                val resolution = cleanString(f, "resolution", if (height != null && height > 0) "${height}p" else "")

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
                        url = cleanString(f, "url", "")
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

        val hasValidPlaylistEntries = isPlaylist && playlistEntries.isNotEmpty()

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
            thumbnail = rootThumbnail,
            isPlaylist = hasValidPlaylistEntries,
            playlistCount = if (hasValidPlaylistEntries) playlistEntries.size else 0,
            playlistEntries = if (hasValidPlaylistEntries) playlistEntries else emptyList(),
            formats = formatsList,
            subtitles = subtitlesList,
            extractorName = cleanString(json, "extractor", if (isPlaylist) "yt-dlp:playlist" else "yt-dlp")
        )
    }

    internal fun parseYtDlpMediaCollection(json: JSONObject, originalUrl: String): com.example.data.model.MediaCollection {
        val type = cleanString(json, "_type", "")
        val hasEntries = json.has("entries") && !json.isNull("entries")
        val isPlaylist = type.equals("playlist", ignoreCase = true) || hasEntries

        val id = cleanString(json, "id", Math.abs(originalUrl.hashCode()).toString())
        val title = cleanString(json, "title", if (isPlaylist) "Untitled Playlist" else "Untitled Media")
        val uploader = cleanString(json, "uploader", cleanString(json, "channel", cleanString(json, "playlist_uploader", "Unknown Uploader")))
        val duration = cleanLong(json, "duration") ?: 0L
        val uploadDate = cleanString(json, "upload_date", "")
        val description = cleanString(json, "description", "")
        var rootThumbnail = cleanString(json, "thumbnail", "")
        val extractorName = cleanString(json, "extractor", if (isPlaylist) "yt-dlp:playlist" else "yt-dlp")

        if (isPlaylist) {
            val mediaItems = mutableListOf<com.example.data.model.MediaItem>()
            val entriesArray = json.optJSONArray("entries")
            if (entriesArray != null) {
                val isYouTube = originalUrl.contains("youtube.com", ignoreCase = true) ||
                        originalUrl.contains("youtu.be", ignoreCase = true) ||
                        extractorName.contains("youtube", ignoreCase = true) ||
                        cleanString(json, "extractor_key", "").contains("youtube", ignoreCase = true)

                for (i in 0 until entriesArray.length()) {
                    val entryObj = entriesArray.optJSONObject(i)
                    if (entryObj == null) {
                        mediaItems.add(
                            com.example.data.model.MediaItem(
                                id = "entry_${i + 1}",
                                title = "Unavailable Video ${i + 1}",
                                sourceUrl = "",
                                webpageUrl = "",
                                thumbnail = "",
                                mediaKind = com.example.data.model.MediaKind.VIDEO,
                                durationSeconds = 0L,
                                isSelected = false,
                                index = i,
                                errorMessage = "Malformed playlist entry at index $i"
                            )
                        )
                        continue
                    }

                    val entryId = cleanString(entryObj, "id", "")
                    var entryUrl = cleanString(entryObj, "url", cleanString(entryObj, "webpage_url", ""))
                    val entryTitle = cleanString(entryObj, "title", if (entryId.isNotBlank()) "Video $entryId" else "")
                    val entryDuration = cleanLong(entryObj, "duration") ?: 0L
                    val entryThumbnail = cleanString(entryObj, "thumbnail", "")

                    if (entryUrl.isBlank() && entryId.isNotBlank()) {
                        if (isYouTube) {
                            entryUrl = "https://www.youtube.com/watch?v=$entryId"
                        } else if (entryId.startsWith("http://", ignoreCase = true) || entryId.startsWith("https://", ignoreCase = true)) {
                            entryUrl = entryId
                        }
                    }

                    val isUnavailable = entryTitle.contains("[Private video]", ignoreCase = true) ||
                            entryTitle.contains("[Deleted video]", ignoreCase = true) ||
                            entryTitle.contains("[Unavailable]", ignoreCase = true) ||
                            (entryUrl.isBlank() && entryId.isBlank())

                    if (isUnavailable) {
                        val validTitle = if (entryTitle.isNotBlank()) entryTitle else "Unavailable Video ${i + 1}"
                        mediaItems.add(
                            com.example.data.model.MediaItem(
                                id = entryId.ifBlank { "entry_${i + 1}" },
                                title = validTitle,
                                sourceUrl = entryUrl,
                                webpageUrl = entryUrl,
                                thumbnail = entryThumbnail,
                                mediaKind = com.example.data.model.MediaKind.VIDEO,
                                durationSeconds = entryDuration,
                                isSelected = false,
                                index = i,
                                errorMessage = "Video is private, deleted, or unavailable"
                            )
                        )
                    } else {
                        val validTitle = if (entryTitle.isNotBlank()) entryTitle else "Video ${i + 1}"
                        mediaItems.add(
                            com.example.data.model.MediaItem(
                                id = entryId.ifBlank { "entry_${i + 1}" },
                                title = validTitle,
                                sourceUrl = entryUrl,
                                webpageUrl = entryUrl,
                                thumbnail = entryThumbnail,
                                mediaKind = com.example.data.model.MediaKind.VIDEO,
                                durationSeconds = entryDuration,
                                isSelected = true,
                                index = i
                            )
                        )
                    }
                }
            }

            if (rootThumbnail.isBlank() && mediaItems.isNotEmpty()) {
                val firstThumb = mediaItems.firstOrNull { it.thumbnail.isNotBlank() }?.thumbnail
                if (!firstThumb.isNullOrBlank()) {
                    rootThumbnail = firstThumb
                }
            }

            return com.example.data.model.MediaCollection(
                id = id,
                title = title,
                webpageUrl = originalUrl,
                uploader = uploader,
                thumbnail = rootThumbnail,
                mediaKind = com.example.data.model.MediaKind.PLAYLIST,
                items = mediaItems,
                extractorName = extractorName,
                description = description,
                uploadDate = uploadDate
            )
        }

        // Single media item parsing
        val formatsList = mutableListOf<FormatInfo>()
        val formatsArray = json.optJSONArray("formats")
        if (formatsArray != null) {
            for (i in 0 until formatsArray.length()) {
                val f = formatsArray.optJSONObject(i) ?: continue
                val formatId = cleanString(f, "format_id", "$i")
                val ext = cleanString(f, "ext", "mp4")
                val width = cleanInt(f, "width")
                val height = cleanInt(f, "height")
                val fps = cleanDouble(f, "fps")
                val vcodec = cleanString(f, "vcodec", "none")
                val acodec = cleanString(f, "acodec", "none")
                val tbr = cleanDouble(f, "tbr")
                val vbr = cleanDouble(f, "vbr")
                val abr = cleanDouble(f, "abr")
                val filesize = cleanLong(f, "filesize")
                val filesizeApprox = cleanLong(f, "filesize_approx")
                val formatNote = cleanString(f, "format_note", "")
                val resolution = cleanString(f, "resolution", if (height != null && height > 0) "${height}p" else "")

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
                        url = cleanString(f, "url", "")
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

        val isAudioOnly = (formatsList.isNotEmpty() && formatsList.all { it.isAudioOnly }) ||
                extractorName.equals("soundcloud", ignoreCase = true)
        val mediaKind = if (isAudioOnly) com.example.data.model.MediaKind.AUDIO else com.example.data.model.MediaKind.VIDEO

        // Calculate size and provenance
        val bestFormat = formatsList.filter { it.isDirectVideo || it.isMuxed || (isAudioOnly && it.isAudioOnly) }
            .maxByOrNull { it.effectiveBitrate ?: 0.0 }
        val exactSize = bestFormat?.filesize ?: formatsList.mapNotNull { it.filesize }.maxOrNull()
        val approxSize = bestFormat?.filesizeApprox ?: formatsList.mapNotNull { it.filesizeApprox }.maxOrNull()

        val (chosenSize, provenance) = when {
            exactSize != null && exactSize > 0 -> exactSize to com.example.data.model.SizeProvenance.EXACT
            approxSize != null && approxSize > 0 -> approxSize to com.example.data.model.SizeProvenance.APPROXIMATE
            duration > 0 -> {
                val estBytes = if (isAudioOnly) duration * 16000L else duration * 187500L
                estBytes to com.example.data.model.SizeProvenance.DERIVED
            }
            else -> null to com.example.data.model.SizeProvenance.UNKNOWN
        }

        val singleItem = com.example.data.model.MediaItem(
            id = id,
            title = title,
            sourceUrl = originalUrl,
            webpageUrl = originalUrl,
            thumbnail = rootThumbnail,
            mediaKind = mediaKind,
            durationSeconds = duration,
            width = formatsList.mapNotNull { it.width }.maxOrNull(),
            height = formatsList.mapNotNull { it.height }.maxOrNull(),
            mimeType = if (isAudioOnly) "audio/mp4" else "video/mp4",
            formats = formatsList,
            subtitles = subtitlesList,
            fileSize = chosenSize,
            sizeProvenance = provenance,
            index = 0
        )

        return com.example.data.model.MediaCollection(
            id = id,
            title = title,
            webpageUrl = originalUrl,
            uploader = uploader,
            thumbnail = rootThumbnail,
            mediaKind = mediaKind,
            items = listOf(singleItem),
            extractorName = extractorName,
            description = description,
            uploadDate = uploadDate
        )
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
