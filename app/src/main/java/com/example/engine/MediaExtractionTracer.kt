package com.example.engine

import com.example.data.model.MediaType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

enum class TraceLevel {
    TRACE, DEBUG, INFO, WARNING, ERROR
}

data class TraceEvent(
    val id: String = UUID.randomUUID().toString().take(8),
    val traceId: String,
    val opId: String? = null,
    val parentOpId: String? = null,
    val timestampNanos: Long = System.nanoTime(),
    val wallTimestamp: Long = System.currentTimeMillis(),
    val elapsedMs: Long = 0L,
    val durationMs: Long? = null,
    val threadName: String = Thread.currentThread().name,
    val component: String,
    val stage: String,
    val event: String,
    val level: TraceLevel = TraceLevel.DEBUG,
    val input: String? = null,
    val output: String? = null,
    val decision: String? = null,
    val reason: String? = null,
    val error: String? = null,
    val details: Map<String, String> = emptyMap()
)

data class CandidateProvenance(
    val candidateId: String,
    val traceId: String,
    val opId: String? = null,
    val source: String,           // e.g. "OG_IMAGE", "HTML_IMG_SRC", "HTML_ATTRIBUTE", "SCRIPT_JSON", "JSON_LD", "VIDEO_SOURCE", "TWITTER_IMAGE", "PLATFORM_METADATA", "DIRECT_HEAD"
    val subSource: String? = null,// e.g. "og:image", "meta[name=viewport]", "shreddit-gallery", "display_url", "edge_sidecar_to_children"
    val attribute: String? = null,// e.g. "content", "src", "href", "content-href"
    val rawValue: String,
    val normalizedValue: String? = null,
    val scheme: String? = null,
    val host: String? = null,
    val path: String? = null,
    val extension: String? = null,
    val mime: String? = null,
    val httpStatus: Int? = null,
    val isValidUrl: Boolean = false,
    val isHttp: Boolean = false,
    val isHttps: Boolean = false,
    val isMedia: Boolean = false,
    val mediaType: MediaType = MediaType.VIDEO,
    val confidence: Float = 0.5f,
    val accepted: Boolean = false,
    val rejected: Boolean = false,
    val rejectionReason: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class ActiveOperation(
    val opId: String,
    val traceId: String,
    val parentOpId: String?,
    val component: String,
    val stage: String,
    val name: String,
    val startNanos: Long = System.nanoTime(),
    val startWallTime: Long = System.currentTimeMillis(),
    val threadName: String = Thread.currentThread().name
)

data class TraceSession(
    val traceId: String,
    val startNanos: Long = System.nanoTime(),
    val startWallTime: Long = System.currentTimeMillis(),
    var endWallTime: Long? = null,
    var totalDurationMs: Long? = null,
    val originalUrl: String,
    var canonicalUrl: String? = null,
    var platform: String? = null,
    var intent: String? = null,
    var confidence: Float? = null,
    var directInspectionMime: String? = null,
    var directInspectionType: String? = null,
    var resolvedMediaType: MediaType? = null,
    var resolvedMime: String? = null,
    var selectedEngine: String? = null,
    var ytdlpEligible: Boolean? = null,
    var ytdlpEligibilityReason: String? = null,
    var ytdlpOutput: String? = null,
    var ytdlpError: String? = null,
    var ffmpegOutput: String? = null,
    var rawHtmlSnippet: String? = null,
    var finalResultSummary: String? = null,
    var finalError: String? = null,
    var isSuccess: Boolean = false,
    var isCancelled: Boolean = false,
    var isTimedOut: Boolean = false,
    val events: MutableList<TraceEvent> = java.util.Collections.synchronizedList(mutableListOf()),
    val candidates: MutableList<CandidateProvenance> = java.util.Collections.synchronizedList(mutableListOf()),
    val activeOps: ConcurrentHashMap<String, ActiveOperation> = ConcurrentHashMap()
)

object MediaExtractionTracer {

    private val opCounter = AtomicInteger(100)
    private val candidateCounter = AtomicInteger(1)
    private val sessions = ConcurrentHashMap<String, TraceSession>()
    private val recentSessionIds = java.util.Collections.synchronizedList(mutableListOf<String>())

    private val _currentSessionFlow = MutableStateFlow<TraceSession?>(null)
    val currentSessionFlow: StateFlow<TraceSession?> = _currentSessionFlow.asStateFlow()

    private val _allSessionsFlow = MutableStateFlow<List<TraceSession>>(emptyList())
    val allSessionsFlow: StateFlow<List<TraceSession>> = _allSessionsFlow.asStateFlow()

    private val _deepTraceEnabled = MutableStateFlow(true)
    val deepTraceEnabled: StateFlow<Boolean> = _deepTraceEnabled.asStateFlow()

    fun setDeepTraceEnabled(enabled: Boolean) {
        _deepTraceEnabled.value = enabled
    }

    fun generateTraceId(): String {
        return "TRACE:" + UUID.randomUUID().toString().take(8).uppercase(Locale.US)
    }

    fun generateOpId(): String {
        return "OP-" + opCounter.incrementAndGet()
    }

    fun generateCandidateId(): String {
        return "C-" + candidateCounter.incrementAndGet()
    }

    fun startSession(originalUrl: String, customTraceId: String? = null): TraceSession {
        val traceId = customTraceId ?: generateTraceId()
        val session = TraceSession(
            traceId = traceId,
            originalUrl = originalUrl
        )
        sessions[traceId] = session
        synchronized(recentSessionIds) {
            recentSessionIds.add(0, traceId)
            if (recentSessionIds.size > 25) {
                val removed = recentSessionIds.removeAt(recentSessionIds.size - 1)
                sessions.remove(removed)
            }
        }
        _currentSessionFlow.value = session
        updateAllSessionsFlow()

        logEvent(
            traceId = traceId,
            component = "MediaExtractionEngine",
            stage = "INPUT_RECEIVED",
            event = "SESSION_START",
            level = TraceLevel.INFO,
            input = originalUrl,
            details = mapOf("originalUrl" to originalUrl, "traceId" to traceId)
        )

        return session
    }

    fun getSession(traceId: String): TraceSession? = sessions[traceId]

    fun startOperation(
        traceId: String,
        component: String,
        stage: String,
        name: String,
        parentOpId: String? = null,
        details: Map<String, String> = emptyMap()
    ): String {
        val session = sessions[traceId]
        val opId = generateOpId()
        val op = ActiveOperation(
            opId = opId,
            traceId = traceId,
            parentOpId = parentOpId,
            component = component,
            stage = stage,
            name = name
        )
        session?.activeOps?.put(opId, op)

        logEvent(
            traceId = traceId,
            opId = opId,
            parentOpId = parentOpId,
            component = component,
            stage = stage,
            event = "${stage}_START",
            level = TraceLevel.DEBUG,
            input = name,
            details = details
        )

        return opId
    }

    fun endOperation(
        traceId: String,
        opId: String,
        result: String? = null,
        decision: String? = null,
        reason: String? = null,
        error: Throwable? = null,
        details: Map<String, String> = emptyMap()
    ) {
        val session = sessions[traceId]
        val op = session?.activeOps?.remove(opId)
        val durationMs = if (op != null) (System.nanoTime() - op.startNanos) / 1_000_000L else null

        val level = when {
            error != null -> TraceLevel.ERROR
            decision != null -> TraceLevel.INFO
            else -> TraceLevel.DEBUG
        }

        val component = op?.component ?: "UnknownComponent"
        val stage = op?.stage ?: "STAGE"

        logEvent(
            traceId = traceId,
            opId = opId,
            parentOpId = op?.parentOpId,
            component = component,
            stage = stage,
            event = if (error != null) "${stage}_FAILED" else "${stage}_RESULT",
            level = level,
            output = result,
            decision = decision,
            reason = reason,
            error = error?.message ?: error?.toString(),
            durationMs = durationMs,
            details = details
        )
    }

    fun logEvent(
        traceId: String,
        opId: String? = null,
        parentOpId: String? = null,
        component: String,
        stage: String,
        event: String,
        level: TraceLevel = TraceLevel.DEBUG,
        input: String? = null,
        output: String? = null,
        decision: String? = null,
        reason: String? = null,
        error: String? = null,
        durationMs: Long? = null,
        details: Map<String, String> = emptyMap()
    ) {
        val session = sessions[traceId]
        val elapsedMs = if (session != null) (System.nanoTime() - session.startNanos) / 1_000_000L else 0L

        val traceEvent = TraceEvent(
            traceId = traceId,
            opId = opId,
            parentOpId = parentOpId,
            elapsedMs = elapsedMs,
            durationMs = durationMs,
            component = component,
            stage = stage,
            event = event,
            level = level,
            input = input,
            output = output,
            decision = decision,
            reason = reason,
            error = error,
            details = details
        )

        session?.events?.add(traceEvent)

        // Log to centralized AppLogger for persistence and logcat visibility
        val logMsg = buildString {
            append("[$traceId]")
            if (opId != null) append("[$opId]")
            append("[$stage] $event")
            if (decision != null) append(" -> decision=$decision")
            if (reason != null) append(" (reason: $reason)")
            if (input != null) append(" | input=${input.take(150)}")
            if (output != null) append(" | output=${output.take(150)}")
            if (error != null) append(" | ERROR: $error")
            if (durationMs != null) append(" | took ${durationMs}ms")
        }

        when (level) {
            TraceLevel.TRACE, TraceLevel.DEBUG -> AppLogger.d(component, logMsg)
            TraceLevel.INFO -> AppLogger.i(component, logMsg)
            TraceLevel.WARNING -> AppLogger.w(component, logMsg)
            TraceLevel.ERROR -> AppLogger.e(component, logMsg)
        }

        if (_currentSessionFlow.value?.traceId == traceId) {
            _currentSessionFlow.value = session
        }
    }

    fun logCandidate(
        traceId: String,
        source: String,
        subSource: String? = null,
        attribute: String? = null,
        rawValue: String,
        opId: String? = null,
        accepted: Boolean = false,
        rejected: Boolean = false,
        rejectionReason: String? = null,
        mediaType: MediaType = MediaType.IMAGE,
        confidence: Float = 0.5f,
        mime: String? = null
    ): CandidateProvenance {
        val session = sessions[traceId]
        val candidateId = generateCandidateId()

        val parsedUri = try {
            if (rawValue.startsWith("http://", ignoreCase = true) || rawValue.startsWith("https://", ignoreCase = true)) {
                URI(rawValue)
            } else null
        } catch (_: Exception) {
            null
        }

        val isValid = parsedUri != null && !parsedUri.host.isNullOrBlank()
        val isHttp = parsedUri?.scheme.equals("http", ignoreCase = true)
        val isHttps = parsedUri?.scheme.equals("https", ignoreCase = true)
        val scheme = parsedUri?.scheme
        val host = parsedUri?.host
        val path = parsedUri?.path
        val extension = path?.substringAfterLast(".", "")?.ifBlank { null }
        val isMedia = MediaTypeResolver.isDirectMediaUrl(rawValue)

        val candidate = CandidateProvenance(
            candidateId = candidateId,
            traceId = traceId,
            opId = opId,
            source = source,
            subSource = subSource,
            attribute = attribute,
            rawValue = rawValue,
            normalizedValue = if (isValid) rawValue else null,
            scheme = scheme,
            host = host,
            path = path,
            extension = extension,
            mime = mime,
            isValidUrl = isValid,
            isHttp = isHttp,
            isHttps = isHttps,
            isMedia = isMedia,
            mediaType = mediaType,
            confidence = confidence,
            accepted = accepted,
            rejected = rejected,
            rejectionReason = rejectionReason
        )

        session?.candidates?.add(candidate)

        logEvent(
            traceId = traceId,
            opId = opId,
            component = "CandidateDiscovery",
            stage = if (accepted) "CANDIDATE_ACCEPTED" else if (rejected) "CANDIDATE_REJECTED" else "CANDIDATE_DISCOVERED",
            event = "CANDIDATE_${candidateId}",
            level = if (rejected) TraceLevel.WARNING else TraceLevel.DEBUG,
            input = "source=$source attribute=$attribute raw=${rawValue.take(100)}",
            decision = if (accepted) "ACCEPTED" else if (rejected) "REJECTED" else "EVALUATING",
            reason = rejectionReason ?: (if (accepted) "Valid media candidate" else "Candidate discovered"),
            details = mapOf(
                "candidateId" to candidateId,
                "source" to source,
                "subSource" to (subSource ?: ""),
                "attribute" to (attribute ?: ""),
                "isValidUrl" to isValid.toString(),
                "isMedia" to isMedia.toString(),
                "mediaType" to mediaType.name
            )
        )

        return candidate
    }

    fun recordHtmlCapture(traceId: String, html: String, sourceUrl: String) {
        val session = sessions[traceId] ?: return
        session.rawHtmlSnippet = html.take(8000)
        logEvent(
            traceId = traceId,
            component = "PageMetadataExtractor",
            stage = "HTML_FETCH_RESULT",
            event = "HTML_CAPTURED",
            level = TraceLevel.DEBUG,
            details = mapOf(
                "sourceUrl" to sourceUrl,
                "htmlLength" to html.length.toString(),
                "snippetLength" to session.rawHtmlSnippet.orEmpty().length.toString()
            )
        )
    }

    fun recordYtDlpOutput(traceId: String, stdout: String?, stderr: String?) {
        val session = sessions[traceId] ?: return
        if (!stdout.isNullOrBlank()) session.ytdlpOutput = stdout.take(4000)
        if (!stderr.isNullOrBlank()) session.ytdlpError = stderr.take(4000)
    }

    fun recordFFmpegOutput(traceId: String, output: String?) {
        val session = sessions[traceId] ?: return
        if (!output.isNullOrBlank()) session.ffmpegOutput = output.take(4000)
    }

    fun completeSession(
        traceId: String,
        isSuccess: Boolean,
        summary: String? = null,
        error: Throwable? = null,
        isCancelled: Boolean = false,
        isTimedOut: Boolean = false
    ) {
        val session = sessions[traceId] ?: return
        session.endWallTime = System.currentTimeMillis()
        session.totalDurationMs = (System.nanoTime() - session.startNanos) / 1_000_000L
        session.isSuccess = isSuccess
        session.isCancelled = isCancelled
        session.isTimedOut = isTimedOut
        session.finalResultSummary = summary
        session.finalError = error?.message ?: error?.toString()

        val eventName = when {
            isCancelled -> "ANALYSIS_CANCELLED"
            isTimedOut -> "ANALYSIS_TIMED_OUT"
            isSuccess -> "ANALYSIS_COMPLETE"
            else -> "ANALYSIS_FAILED"
        }

        val level = when {
            isSuccess -> TraceLevel.INFO
            isCancelled -> TraceLevel.WARNING
            else -> TraceLevel.ERROR
        }

        logEvent(
            traceId = traceId,
            component = "MediaExtractionEngine",
            stage = eventName,
            event = "SESSION_END",
            level = level,
            output = summary,
            error = session.finalError,
            durationMs = session.totalDurationMs,
            details = mapOf(
                "totalDurationMs" to (session.totalDurationMs?.toString() ?: "0"),
                "isSuccess" to isSuccess.toString(),
                "isCancelled" to isCancelled.toString(),
                "isTimedOut" to isTimedOut.toString()
            )
        )

        updateAllSessionsFlow()
    }

    fun checkStalledOperations(traceId: String, thresholdMs: Long = 6000L): List<ActiveOperation> {
        val session = sessions[traceId] ?: return emptyList()
        val now = System.nanoTime()
        val stalled = mutableListOf<ActiveOperation>()

        for ((_, op) in session.activeOps) {
            val elapsedMs = (now - op.startNanos) / 1_000_000L
            if (elapsedMs > thresholdMs) {
                stalled.add(op)
                logEvent(
                    traceId = traceId,
                    opId = op.opId,
                    parentOpId = op.parentOpId,
                    component = op.component,
                    stage = "STAGE_STALLED",
                    event = "WATCHDOG_STALL_WARNING",
                    level = TraceLevel.WARNING,
                    reason = "Operation ${op.name} has been running for ${elapsedMs}ms (> threshold ${thresholdMs}ms)",
                    details = mapOf(
                        "elapsedMs" to elapsedMs.toString(),
                        "thresholdMs" to thresholdMs.toString(),
                        "thread" to op.threadName
                    )
                )
            }
        }
        return stalled
    }

    private fun updateAllSessionsFlow() {
        val list = mutableListOf<TraceSession>()
        synchronized(recentSessionIds) {
            for (id in recentSessionIds) {
                sessions[id]?.let { list.add(it) }
            }
        }
        _allSessionsFlow.value = list
    }

    fun clearAllTraces() {
        sessions.clear()
        synchronized(recentSessionIds) {
            recentSessionIds.clear()
        }
        _currentSessionFlow.value = null
        _allSessionsFlow.value = emptyList()
    }

    fun generateSummaryMarkdown(session: TraceSession): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        return buildString {
            appendLine("================================================================================")
            appendLine("DEEP TRACE DIAGNOSTIC REPORT: [${session.traceId}]")
            appendLine("================================================================================")
            appendLine("- **Start Time**: ${sdf.format(Date(session.startWallTime))}")
            appendLine("- **Total Duration**: ${session.totalDurationMs ?: ((System.nanoTime() - session.startNanos) / 1_000_000L)} ms")
            appendLine("- **Final Status**: ${if (session.isSuccess) "SUCCESS" else if (session.isCancelled) "CANCELLED" else if (session.isTimedOut) "TIMED OUT" else "FAILED"}")
            if (session.finalError != null) {
                appendLine("- **Final Error**: ${session.finalError}")
            }
            appendLine()
            appendLine("### 1. URL & Platform Identification")
            appendLine("- **Original URL**: `${session.originalUrl}`")
            appendLine("- **Canonical URL**: `${session.canonicalUrl ?: "Unresolved"}`")
            appendLine("- **Detected Platform**: `${session.platform ?: "Unknown"}`")
            appendLine("- **Semantic Intent**: `${session.intent ?: "Unknown"}` (Confidence: ${session.confidence ?: 1.0f})")
            appendLine("- **Resolved Media Type**: `${session.resolvedMediaType?.name ?: "Unknown"}`")
            appendLine("- **Resolved MIME Type**: `${session.resolvedMime ?: "Unknown"}`")
            appendLine("- **Selected Engine**: `${session.selectedEngine ?: "None"}`")
            appendLine("- **yt-dlp Eligible**: `${session.ytdlpEligible ?: false}` (Reason: ${session.ytdlpEligibilityReason ?: "N/A"})")
            appendLine()
            appendLine("### 2. Candidate Discovery & Provenance Table (${session.candidates.size} Candidates)")
            if (session.candidates.isEmpty()) {
                appendLine("*(No media candidates were discovered)*")
            } else {
                appendLine("| ID | Source | Attribute | Value | Valid | Media | Status | Rejection Reason |")
                appendLine("|----|--------|-----------|-------|-------|-------|--------|------------------|")
                for (c in session.candidates) {
                    val status = if (c.accepted) "ACCEPTED" else if (c.rejected) "REJECTED" else "PENDING"
                    val valSnippet = c.rawValue.replace("|", "\\|").take(40)
                    appendLine("| ${c.candidateId} | ${c.source} | ${c.attribute ?: "-"} | `$valSnippet` | ${c.isValidUrl} | ${c.isMedia} | **$status** | ${c.rejectionReason ?: "-"} |")
                }
            }
            appendLine()
            appendLine("### 3. Active / Stalled Operations (${session.activeOps.size})")
            if (session.activeOps.isEmpty()) {
                appendLine("*(All operations completed cleanly)*")
            } else {
                for ((opId, op) in session.activeOps) {
                    val activeMs = (System.nanoTime() - op.startNanos) / 1_000_000L
                    appendLine("- `[$opId]` **${op.component} / ${op.stage}**: ${op.name} (Running for ${activeMs}ms on thread `${op.threadName}`)")
                }
            }
            appendLine()
            appendLine("### 4. High-Precision Pipeline Timeline (${session.events.size} Events)")
            val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
            for (e in session.events) {
                val sign = if (e.elapsedMs >= 0) "+" else ""
                val dur = if (e.durationMs != null) " [took ${e.durationMs}ms]" else ""
                val err = if (e.error != null) " [ERR: ${e.error}]" else ""
                val dec = if (e.decision != null) " -> ${e.decision}" else ""
                val op = if (e.opId != null) " (${e.opId})" else ""
                appendLine("[${timeFmt.format(Date(e.wallTimestamp))}][${sign}${e.elapsedMs}ms][${e.level.name}] ${e.component} :: ${e.stage} :: ${e.event}$op$dur$dec$err")
            }

            if (!session.ytdlpError.isNullOrBlank()) {
                appendLine()
                appendLine("### 5. yt-dlp Output / Stderr")
                appendLine("```")
                appendLine(session.ytdlpError)
                appendLine("```")
            }

            if (!session.ffmpegOutput.isNullOrBlank()) {
                appendLine()
                appendLine("### 6. FFmpeg Output")
                appendLine("```")
                appendLine(session.ffmpegOutput)
                appendLine("```")
            }
            appendLine("================================================================================")
        }
    }

    fun generateDetailedJson(session: TraceSession): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        return buildString {
            append("{\n")
            append("  \"traceId\": \"${session.traceId}\",\n")
            append("  \"originalUrl\": \"${escapeJson(session.originalUrl)}\",\n")
            append("  \"canonicalUrl\": \"${escapeJson(session.canonicalUrl ?: "")}\",\n")
            append("  \"platform\": \"${escapeJson(session.platform ?: "")}\",\n")
            append("  \"intent\": \"${escapeJson(session.intent ?: "")}\",\n")
            append("  \"resolvedMediaType\": \"${session.resolvedMediaType?.name ?: ""}\",\n")
            append("  \"selectedEngine\": \"${escapeJson(session.selectedEngine ?: "")}\",\n")
            append("  \"totalDurationMs\": ${session.totalDurationMs ?: 0},\n")
            append("  \"isSuccess\": ${session.isSuccess},\n")
            append("  \"finalError\": \"${escapeJson(session.finalError ?: "")}\",\n")
            append("  \"candidates\": [\n")
            session.candidates.forEachIndexed { idx, c ->
                append("    {\n")
                append("      \"candidateId\": \"${c.candidateId}\",\n")
                append("      \"source\": \"${escapeJson(c.source)}\",\n")
                append("      \"attribute\": \"${escapeJson(c.attribute ?: "")}\",\n")
                append("      \"rawValue\": \"${escapeJson(c.rawValue)}\",\n")
                append("      \"isValidUrl\": ${c.isValidUrl},\n")
                append("      \"isMedia\": ${c.isMedia},\n")
                append("      \"accepted\": ${c.accepted},\n")
                append("      \"rejected\": ${c.rejected},\n")
                append("      \"rejectionReason\": \"${escapeJson(c.rejectionReason ?: "")}\"\n")
                append("    }${if (idx < session.candidates.size - 1) "," else ""}\n")
            }
            append("  ],\n")
            append("  \"events\": [\n")
            session.events.forEachIndexed { idx, e ->
                append("    {\n")
                append("      \"elapsedMs\": ${e.elapsedMs},\n")
                append("      \"component\": \"${escapeJson(e.component)}\",\n")
                append("      \"stage\": \"${escapeJson(e.stage)}\",\n")
                append("      \"event\": \"${escapeJson(e.event)}\",\n")
                append("      \"level\": \"${e.level.name}\",\n")
                append("      \"decision\": \"${escapeJson(e.decision ?: "")}\",\n")
                append("      \"reason\": \"${escapeJson(e.reason ?: "")}\",\n")
                append("      \"error\": \"${escapeJson(e.error ?: "")}\"\n")
                append("    }${if (idx < session.events.size - 1) "," else ""}\n")
            }
            append("  ]\n")
            append("}")
        }
    }

    private fun escapeJson(str: String): String {
        return str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
