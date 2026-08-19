package com.example.engine

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

object FFmpegBinaryManager {
    private const val TAG = "FFmpegBinaryManager"

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun getBinDir(context: Context): File {
        return FFmpegDetector.getPreferredBinDir(context)
    }

    fun getFFmpegFile(context: Context): File {
        return FFmpegDetector.getPreferredFFmpegFile(context)
    }

    fun getFFprobeFile(context: Context): File {
        return FFmpegDetector.getPreferredFFprobeFile(context)
    }

    data class AbiCandidate(
        val abiName: String,
        val urls: List<String>
    )

    /**
     * Resolves high-priority verified native Android static binary URLs for the supported ABIs.
     * Uses standalone static PIE executables built specifically for Android Bionic runtime.
     */
    fun getCandidateSourcesForDevice(): List<AbiCandidate> {
        val supported = Build.SUPPORTED_ABIS
        val candidates = mutableListOf<AbiCandidate>()

        for (abi in supported) {
            val normalized = normalizeAbi(abi)
            val urls = when (normalized) {
                "arm64-v8a" -> listOf(
                    "https://github.com/Tyrrrz/FFmpegBin/releases/download/8.1.2/ffmpeg-android-arm64.zip",
                    "https://github.com/husen-hn/ffmpeg-android-binary/releases/download/v2.0.0/arm64-v8a.zip"
                )
                "armeabi-v7a" -> listOf(
                    "https://github.com/Tyrrrz/FFmpegBin/releases/download/8.1.2/ffmpeg-android-arm.zip",
                    "https://github.com/husen-hn/ffmpeg-android-binary/releases/download/v2.0.0/armeabi-v7a.zip"
                )
                "x86_64" -> listOf(
                    "https://github.com/Tyrrrz/FFmpegBin/releases/download/8.1.2/ffmpeg-android-x64.zip",
                    "https://github.com/husen-hn/ffmpeg-android-binary/releases/download/v2.0.0/x86_64.zip"
                )
                "x86" -> listOf(
                    "https://github.com/Tyrrrz/FFmpegBin/releases/download/8.1.2/ffmpeg-android-x86.zip"
                )
                else -> emptyList()
            }

            if (urls.isNotEmpty() && candidates.none { it.abiName == normalized }) {
                candidates.add(AbiCandidate(normalized, urls))
            }
        }

        // Fallback default if list is somehow empty
        if (candidates.isEmpty()) {
            candidates.add(
                AbiCandidate(
                    "arm64-v8a",
                    listOf("https://github.com/Tyrrrz/FFmpegBin/releases/download/8.1.2/ffmpeg-android-arm64.zip")
                )
            )
        }

        return candidates
    }

    private fun normalizeAbi(rawAbi: String): String {
        return when {
            rawAbi.contains("arm64", ignoreCase = true) || rawAbi.contains("aarch64", ignoreCase = true) -> "arm64-v8a"
            rawAbi.contains("armeabi", ignoreCase = true) || rawAbi.contains("v7a", ignoreCase = true) -> "armeabi-v7a"
            rawAbi.contains("x86_64", ignoreCase = true) || rawAbi.contains("amd64", ignoreCase = true) -> "x86_64"
            rawAbi.contains("x86", ignoreCase = true) || rawAbi.contains("i686", ignoreCase = true) || rawAbi.contains("i386", ignoreCase = true) -> "x86"
            else -> rawAbi
        }
    }

    suspend fun installOrUpdateFFmpeg(
        context: Context,
        onProgress: (Float) -> Unit = {}
    ): Result<FFmpegStatus> = withContext(Dispatchers.IO) {
        val abiCandidates = getCandidateSourcesForDevice()
        val finalBinDir = getBinDir(context)
        val finalFFmpegFile = getFFmpegFile(context)
        val finalFFprobeFile = getFFprobeFile(context)

        AppLogger.i(TAG, "Starting native FFmpeg installation. Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
        onProgress(5f)

        var lastFailureReason = "No matching native binary source found"

        // Iterate through device supported ABIs and their candidate URLs
        for (candidate in abiCandidates) {
            AppLogger.i(TAG, "Evaluating sources for ABI: ${candidate.abiName}")

            for (url in candidate.urls) {
                val stagingDir = File(context.cacheDir, "ffmpeg_stage_${System.currentTimeMillis()}")
                try {
                    stagingDir.mkdirs()
                    AppLogger.i(TAG, "Attempting download from: $url")
                    onProgress(10f)

                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "VideoDownloader-Android-Installer/2.0")
                        .build()

                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) {
                        val code = response.code
                        response.close()
                        throw Exception("HTTP $code: ${response.message}")
                    }

                    val contentType = response.header("Content-Type") ?: ""
                    if (contentType.contains("text/html", ignoreCase = true) || contentType.contains("text/plain", ignoreCase = true)) {
                        response.close()
                        throw Exception("Server returned HTML or text error page instead of binary package: $contentType")
                    }

                    val body = response.body ?: run {
                        response.close()
                        throw Exception("Null response body")
                    }

                    val contentLength = body.contentLength()
                    val downloadedPackage = File(stagingDir, "package.tmp")

                    body.byteStream().use { input ->
                        FileOutputStream(downloadedPackage).use { output ->
                            val buffer = ByteArray(65536)
                            var read: Int
                            var totalRead = 0L
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                totalRead += read
                                if (contentLength > 0) {
                                    val pct = 10f + (totalRead.toFloat() / contentLength.toFloat()) * 70f
                                    onProgress(pct.coerceIn(10f, 80f))
                                }
                            }
                        }
                    }
                    response.close()

                    if (!downloadedPackage.exists() || downloadedPackage.length() == 0L) {
                        throw Exception("Downloaded package is 0 bytes")
                    }

                    // Validate magic bytes to reject HTML / corrupt files
                    val headerBytes = ByteArray(8)
                    FileInputStream(downloadedPackage).use { it.read(headerBytes) }
                    val isZip = headerBytes.size >= 4 && headerBytes[0] == 0x50.toByte() && headerBytes[1] == 0x4B.toByte() &&
                            headerBytes[2] == 0x03.toByte() && headerBytes[3] == 0x04.toByte()
                    val isElf = headerBytes.size >= 4 && headerBytes[0] == 0x7F.toByte() && headerBytes[1] == 'E'.code.toByte() &&
                            headerBytes[2] == 'L'.code.toByte() && headerBytes[3] == 'F'.code.toByte()

                    if (!isZip && !isElf) {
                        val headerStr = String(headerBytes, Charsets.US_ASCII)
                        throw Exception("Downloaded file is neither a valid ZIP nor ELF binary (Header: $headerStr)")
                    }

                    val extractedBinDir = File(stagingDir, "extracted")
                    extractedBinDir.mkdirs()

                    if (isZip) {
                        AppLogger.i(TAG, "Extracting ZIP package...")
                        extractZipRecursively(downloadedPackage, extractedBinDir)
                    } else {
                        // Direct standalone ELF
                        val standalone = File(extractedBinDir, "ffmpeg")
                        downloadedPackage.renameTo(standalone)
                    }

                    // Locate and validate candidates in staging
                    val candidateFfmpeg = findExecutableInTree(extractedBinDir, "ffmpeg")
                    if (candidateFfmpeg == null || !candidateFfmpeg.exists()) {
                        throw Exception("No valid 'ffmpeg' executable found inside downloaded archive")
                    }

                    // Strict rejection of forbidden file types
                    val name = candidateFfmpeg.name.lowercase()
                    if (name.endsWith(".aar") || name.endsWith(".so") || name.endsWith(".dll") || name.endsWith(".exe") || name.endsWith(".html")) {
                        throw Exception("Rejected non-standalone file: $name")
                    }

                    FFmpegDetector.ensureExecutable(candidateFfmpeg)
                    onProgress(85f)

                    // PRE-REPLACEMENT VERIFICATION:
                    // Actually test candidate binary with `ffmpeg -version` before replacing existing
                    AppLogger.i(TAG, "Verifying candidate binary at ${candidateFfmpeg.absolutePath}...")
                    val testResult = FFmpegDetector.executeBinary(candidateFfmpeg, "ffmpeg")

                    if (!testResult.isSuccess || testResult.versionLine == null) {
                        val failMsg = testResult.errorMessage ?: "Exit code ${testResult.exitCode}"
                        throw Exception("Pre-flight execution verification failed on device architecture: $failMsg")
                    }

                    AppLogger.i(TAG, "Candidate binary passed verification: ${testResult.versionLine}")
                    onProgress(92f)

                    // Candidate is verified working! Now perform atomic swap into app bin directory
                    val tempFinalFfmpeg = File(finalBinDir, "ffmpeg_install_${System.currentTimeMillis()}.tmp")
                    if (tempFinalFfmpeg.exists()) tempFinalFfmpeg.delete()

                    copyFileWithPermissions(candidateFfmpeg, tempFinalFfmpeg)
                    FFmpegDetector.ensureExecutable(tempFinalFfmpeg)

                    // Atomic replace
                    if (finalFFmpegFile.exists()) {
                        finalFFmpegFile.delete()
                    }
                    if (!tempFinalFfmpeg.renameTo(finalFFmpegFile)) {
                        // If renameTo fails across file boundaries, copy and delete
                        copyFileWithPermissions(tempFinalFfmpeg, finalFFmpegFile)
                        tempFinalFfmpeg.delete()
                    }
                    FFmpegDetector.ensureExecutable(finalFFmpegFile)

                    // Also check and install ffprobe if present in archive
                    val candidateFfprobe = findExecutableInTree(extractedBinDir, "ffprobe")
                    if (candidateFfprobe != null && candidateFfprobe.exists()) {
                        FFmpegDetector.ensureExecutable(candidateFfprobe)
                        val probeTest = FFmpegDetector.executeBinary(candidateFfprobe, "ffprobe")
                        if (probeTest.isSuccess) {
                            val tempFinalProbe = File(finalBinDir, "ffprobe_install_${System.currentTimeMillis()}.tmp")
                            copyFileWithPermissions(candidateFfprobe, tempFinalProbe)
                            FFmpegDetector.ensureExecutable(tempFinalProbe)
                            if (finalFFprobeFile.exists()) finalFFprobeFile.delete()
                            if (!tempFinalProbe.renameTo(finalFFprobeFile)) {
                                copyFileWithPermissions(tempFinalProbe, finalFFprobeFile)
                                tempFinalProbe.delete()
                            }
                            FFmpegDetector.ensureExecutable(finalFFprobeFile)
                            AppLogger.i(TAG, "FFprobe installed and verified: ${probeTest.versionLine}")
                        }
                    }

                    onProgress(100f)

                    // Final system status check
                    val finalStatus = FFmpegDetector.detect(context)
                    if (finalStatus.isAvailable) {
                        AppLogger.i(TAG, "FFmpeg successfully installed and active: ${finalStatus.version}")
                        // Clean up staging directory
                        deleteRecursive(stagingDir)
                        return@withContext Result.success(finalStatus)
                    } else {
                        throw Exception("Post-install status check failed: ${finalStatus.guidance}")
                    }

                } catch (e: Exception) {
                    val msg = e.message ?: e.javaClass.simpleName
                    AppLogger.w(TAG, "Failed installation attempt from $url: $msg")
                    lastFailureReason = msg
                } finally {
                    deleteRecursive(stagingDir)
                }
            }
        }

        // If installation failed completely, ensure no corrupt 0-byte binary remains
        val finalStatus = FFmpegDetector.detect(context)
        if (finalStatus.isAvailable) {
            Result.success(finalStatus)
        } else {
            Result.failure(Exception("FFmpeg installation failed: $lastFailureReason. Please check network connection."))
        }
    }

    private fun extractZipRecursively(zipFile: File, outputDir: File) {
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val entryName = entry.name
                if (!entry.isDirectory) {
                    // Normalize target file
                    val safeName = entryName.replace("../", "")
                    val targetFile = File(outputDir, safeName)
                    targetFile.parentFile?.mkdirs()

                    FileOutputStream(targetFile).use { fos ->
                        zis.copyTo(fos)
                    }

                    // If file is named ffmpeg or ffprobe, set executable bit
                    val simpleName = targetFile.name
                    if (simpleName == "ffmpeg" || simpleName == "ffprobe" || simpleName.startsWith("lib")) {
                        targetFile.setReadable(true, false)
                        targetFile.setExecutable(true, false)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun findExecutableInTree(dir: File, targetName: String): File? {
        if (!dir.exists() || !dir.isDirectory) return null
        val files = dir.listFiles() ?: return null

        // 1. Direct match
        for (f in files) {
            if (f.isFile && f.name.equals(targetName, ignoreCase = true) && f.length() > 1000L) {
                // Reject .so, .aar, .dll, .exe
                val lower = f.name.lowercase()
                if (!lower.endsWith(".so") && !lower.endsWith(".aar") && !lower.endsWith(".dll") && !lower.endsWith(".exe")) {
                    return f
                }
            }
        }

        // 2. Search subdirectories (like bin/ or x86_64/bin/)
        for (f in files) {
            if (f.isDirectory) {
                val found = findExecutableInTree(f, targetName)
                if (found != null) return found
            }
        }

        return null
    }

    private fun copyFileWithPermissions(src: File, dest: File) {
        FileInputStream(src).use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
        dest.setReadable(true, false)
        dest.setExecutable(true, false)
    }

    private fun deleteRecursive(fileOrDir: File) {
        try {
            if (fileOrDir.isDirectory) {
                fileOrDir.listFiles()?.forEach { deleteRecursive(it) }
            }
            fileOrDir.delete()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }
}
