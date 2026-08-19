package com.example.download

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.example.data.model.MediaType
import com.example.engine.AppLogger
import java.io.File
import java.io.InputStream
import java.io.OutputStream

object StorageUtils {

    const val SUBDIR_VIDEO = "Video"
    const val SUBDIR_MUSIC = "Music"
    const val SUBDIR_AUDIO = "Audio"
    const val SUBDIR_SUBTITLES = "Subtitles"
    const val SUBDIR_IMAGES = "Images"
    const val SUBDIR_OTHER = "Other"

    /**
     * Determines the appropriate subfolder name based on MediaType and file extension.
     */
    fun getSubfolderForMediaType(mediaType: MediaType, ext: String = ""): String {
        val cleanExt = ext.lowercase().removePrefix(".")
        return when {
            cleanExt in listOf("vtt", "srt", "ass", "lrc") -> SUBDIR_SUBTITLES
            cleanExt in listOf("jpg", "jpeg", "png", "webp", "gif") -> SUBDIR_IMAGES
            mediaType == MediaType.VIDEO -> SUBDIR_VIDEO
            mediaType == MediaType.AUDIO -> {
                if (cleanExt in listOf("mp3", "flac", "m4a", "aac", "wav", "alac", "wma")) {
                    SUBDIR_MUSIC
                } else {
                    SUBDIR_AUDIO
                }
            }
            mediaType == MediaType.PLAYLIST -> SUBDIR_VIDEO
            else -> SUBDIR_OTHER
        }
    }

    /**
     * Persists SAF tree URI permissions across app restarts.
     */
    fun takePersistableUriPermission(context: Context, treeUri: Uri): Boolean {
        return try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
            AppLogger.i("StorageUtils", "Successfully took persistable permission for SAF tree: $treeUri")
            true
        } catch (e: Exception) {
            AppLogger.e("StorageUtils", "Failed to take persistable URI permission: ${e.message}")
            false
        }
    }

    /**
     * Extracts a user-friendly display name from a tree URI.
     */
    fun getDisplayNameForTreeUri(context: Context, treeUriString: String): String {
        if (treeUriString.isBlank()) return "Default App Storage"
        return try {
            val uri = Uri.parse(treeUriString)
            val doc = DocumentFile.fromTreeUri(context, uri)
            val name = doc?.name
            if (!name.isNullOrBlank()) {
                name
            } else {
                val docId = DocumentsContract.getTreeDocumentId(uri)
                docId.substringAfterLast(":", docId).substringAfterLast("/")
            }
        } catch (e: Exception) {
            "Custom SAF Location"
        }
    }

    /**
     * Checks if the configured SAF tree URI is valid and writable.
     */
    fun isSafUriWritable(context: Context, treeUriString: String): Boolean {
        if (treeUriString.isBlank()) return false
        return try {
            val uri = Uri.parse(treeUriString)
            val doc = DocumentFile.fromTreeUri(context, uri)
            doc != null && doc.exists() && doc.canWrite()
        } catch (e: Exception) {
            AppLogger.w("StorageUtils", "Error checking SAF tree writability: ${e.message}")
            false
        }
    }

    /**
     * Resolves or creates a specific subfolder inside the root DocumentFile tree.
     */
    fun getOrCreateSubfolder(context: Context, treeUri: Uri, subfolderName: String): DocumentFile? {
        return try {
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return null
            if (!rootDoc.exists() || !rootDoc.canWrite()) {
                AppLogger.e("StorageUtils", "Root SAF tree is not accessible or not writable: $treeUri")
                return null
            }

            val existing = rootDoc.findFile(subfolderName)
            if (existing != null && existing.isDirectory) {
                existing
            } else {
                rootDoc.createDirectory(subfolderName)
            }
        } catch (e: Exception) {
            AppLogger.e("StorageUtils", "Error getting/creating subfolder '$subfolderName': ${e.message}")
            null
        }
    }

    /**
     * Resolves the target directory inside app-specific external storage (fallback).
     */
    fun getFallbackDownloadDirectory(context: Context, subfolderName: String): File {
        val externalDownloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val baseDir = if (externalDownloads != null && externalDownloads.canWrite()) {
            externalDownloads
        } else {
            File(context.filesDir, "downloads")
        }
        val subDir = File(baseDir, subfolderName)
        if (!subDir.exists()) {
            subDir.mkdirs()
        }
        return subDir
    }

    /**
     * Copies a downloaded staging file into the user-selected SAF subfolder.
     * Returns the Document URI as a string on success.
     */
    fun exportFileToSaf(
        context: Context,
        sourceFile: File,
        treeUriString: String,
        mediaType: MediaType,
        customFilename: String? = null
    ): Result<String> {
        return try {
            if (!sourceFile.exists()) {
                return Result.failure(Exception("Source file does not exist: ${sourceFile.absolutePath}"))
            }

            val treeUri = Uri.parse(treeUriString)
            val subfolderName = getSubfolderForMediaType(mediaType, sourceFile.extension)
            val targetFolder = getOrCreateSubfolder(context, treeUri, subfolderName)
                ?: return Result.failure(Exception("Cannot access or create SAF folder: $subfolderName. Check folder permissions."))

            val fileName = customFilename ?: sourceFile.name
            val mimeType = getMimeTypeForExtension(sourceFile.extension, mediaType)

            // Remove existing file with same name if any
            val existing = targetFolder.findFile(fileName)
            if (existing != null && existing.exists()) {
                existing.delete()
            }

            val newDocFile = targetFolder.createFile(mimeType, fileName)
                ?: return Result.failure(Exception("Failed to create document file '$fileName' in SAF location $subfolderName"))

            context.contentResolver.openOutputStream(newDocFile.uri, "w")?.use { outStream ->
                sourceFile.inputStream().use { inStream ->
                    inStream.copyTo(outStream, bufferSize = 65536)
                }
            } ?: return Result.failure(Exception("Failed to open output stream to SAF document: ${newDocFile.uri}"))

            // Clean up staging file after successful export
            try {
                sourceFile.delete()
            } catch (e: Exception) {
                AppLogger.w("StorageUtils", "Could not delete temporary staging file: ${e.message}")
            }

            AppLogger.i("StorageUtils", "Exported downloaded file to SAF: ${newDocFile.uri} ($subfolderName/$fileName)")
            Result.success(newDocFile.uri.toString())
        } catch (e: Exception) {
            AppLogger.e("StorageUtils", "exportFileToSaf failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Gets a staging directory for yt-dlp or direct download process.
     */
    fun getStagingDirectory(context: Context): File {
        val staging = File(context.cacheDir, "staging_downloads")
        if (!staging.exists()) {
            staging.mkdirs()
        }
        return staging
    }

    /**
     * Notifies Android MediaStore / MediaScanner for regular File objects.
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
     * Opens a media file from either a content:// URI (SAF) or a file path.
     */
    fun openMediaFile(context: Context, pathOrUri: String, mediaType: MediaType = MediaType.VIDEO): Result<Unit> {
        return try {
            val (uri, mimeType) = if (pathOrUri.startsWith("content://")) {
                val parsedUri = Uri.parse(pathOrUri)
                val doc = DocumentFile.fromSingleUri(context, parsedUri)
                val ext = doc?.name?.substringAfterLast('.', "") ?: ""
                val type = doc?.type ?: getMimeTypeForExtension(ext, mediaType)
                Pair(parsedUri, type)
            } else {
                val file = File(pathOrUri)
                if (!file.exists()) {
                    return Result.failure(Exception("File does not exist: $pathOrUri"))
                }
                val fileUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                Pair(fileUri, getMimeTypeForExtension(file.extension, mediaType))
            }

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
    fun shareMediaFile(context: Context, pathOrUri: String, mediaType: MediaType, title: String): Result<Unit> {
        return try {
            if (pathOrUri.isNotBlank()) {
                val (uri, mimeType) = if (pathOrUri.startsWith("content://")) {
                    val parsedUri = Uri.parse(pathOrUri)
                    val doc = DocumentFile.fromSingleUri(context, parsedUri)
                    val ext = doc?.name?.substringAfterLast('.', "") ?: ""
                    val type = doc?.type ?: getMimeTypeForExtension(ext, mediaType)
                    Pair(parsedUri, type)
                } else {
                    val file = File(pathOrUri)
                    if (file.exists()) {
                        val fileUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                        Pair(fileUri, getMimeTypeForExtension(file.extension, mediaType))
                    } else {
                        Pair(null, null)
                    }
                }

                if (uri != null) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = mimeType ?: "*/*"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, title)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Media").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                    return Result.success(Unit)
                }
            }

            // Fallback to text link sharing
            val textIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, title)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(textIntent, "Share").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e("StorageUtils", "Failed to share media file: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Deletes a file either via SAF DocumentFile (content://) or java.io.File.
     */
    fun deleteMediaFile(context: Context, pathOrUri: String): Boolean {
        if (pathOrUri.isBlank()) return false
        return try {
            if (pathOrUri.startsWith("content://")) {
                val uri = Uri.parse(pathOrUri)
                val doc = DocumentFile.fromSingleUri(context, uri)
                doc?.delete() == true
            } else {
                val file = File(pathOrUri)
                if (file.exists()) file.delete() else false
            }
        } catch (e: Exception) {
            AppLogger.w("StorageUtils", "Failed to delete media file $pathOrUri: ${e.message}")
            false
        }
    }

    /**
     * Gets the file size of a content URI or disk path.
     */
    fun getFileSize(context: Context, pathOrUri: String): Long {
        if (pathOrUri.isBlank()) return 0L
        return try {
            if (pathOrUri.startsWith("content://")) {
                val uri = Uri.parse(pathOrUri)
                val doc = DocumentFile.fromSingleUri(context, uri)
                doc?.length() ?: 0L
            } else {
                val file = File(pathOrUri)
                if (file.exists()) file.length() else 0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    fun getMimeTypeForExtension(ext: String, mediaType: MediaType = MediaType.VIDEO): String {
        return when (ext.lowercase().removePrefix(".")) {
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
            "vtt" -> "text/vtt"
            "srt" -> "application/x-subrip"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> if (mediaType == MediaType.AUDIO) "audio/*" else "video/*"
        }
    }
}
