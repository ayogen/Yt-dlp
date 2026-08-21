package com.example.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.DownloadStatus
import com.example.data.model.DownloadTaskEntity
import com.example.data.model.MediaType
import com.example.data.model.formatBytes
import com.example.data.model.formatEta
import com.example.data.model.formatSpeed
import com.example.download.StorageUtils
import com.example.engine.EngineDiagnosticError
import com.example.ui.MainViewModel
import com.example.ui.NavigationTab
import com.example.ui.components.DiagnosticErrorDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.GradientProgressBar
import com.example.ui.components.MediaTypeBadge
import com.example.ui.components.StatusBadge
import com.example.ui.components.TechnicalLogsDialog
import com.example.ui.theme.ElegantAmber
import com.example.ui.theme.ElegantDarkBorder
import com.example.ui.theme.ElegantDarkCard
import com.example.ui.theme.ElegantDarkSurfaceVariant
import com.example.ui.theme.ElegantGreen
import com.example.ui.theme.ElegantLavenderOnPrimary
import com.example.ui.theme.ElegantLavenderPrimary
import com.example.ui.theme.ElegantRed
import com.example.ui.theme.ElegantTextPrimary
import com.example.ui.theme.ElegantTextSecondary
import com.example.ui.theme.ElegantTextTertiary

@Composable
fun DownloadsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val tasks by viewModel.tasks.collectAsState()
    val logs by viewModel.logs.collectAsState()

    var showLogsDialog by remember { mutableStateOf(false) }
    var activeErrorDialog by remember { mutableStateOf<EngineDiagnosticError?>(null) }

    val activeCount = tasks.count { it.status == DownloadStatus.DOWNLOADING }
    val queuedCount = tasks.count { it.status == DownloadStatus.QUEUED }
    val totalSpeed = tasks.filter { it.status == DownloadStatus.DOWNLOADING }.sumOf { it.speedBytesPerSec }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Top Metrics Summary Banner
        Card(
            colors = CardDefaults.cardColors(containerColor = ElegantDarkCard),
            shape = RoundedCornerShape(18.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.verticalGradient(listOf(ElegantDarkBorder, Color.Transparent))),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text(text = "Active", fontSize = 11.sp, color = ElegantTextSecondary)
                        Text(text = "$activeCount", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ElegantLavenderPrimary)
                    }
                    Column {
                        Text(text = "Queued", fontSize = 11.sp, color = ElegantTextSecondary)
                        Text(text = "$queuedCount", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ElegantAmber)
                    }
                    Column {
                        Text(text = "Speed", fontSize = 11.sp, color = ElegantTextSecondary)
                        Text(
                            text = formatSpeed(totalSpeed),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (totalSpeed > 0) ElegantGreen else ElegantTextTertiary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showLogsDialog = true }) {
                        Icon(imageVector = Icons.Default.ListAlt, contentDescription = "View Logs", tint = ElegantLavenderPrimary)
                    }
                    if (tasks.any { it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.CANCELLED || it.status == DownloadStatus.FAILED }) {
                        IconButton(onClick = { viewModel.clearFinishedTasks() }) {
                            Icon(imageVector = Icons.Default.ClearAll, contentDescription = "Clear Finished", tint = ElegantTextSecondary)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Tasks List or Empty State
        if (tasks.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                EmptyStateCard(
                    icon = Icons.Default.Download,
                    title = "No Active Tasks",
                    description = "Paste any video or audio link on the Home tab to start downloading.",
                    actionLabel = "Go to Home",
                    onAction = { viewModel.selectTab(NavigationTab.HOME) }
                )
            }
        } else {
            val queuedTasks = tasks.filter { it.status == DownloadStatus.QUEUED }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                itemsIndexed(tasks, key = { _, it -> it.id }) { index, task ->
                    val isQueued = task.status == DownloadStatus.QUEUED
                    val queuedIndex = if (isQueued) queuedTasks.indexOfFirst { it.id == task.id } else -1
                    val canMoveUp = isQueued && queuedIndex > 0
                    val canMoveDown = isQueued && queuedIndex >= 0 && queuedIndex < queuedTasks.size - 1

                    DownloadTaskCard(
                        task = task,
                        canMoveUp = canMoveUp,
                        canMoveDown = canMoveDown,
                        onMoveUp = { viewModel.moveQueueItemUp(task.id) },
                        onMoveDown = { viewModel.moveQueueItemDown(task.id) },
                        onPause = { viewModel.pauseTask(task.id) },
                        onResume = { viewModel.resumeTask(task) },
                        onCancel = { viewModel.cancelTask(task.id) },
                        onRetry = { viewModel.retryTask(task) },
                        onDelete = { viewModel.deleteTask(task.id, false) },
                        onOpen = {
                            if (task.outputPath.isNotBlank()) {
                                val openResult = StorageUtils.openMediaFile(context, task.outputPath, task.mediaType)
                                if (openResult.isFailure) {
                                    viewModel.showToast("Cannot open file: ${openResult.exceptionOrNull()?.message}")
                                }
                            }
                        },
                        onViewError = {
                            activeErrorDialog = EngineDiagnosticError(
                                title = "Download Issue (${task.title.take(20)})",
                                reason = task.errorMessage ?: "Unknown error occurred",
                                suggestedAction = "Retry with different format or verify link",
                                technicalDetails = task.detailedLogs.ifBlank { "Task ID: ${task.id}\nURL: ${task.url}" }
                            )
                        }
                    )
                }
            }
        }
    }

    if (showLogsDialog) {
        TechnicalLogsDialog(
            logs = logs,
            onDismiss = { showLogsDialog = false },
            onCopy = { viewModel.copyToClipboard(it, "Engine Logs") }
        )
    }

    activeErrorDialog?.let { err ->
        DiagnosticErrorDialog(
            error = err,
            onDismiss = { activeErrorDialog = null },
            onCopy = { viewModel.copyToClipboard(it, "Error diagnostic") }
        )
    }
}

@Composable
fun DownloadTaskCard(
    task: DownloadTaskEntity,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
    onViewError: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ElegantDarkCard),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.verticalGradient(listOf(ElegantDarkBorder, Color.Transparent))),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Thumbnail + Title + Badges
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(80.dp, 60.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ElegantDarkSurfaceVariant)
                ) {
                    if (task.thumbnail.isNotBlank()) {
                        AsyncImage(
                            model = task.thumbnail,
                            contentDescription = task.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = if (task.mediaType == MediaType.AUDIO) Icons.Default.DownloadDone else Icons.Default.Download,
                            contentDescription = null,
                            tint = ElegantLavenderPrimary,
                            modifier = Modifier
                                .size(28.dp)
                                .align(Alignment.Center)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = ElegantTextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MediaTypeBadge(mediaType = task.mediaType)
                        StatusBadge(status = task.status)
                        if (task.qualityLabel.isNotBlank()) {
                            Surface(
                                color = ElegantDarkSurfaceVariant,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = task.qualityLabel,
                                    color = ElegantTextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Progress Bar & Technical Stats
            if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.PAUSED || task.status == DownloadStatus.PROCESSING) {
                GradientProgressBar(
                    progress = task.progress,
                    isPaused = task.status == DownloadStatus.PAUSED
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${String.format("%.1f", task.progress)}% • ${formatBytes(task.downloadedBytes)} / ${if (task.totalBytes > 0) formatBytes(task.totalBytes) else "..."}",
                        fontSize = 11.sp,
                        color = ElegantTextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (task.status == DownloadStatus.DOWNLOADING && task.speedBytesPerSec > 0) {
                            Text(
                                text = formatSpeed(task.speedBytesPerSec),
                                fontSize = 11.sp,
                                color = ElegantLavenderPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (task.etaSeconds > 0) {
                            Text(
                                text = "ETA: ${formatEta(task.etaSeconds)}",
                                fontSize = 11.sp,
                                color = ElegantTextTertiary
                            )
                        }
                    }
                }
            }

            // Playlist progress indicator if playlist
            if (task.isPlaylist && task.playlistTotal > 1) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Playlist Item ${task.playlistIndex} of ${task.playlistTotal}",
                    fontSize = 11.sp,
                    color = ElegantAmber,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Retry attempt notice if applicable
            if (task.retryAttempt > 0 && task.status == DownloadStatus.QUEUED) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Auto-retry attempt #${task.retryAttempt} scheduled with exponential backoff",
                    fontSize = 10.sp,
                    color = ElegantAmber
                )
            }

            // Error Message Box
            if (task.status == DownloadStatus.FAILED && task.errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = ElegantRed.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = task.errorMessage,
                            color = ElegantRed,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onViewError) {
                            Text("Diagnostics", color = ElegantRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                when (task.status) {
                    DownloadStatus.DOWNLOADING -> {
                        OutlinedButton(
                            onClick = onPause,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(36.dp).testTag("pause_button")
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = "Pause", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pause", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = onCancel,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(36.dp).testTag("cancel_button")
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = "Cancel", tint = ElegantRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cancel", color = ElegantRed, fontSize = 12.sp)
                        }
                    }

                    DownloadStatus.PAUSED -> {
                        Button(
                            onClick = onResume,
                            colors = ButtonDefaults.buttonColors(containerColor = ElegantLavenderPrimary, contentColor = ElegantLavenderOnPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(36.dp).testTag("resume_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Resume", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = onCancel) {
                            Text("Cancel", color = ElegantRed, fontSize = 12.sp)
                        }
                    }

                    DownloadStatus.QUEUED -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Queue #${task.queuePosition}",
                                color = ElegantTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            if (canMoveUp) {
                                IconButton(
                                    onClick = onMoveUp,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ArrowUpward,
                                        contentDescription = "Move Up in Queue",
                                        tint = ElegantLavenderPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            if (canMoveDown) {
                                IconButton(
                                    onClick = onMoveDown,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ArrowDownward,
                                        contentDescription = "Move Down in Queue",
                                        tint = ElegantLavenderPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        TextButton(onClick = onCancel) {
                            Text("Cancel", color = ElegantTextSecondary, fontSize = 12.sp)
                        }
                    }

                    DownloadStatus.COMPLETED -> {
                        Button(
                            onClick = onOpen,
                            colors = ButtonDefaults.buttonColors(containerColor = ElegantGreen, contentColor = Color(0xFF0F172A)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(36.dp).testTag("open_file_button")
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Open File", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Open", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ElegantTextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }

                    DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                        Button(
                            onClick = onRetry,
                            colors = ButtonDefaults.buttonColors(containerColor = ElegantLavenderPrimary, contentColor = ElegantLavenderOnPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(36.dp).testTag("retry_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retry", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ElegantTextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }

                    else -> {}
                }
            }
        }
    }
}
