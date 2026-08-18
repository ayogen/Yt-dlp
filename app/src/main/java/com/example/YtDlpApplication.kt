package com.example

import android.app.Application
import com.example.data.db.AppDatabase
import com.example.download.DownloadManager
import com.example.download.DownloadRepository
import com.example.engine.AppLogger
import com.example.engine.YtDlpEngine

class YtDlpApplication : Application() {
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
        repository = DownloadRepository(database, engine, downloadManager)
    }

    companion object {
        lateinit var instance: YtDlpApplication
            private set
    }
}
