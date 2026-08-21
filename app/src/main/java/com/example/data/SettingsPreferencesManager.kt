package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.AppSettings
import com.example.data.model.AudioFormat
import com.example.data.model.AudioQualityPreset
import com.example.data.model.OutputContainer
import com.example.data.model.VideoQualityPreset
import com.example.engine.AppLogger

class SettingsPreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun loadSettings(): AppSettings {
        return try {
            AppSettings(
                maxConcurrentDownloads = prefs.getInt(KEY_MAX_CONCURRENT_DOWNLOADS, 1),
                defaultVideoQuality = parseEnum(prefs.getString(KEY_DEFAULT_VIDEO_QUALITY, null), VideoQualityPreset.BEST),
                defaultAudioQuality = parseEnum(prefs.getString(KEY_DEFAULT_AUDIO_QUALITY, null), AudioQualityPreset.BEST),
                defaultContainer = parseEnum(prefs.getString(KEY_DEFAULT_CONTAINER, null), OutputContainer.MP4),
                defaultAudioFormat = parseEnum(prefs.getString(KEY_DEFAULT_AUDIO_FORMAT, null), AudioFormat.MP3),
                downloadLocationUri = prefs.getString(KEY_DOWNLOAD_LOCATION_URI, "") ?: "",
                downloadLocationDisplayName = prefs.getString(KEY_DOWNLOAD_LOCATION_DISPLAY_NAME, "Default App Storage") ?: "Default App Storage",
                resumeDownloads = prefs.getBoolean(KEY_RESUME_DOWNLOADS, true),
                retryCount = prefs.getInt(KEY_RETRY_COUNT, 3),
                customYtDlpArgs = prefs.getString(KEY_CUSTOM_YT_DLP_ARGS, "") ?: "",
                verboseLogging = prefs.getBoolean(KEY_VERBOSE_LOGGING, false),
                cookiesFilePath = prefs.getString(KEY_COOKIES_FILE_PATH, "") ?: "",
                sanitizeFilenames = prefs.getBoolean(KEY_SANITIZE_FILENAMES, true),
                organizeByUploader = prefs.getBoolean(KEY_ORGANIZE_BY_UPLOADER, false),
                embedSubtitles = prefs.getBoolean(KEY_EMBED_SUBTITLES, false),
                embedThumbnail = prefs.getBoolean(KEY_EMBED_THUMBNAIL, true),
                filenameTemplate = prefs.getString(KEY_FILENAME_TEMPLATE, "%(title)s.%(ext)s") ?: "%(title)s.%(ext)s",
                autoStartDownloads = prefs.getBoolean(KEY_AUTO_START_DOWNLOADS, true),
                confirmDelete = prefs.getBoolean(KEY_CONFIRM_DELETE, true),
                darkTheme = prefs.getBoolean(KEY_DARK_THEME, true),
                detectClipboardLinks = prefs.getBoolean(KEY_DETECT_CLIPBOARD_LINKS, true),
                customProfilesJson = prefs.getString(KEY_CUSTOM_PROFILES_JSON, "") ?: ""
            )
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error loading saved settings, falling back to defaults: ${e.message}")
            AppSettings()
        }
    }

    fun saveSettings(settings: AppSettings) {
        try {
            prefs.edit()
                .putInt(KEY_MAX_CONCURRENT_DOWNLOADS, settings.maxConcurrentDownloads)
                .putString(KEY_DEFAULT_VIDEO_QUALITY, settings.defaultVideoQuality.name)
                .putString(KEY_DEFAULT_AUDIO_QUALITY, settings.defaultAudioQuality.name)
                .putString(KEY_DEFAULT_CONTAINER, settings.defaultContainer.name)
                .putString(KEY_DEFAULT_AUDIO_FORMAT, settings.defaultAudioFormat.name)
                .putString(KEY_DOWNLOAD_LOCATION_URI, settings.downloadLocationUri)
                .putString(KEY_DOWNLOAD_LOCATION_DISPLAY_NAME, settings.downloadLocationDisplayName)
                .putBoolean(KEY_RESUME_DOWNLOADS, settings.resumeDownloads)
                .putInt(KEY_RETRY_COUNT, settings.retryCount)
                .putString(KEY_CUSTOM_YT_DLP_ARGS, settings.customYtDlpArgs)
                .putBoolean(KEY_VERBOSE_LOGGING, settings.verboseLogging)
                .putString(KEY_COOKIES_FILE_PATH, settings.cookiesFilePath)
                .putBoolean(KEY_SANITIZE_FILENAMES, settings.sanitizeFilenames)
                .putBoolean(KEY_ORGANIZE_BY_UPLOADER, settings.organizeByUploader)
                .putBoolean(KEY_EMBED_SUBTITLES, settings.embedSubtitles)
                .putBoolean(KEY_EMBED_THUMBNAIL, settings.embedThumbnail)
                .putString(KEY_FILENAME_TEMPLATE, settings.filenameTemplate)
                .putBoolean(KEY_AUTO_START_DOWNLOADS, settings.autoStartDownloads)
                .putBoolean(KEY_CONFIRM_DELETE, settings.confirmDelete)
                .putBoolean(KEY_DARK_THEME, settings.darkTheme)
                .putBoolean(KEY_DETECT_CLIPBOARD_LINKS, settings.detectClipboardLinks)
                .putString(KEY_CUSTOM_PROFILES_JSON, settings.customProfilesJson)
                .apply()
            AppLogger.d(TAG, "Settings successfully persisted to disk")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to save settings: ${e.message}")
        }
    }

    private inline fun <reified T : Enum<T>> parseEnum(name: String?, defaultValue: T): T {
        if (name.isNullOrBlank()) return defaultValue
        return try {
            java.lang.Enum.valueOf(T::class.java, name)
        } catch (e: Exception) {
            defaultValue
        }
    }

    companion object {
        private const val TAG = "SettingsPreferences"
        private const val PREFS_NAME = "app_user_settings_prefs"

        private const val KEY_MAX_CONCURRENT_DOWNLOADS = "max_concurrent_downloads"
        private const val KEY_DEFAULT_VIDEO_QUALITY = "default_video_quality"
        private const val KEY_DEFAULT_AUDIO_QUALITY = "default_audio_quality"
        private const val KEY_DEFAULT_CONTAINER = "default_container"
        private const val KEY_DEFAULT_AUDIO_FORMAT = "default_audio_format"
        private const val KEY_DOWNLOAD_LOCATION_URI = "download_location_uri"
        private const val KEY_DOWNLOAD_LOCATION_DISPLAY_NAME = "download_location_display_name"
        private const val KEY_RESUME_DOWNLOADS = "resume_downloads"
        private const val KEY_RETRY_COUNT = "retry_count"
        private const val KEY_CUSTOM_YT_DLP_ARGS = "custom_yt_dlp_args"
        private const val KEY_VERBOSE_LOGGING = "verbose_logging"
        private const val KEY_COOKIES_FILE_PATH = "cookies_file_path"
        private const val KEY_SANITIZE_FILENAMES = "sanitize_filenames"
        private const val KEY_ORGANIZE_BY_UPLOADER = "organize_by_uploader"
        private const val KEY_EMBED_SUBTITLES = "embed_subtitles"
        private const val KEY_EMBED_THUMBNAIL = "embed_thumbnail"
        private const val KEY_FILENAME_TEMPLATE = "filename_template"
        private const val KEY_AUTO_START_DOWNLOADS = "auto_start_downloads"
        private const val KEY_CONFIRM_DELETE = "confirm_delete"
        private const val KEY_DARK_THEME = "dark_theme"
        private const val KEY_DETECT_CLIPBOARD_LINKS = "detect_clipboard_links"
        private const val KEY_CUSTOM_PROFILES_JSON = "custom_profiles_json"
    }
}
