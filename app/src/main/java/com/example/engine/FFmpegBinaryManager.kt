package com.example.engine

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

object FFmpegBinaryManager {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun getBinDir(context: Context): File {
        val binDir = File(context.filesDir, "bin")
        if (!binDir.exists()) {
            binDir.mkdirs()
        }
        return binDir
    }

    fun getFFmpegFile(context: Context): File {
        return File(getBinDir(context), "ffmpeg")
    }

    fun getFFprobeFile(context: Context): File {
        return File(getBinDir(context), "ffprobe")
    }

    fun getDevicePrimaryAbi(): String {
        val abis = Build.SUPPORTED_ABIS
        return if (abis.isNotEmpty()) abis[0] else "arm64-v8a"
    }

    /**
     * Resolves the download URL for the target ABI.
     */
    fun getDownloadUrlsForAbi(abi: String): List<String> {
        val normalizedAbi = when {
            abi.contains("arm64", ignoreCase = true) || abi.contains("aarch64", ignoreCase = true) -> "arm64-v8a"
            abi.contains("armeabi", ignoreCase = true) || abi.contains("v7a", ignoreCase = true) -> "armeabi-v7a"
            abi.contains("x86_64", ignoreCase = true) || abi.contains("amd64", ignoreCase = true) -> "x86_64"
            abi.contains("x86", ignoreCase = true) -> "x86"
            else -> "arm64-v8a"
        }

        return when (normalizedAbi) {
            "arm64-v8a" -> listOf(
                "https://github.com/arthenica/ffmpeg-kit/releases/download/v6.0-2/ffmpeg-kit-full-arm64-v8a.zip",
                "https://github.com/yt-dlp/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-linuxarm64-gpl.tar.xz",
                "https://raw.githubusercontent.com/yt-dlp/FFmpeg-Builds/master/bin/ffmpeg"
            )
            "armeabi-v7a" -> listOf(
                "https://github.com/arthenica/ffmpeg-kit/releases/download/v6.0-2/ffmpeg-kit-full-armeabi-v7a.zip",
                "https://github.com/yt-dlp/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-linuxarmv7l-gpl.tar.xz"
            )
            "x86_64" -> listOf(
                "https://github.com/arthenica/ffmpeg-kit/releases/download/v6.0-2/ffmpeg-kit-full-x86_64.zip",
                "https://github.com/yt-dlp/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-linux64-gpl.tar.xz"
            )
            "x86" -> listOf(
                "https://github.com/arthenica/ffmpeg-kit/releases/download/v6.0-2/ffmpeg-kit-full-x86.zip",
                "https://github.com/yt-dlp/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-linux32-gpl.tar.xz"
            )
            else -> listOf(
                "https://github.com/arthenica/ffmpeg-kit/releases/download/v6.0-2/ffmpeg-kit-full-arm64-v8a.zip"
            )
        }
    }

    suspend fun installOrUpdateFFmpeg(
        context: Context,
        onProgress: (Float) -> Unit = {}
    ): Result<FFmpegStatus> = withContext(Dispatchers.IO) {
        val abi = getDevicePrimaryAbi()
        val urls = getDownloadUrlsForAbi(abi)
        val binDir = getBinDir(context)
        val ffmpegFile = getFFmpegFile(context)
        val ffprobeFile = getFFprobeFile(context)

        AppLogger.i("FFmpegBinaryManager", "Beginning FFmpeg binary installation for ABI: $abi...")

        var lastError: Exception? = null

        for (url in urls) {
            try {
                AppLogger.i("FFmpegBinaryManager", "Attempting download from: $url")
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "YtDlpDownloader-Android-Installer")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}: ${response.message}")
                }

                val body = response.body ?: throw Exception("Empty response body from $url")
                val contentLength = body.contentLength()
                val tempDownloadFile = File(binDir, "ffmpeg_dl_${System.currentTimeMillis()}.tmp")

                body.byteStream().use { input ->
                    FileOutputStream(tempDownloadFile).use { output ->
                        val buffer = ByteArray(32768)
                        var bytesRead: Int
                        var totalRead = 0L
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (contentLength > 0) {
                                val progress = (totalRead.toFloat() / contentLength.toFloat()) * 90f
                                onProgress(progress)
                            }
                        }
                    }
                }

                // Check if downloaded file is a ZIP archive or standalone binary
                val isZip = isZipArchive(tempDownloadFile)
                if (isZip) {
                    AppLogger.i("FFmpegBinaryManager", "Extracting binaries from ZIP archive...")
                    extractBinariesFromZip(tempDownloadFile, ffmpegFile, ffprobeFile)
                    tempDownloadFile.delete()
                } else {
                    // Direct binary
                    if (ffmpegFile.exists()) ffmpegFile.delete()
                    tempDownloadFile.renameTo(ffmpegFile)
                }

                // Set permissions
                if (ffmpegFile.exists()) {
                    ffmpegFile.setExecutable(true, false)
                    ffmpegFile.setReadable(true, false)
                }
                if (ffprobeFile.exists()) {
                    ffprobeFile.setExecutable(true, false)
                    ffprobeFile.setReadable(true, false)
                }

                onProgress(100f)

                // Verify with detector
                val status = FFmpegDetector.detect(context)
                if (status.isAvailable) {
                    AppLogger.i("FFmpegBinaryManager", "FFmpeg successfully installed and verified: ${status.version}")
                    return@withContext Result.success(status)
                } else {
                    AppLogger.w("FFmpegBinaryManager", "Installed binary failed execution test: ${status.guidance}")
                    lastError = Exception("Downloaded binary is not executable on this device: ${status.guidance}")
                }
            } catch (e: Exception) {
                AppLogger.w("FFmpegBinaryManager", "Failed downloading/extracting from $url: ${e.message}")
                lastError = e
            }
        }

        val finalStatus = FFmpegDetector.detect(context)
        if (finalStatus.isAvailable) {
            Result.success(finalStatus)
        } else {
            Result.failure(lastError ?: Exception("Failed to install FFmpeg binary from available sources."))
        }
    }

    private fun isZipArchive(file: File): Boolean {
        return try {
            file.inputStream().use { input ->
                val header = ByteArray(4)
                val read = input.read(header)
                read == 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
                        header[2] == 0x03.toByte() && header[3] == 0x04.toByte()
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun extractBinariesFromZip(zipFile: File, destFfmpeg: File, destFfprobe: File) {
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name.substringAfterLast("/")
                if (name == "ffmpeg" || name.equals("ffmpeg.exe", ignoreCase = true) || name == "libffmpeg.so") {
                    if (destFfmpeg.exists()) destFfmpeg.delete()
                    FileOutputStream(destFfmpeg).use { fos ->
                        zis.copyTo(fos)
                    }
                } else if (name == "ffprobe" || name.equals("ffprobe.exe", ignoreCase = true) || name == "libffprobe.so") {
                    if (destFfprobe.exists()) destFfprobe.delete()
                    FileOutputStream(destFfprobe).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
