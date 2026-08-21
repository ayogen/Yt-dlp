package com.example.data.model

data class DiagnosticReport(
    val timestamp: Long = System.currentTimeMillis(),
    val appVersion: String,
    val androidSdk: Int,
    val deviceModel: String,
    val deviceAbi: String,
    val ytdlpStatus: String,
    val ytdlpVersion: String,
    val ffmpegStatus: String,
    val ffmpegVersion: String,
    val ffmpegBinaryPath: String,
    val networkConnected: Boolean,
    val networkType: String,
    val networkHasInternet: Boolean,
    val internalStorageFreeBytes: Long,
    val internalStorageTotalBytes: Long,
    val downloadLocationUri: String,
    val downloadLocationWritable: Boolean,
    val backgroundExecutionActive: Boolean,
    val activeConcurrencySlots: Int,
    val maxConcurrentDownloads: Int,
    val storageTestPassed: Boolean,
    val diagnosticsLogsSummary: String
) {
    fun toSanitizedMarkdown(): String {
        return buildString {
            appendLine("### Transcode Diagnostic Report")
            appendLine("- **Date/Time**: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(timestamp))}")
            appendLine("- **Application Version**: $appVersion")
            appendLine("- **Android OS**: API $androidSdk ($deviceModel)")
            appendLine("- **Primary Architecture (ABI)**: $deviceAbi")
            appendLine()
            appendLine("#### Media Engine Runtime")
            appendLine("- **yt-dlp Core**: $ytdlpStatus (Version: $ytdlpVersion)")
            appendLine("- **Native FFmpeg**: $ffmpegStatus (Version: $ffmpegVersion)")
            appendLine("- **FFmpeg Binary Path**: $ffmpegBinaryPath")
            appendLine()
            appendLine("#### Network & Connectivity")
            appendLine("- **Active Network**: $networkType (Connected: $networkConnected, Internet Capable: $networkHasInternet)")
            appendLine()
            appendLine("#### Storage & Permissions")
            appendLine("- **App Storage Free Space**: ${formatBytes(internalStorageFreeBytes)} / ${formatBytes(internalStorageTotalBytes)}")
            appendLine("- **Download Location Active**: ${if (downloadLocationUri.isNotBlank()) "SAF Directory" else "Default App Storage"}")
            appendLine("- **Write Permission Verified**: ${if (downloadLocationWritable) "PASS (Writable)" else "FAIL (Not Writable / Default App Storage)"}")
            appendLine("- **Write Self-Test**: ${if (storageTestPassed) "SUCCESS" else "FAIL"}")
            appendLine()
            appendLine("#### Download Pipeline & Service")
            appendLine("- **Background Service Running**: $backgroundExecutionActive")
            appendLine("- **Concurrency Slots**: $activeConcurrencySlots / $maxConcurrentDownloads")
            appendLine()
            appendLine("*(Note: All credentials, URLs, cookies, and tokens have been sanitized from this report)*")
        }
    }
}
