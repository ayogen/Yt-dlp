package com.example.download

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.data.model.MediaType
import com.example.engine.AppLogger
import java.io.File

object StorageUtils {

    /**
     * Resolves the target download directory based on settings and available external storage.
     * Uses Scoped Storage compatible paths.
     */
    fun getDownloadDirectory(context: Context, relativeSubDir: String = "VideoDownloader"): File {
        val externalDownloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val dir = if (externalDownloads != null && externalDownloads.canWrite()) {
            File(externalDownloads, relativeSubDir)
        } else {
            File(context.filesDir, relativeSubDir)
        }
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Notifies Android MediaStore / MediaScanner that a new media file is available.
     */
    fun scanMediaFile(context: Context, file: File, mimeType: String? = null) {
        if (!file.exists()) return
        try {
            MediaScannerConnection.scanFile(
                context.applicationContext,
                arrayOf(file.absolutePath),
                if (mimeType != null) arrayOf(mimeType) else null
            ) { path, uri ->
                AppLogger.d("StorageUtils", "Scanned media into system index: $path -> $uri")
            }
        } catch (e: Exception) {
            AppLogger.w("StorageUtils", "Media scanning warning: ${e.message}")
        }
    }

    /**
     * Safely opens a downloaded media file with an external player using FileProvider.
     */
    fun openMediaFile(context: Context, filePath: String, mediaType: MediaType = MediaType.VIDEO): Result<Unit> {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                return Result.failure(Exception("File does not exist on disk: $filePath"))
            }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val mimeType = getMimeTypeForFile(file, mediaType)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(intent, "Open with").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e("StorageUtils", "Failed to open media file: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Shares media file or its download link to other apps.
     */
    fun shareMediaFile(context: Context, filePath: String, mediaType: MediaType, title: String): Result<Unit> {
        return try {
            val file = File(filePath)
            if (file.exists()) {
                val uri: Uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val mimeType = getMimeTypeForFile(file, mediaType)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Media").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } else {
                val textIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, title)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(textIntent, "Share").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e("StorageUtils", "Failed to share media file: ${e.message}")
            Result.failure(e)
        }
    }

    private fun getMimeTypeForFile(file: File, mediaType: MediaType): String {
        val ext = file.extension.lowercase()
        return when (ext) {
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> if (mediaType == MediaType.AUDIO) "audio/webm" else "video/webm"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "opus" -> "audio/opus"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            else -> if (mediaType == MediaType.AUDIO) "audio/*" else "video/*"
        }
    }
}
