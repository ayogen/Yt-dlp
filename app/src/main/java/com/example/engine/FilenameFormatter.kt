package com.example.engine

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FilenameFormatter {
    private val ILLEGAL_CHARS = Regex("[\\\\/:*?\"<>|]")

    fun sanitize(name: String): String {
        var clean = name.replace(ILLEGAL_CHARS, "_")
            .replace("\r", "")
            .replace("\n", "")
            .trim()

        // Avoid reserved names on various filesystems
        if (clean.isBlank()) clean = "media_download"
        if (clean.length > 180) {
            clean = clean.substring(0, 180).trim()
        }
        return clean
    }

    fun format(
        template: String,
        title: String,
        uploader: String = "Unknown",
        id: String = "media",
        ext: String = "mp4",
        uploadDate: String = "",
        resolution: String = ""
    ): String {
        var result = template.ifBlank { "%(title)s.%(ext)s" }

        val cleanTitle = sanitize(title)
        val cleanUploader = sanitize(uploader)
        val cleanId = sanitize(id)
        val cleanExt = sanitize(ext.removePrefix("."))
        val dateStr = if (uploadDate.isNotBlank()) {
            sanitize(uploadDate)
        } else {
            SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        }

        result = result.replace("%(title)s", cleanTitle)
            .replace("%(uploader)s", cleanUploader)
            .replace("%(channel)s", cleanUploader)
            .replace("%(id)s", cleanId)
            .replace("%(ext)s", cleanExt)
            .replace("%(upload_date)s", dateStr)
            .replace("%(resolution)s", sanitize(resolution))

        // Clean any leftovers or directory separators if template doesn't specify folders
        val parts = result.split("/")
        val sanitizedParts = parts.map { sanitize(it) }.filter { it.isNotBlank() }

        val finalName = sanitizedParts.joinToString(File.separator)
        return if (finalName.endsWith(".$cleanExt", ignoreCase = true)) {
            finalName
        } else {
            "$finalName.$cleanExt"
        }
    }
}
