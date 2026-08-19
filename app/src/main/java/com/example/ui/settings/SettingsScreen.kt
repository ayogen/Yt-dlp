package com.example.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AudioFormat
import com.example.data.model.EngineState
import com.example.data.model.OutputContainer
import com.example.download.StorageUtils
import com.example.engine.FFmpegState
import com.example.engine.FilenameFormatter
import com.example.ui.MainViewModel
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
fun SettingsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val ytdlpStatus by viewModel.ytdlpStatus.collectAsState()
    val versionInfo by viewModel.versionInfo.collectAsState()
    val ffmpegStatus by viewModel.ffmpegStatus.collectAsState()
    val isUpdatingYtDlp by viewModel.isUpdatingYtDlp.collectAsState()
    val updateProgress by viewModel.updateProgress.collectAsState()
    val isUpdatingFFmpeg by viewModel.isUpdatingFFmpeg.collectAsState()
    val ffmpegUpdateProgress by viewModel.ffmpegUpdateProgress.collectAsState()
    val isCheckingUpdates by viewModel.isCheckingUpdates.collectAsState()
    val updateCheckMessage by viewModel.updateCheckMessage.collectAsState()
    val logs by viewModel.logs.collectAsState()

    var showLogsDialog by remember { mutableStateOf(false) }

    // Local mutable copy of settings for editing
    var currentSettings by remember(settings) { mutableStateOf(settings) }

    // SAF Document Tree Folder Launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onDownloadLocationSelected(uri)
        }
    }

    val isSafActive = settings.downloadLocationUri.isNotBlank() && StorageUtils.isSafUriWritable(context, settings.downloadLocationUri)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Section 1: Media Engines
        SettingsSectionHeader(icon = Icons.Default.SettingsSuggest, title = "Media Engines")
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = ElegantDarkCard),
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.verticalGradient(listOf(ElegantDarkBorder, Color.Transparent))),
            modifier = Modifier.fillMaxWidth().testTag("media_engines_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header & Unified Update Check Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Engine Runtime Status",
                            fontWeight = FontWeight.Bold,
                            color = ElegantTextPrimary,
                            fontSize = 14.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = ElegantLavenderPrimary, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ABI: ${viewModel.deviceAbi}",
                                color = ElegantLavenderPrimary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.checkForEngineUpdates() },
                        enabled = !isCheckingUpdates && !isUpdatingYtDlp && !isUpdatingFFmpeg,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElegantLavenderPrimary,
                            contentColor = ElegantLavenderOnPrimary,
                            disabledContainerColor = ElegantDarkSurfaceVariant,
                            disabledContentColor = ElegantTextTertiary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("check_engine_updates_button")
                    ) {
                        if (isCheckingUpdates) {
                            CircularProgressIndicator(
                                color = ElegantLavenderOnPrimary,
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Checking...", fontSize = 12.sp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Check for Updates", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Update result message banner if present
                if (updateCheckMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = if (updateCheckMessage?.startsWith("✓") == true) ElegantGreen.copy(alpha = 0.15f) else ElegantAmber.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = updateCheckMessage ?: "",
                            color = if (updateCheckMessage?.startsWith("✓") == true) ElegantGreen else ElegantAmber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Divider(color = ElegantDarkBorder)
                Spacer(modifier = Modifier.height(14.dp))

                // Engine 1: yt-dlp Core Extractor
                val isYtDlpReady = ytdlpStatus?.isReady == true
                val ytdlpState = when {
                    isUpdatingYtDlp -> EngineState.UPDATING
                    isYtDlpReady -> EngineState.READY
                    else -> ytdlpStatus?.state ?: EngineState.MISSING
                }
                val (ytdlpColor, ytdlpLabel) = when (ytdlpState) {
                    EngineState.READY -> ElegantGreen to "Ready ✓"
                    EngineState.UPDATING, EngineState.INSTALLING -> ElegantLavenderPrimary to "Updating..."
                    EngineState.MISSING -> ElegantAmber to "Missing"
                    EngineState.INVALID -> ElegantRed to "Invalid"
                    EngineState.ERROR -> ElegantRed to "Error"
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "yt-dlp Core Extractor",
                                fontWeight = FontWeight.Bold,
                                color = ElegantTextPrimary,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = ytdlpColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = ytdlpLabel,
                                    color = ytdlpColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Version: ${ytdlpStatus?.version ?: versionInfo?.currentVersion ?: "Not Installed"}",
                            color = ElegantLavenderPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Button(
                        onClick = { viewModel.updateYtDlpBinary() },
                        enabled = !isUpdatingYtDlp,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isYtDlpReady) ElegantDarkSurfaceVariant else ElegantLavenderPrimary,
                            contentColor = if (isYtDlpReady) ElegantTextPrimary else ElegantLavenderOnPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp).testTag("action_ytdlp_button")
                    ) {
                        if (isUpdatingYtDlp) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = ElegantLavenderPrimary
                            )
                        } else {
                            Text(
                                text = if (isYtDlpReady) "Reinstall" else "Install",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (isUpdatingYtDlp) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { updateProgress / 100f },
                        color = ElegantLavenderPrimary,
                        trackColor = ElegantDarkSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Divider(color = ElegantDarkBorder)
                Spacer(modifier = Modifier.height(14.dp))

                // Engine 2: FFmpeg Transcoder & Muxer
                val isFFmpegAvailable = ffmpegStatus?.isAvailable == true
                val ffmpegState = when {
                    isUpdatingFFmpeg -> EngineState.UPDATING
                    isFFmpegAvailable -> EngineState.READY
                    else -> ffmpegStatus?.engineState ?: EngineState.MISSING
                }
                val (ffmpegColor, ffmpegLabel) = when (ffmpegState) {
                    EngineState.READY -> ElegantGreen to "Ready ✓"
                    EngineState.UPDATING, EngineState.INSTALLING -> ElegantLavenderPrimary to "Installing..."
                    EngineState.MISSING -> ElegantAmber to "Missing"
                    EngineState.INVALID -> ElegantRed to "Invalid"
                    EngineState.ERROR -> ElegantRed to "Error"
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "FFmpeg Post-Processor",
                                fontWeight = FontWeight.Bold,
                                color = ElegantTextPrimary,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = ffmpegColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = ffmpegLabel,
                                    color = ffmpegColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (ffmpegStatus?.version != null) {
                            Text(
                                text = "FFmpeg: ${ffmpegStatus?.version}",
                                color = ElegantLavenderPrimary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        } else {
                            Text(
                                text = "Version: Not Installed",
                                color = ElegantTextTertiary,
                                fontSize = 11.sp
                            )
                        }
                        if (ffmpegStatus?.ffprobeVersion != null) {
                            Text(
                                text = "FFprobe: ${ffmpegStatus?.ffprobeVersion}",
                                color = ElegantTextTertiary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.installOrUpdateFFmpeg() },
                        enabled = !isUpdatingFFmpeg,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFFmpegAvailable) ElegantDarkSurfaceVariant else ElegantLavenderPrimary,
                            contentColor = if (isFFmpegAvailable) ElegantTextPrimary else ElegantLavenderOnPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp).testTag("action_ffmpeg_button")
                    ) {
                        if (isUpdatingFFmpeg) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = ElegantLavenderPrimary
                            )
                        } else {
                            Text(
                                text = if (isFFmpegAvailable) "Reinstall" else "Install",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (isUpdatingFFmpeg) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { ffmpegUpdateProgress / 100f },
                        color = ElegantLavenderPrimary,
                        trackColor = ElegantDarkSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section 2: Storage & Download Location (SAF)
        SettingsSectionHeader(icon = Icons.Default.Folder, title = "Storage & Download Location")
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = ElegantDarkCard),
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.verticalGradient(listOf(ElegantDarkBorder, Color.Transparent))),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Root Download Location",
                            fontWeight = FontWeight.Bold,
                            color = ElegantTextPrimary,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isSafActive) {
                                "Custom SAF Root: ${settings.downloadLocationDisplayName}"
                            } else {
                                "Default Storage (App Isolated / Internal)"
                            },
                            color = if (isSafActive) ElegantGreen else ElegantLavenderPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }

                    Surface(
                        color = if (isSafActive) ElegantGreen.copy(alpha = 0.15f) else ElegantDarkSurfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = if (isSafActive) Icons.Default.CheckCircle else Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = if (isSafActive) ElegantGreen else ElegantTextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isSafActive) "SAF ACTIVE" else "DEFAULT",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSafActive) ElegantGreen else ElegantTextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Select one root directory using Android Storage Access Framework (SAF). Subfolders (Video/, Music/, Audio/, Subtitles/, Images/, Other/) are automatically created inside your chosen directory.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ElegantTextSecondary,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons for Folder Selection
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { folderPickerLauncher.launch(null) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElegantLavenderPrimary,
                            contentColor = ElegantLavenderOnPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("choose_download_location_button")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isSafActive) "Change Folder" else "Select SAF Folder",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    if (isSafActive) {
                        OutlinedButton(
                            onClick = { viewModel.resetDownloadLocationToDefault() },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("reset_download_location_button")
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset", fontSize = 12.sp)
                        }
                    }
                }

                // Subfolders mapping visual explanation
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF141218))
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            text = "Automatic Media Subfolder Routing:",
                            fontSize = 10.sp,
                            color = ElegantTextTertiary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• Video/ (MP4, MKV, WebM videos)\n• Music/ (MP3, M4A, FLAC, AAC tracks)\n• Audio/ (Opus, WAV, general audio)\n• Subtitles/ (.vtt, .srt tracks)\n• Images/ (Thumbnails, cover art)",
                            fontSize = 11.sp,
                            color = ElegantTextSecondary,
                            lineHeight = 16.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section 3: Download Configuration
        SettingsSectionHeader(icon = Icons.Default.Download, title = "Download Configuration")
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = ElegantDarkCard),
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.verticalGradient(listOf(ElegantDarkBorder, Color.Transparent))),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Max Concurrent Downloads
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(text = "Simultaneous Downloads", fontWeight = FontWeight.Bold, color = ElegantTextPrimary, fontSize = 13.sp)
                        Text(text = "Max active downloads at once", color = ElegantTextSecondary, fontSize = 11.sp)
                    }
                    Text(
                        text = "${currentSettings.maxConcurrentDownloads}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElegantLavenderPrimary
                    )
                }

                Slider(
                    value = currentSettings.maxConcurrentDownloads.toFloat(),
                    onValueChange = {
                        val updated = currentSettings.copy(maxConcurrentDownloads = it.toInt())
                        currentSettings = updated
                        viewModel.updateSettings(updated)
                    },
                    valueRange = 1f..10f,
                    steps = 8,
                    colors = SliderDefaults.colors(
                        thumbColor = ElegantLavenderPrimary,
                        activeTrackColor = ElegantLavenderPrimary,
                        inactiveTrackColor = ElegantDarkSurfaceVariant
                    ),
                    modifier = Modifier.testTag("max_downloads_slider")
                )

                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = ElegantDarkBorder)
                Spacer(modifier = Modifier.height(12.dp))

                // Default Video Container
                Text(text = "Default Video Container", fontWeight = FontWeight.Bold, color = ElegantTextPrimary, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(OutputContainer.MP4, OutputContainer.MKV, OutputContainer.WEBM).forEach { container ->
                        FilterChip(
                            selected = currentSettings.defaultContainer == container,
                            onClick = {
                                val updated = currentSettings.copy(defaultContainer = container)
                                currentSettings = updated
                                viewModel.updateSettings(updated)
                            },
                            label = { Text(container.ext.uppercase(), fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElegantLavenderPrimary,
                                selectedLabelColor = ElegantLavenderOnPrimary,
                                containerColor = ElegantDarkSurfaceVariant,
                                labelColor = ElegantTextPrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Default Audio Format
                Text(text = "Default Audio Format", fontWeight = FontWeight.Bold, color = ElegantTextPrimary, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(AudioFormat.MP3, AudioFormat.M4A, AudioFormat.OPUS, AudioFormat.FLAC).forEach { fmt ->
                        FilterChip(
                            selected = currentSettings.defaultAudioFormat == fmt,
                            onClick = {
                                val updated = currentSettings.copy(defaultAudioFormat = fmt)
                                currentSettings = updated
                                viewModel.updateSettings(updated)
                            },
                            label = { Text(fmt.displayName, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElegantLavenderPrimary,
                                selectedLabelColor = ElegantLavenderOnPrimary,
                                containerColor = ElegantDarkSurfaceVariant,
                                labelColor = ElegantTextPrimary
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section 4: Filename Formatting
        SettingsSectionHeader(icon = Icons.Default.AutoFixHigh, title = "Filename Template")
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = ElegantDarkCard),
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.verticalGradient(listOf(ElegantDarkBorder, Color.Transparent))),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Custom Template Expression",
                    fontWeight = FontWeight.Bold,
                    color = ElegantTextPrimary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = currentSettings.filenameTemplate,
                    onValueChange = {
                        val updated = currentSettings.copy(filenameTemplate = it)
                        currentSettings = updated
                        viewModel.updateSettings(updated)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElegantLavenderPrimary,
                        unfocusedBorderColor = ElegantDarkBorder,
                        focusedContainerColor = ElegantDarkSurfaceVariant,
                        unfocusedContainerColor = ElegantDarkSurfaceVariant,
                        focusedTextColor = ElegantTextPrimary,
                        unfocusedTextColor = ElegantTextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("filename_template_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Presets
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "Title" to "%(title)s.%(ext)s",
                        "Title + ID" to "%(title)s [%(id)s].%(ext)s",
                        "Uploader + Title" to "%(uploader)s - %(title)s.%(ext)s"
                    ).forEach { (label, tmpl) ->
                        FilterChip(
                            selected = currentSettings.filenameTemplate == tmpl,
                            onClick = {
                                val updated = currentSettings.copy(filenameTemplate = tmpl)
                                currentSettings = updated
                                viewModel.updateSettings(updated)
                            },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Preview Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF141218))
                        .padding(10.dp)
                ) {
                    Column {
                        Text(text = "Preview Output:", fontSize = 10.sp, color = ElegantTextTertiary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = FilenameFormatter.format(
                                template = currentSettings.filenameTemplate,
                                title = "Rick Astley - Never Gonna Give You Up",
                                uploader = "RickAstleyVEVO",
                                id = "dQw4w9WgXcQ",
                                ext = "mp4"
                            ),
                            fontSize = 12.sp,
                            color = ElegantLavenderPrimary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section 5: Advanced & Cookies
        SettingsSectionHeader(icon = Icons.Default.Cookie, title = "Authentication & CLI Arguments")
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = ElegantDarkCard),
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.verticalGradient(listOf(ElegantDarkBorder, Color.Transparent))),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Cookies File Path (Optional)",
                    fontWeight = FontWeight.Bold,
                    color = ElegantTextPrimary,
                    fontSize = 13.sp
                )
                Text(
                    text = "Specify path to cookies.txt for accessing private/authenticated media",
                    color = ElegantTextSecondary,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = currentSettings.cookiesFilePath,
                    onValueChange = {
                        val updated = currentSettings.copy(cookiesFilePath = it)
                        currentSettings = updated
                        viewModel.updateSettings(updated)
                    },
                    placeholder = { Text("/path/to/cookies.txt", color = ElegantTextTertiary, fontSize = 12.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElegantLavenderPrimary,
                        unfocusedBorderColor = ElegantDarkBorder,
                        focusedContainerColor = ElegantDarkSurfaceVariant,
                        unfocusedContainerColor = ElegantDarkSurfaceVariant,
                        focusedTextColor = ElegantTextPrimary,
                        unfocusedTextColor = ElegantTextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Custom yt-dlp Arguments (Optional)",
                    fontWeight = FontWeight.Bold,
                    color = ElegantTextPrimary,
                    fontSize = 13.sp
                )
                Text(
                    text = "Additional CLI flags (e.g. --referer, --geo-bypass)",
                    color = ElegantTextSecondary,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = currentSettings.customYtDlpArgs,
                    onValueChange = {
                        val updated = currentSettings.copy(customYtDlpArgs = it)
                        currentSettings = updated
                        viewModel.updateSettings(updated)
                    },
                    placeholder = { Text("--no-mtime --geo-bypass", color = ElegantTextTertiary, fontSize = 12.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElegantLavenderPrimary,
                        unfocusedBorderColor = ElegantDarkBorder,
                        focusedContainerColor = ElegantDarkSurfaceVariant,
                        unfocusedContainerColor = ElegantDarkSurfaceVariant,
                        focusedTextColor = ElegantTextPrimary,
                        unfocusedTextColor = ElegantTextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section 6: Diagnostics & Live Logs
        SettingsSectionHeader(icon = Icons.Default.ListAlt, title = "Diagnostics & System Logs")
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = ElegantDarkCard),
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.verticalGradient(listOf(ElegantDarkBorder, Color.Transparent))),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Real-Time Engine Logs",
                    fontWeight = FontWeight.Bold,
                    color = ElegantTextPrimary,
                    fontSize = 13.sp
                )
                Text(
                    text = "Inspect background commands, extraction traces, and error outputs",
                    color = ElegantTextSecondary,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { showLogsDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = ElegantLavenderPrimary, contentColor = ElegantLavenderOnPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("view_system_logs_button")
                    ) {
                        Icon(Icons.Default.ListAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("View Engine Logs", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val sysInfo = "ABI: ${viewModel.deviceAbi}\nyt-dlp: ${ytdlpStatus?.version ?: versionInfo?.currentVersion ?: "Not Installed"} (State: ${ytdlpStatus?.state})\nFFmpeg: ${ffmpegStatus?.version ?: "Not Installed"} (State: ${ffmpegStatus?.state})\nSettings: $currentSettings"
                            viewModel.copyToClipboard(sysInfo, "System Diagnostics")
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Copy System Info", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Legal & Responsible Use Notice
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141218)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = ElegantTextTertiary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Responsible Usage Notice", fontWeight = FontWeight.Bold, color = ElegantTextSecondary, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "This application is a generic client interface powered by yt-dlp. Please ensure you have permission to download media and adhere to all relevant intellectual property laws and terms of service.",
                    color = ElegantTextTertiary,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showLogsDialog) {
        TechnicalLogsDialog(
            logs = logs,
            onDismiss = { showLogsDialog = false },
            onCopy = { viewModel.copyToClipboard(it, "Engine Logs") }
        )
    }
}

@Composable
fun SettingsSectionHeader(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = ElegantLavenderPrimary, modifier = Modifier.size(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = ElegantTextPrimary
        )
    }
}
