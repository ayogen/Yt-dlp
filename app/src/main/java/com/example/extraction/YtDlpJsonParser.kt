package com.example.extraction

import com.example.extraction.model.YtDlpFormatDto
import com.example.extraction.model.YtDlpInfoDto
import com.example.extraction.model.YtDlpPlaylistEntryDto
import com.example.extraction.model.YtDlpSubtitleDto
import org.json.JSONArray
import org.json.JSONObject

object YtDlpJsonParser {

    fun parse(json: JSONObject, originalUrl: String): YtDlpInfoDto {
        val id = cleanString(json, "id", Math.abs(originalUrl.hashCode()).toString())
        val title = cleanString(json, "title", "Video")
        val uploader = cleanString(json, "uploader", cleanString(json, "channel", ""))
        val channel = cleanString(json, "channel", uploader)
        val duration = cleanLong(json, "duration", 0L)
        val viewCount = if (json.has("view_count") && !json.isNull("view_count")) cleanLong(json, "view_count", 0L) else null
        val likeCount = if (json.has("like_count") && !json.isNull("like_count")) cleanLong(json, "like_count", 0L) else null
        val uploadDate = cleanString(json, "upload_date", "")
        val description = cleanString(json, "description", "")
        val rootThumbnail = cleanString(json, "thumbnail", "")
        val type = if (json.has("_type")) cleanString(json, "_type", "") else null
        val extractor = if (json.has("extractor")) cleanString(json, "extractor", "") else null
        val extractorKey = if (json.has("extractor_key")) cleanString(json, "extractor_key", "") else null
        val rootFilesize = if (json.has("filesize") && !json.isNull("filesize")) cleanLong(json, "filesize", 0L) else null
        val rootFilesizeApprox = if (json.has("filesize_approx") && !json.isNull("filesize_approx")) cleanLong(json, "filesize_approx", 0L) else null

        // Parse Playlist Entries
        val playlistEntries = mutableListOf<YtDlpPlaylistEntryDto>()
        val entriesArray = json.optJSONArray("entries")
        if (entriesArray != null) {
            for (i in 0 until entriesArray.length()) {
                val entryObj = entriesArray.optJSONObject(i) ?: continue
                val entryId = cleanString(entryObj, "id", "")
                val entryTitle = cleanString(entryObj, "title", "Track #${i + 1}")
                var entryUrl = cleanString(entryObj, "url", "")
                val entryDuration = cleanLong(entryObj, "duration", 0L)
                val entryThumbnail = cleanString(entryObj, "thumbnail", rootThumbnail)
                val entryUploader = cleanString(entryObj, "uploader", uploader)

                if (entryUrl.isBlank() && entryId.isNotBlank()) {
                    entryUrl = when {
                        extractor?.contains("youtube", ignoreCase = true) == true ||
                        originalUrl.contains("youtube.com") || originalUrl.contains("youtu.be") -> {
                            "https://www.youtube.com/watch?v=$entryId"
                        }
                        extractor?.contains("soundcloud", ignoreCase = true) == true ||
                        originalUrl.contains("soundcloud.com") -> {
                            "https://soundcloud.com/$entryId"
                        }
                        else -> {
                            val base = originalUrl.substringBefore("?")
                            if (base.endsWith("/")) "$base$entryId" else "$base/$entryId"
                        }
                    }
                }

                if (entryId.isNotBlank() || entryUrl.isNotBlank()) {
                    playlistEntries.add(
                        YtDlpPlaylistEntryDto(
                            id = entryId.ifBlank { "entry_$i" },
                            title = entryTitle,
                            url = entryUrl.ifBlank { originalUrl },
                            duration = entryDuration,
                            thumbnail = entryThumbnail,
                            uploader = entryUploader
                        )
                    )
                }
            }
        }

        // Parse Formats
        val formatsList = mutableListOf<YtDlpFormatDto>()
        val formatsArray = json.optJSONArray("formats")
        if (formatsArray != null) {
            for (i in 0 until formatsArray.length()) {
                val f = formatsArray.optJSONObject(i) ?: continue
                val formatId = cleanString(f, "format_id", i.toString())
                val ext = cleanString(f, "ext", "mp4")
                val width = if (f.has("width") && !f.isNull("width")) cleanInt(f, "width", 0) else null
                val height = if (f.has("height") && !f.isNull("height")) cleanInt(f, "height", 0) else null
                val resolution = cleanString(f, "resolution", if (height != null && height > 0) "${height}p" else "")
                val fps = if (f.has("fps") && !f.isNull("fps")) cleanDouble(f, "fps", 0.0) else null
                val vcodec = cleanString(f, "vcodec", "none")
                val acodec = cleanString(f, "acodec", "none")
                val tbr = if (f.has("tbr") && !f.isNull("tbr")) cleanDouble(f, "tbr", 0.0) else null
                val vbr = if (f.has("vbr") && !f.isNull("vbr")) cleanDouble(f, "vbr", 0.0) else null
                val abr = if (f.has("abr") && !f.isNull("abr")) cleanDouble(f, "abr", 0.0) else null
                val filesize = if (f.has("filesize") && !f.isNull("filesize")) cleanLong(f, "filesize", 0L) else null
                val filesizeApprox = if (f.has("filesize_approx") && !f.isNull("filesize_approx")) cleanLong(f, "filesize_approx", 0L) else null
                val formatNote = cleanString(f, "format_note", "")
                val url = cleanString(f, "url", "")
                val protocol = cleanString(f, "protocol", "https")

                formatsList.add(
                    YtDlpFormatDto(
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
                        url = url,
                        protocol = protocol
                    )
                )
            }
        }

        // Parse Subtitles
        val subtitlesList = mutableListOf<YtDlpSubtitleDto>()
        val subsObj = json.optJSONObject("subtitles")
        if (subsObj != null) {
            val keys = subsObj.keys()
            while (keys.hasNext()) {
                val lang = keys.next()
                val langArr = subsObj.optJSONArray(lang)
                val ext = if (langArr != null && langArr.length() > 0) {
                    val subFirst = langArr.optJSONObject(0)
                    cleanString(subFirst ?: JSONObject(), "ext", "vtt")
                } else "vtt"
                val subUrl = if (langArr != null && langArr.length() > 0) {
                    val subFirst = langArr.optJSONObject(0)
                    cleanString(subFirst ?: JSONObject(), "url", "")
                } else ""

                subtitlesList.add(
                    YtDlpSubtitleDto(
                        language = lang,
                        ext = ext,
                        url = subUrl,
                        name = lang.uppercase()
                    )
                )
            }
        }

        return YtDlpInfoDto(
            id = id,
            title = title,
            webpageUrl = originalUrl,
            uploader = uploader,
            channel = channel,
            duration = duration,
            viewCount = viewCount,
            likeCount = likeCount,
            uploadDate = uploadDate,
            description = description,
            thumbnail = rootThumbnail,
            type = type,
            extractor = extractor,
            extractorKey = extractorKey,
            filesize = rootFilesize,
            filesizeApprox = rootFilesizeApprox,
            formats = formatsList,
            subtitles = subtitlesList,
            entries = playlistEntries
        )
    }

    private fun cleanString(json: JSONObject, key: String, default: String): String {
        if (!json.has(key) || json.isNull(key)) return default
        val v = json.opt(key) ?: return default
        if (v == JSONObject.NULL) return default
        val s = v.toString().trim()
        return if (s == "null" || s.isEmpty()) default else s
    }

    private fun cleanLong(json: JSONObject, key: String, default: Long): Long {
        if (!json.has(key) || json.isNull(key)) return default
        val v = json.opt(key) ?: return default
        if (v is Number) return v.toLong()
        return v.toString().toLongOrNull() ?: default
    }

    private fun cleanInt(json: JSONObject, key: String, default: Int): Int {
        if (!json.has(key) || json.isNull(key)) return default
        val v = json.opt(key) ?: return default
        if (v is Number) return v.toInt()
        return v.toString().toIntOrNull() ?: default
    }

    private fun cleanDouble(json: JSONObject, key: String, default: Double): Double {
        if (!json.has(key) || json.isNull(key)) return default
        val v = json.opt(key) ?: return default
        if (v is Number) return v.toDouble()
        return v.toString().toDoubleOrNull() ?: default
    }
}
