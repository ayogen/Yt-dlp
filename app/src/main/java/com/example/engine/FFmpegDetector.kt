package com.example.engine

import android.content.Context
import android.os.Build
import com.example.data.model.EngineState
import java.io.File
import java.util.concurrent.TimeUnit

enum class FFmpegState(val displayName: String) {
    AVAILABLE("FFmpeg Available"),
    MISSING("FFmpeg Missing"),
    INVALID_NOT_EXECUTABLE("FFmpeg Invalid/Not Executable")
}

data class BinaryExecutionResult(
    val isSuccess: Boolean,
    val versionLine: String?,
    val exitCode: Int,
    val output: String,
    val errorMessage: String? = null
)

data class FFmpegStatus(
    val state: FFmpegState,
    val binaryPath: String?,
    val ffprobePath: String?,
    val version: String?,
    val ffprobeVersion: String?,
    val isExecutable: Boolean,
    val isFfprobeAvailable: Boolean,
    val abi: String,
    val fileSize: Long,
    val capabilities: List<String>,
    val guidance: String,
    val diagnosticDetails: String? = null
) {
    val isAvailable: Boolean get() = state == FFmpegState.AVAILABLE
    val engineState: EngineState get() = when (state) {
        FFmpegState.AVAILABLE -> EngineState.READY
        FFmpegState.MISSING -> EngineState.MISSING
        FFmpegState.INVALID_NOT_EXECUTABLE -> EngineState.INVALID
    }
}

object FFmpegDetector {
    private const val TAG = "FFmpegDetector"
    private const val EXECUTION_TIMEOUT_SECONDS = 5L

    fun getPreferredBinDir(context: Context): File {
        val binDir = File(context.filesDir, "bin")
        if (!binDir.exists()) {
            binDir.mkdirs()
        }
        return binDir
    }

    fun getPreferredFFmpegFile(context: Context): File {
        return File(getPreferredBinDir(context), "ffmpeg")
    }

    fun getPreferredFFprobeFile(context: Context): File {
        return File(getPreferredBinDir(context), "ffprobe")
    }

    /**
     * Detects FFmpeg status according to strict Android specifications.
     * Prefers ONLY context.filesDir/bin/ffmpeg and does NOT depend on system/temp directories.
     */
    fun detect(context: Context): FFmpegStatus {
        val abiList = Build.SUPPORTED_ABIS.joinToString(", ")
        val primaryAbi = if (Build.SUPPORTED_ABIS.isNotEmpty()) Build.SUPPORTED_ABIS[0] else "unknown"
        val ffmpegFile = getPreferredFFmpegFile(context)
        val ffprobeFile = getPreferredFFprobeFile(context)

        // 1. Check if file exists
        if (!ffmpegFile.exists()) {
            AppLogger.i(TAG, "FFmpeg binary missing at ${ffmpegFile.absolutePath} (ABIs: $abiList)")
            return FFmpegStatus(
                state = FFmpegState.MISSING,
                binaryPath = null,
                ffprobePath = null,
                version = null,
                ffprobeVersion = null,
                isExecutable = false,
                isFfprobeAvailable = false,
                abi = primaryAbi,
                fileSize = 0L,
                capabilities = emptyList(),
                guidance = "FFmpeg is not installed on this device. A native executable is required for 1080p+ stream muxing and audio conversions. Please tap 'Install' below.",
                diagnosticDetails = "File missing at ${ffmpegFile.absolutePath}. Device ABIs: $abiList"
            )
        }

        val fileSize = ffmpegFile.length()
        if (fileSize == 0L) {
            AppLogger.w(TAG, "FFmpeg binary exists at ${ffmpegFile.absolutePath} but is 0 bytes (corrupted).")
            // Clean up 0-byte corrupt file
            try { ffmpegFile.delete() } catch (e: Exception) {}
            return FFmpegStatus(
                state = FFmpegState.MISSING,
                binaryPath = null,
                ffprobePath = null,
                version = null,
                ffprobeVersion = null,
                isExecutable = false,
                isFfprobeAvailable = false,
                abi = primaryAbi,
                fileSize = 0L,
                capabilities = emptyList(),
                guidance = "FFmpeg binary was empty/corrupted and removed. Please tap 'Install' to download a valid binary.",
                diagnosticDetails = "Zero-byte file at ${ffmpegFile.absolutePath}"
            )
        }

        // Ensure executable permissions
        ensureExecutable(ffmpegFile)
        val canExec = ffmpegFile.canExecute()

        // 2. Actually execute `ffmpeg -version` with strict timeout
        val ffmpegExecResult = executeBinary(ffmpegFile, "ffmpeg")

        // 3. Check ffprobe separately (FFmpeg availability does NOT imply FFprobe availability)
        val ffprobeStatus = checkFFprobe(ffprobeFile)

        AppLogger.i(
            TAG,
            "FFmpeg Check -> Path: ${ffmpegFile.absolutePath}, Size: $fileSize bytes, canExecute: $canExec, " +
                    "ExitCode: ${ffmpegExecResult.exitCode}, Success: ${ffmpegExecResult.isSuccess}, " +
                    "Version: ${ffmpegExecResult.versionLine ?: "N/A"}, Error: ${ffmpegExecResult.errorMessage ?: "None"}"
        )
        AppLogger.i(
            TAG,
            "FFprobe Check -> Path: ${if (ffprobeFile.exists()) ffprobeFile.absolutePath else "N/A"}, " +
                    "Available: ${ffprobeStatus.first}, Version: ${ffprobeStatus.second ?: "N/A"}"
        )

        if (ffmpegExecResult.isSuccess && ffmpegExecResult.versionLine != null) {
            return FFmpegStatus(
                state = FFmpegState.AVAILABLE,
                binaryPath = ffmpegFile.absolutePath,
                ffprobePath = if (ffprobeStatus.first) ffprobeFile.absolutePath else null,
                version = ffmpegExecResult.versionLine,
                ffprobeVersion = ffprobeStatus.second,
                isExecutable = true,
                isFfprobeAvailable = ffprobeStatus.first,
                abi = primaryAbi,
                fileSize = fileSize,
                capabilities = listOf(
                    "Video + Audio Muxing (1080p, 1440p, 4K, 8K)",
                    "Audio Extraction & Transcoding (MP3, M4A, FLAC, Opus)",
                    "MP4 / MKV / WebM Container Remuxing",
                    "Metadata & Subtitle Processing"
                ),
                guidance = "Native FFmpeg binary active and verified (${ffmpegExecResult.versionLine}).",
                diagnosticDetails = "ABI: $primaryAbi | Size: ${fileSize / (1024 * 1024)}MB | Path: ${ffmpegFile.absolutePath}"
            )
        } else {
            val diag = ffmpegExecResult.errorMessage ?: "Exit code ${ffmpegExecResult.exitCode}"
            return FFmpegStatus(
                state = FFmpegState.INVALID_NOT_EXECUTABLE,
                binaryPath = ffmpegFile.absolutePath,
                ffprobePath = null,
                version = null,
                ffprobeVersion = null,
                isExecutable = false,
                isFfprobeAvailable = false,
                abi = primaryAbi,
                fileSize = fileSize,
                capabilities = emptyList(),
                guidance = "Binary exists at ${ffmpegFile.absolutePath} but failed execution ($diag). Tap 'Reinstall' to install a compatible binary.",
                diagnosticDetails = "Execution test failed: $diag. ABIs: $abiList. canExecute=$canExec, size=$fileSize"
            )
        }
    }

    private fun checkFFprobe(ffprobeFile: File): Pair<Boolean, String?> {
        if (!ffprobeFile.exists() || ffprobeFile.length() == 0L) {
            return Pair(false, null)
        }
        ensureExecutable(ffprobeFile)
        val result = executeBinary(ffprobeFile, "ffprobe")
        return if (result.isSuccess && result.versionLine != null) {
            Pair(true, result.versionLine)
        } else {
            Pair(false, null)
        }
    }

    fun ensureExecutable(file: File): Boolean {
        if (!file.exists()) return false
        try {
            file.setReadable(true, false)
            file.setWritable(true, false)
            file.setExecutable(true, false)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed setting file permissions on ${file.name}: ${e.message}")
        }
        try {
            val pb = ProcessBuilder("chmod", "755", file.absolutePath)
            val p = pb.start()
            p.waitFor(2, TimeUnit.SECONDS)
        } catch (e: Exception) {
            // Ignore if restricted
        }
        return file.exists() && file.length() > 0 && file.canExecute()
    }

    /**
     * Executes `binary -version` with a 5-second timeout and captures real process output and error diagnostics.
     */
    fun executeBinary(binary: File, expectedKeyword: String): BinaryExecutionResult {
        if (!binary.exists()) {
            return BinaryExecutionResult(false, null, -1, "", "File does not exist")
        }
        if (binary.length() == 0L) {
            return BinaryExecutionResult(false, null, -1, "", "File is 0 bytes")
        }

        return try {
            val process = ProcessBuilder(binary.absolutePath, "-version")
                .redirectErrorStream(true)
                .start()

            val outputBuffer = StringBuilder()
            val readerThread = Thread {
                try {
                    process.inputStream.bufferedReader().use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            outputBuffer.appendLine(line)
                        }
                    }
                } catch (e: Exception) {
                    // Stream closed
                }
            }
            readerThread.start()

            val finished = process.waitFor(EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                readerThread.interrupt()
                return BinaryExecutionResult(
                    isSuccess = false,
                    versionLine = null,
                    exitCode = -1,
                    output = outputBuffer.toString(),
                    errorMessage = "Execution timed out after ${EXECUTION_TIMEOUT_SECONDS}s"
                )
            }

            readerThread.join(500)
            val exitCode = process.exitValue()
            val rawOutput = outputBuffer.toString().trim()
            val firstLine = rawOutput.lines().firstOrNull { it.isNotBlank() }?.trim()

            val isMatch = exitCode == 0 && firstLine != null && firstLine.contains(expectedKeyword, ignoreCase = true)

            if (isMatch) {
                BinaryExecutionResult(
                    isSuccess = true,
                    versionLine = firstLine,
                    exitCode = exitCode,
                    output = rawOutput,
                    errorMessage = null
                )
            } else {
                val errorMsg = if (exitCode != 0) {
                    "Process exited with code $exitCode: ${rawOutput.take(150)}"
                } else {
                    "Output did not contain expected keyword '$expectedKeyword': ${rawOutput.take(150)}"
                }
                BinaryExecutionResult(
                    isSuccess = false,
                    versionLine = null,
                    exitCode = exitCode,
                    output = rawOutput,
                    errorMessage = errorMsg
                )
            }
        } catch (e: Exception) {
            val message = e.message ?: e.javaClass.simpleName
            AppLogger.w(TAG, "ProcessBuilder failed on ${binary.name}: $message")
            BinaryExecutionResult(
                isSuccess = false,
                versionLine = null,
                exitCode = -1,
                output = "",
                errorMessage = message
            )
        }
    }
}
