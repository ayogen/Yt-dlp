package com.example.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.YtDlpApplication
import com.example.data.model.formatBytes
import com.example.data.model.formatSpeed
import com.example.engine.AppLogger

class DownloadForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "yt_dlp_download_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_SERVICE = "com.example.download.START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.example.download.STOP_SERVICE"
        const val ACTION_UPDATE_PROGRESS = "com.example.download.UPDATE_PROGRESS"
        const val ACTION_PAUSE = "com.example.download.ACTION_PAUSE"
        const val ACTION_RESUME = "com.example.download.ACTION_RESUME"
        const val ACTION_CANCEL = "com.example.download.ACTION_CANCEL"

        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_DOWNLOADED = "extra_downloaded"
        const val EXTRA_TOTAL = "extra_total"
        const val EXTRA_SPEED = "extra_speed"
        const val EXTRA_ACTIVE_COUNT = "extra_active_count"

        fun startService(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }

        fun updateProgress(
            context: Context,
            taskId: String,
            title: String,
            progress: Float,
            downloaded: Long,
            total: Long,
            speed: Double,
            activeCount: Int
        ) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_UPDATE_PROGRESS
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_PROGRESS, progress)
                putExtra(EXTRA_DOWNLOADED, downloaded)
                putExtra(EXTRA_TOTAL, total)
                putExtra(EXTRA_SPEED, speed)
                putExtra(EXTRA_ACTIVE_COUNT, activeCount)
            }
            context.startService(intent)
        }
    }

    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SERVICE -> {
                val initialNotification = buildNotification(
                    title = "Video Downloader Active",
                    content = "Managing background download queue",
                    progress = 0,
                    isIndeterminate = true
                )
                startForegroundWithServiceType(initialNotification)
            }

            ACTION_UPDATE_PROGRESS -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: ""
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Downloading media"
                val progress = intent.getFloatExtra(EXTRA_PROGRESS, 0f)
                val downloaded = intent.getLongExtra(EXTRA_DOWNLOADED, 0L)
                val total = intent.getLongExtra(EXTRA_TOTAL, 0L)
                val speed = intent.getDoubleExtra(EXTRA_SPEED, 0.0)
                val activeCount = intent.getIntExtra(EXTRA_ACTIVE_COUNT, 1)

                val speedStr = if (speed > 0) " • ${formatSpeed(speed)}" else ""
                val sizeStr = if (total > 0) "${formatBytes(downloaded)} / ${formatBytes(total)}" else formatBytes(downloaded)
                val content = "$sizeStr ($progress.toInt()%)$speedStr • $activeCount active"

                val notification = buildNotification(
                    title = title,
                    content = content,
                    progress = progress.toInt(),
                    isIndeterminate = total <= 0L,
                    taskId = taskId
                )
                notificationManager.notify(NOTIFICATION_ID, notification)
            }

            ACTION_PAUSE -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID)
                if (!taskId.isNullOrBlank()) {
                    YtDlpApplication.instance.downloadManager.pauseTask(taskId)
                }
            }

            ACTION_CANCEL -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID)
                if (!taskId.isNullOrBlank()) {
                    YtDlpApplication.instance.downloadManager.cancelTask(taskId)
                }
            }

            ACTION_STOP_SERVICE -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundWithServiceType(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(
        title: String,
        content: String,
        progress: Int,
        isIndeterminate: Boolean,
        taskId: String? = null
    ): Notification {
        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setProgress(100, progress, isIndeterminate)

        if (!taskId.isNullOrBlank()) {
            // Add Pause Intent
            val pauseIntent = Intent(this, DownloadForegroundService::class.java).apply {
                action = ACTION_PAUSE
                putExtra(EXTRA_TASK_ID, taskId)
            }
            val pausePending = PendingIntent.getService(
                this,
                taskId.hashCode() + 1,
                pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", pausePending)

            // Add Cancel Intent
            val cancelIntent = Intent(this, DownloadForegroundService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_TASK_ID, taskId)
            }
            val cancelPending = PendingIntent.getService(
                this,
                taskId.hashCode() + 2,
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPending)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time progress for active yt-dlp media downloads"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
