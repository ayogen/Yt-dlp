package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.util.DebugLogger
import com.example.data.db.AppDatabase
import com.example.download.DownloadManager
import com.example.download.DownloadRepository
import com.example.engine.AppLogger
import com.example.engine.FFmpegBinaryManager
import com.example.engine.YtDlpBinaryManager
import com.example.engine.YtDlpEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class YtDlpApplication : Application(), ImageLoaderFactory {
    lateinit var database: AppDatabase
        private set
    lateinit var engine: YtDlpEngine
        private set
    lateinit var downloadManager: DownloadManager
        private set
    lateinit var repository: DownloadRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getInstance(this)
        AppLogger.init(database.logDao())
        AppLogger.i("YtDlpApplication", "Application initialized with Room database")

        engine = YtDlpEngine(this)
        downloadManager = DownloadManager(this, database, engine)
        repository = DownloadRepository(database, engine, downloadManager, this)

        // Automatically initialize yt-dlp Python runtime and FFmpeg native binaries on startup
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                AppLogger.i("YtDlpApplication", "Triggering startup media engine initialization...")
                YtDlpBinaryManager.ensureInitialized(this@YtDlpApplication)
                FFmpegBinaryManager.ensureInitialized(this@YtDlpApplication)
                AppLogger.i("YtDlpApplication", "Startup media engine initialization complete")
            } catch (e: Exception) {
                AppLogger.w("YtDlpApplication", "Startup media engine initialization warning: ${e.message}")
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .allowHardware(true)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
    }

    companion object {
        lateinit var instance: YtDlpApplication
            private set
    }
}
