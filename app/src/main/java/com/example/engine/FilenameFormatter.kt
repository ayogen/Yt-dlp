package com.example.engine

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FilenameFormatter {
    private val ILLEGAL_CHARS = Regex("[\\\\/:*?\"<>|\\x00-\\x1F\\x7F]")
    private val EXCESSIVE_WHITESPACE = Regex("\\s+")

    // 100 UTF-16 code units / conservative char limit to safely fit well within 255-byte limit in ext4/FAT32/F2FS
    private const val MAX_BASE_FILENAME_LENGTH = 100

    fun sanitize(name: String, fallbackId: String = "media"): String {
        var clean = name
            .replace(ILLEGAL_CHARS, "_")
            .replace(EXCESSIVE_WHITESPACE, " ")
            .trim(' ', '.', '_', '-')

        if (clean.isBlank()) {
            val safeId = fallbackId.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(32)
            clean = if (safeId.isNotBlank()) "media_$safeId" else "media_download"
        }

        // Truncate cleanly by UTF-8 bytes and characters
        if (clean.length > MAX_BASE_FILENAME_LENGTH) {
            clean = clean.substring(0, MAX_BASE_FILENAME_LENGTH).trim(' ', '.', '_', '-')
        }

        // Additional safeguard for multi-byte Unicode strings (e.g. Arabic, emojis, CJK) where 1 char = 2-4 bytes
        clean = truncateUtf8Bytes(clean, 180).trim(' ', '.', '_', '-')

        if (clean.isBlank()) {
            val safeId = fallbackId.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(32)
            clean = if (safeId.isNotBlank()) "media_$safeId" else "media_download"
        }

        return clean
    }

    private fun truncateUtf8Bytes(str: String, maxBytes: Int): String {
        val bytes = str.toByteArray(Charsets.UTF_8)
        if (bytes.size <= maxBytes) return str

        // Reduce string character by character until UTF-8 byte representation fits
        var truncated = str
        while (truncated.isNotEmpty() && truncated.toByteArray(Charsets.UTF_8).size > maxBytes) {
            truncated = truncated.dropLast(1)
        }
        return truncated
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

        val cleanTitle = sanitize(title, id)
        val cleanUploader = sanitize(uploader, "uploader")
        val cleanId = sanitize(id, "id")
        val cleanExt = ext.removePrefix(".").replace(ILLEGAL_CHARS, "").trim().ifBlank { "mp4" }
        val dateStr = if (uploadDate.isNotBlank()) {
            sanitize(uploadDate, "date")
        } else {
            SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        }

        result = result.replace("%(title)s", cleanTitle)
            .replace("%(uploader)s", cleanUploader)
            .replace("%(channel)s", cleanUploader)
            .replace("%(id)s", cleanId)
            .replace("%(ext)s", cleanExt)
            .replace("%(upload_date)s", dateStr)
            .replace("%(resolution)s", sanitize(resolution, "res"))

        // Clean any leftovers or directory separators if template doesn't specify folders
        val parts = result.split("/")
        val sanitizedParts = parts.map { sanitize(it, id) }.filter { it.isNotBlank() }

        val baseJoined = if (sanitizedParts.isNotEmpty()) {
            sanitizedParts.joinToString(File.separator)
        } else {
            "media_$cleanId"
        }

        return if (baseJoined.endsWith(".$cleanExt", ignoreCase = true)) {
            baseJoined
        } else {
            "$baseJoined.$cleanExt"
        }
    }
}

