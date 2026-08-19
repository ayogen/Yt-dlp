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
     * Resolves high-priority verified native Android static binary URLs in the order of Build.SUPPORTED_ABIS.
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
            rawAbi.contains("armeabi-v7a", ignoreCase = true) || rawAbi.contains("v7a", ignoreCase = true) -> "armeabi-v7a"
            rawAbi.contains("armeabi", ignoreCase = true) -> "armeabi"
            rawAbi.contains("x86_64", ignoreCase = true) || rawAbi.contains("amd64", ignoreCase = true) -> "x86_64"
            rawAbi.contains("x86", ignoreCase = true) || rawAbi.contains("i686", ignoreCase = true) || rawAbi.contains("i386", ignoreCase = true) -> "x86"
            else -> rawAbi
        }
    }

    /**
     * Explicitly sets full executable permissions on a file via both File API and chmod 755.
     */
    fun applyFullExecutablePermissions(file: File): Boolean {
        if (!file.exists()) return false

        try {
            file.setReadable(true, false)
            file.setWritable(true, false)
            file.setExecutable(true, false)
        } catch (e: Exception) {
            AppLogger.w(TAG, "File API permission set failed on ${file.name}: ${e.message}")
        }

        try {
            val pb = ProcessBuilder("chmod", "755", file.absolutePath)
            val p = pb.start()
            p.waitFor(2, TimeUnit.SECONDS)
        } catch (e: Exception) {
            // Ignore chmod process exception if restricted
        }

        return file.exists() && file.length() > 0 && file.canExecute()
    }

    suspend fun installOrUpdateFFmpeg(
        context: Context,
        onProgress: (Float) -> Unit = {}
    ): Result<FFmpegStatus> = withContext(Dispatchers.IO) {
        val supportedAbis = Build.SUPPORTED_ABIS
        val primaryAbi = if (supportedAbis.isNotEmpty()) supportedAbis[0] else "arm64-v8a"
        val abiCandidates = getCandidateSourcesForDevice()
        val finalBinDir = getBinDir(context)
        val finalFFmpegFile = getFFmpegFile(context)
        val finalFFprobeFile = getFFprobeFile(context)

        AppLogger.i(TAG, "Starting native FFmpeg installation. Supported ABIs (in order): ${supportedAbis.joinToString(", ")}. Primary: $primaryAbi")
        onProgress(5f)

        var lastFailureReason = "No matching native binary source found"

        // Iterate through device supported ABIs strictly in order of Build.SUPPORTED_ABIS priority
        for (candidate in abiCandidates) {
            AppLogger.i(TAG, "Targeting binary sources for ABI: ${candidate.abiName}")

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
                        throw Exception("Server returned HTML error page instead of binary package: $contentType")
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
                                    val pct = 10f + (totalRead.toFloat() / contentLength.toFloat()) * 65f
                                    onProgress(pct.coerceIn(10f, 75f))
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

                    onProgress(80f)

                    // Locate ABI-specific candidate according to Build.SUPPORTED_ABIS order
                    val candidateFfmpeg = findBestExecutableForDevice(extractedBinDir, "ffmpeg", supportedAbis)
                    if (candidateFfmpeg == null || !candidateFfmpeg.exists()) {
                        throw Exception("No valid 'ffmpeg' executable matching device ABIs found inside archive")
                    }

                    // Explicitly set executable permissions BEFORE pre-flight test
                    val permissionsOk = applyFullExecutablePermissions(candidateFfmpeg)

                    AppLogger.i(
                        TAG,
                        "Selected candidate: ${candidateFfmpeg.absolutePath} | " +
                                "Size: ${candidateFfmpeg.length()} bytes | " +
                                "canRead: ${candidateFfmpeg.canRead()} | " +
                                "canWrite: ${candidateFfmpeg.canWrite()} | " +
                                "canExecute: ${candidateFfmpeg.canExecute()} (applied: $permissionsOk)"
                    )

                    if (!candidateFfmpeg.canExecute()) {
                        throw Exception("FFmpeg executable permission/setup failed: Could not set executable permissions on ${candidateFfmpeg.absolutePath}")
                    }

                    onProgress(85f)

                    // PRE-FLIGHT VERIFICATION:
                    // Must execute candidate binary with `-version` before touching app bin directory
                    AppLogger.i(TAG, "Running pre-flight verification on: ${candidateFfmpeg.absolutePath} -version")
                    val preFlightResult = FFmpegDetector.executeBinary(candidateFfmpeg, "ffmpeg")

                    if (!preFlightResult.isSuccess || preFlightResult.versionLine == null) {
                        val failMsg = preFlightResult.errorMessage ?: "Exit code ${preFlightResult.exitCode}"
                        throw Exception("Pre-flight execution verification failed on ${candidateFfmpeg.absolutePath}: $failMsg")
                    }

                    AppLogger.i(TAG, "Pre-flight verification passed: ${preFlightResult.versionLine}")
                    onProgress(90f)

                    // Candidate verified! Copy executable into final filesDir/bin/ffmpeg
                    finalBinDir.mkdirs()
                    val tempFinalFfmpeg = File(finalBinDir, "ffmpeg_install_${System.currentTimeMillis()}.tmp")
                    if (tempFinalFfmpeg.exists()) tempFinalFfmpeg.delete()

                    copyFileWithPermissions(candidateFfmpeg, tempFinalFfmpeg)
                    val finalPermOk = applyFullExecutablePermissions(tempFinalFfmpeg)

                    if (finalFFmpegFile.exists()) {
                        finalFFmpegFile.delete()
                    }
                    if (!tempFinalFfmpeg.renameTo(finalFFmpegFile)) {
                        copyFileWithPermissions(tempFinalFfmpeg, finalFFmpegFile)
                        tempFinalFfmpeg.delete()
                    }

                    // Set executable permissions AGAIN on final destination
                    applyFullExecutablePermissions(finalFFmpegFile)

                    // POST-INSTALL VERIFICATION:
                    // Execute installed binary at context.filesDir/bin/ffmpeg -version
                    AppLogger.i(TAG, "Running final verification on installed binary: ${finalFFmpegFile.absolutePath} -version")
                    val postInstallResult = FFmpegDetector.executeBinary(finalFFmpegFile, "ffmpeg")

                    if (!postInstallResult.isSuccess || postInstallResult.versionLine == null) {
                        // Purge invalid installed file
                        try { finalFFmpegFile.delete() } catch (e: Exception) {}
                        val failMsg = postInstallResult.errorMessage ?: "Exit code ${postInstallResult.exitCode}"
                        throw Exception("Final binary verification failed on ${finalFFmpegFile.absolutePath}: $failMsg")
                    }

                    AppLogger.i(TAG, "Installed FFmpeg verified successfully: ${postInstallResult.versionLine}")

                    // Check and install ffprobe companion if present in archive
                    val candidateFfprobe = findBestExecutableForDevice(extractedBinDir, "ffprobe", supportedAbis)
                    if (candidateFfprobe != null && candidateFfprobe.exists()) {
                        applyFullExecutablePermissions(candidateFfprobe)
                        val probePreFlight = FFmpegDetector.executeBinary(candidateFfprobe, "ffprobe")
                        if (probePreFlight.isSuccess) {
                            val tempFinalProbe = File(finalBinDir, "ffprobe_install_${System.currentTimeMillis()}.tmp")
                            copyFileWithPermissions(candidateFfprobe, tempFinalProbe)
                            applyFullExecutablePermissions(tempFinalProbe)
                            if (finalFFprobeFile.exists()) finalFFprobeFile.delete()
                            if (!tempFinalProbe.renameTo(finalFFprobeFile)) {
                                copyFileWithPermissions(tempFinalProbe, finalFFprobeFile)
                                tempFinalProbe.delete()
                            }
                            applyFullExecutablePermissions(finalFFprobeFile)
                            val probeFinalTest = FFmpegDetector.executeBinary(finalFFprobeFile, "ffprobe")
                            if (probeFinalTest.isSuccess) {
                                AppLogger.i(TAG, "FFprobe companion installed and verified: ${probeFinalTest.versionLine}")
                            }
                        }
                    }

                    onProgress(100f)

                    // Delete staging directory on success
                    deleteRecursive(stagingDir)

                    // Final status detection
                    val finalStatus = FFmpegDetector.detect(context)
                    if (finalStatus.isAvailable) {
                        AppLogger.i(TAG, "FFmpeg installation completed successfully (${finalStatus.version})")
                        return@withContext Result.success(finalStatus)
                    } else {
                        throw Exception("Post-install status check reported non-available state: ${finalStatus.guidance}")
                    }

                } catch (e: Exception) {
                    val msg = e.message ?: e.javaClass.simpleName
                    AppLogger.w(TAG, "Installation failed with source $url: $msg")
                    lastFailureReason = msg
                } finally {
                    deleteRecursive(stagingDir)
                }
            }
        }

        // Clean up any corrupt state and perform final detect
        val finalStatus = FFmpegDetector.detect(context)
        if (finalStatus.isAvailable) {
            Result.success(finalStatus)
        } else {
            val descriptiveError = when {
                lastFailureReason.contains("permission", ignoreCase = true) || lastFailureReason.contains("chmod", ignoreCase = true) || lastFailureReason.contains("error=13", ignoreCase = true) -> {
                    "FFmpeg executable permission/setup failed: $lastFailureReason"
                }
                lastFailureReason.contains("Pre-flight", ignoreCase = true) || lastFailureReason.contains("Exec format", ignoreCase = true) -> {
                    "FFmpeg execution compatibility verification failed: $lastFailureReason"
                }
                lastFailureReason.contains("HTTP", ignoreCase = true) || lastFailureReason.contains("connect", ignoreCase = true) || lastFailureReason.contains("timeout", ignoreCase = true) -> {
                    "Network error downloading FFmpeg package: $lastFailureReason"
                }
                else -> {
                    "FFmpeg installation failed: $lastFailureReason"
                }
            }
            Result.failure(Exception(descriptiveError))
        }
    }

    private fun extractZipRecursively(zipFile: File, outputDir: File) {
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val entryName = entry.name
                if (!entry.isDirectory) {
                    val safeName = entryName.replace("../", "")
                    val targetFile = File(outputDir, safeName)
                    targetFile.parentFile?.mkdirs()

                    FileOutputStream(targetFile).use { fos ->
                        zis.copyTo(fos)
                    }

                    // Apply permissions immediately on extraction
                    val simpleName = targetFile.name.lowercase()
                    if (simpleName == "ffmpeg" || simpleName == "ffprobe" || simpleName.startsWith("lib")) {
                        applyFullExecutablePermissions(targetFile)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    /**
     * Recursively locates all candidates with name matching targetName, excluding .so, .aar, .dll, .exe, .html,
     * and selects the candidate that strictly best matches the device Build.SUPPORTED_ABIS priority order.
     */
    fun findBestExecutableForDevice(dir: File, targetName: String, supportedAbis: Array<String>): File? {
        val allMatchingFiles = mutableListOf<File>()
        collectMatchingFiles(dir, targetName, allMatchingFiles)

        if (allMatchingFiles.isEmpty()) return null
        if (allMatchingFiles.size == 1) return allMatchingFiles.first()

        AppLogger.i(TAG, "Found ${allMatchingFiles.size} matching candidate binaries: ${allMatchingFiles.map { it.absolutePath }}")

        // Score candidates based on Build.SUPPORTED_ABIS order
        for ((priorityIndex, abi) in supportedAbis.withIndex()) {
            val normalizedAbi = normalizeAbi(abi)
            val matchedCandidate = allMatchingFiles.firstOrNull { file ->
                val path = file.absolutePath.replace("\\", "/")
                path.contains("/$normalizedAbi/", ignoreCase = true) ||
                        path.contains("/$abi/", ignoreCase = true) ||
                        path.contains("-$normalizedAbi", ignoreCase = true) ||
                        path.contains("_$normalizedAbi", ignoreCase = true)
            }

            if (matchedCandidate != null) {
                AppLogger.i(TAG, "Selected ABI candidate [Priority #$priorityIndex - $abi]: ${matchedCandidate.absolutePath}")
                return matchedCandidate
            }
        }

        // If no explicit ABI directory match, pick candidate in highest-level directory
        return allMatchingFiles.minByOrNull { it.absolutePath.count { char -> char == '/' } } ?: allMatchingFiles.first()
    }

    private fun collectMatchingFiles(dir: File, targetName: String, result: MutableList<File>) {
        if (!dir.exists() || !dir.isDirectory) return
        val files = dir.listFiles() ?: return

        for (f in files) {
            if (f.isDirectory) {
                collectMatchingFiles(f, targetName, result)
            } else if (f.isFile && f.name.equals(targetName, ignoreCase = true) && f.length() > 1000L) {
                val lower = f.name.lowercase()
                if (!lower.endsWith(".so") && !lower.endsWith(".aar") && !lower.endsWith(".dll") && !lower.endsWith(".exe") && !lower.endsWith(".html")) {
                    result.add(f)
                }
            }
        }
    }

    private fun copyFileWithPermissions(src: File, dest: File) {
        FileInputStream(src).use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
        applyFullExecutablePermissions(dest)
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
