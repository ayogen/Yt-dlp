package com.example.engine

import com.example.data.model.MediaType
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.util.concurrent.TimeUnit

object DirectMediaInspector {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    data class InspectionResult(
        val isDirectMedia: Boolean,
        val mediaType: MediaType,
        val mimeType: String,
        val contentLength: Long?,
        val width: Int? = null,
        val height: Int? = null,
        val suggestedExt: String = "bin"
    )

    private fun isKnownSocialWebpage(url: String): Boolean {
        val lower = url.lowercase()
        val clean = lower.substringBefore("?").substringBefore("#")
        val hasDirectExt = clean.endsWith(".jpg") || clean.endsWith(".jpeg") || clean.endsWith(".png") ||
                clean.endsWith(".webp") || clean.endsWith(".gif") || clean.endsWith(".mp4") ||
                clean.endsWith(".webm") || clean.endsWith(".mkv") || clean.endsWith(".mp3") ||
                clean.endsWith(".m4a") || clean.endsWith(".flac") || clean.endsWith(".opus") ||
                clean.endsWith(".wav")
        if (hasDirectExt) return false

        return lower.contains("instagram.com") || lower.contains("instagr.am") ||
                lower.contains("facebook.com") || lower.contains("fb.watch") ||
                lower.contains("tiktok.com") || lower.contains("youtube.com") ||
                lower.contains("youtu.be") || lower.contains("twitter.com") ||
                lower.contains("x.com") || lower.contains("vimeo.com")
    }

    /**
     * Inspects a target URL to check whether it directly points to a media resource (Image, Video, Audio)
     * using HTTP HEAD and/or ranged GET request inspection with magic number validation.
     * Supports coroutine cancellation.
     */
    suspend fun inspectUrl(url: String, traceId: String? = null): InspectionResult {
        val effectiveTraceId = traceId ?: MediaExtractionTracer.currentSessionFlow.value?.traceId
        val opId = if (effectiveTraceId != null) {
            MediaExtractionTracer.startOperation(
                traceId = effectiveTraceId,
                component = "DirectMediaInspector",
                stage = "DIRECT_INSPECTION",
                name = "inspectUrl",
                details = mapOf("url" to url)
            )
        } else null

        if (url.isBlank()) {
            val res = InspectionResult(false, MediaType.VIDEO, "unknown", null)
            recordResult(effectiveTraceId, opId, res, "BLANK_URL", "URL is blank")
            return res
        }

        // Fast-path for explicit clean media extensions if network is not needed or as a hint
        val cleanUrl = url.substringBefore("?").substringBefore("#").lowercase()

        // If it is a known social/video webpage that does not end in a direct media extension,
        // avoid blocking on slow HTTP HEAD/Range requests that are often rejected or rate-limited.
        if (isKnownSocialWebpage(url)) {
            val res = InspectionResult(false, MediaType.VIDEO, "text/html", null)
            recordResult(effectiveTraceId, opId, res, "KNOWN_SOCIAL_WEBPAGE", "Fast-path skipped HEAD request for social platform webpage")
            return res
        }

        try {
            // 1. Attempt HTTP HEAD request
            if (effectiveTraceId != null) {
                MediaExtractionTracer.logEvent(
                    traceId = effectiveTraceId,
                    opId = opId,
                    component = "DirectMediaInspector",
                    stage = "DIRECT_INSPECTION",
                    event = "HTTP_HEAD_REQUEST_START",
                    level = TraceLevel.DEBUG,
                    input = url
                )
            }

            val headRequest = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Accept", "image/*,video/*,audio/*,*/*")
                .build()

            val headResponse = try {
                CancellableNetworkClient.executeCancellable(httpClient, headRequest)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }

            if (headResponse != null && headResponse.isSuccessful) {
                val rawContentType = headResponse.header("Content-Type").orEmpty().lowercase()
                val contentLength = headResponse.header("Content-Length")?.toLongOrNull()
                headResponse.close()

                if (effectiveTraceId != null) {
                    MediaExtractionTracer.logEvent(
                        traceId = effectiveTraceId,
                        opId = opId,
                        component = "DirectMediaInspector",
                        stage = "DIRECT_INSPECTION",
                        event = "HTTP_HEAD_RESPONSE",
                        level = TraceLevel.DEBUG,
                        output = "ContentType=$rawContentType ContentLength=$contentLength",
                        details = mapOf("rawContentType" to rawContentType, "contentLength" to (contentLength?.toString() ?: "unknown"))
                    )
                }

                val classified = classifyContentType(rawContentType, cleanUrl)
                if (classified != null) {
                    val res = InspectionResult(
                        isDirectMedia = true,
                        mediaType = classified.first,
                        mimeType = classified.second,
                        contentLength = contentLength,
                        suggestedExt = getExtensionForMime(classified.second, cleanUrl)
                    )
                    recordResult(effectiveTraceId, opId, res, "HEAD_CONTENT_TYPE_MATCH", "Matched MIME type ${classified.second}")
                    return res
                }

                // If content type is text/html or application/json, it's not direct media
                if (rawContentType.contains("text/html") || rawContentType.contains("application/xhtml") || rawContentType.contains("application/json")) {
                    val res = InspectionResult(false, MediaType.VIDEO, rawContentType, null)
                    recordResult(effectiveTraceId, opId, res, "HTML_OR_JSON_CONTENT", "Response Content-Type indicates non-media container: $rawContentType")
                    return res
                }
            } else {
                headResponse?.close()
            }

            // 2. Fallback to GET with Range or small stream to read headers & initial magic bytes
            if (effectiveTraceId != null) {
                MediaExtractionTracer.logEvent(
                    traceId = effectiveTraceId,
                    opId = opId,
                    component = "DirectMediaInspector",
                    stage = "DIRECT_INSPECTION",
                    event = "HTTP_RANGE_GET_START",
                    level = TraceLevel.DEBUG,
                    input = "Range: bytes=0-4095"
                )
            }

            val getRequest = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Range", "bytes=0-4095")
                .header("Accept", "image/*,video/*,audio/*,*/*")
                .build()

            val getResponse = try {
                CancellableNetworkClient.executeCancellable(httpClient, getRequest)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }

            if (getResponse != null) {
                getResponse.use { resp ->
                    val rawContentType = resp.header("Content-Type").orEmpty().lowercase()
                    var contentLength = resp.header("Content-Length")?.toLongOrNull()
                    val contentRange = resp.header("Content-Range")
                    if (contentRange != null && contentRange.contains("/")) {
                        val totalFromRange = contentRange.substringAfterLast("/").trim().toLongOrNull()
                        if (totalFromRange != null && totalFromRange > 0) {
                            contentLength = totalFromRange
                        }
                    }

                    if (!resp.isSuccessful && resp.code != 206) {
                        val fallback = inspectByExtensionOnly(cleanUrl)
                        recordResult(effectiveTraceId, opId, fallback, "HTTP_ERROR_EXT_FALLBACK", "HTTP status ${resp.code}, fell back to extension")
                        return fallback
                    }

                    // Read the first bytes for magic signature and dimension detection
                    val responseBody = resp.body
                    if (responseBody != null) {
                        val stream = responseBody.byteStream()
                        val headerBytes = ByteArray(4096)
                        val bytesRead = readFully(stream, headerBytes)

                        if (bytesRead > 0) {
                            // Check magic bytes
                            val magicClassified = classifyMagicBytes(headerBytes, bytesRead)
                            if (magicClassified != null) {
                                val (mediaType, mimeType) = magicClassified
                                val (w, h) = if (mediaType == MediaType.IMAGE) extractImageDimensions(headerBytes, bytesRead, mimeType) else Pair(null, null)
                                val res = InspectionResult(
                                    isDirectMedia = true,
                                    mediaType = mediaType,
                                    mimeType = mimeType,
                                    contentLength = contentLength,
                                    width = w,
                                    height = h,
                                    suggestedExt = getExtensionForMime(mimeType, cleanUrl)
                                )
                                recordResult(effectiveTraceId, opId, res, "MAGIC_BYTES_MATCH", "Identified magic bytes signature for $mimeType ($w x $h)")
                                return res
                            }
                        }
                    }

                    // Check Content-Type header if magic bytes were inconclusive
                    val classified = classifyContentType(rawContentType, cleanUrl)
                    if (classified != null) {
                        val res = InspectionResult(
                            isDirectMedia = true,
                            mediaType = classified.first,
                            mimeType = classified.second,
                            contentLength = contentLength,
                            suggestedExt = getExtensionForMime(classified.second, cleanUrl)
                        )
                        recordResult(effectiveTraceId, opId, res, "GET_CONTENT_TYPE_MATCH", "Matched MIME type from range response: ${classified.second}")
                        return res
                    }

                    // If text/html, it's definitely a webpage
                    if (rawContentType.contains("text/html") || rawContentType.contains("application/xhtml")) {
                        val res = InspectionResult(false, MediaType.VIDEO, rawContentType, null)
                        recordResult(effectiveTraceId, opId, res, "HTML_PAGE_CONTENT", "Range response content type is text/html")
                        return res
                    }
                }
            }

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.d("DirectMediaInspector", "Direct media inspection network check failed for $url: ${e.message}")
        }

        // Final fallback: extension inspection
        val fallback = inspectByExtensionOnly(cleanUrl)
        recordResult(effectiveTraceId, opId, fallback, "EXTENSION_ONLY_FALLBACK", "Network inspection inconclusive, evaluated extension only")
        return fallback
    }

    private fun recordResult(traceId: String?, opId: String?, res: InspectionResult, decision: String, reason: String) {
        if (traceId != null) {
            val session = MediaExtractionTracer.getSession(traceId)
            session?.directInspectionType = res.mediaType.name
            session?.directInspectionMime = res.mimeType

            if (opId != null) {
                MediaExtractionTracer.endOperation(
                    traceId = traceId,
                    opId = opId,
                    result = "isDirectMedia=${res.isDirectMedia} type=${res.mediaType} mime=${res.mimeType} length=${res.contentLength}",
                    decision = decision,
                    reason = reason,
                    details = mapOf(
                        "isDirectMedia" to res.isDirectMedia.toString(),
                        "mediaType" to res.mediaType.name,
                        "mimeType" to res.mimeType,
                        "contentLength" to (res.contentLength?.toString() ?: "unknown"),
                        "dimensions" to "${res.width ?: 0}x${res.height ?: 0}"
                    )
                )
            }
        }
    }

    private fun inspectByExtensionOnly(cleanUrl: String): InspectionResult {
        return when {
            cleanUrl.endsWith(".jpg") || cleanUrl.endsWith(".jpeg") ->
                InspectionResult(true, MediaType.IMAGE, "image/jpeg", null, suggestedExt = "jpg")
            cleanUrl.endsWith(".png") ->
                InspectionResult(true, MediaType.IMAGE, "image/png", null, suggestedExt = "png")
            cleanUrl.endsWith(".webp") ->
                InspectionResult(true, MediaType.IMAGE, "image/webp", null, suggestedExt = "webp")
            cleanUrl.endsWith(".gif") ->
                InspectionResult(true, MediaType.IMAGE, "image/gif", null, suggestedExt = "gif")
            cleanUrl.endsWith(".avif") ->
                InspectionResult(true, MediaType.IMAGE, "image/avif", null, suggestedExt = "avif")
            cleanUrl.endsWith(".bmp") ->
                InspectionResult(true, MediaType.IMAGE, "image/bmp", null, suggestedExt = "bmp")
            cleanUrl.endsWith(".heic") || cleanUrl.endsWith(".heif") ->
                InspectionResult(true, MediaType.IMAGE, "image/heic", null, suggestedExt = "heic")
            cleanUrl.endsWith(".svg") ->
                InspectionResult(true, MediaType.IMAGE, "image/svg+xml", null, suggestedExt = "svg")
            cleanUrl.endsWith(".mp4") ->
                InspectionResult(true, MediaType.VIDEO, "video/mp4", null, suggestedExt = "mp4")
            cleanUrl.endsWith(".webm") ->
                InspectionResult(true, MediaType.VIDEO, "video/webm", null, suggestedExt = "webm")
            cleanUrl.endsWith(".mkv") ->
                InspectionResult(true, MediaType.VIDEO, "video/x-matroska", null, suggestedExt = "mkv")
            cleanUrl.endsWith(".mov") ->
                InspectionResult(true, MediaType.VIDEO, "video/quicktime", null, suggestedExt = "mov")
            cleanUrl.endsWith(".mp3") ->
                InspectionResult(true, MediaType.AUDIO, "audio/mpeg", null, suggestedExt = "mp3")
            cleanUrl.endsWith(".m4a") ->
                InspectionResult(true, MediaType.AUDIO, "audio/mp4", null, suggestedExt = "m4a")
            cleanUrl.endsWith(".opus") || cleanUrl.endsWith(".ogg") ->
                InspectionResult(true, MediaType.AUDIO, "audio/opus", null, suggestedExt = "opus")
            cleanUrl.endsWith(".flac") ->
                InspectionResult(true, MediaType.AUDIO, "audio/flac", null, suggestedExt = "flac")
            cleanUrl.endsWith(".wav") ->
                InspectionResult(true, MediaType.AUDIO, "audio/wav", null, suggestedExt = "wav")
            else ->
                InspectionResult(false, MediaType.VIDEO, "unknown", null)
        }
    }

    fun classifyContentType(contentType: String, urlHint: String = ""): Pair<MediaType, String>? {
        val type = contentType.substringBefore(";").trim().lowercase()
        return when {
            // Images
            type.startsWith("image/") -> {
                val cleanType = if (type == "image/jpg") "image/jpeg" else type
                MediaType.IMAGE to cleanType
            }
            // Videos
            type.startsWith("video/") -> MediaType.VIDEO to type
            type == "application/x-mpegurl" || type == "application/vnd.apple.mpegurl" -> MediaType.VIDEO to "video/mp4"
            // Audios
            type.startsWith("audio/") -> MediaType.AUDIO to type
            type == "application/ogg" -> MediaType.AUDIO to "audio/ogg"
            // Octet stream with url hint
            type == "application/octet-stream" || type == "binary/octet-stream" -> {
                when {
                    urlHint.endsWith(".jpg") || urlHint.endsWith(".jpeg") -> MediaType.IMAGE to "image/jpeg"
                    urlHint.endsWith(".png") -> MediaType.IMAGE to "image/png"
                    urlHint.endsWith(".webp") -> MediaType.IMAGE to "image/webp"
                    urlHint.endsWith(".gif") -> MediaType.IMAGE to "image/gif"
                    urlHint.endsWith(".mp4") -> MediaType.VIDEO to "video/mp4"
                    urlHint.endsWith(".mp3") -> MediaType.AUDIO to "audio/mpeg"
                    else -> null
                }
            }
            else -> null
        }
    }

    fun classifyMagicBytes(bytes: ByteArray, length: Int): Pair<MediaType, String>? {
        if (length < 4) return null

        // JPEG: FF D8 FF
        if (length >= 3 && (bytes[0].toInt() and 0xFF) == 0xFF && (bytes[1].toInt() and 0xFF) == 0xD8 && (bytes[2].toInt() and 0xFF) == 0xFF) {
            return MediaType.IMAGE to "image/jpeg"
        }

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (length >= 8 && (bytes[0].toInt() and 0xFF) == 0x89 && bytes[1] == 'P'.code.toByte() && bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte()) {
            return MediaType.IMAGE to "image/png"
        }

        // GIF: GIF87a or GIF89a
        if (length >= 6 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() && bytes[3] == '8'.code.toByte()) {
            return MediaType.IMAGE to "image/gif"
        }

        // BMP: 'B' 'M'
        if (length >= 2 && bytes[0] == 'B'.code.toByte() && bytes[1] == 'M'.code.toByte()) {
            return MediaType.IMAGE to "image/bmp"
        }

        // WebP: RIFF .... WEBP
        if (length >= 12 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() && bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
        ) {
            return MediaType.IMAGE to "image/webp"
        }

        // Matroska / WebM EBML: 1A 45 DF A3
        if (length >= 4 && (bytes[0].toInt() and 0xFF) == 0x1A && (bytes[1].toInt() and 0xFF) == 0x45 && (bytes[2].toInt() and 0xFF) == 0xDF && (bytes[3].toInt() and 0xFF) == 0xA3) {
            return MediaType.VIDEO to "video/webm"
        }

        // MP4 / M4A / AVIF: ftyp box in first 32 bytes
        val headerString = String(bytes, 0, length.coerceAtMost(64), Charsets.US_ASCII)
        if (headerString.contains("ftyp")) {
            return when {
                headerString.contains("avif") || headerString.contains("avis") -> MediaType.IMAGE to "image/avif"
                headerString.contains("heic") || headerString.contains("heix") || headerString.contains("mif1") -> MediaType.IMAGE to "image/heic"
                headerString.contains("M4A ") -> MediaType.AUDIO to "audio/mp4"
                else -> MediaType.VIDEO to "video/mp4"
            }
        }

        // MP3: ID3 or sync frame
        if (length >= 3 && bytes[0] == 'I'.code.toByte() && bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()) {
            return MediaType.AUDIO to "audio/mpeg"
        }
        if (length >= 2 && (bytes[0].toInt() and 0xFF) == 0xFF && ((bytes[1].toInt() and 0xE0) == 0xE0)) {
            return MediaType.AUDIO to "audio/mpeg"
        }

        // FLAC: fLaC
        if (length >= 4 && bytes[0] == 'f'.code.toByte() && bytes[1] == 'L'.code.toByte() && bytes[2] == 'a'.code.toByte() && bytes[3] == 'C'.code.toByte()) {
            return MediaType.AUDIO to "audio/flac"
        }

        // Ogg / Opus: OggS
        if (length >= 4 && bytes[0] == 'O'.code.toByte() && bytes[1] == 'g'.code.toByte() && bytes[2] == 'g'.code.toByte() && bytes[3] == 'S'.code.toByte()) {
            return MediaType.AUDIO to "audio/ogg"
        }

        // WAV: RIFF .... WAVE
        if (length >= 12 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'A'.code.toByte() && bytes[10] == 'V'.code.toByte() && bytes[11] == 'E'.code.toByte()
        ) {
            return MediaType.AUDIO to "audio/wav"
        }

        return null
    }

    private fun extractImageDimensions(bytes: ByteArray, length: Int, mimeType: String): Pair<Int?, Int?> {
        return try {
            when (mimeType) {
                "image/png" -> {
                    if (length >= 24) {
                        val width = readInt32BE(bytes, 16)
                        val height = readInt32BE(bytes, 20)
                        if (width > 0 && height > 0) Pair(width, height) else Pair(null, null)
                    } else Pair(null, null)
                }
                "image/gif" -> {
                    if (length >= 10) {
                        val width = readInt16LE(bytes, 6)
                        val height = readInt16LE(bytes, 8)
                        if (width > 0 && height > 0) Pair(width, height) else Pair(null, null)
                    } else Pair(null, null)
                }
                "image/bmp" -> {
                    if (length >= 26) {
                        val width = readInt32LE(bytes, 18)
                        val height = Math.abs(readInt32LE(bytes, 22))
                        if (width > 0 && height > 0) Pair(width, height) else Pair(null, null)
                    } else Pair(null, null)
                }
                "image/jpeg" -> {
                    var offset = 2
                    var foundW: Int? = null
                    var foundH: Int? = null
                    while (offset + 8 < length) {
                        if ((bytes[offset].toInt() and 0xFF) == 0xFF) {
                            val marker = bytes[offset + 1].toInt() and 0xFF
                            // SOF0 (0xC0), SOF1 (0xC1), SOF2 (0xC2)
                            if (marker == 0xC0 || marker == 0xC1 || marker == 0xC2) {
                                val h = readInt16BE(bytes, offset + 5)
                                val w = readInt16BE(bytes, offset + 7)
                                if (w > 0 && h > 0) {
                                    foundW = w
                                    foundH = h
                                    break
                                }
                            }
                            val segmentLength = readInt16BE(bytes, offset + 2)
                            if (segmentLength <= 0) break
                            offset += 2 + segmentLength
                        } else {
                            offset++
                        }
                    }
                    Pair(foundW, foundH)
                }
                else -> Pair(null, null)
            }
        } catch (e: Exception) {
            Pair(null, null)
        }
    }

    private fun getExtensionForMime(mimeType: String, urlHint: String = ""): String {
        return when (mimeType.lowercase()) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/avif" -> "avif"
            "image/bmp" -> "bmp"
            "image/heic" -> "heic"
            "image/heif" -> "heif"
            "image/svg+xml" -> "svg"
            "video/mp4" -> "mp4"
            "video/webm" -> "webm"
            "video/x-matroska" -> "mkv"
            "video/quicktime" -> "mov"
            "audio/mpeg" -> "mp3"
            "audio/mp4", "audio/m4a", "audio/aac" -> "m4a"
            "audio/opus", "audio/ogg" -> "opus"
            "audio/flac" -> "flac"
            "audio/wav" -> "wav"
            else -> {
                val ext = urlHint.substringAfterLast('.', "")
                if (ext.isNotBlank() && ext.length in 2..5) ext else "bin"
            }
        }
    }

    private fun readFully(stream: InputStream, buffer: ByteArray): Int {
        var totalRead = 0
        while (totalRead < buffer.size) {
            val read = stream.read(buffer, totalRead, buffer.size - totalRead)
            if (read == -1) break
            totalRead += read
        }
        return totalRead
    }

    private fun readInt32BE(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                (bytes[offset + 3].toInt() and 0xFF)
    }

    private fun readInt32LE(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun readInt16BE(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 8) or
                (bytes[offset + 1].toInt() and 0xFF)
    }

    private fun readInt16LE(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }
}
