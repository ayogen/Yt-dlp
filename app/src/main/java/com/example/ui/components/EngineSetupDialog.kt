package com.example.ui.components

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.EngineState
import com.example.engine.FFmpegStatus
import com.example.engine.YtDlpStatus
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
fun EngineSetupDialog(
    isVisible: Boolean,
    isSettingUp: Boolean,
    setupError: String?,
    ytdlpStatus: YtDlpStatus?,
    ffmpegStatus: FFmpegStatus?,
    ytdlpProgress: Float,
    ffmpegProgress: Float,
    deviceAbi: String,
    onStartSetup: () -> Unit,
    onCancel: () -> Unit,
    onContinue: () -> Unit
) {
    if (!isVisible) return

    val isYtDlpReady = ytdlpStatus?.isReady == true
    val isFFmpegReady = ffmpegStatus?.isAvailable == true
    val isAllReady = isYtDlpReady && isFFmpegReady

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    AlertDialog(
        onDismissRequest = {
            if (isAllReady) onContinue()
        },
        properties = DialogProperties(
            dismissOnBackPress = isAllReady,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier
            .padding(20.dp)
            .fillMaxWidth()
            .testTag("first_launch_engine_setup_dialog"),
        containerColor = ElegantDarkCard,
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(if (isAllReady) ElegantGreen.copy(alpha = 0.2f) else ElegantLavenderPrimary.copy(alpha = 0.2f))
                ) {
                    Icon(
                        imageVector = if (isAllReady) Icons.Default.CheckCircle else Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = if (isAllReady) ElegantGreen else ElegantLavenderPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = if (isAllReady) "Engines Ready" else "Preparing Media Engines",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = ElegantTextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (isAllReady)
                        "All native components are verified and ready for extraction."
                    else
                        "One-time setup to download and verify native binaries for your device.",
                    fontSize = 12.sp,
                    color = ElegantTextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Device Architecture Badge
                Surface(
                    color = ElegantDarkSurfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                tint = ElegantLavenderPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Device Architecture:",
                                fontSize = 12.sp,
                                color = ElegantTextSecondary
                            )
                        }
                        Text(
                            text = deviceAbi,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = ElegantLavenderPrimary
                        )
                    }
                }

                // Component 1: yt-dlp
                EngineComponentCard(
                    name = "yt-dlp Core Extractor",
                    subtitle = "Parses media metadata & streams",
                    state = when {
                        isYtDlpReady -> EngineState.READY
                        isSettingUp && !isYtDlpReady -> EngineState.INSTALLING
                        setupError != null && !isYtDlpReady -> EngineState.ERROR
                        else -> ytdlpStatus?.state ?: EngineState.MISSING
                    },
                    version = ytdlpStatus?.version,
                    progress = ytdlpProgress,
                    isSettingUp = isSettingUp && !isYtDlpReady,
                    testTagPrefix = "setup_ytdlp"
                )

                // Component 2: FFmpeg
                EngineComponentCard(
                    name = "FFmpeg Native Engine",
                    subtitle = "1080p+ stream muxing & audio conversion",
                    state = when {
                        isFFmpegReady -> EngineState.READY
                        isSettingUp && isYtDlpReady && !isFFmpegReady -> EngineState.INSTALLING
                        isSettingUp && !isYtDlpReady -> EngineState.MISSING
                        setupError != null && !isFFmpegReady -> EngineState.ERROR
                        else -> ffmpegStatus?.engineState ?: EngineState.MISSING
                    },
                    version = ffmpegStatus?.version,
                    progress = ffmpegProgress,
                    isSettingUp = isSettingUp && isYtDlpReady && !isFFmpegReady,
                    testTagPrefix = "setup_ffmpeg"
                )

                // Error Banner if failed
                if (setupError != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ElegantRed.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(10.dp),
                        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.verticalGradient(listOf(ElegantRed.copy(0.4f), Color.Transparent))),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = ElegantRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Setup Incomplete",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElegantRed
                                )
                                Text(
                                    text = setupError,
                                    fontSize = 11.sp,
                                    color = ElegantTextPrimary
                                )
                                Text(
                                    text = "Internet connection required to prepare the media engines.",
                                    fontSize = 11.sp,
                                    color = ElegantTextSecondary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Info note
                Text(
                    text = "Engines are saved locally to application storage and will be reused for all downloads.",
                    fontSize = 10.sp,
                    color = ElegantTextTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }
        },
        confirmButton = {
            if (isAllReady) {
                Button(
                    onClick = onContinue,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElegantGreen,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("continue_to_app_button")
                ) {
                    Text("Continue to App", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            } else if (setupError != null || (!isSettingUp && !isAllReady)) {
                Button(
                    onClick = onStartSetup,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElegantLavenderPrimary,
                        contentColor = ElegantLavenderOnPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("retry_engine_setup_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry Setup", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        },
        dismissButton = {
            if (isSettingUp) {
                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cancel_engine_setup_button")
                ) {
                    Text("Cancel", color = ElegantTextSecondary)
                }
            }
        }
    )
}

@Composable
private fun EngineComponentCard(
    name: String,
    subtitle: String,
    state: EngineState,
    version: String?,
    progress: Float,
    isSettingUp: Boolean,
    testTagPrefix: String
) {
    val (statusColor, statusLabel) = when (state) {
        EngineState.READY -> ElegantGreen to "Ready ✓"
        EngineState.INSTALLING -> ElegantLavenderPrimary to "Installing..."
        EngineState.UPDATING -> ElegantLavenderPrimary to "Updating..."
        EngineState.MISSING -> ElegantAmber to "Pending"
        EngineState.INVALID -> ElegantRed to "Invalid"
        EngineState.ERROR -> ElegantRed to "Failed"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = ElegantDarkSurfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.verticalGradient(
                listOf(
                    if (state == EngineState.READY) ElegantGreen.copy(0.3f) else ElegantDarkBorder,
                    Color.Transparent
                )
            )
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = ElegantTextPrimary
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = ElegantTextTertiary
                    )
                }

                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        if (isSettingUp) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                strokeWidth = 1.5.dp,
                                color = statusColor
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = statusLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }
            }

            if (state == EngineState.READY && version != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Version: $version",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = ElegantTextSecondary
                )
            }

            if (isSettingUp) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (progress > 85f) "Verifying execution..." else "Downloading binary...",
                        fontSize = 10.sp,
                        color = ElegantLavenderPrimary
                    )
                    Text(
                        text = "${progress.toInt()}%",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElegantLavenderPrimary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress / 100f },
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
}
