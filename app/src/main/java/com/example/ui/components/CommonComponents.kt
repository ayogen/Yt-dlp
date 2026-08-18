package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DownloadStatus
import com.example.data.model.LogEntryEntity
import com.example.data.model.LogLevel
import com.example.data.model.MediaType
import com.example.engine.EngineDiagnosticError
import com.example.ui.theme.ElegantAmber
import com.example.ui.theme.ElegantDarkBackground
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatusBadge(status: DownloadStatus, modifier: Modifier = Modifier) {
    val (bgColor, textColor) = when (status) {
        DownloadStatus.QUEUED -> ElegantDarkSurfaceVariant to ElegantTextSecondary
        DownloadStatus.ANALYZING -> Color(0xFF381E72) to ElegantLavenderPrimary
        DownloadStatus.DOWNLOADING -> Color(0xFF281944) to ElegantLavenderPrimary
        DownloadStatus.PAUSED -> Color(0xFF4A3419) to ElegantAmber
        DownloadStatus.PROCESSING -> Color(0xFF3B2554) to ElegantLavenderPrimary
        DownloadStatus.COMPLETED -> Color(0xFF133826) to ElegantGreen
        DownloadStatus.FAILED -> Color(0xFF4C1D1D) to ElegantRed
        DownloadStatus.CANCELLED -> ElegantDarkSurfaceVariant to ElegantTextTertiary
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            if (status == DownloadStatus.DOWNLOADING || status == DownloadStatus.ANALYZING) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(textColor)
                        .alpha(alpha)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = status.displayName,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun MediaTypeBadge(mediaType: MediaType, modifier: Modifier = Modifier) {
    val (color, text) = when (mediaType) {
        MediaType.VIDEO -> ElegantLavenderPrimary to "VIDEO"
        MediaType.AUDIO -> ElegantAmber to "AUDIO"
        MediaType.PLAYLIST -> ElegantGreen to "PLAYLIST"
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(6.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(color.copy(0.4f), color.copy(0.2f)))),
        modifier = modifier
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun GradientProgressBar(
    progress: Float,
    isPaused: Boolean = false,
    modifier: Modifier = Modifier
) {
    val fraction = (progress / 100f).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(ElegantDarkSurfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (isPaused) {
                        Brush.horizontalGradient(listOf(ElegantAmber.copy(0.7f), ElegantAmber))
                    } else {
                        Brush.horizontalGradient(listOf(Color(0xFF9A82DB), ElegantLavenderPrimary))
                    }
                )
        )
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ElegantDarkCard),
        shape = RoundedCornerShape(20.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.verticalGradient(listOf(ElegantDarkBorder, Color.Transparent))),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(ElegantDarkSurfaceVariant)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ElegantLavenderPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = ElegantTextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = ElegantTextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(containerColor = ElegantLavenderPrimary, contentColor = ElegantLavenderOnPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("empty_state_action_button")
                ) {
                    Text(text = actionLabel, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun TechnicalLogsDialog(
    logs: List<LogEntryEntity>,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit
) {
    var selectedLevel by remember { mutableStateOf<LogLevel?>(null) }
    val filteredLogs = remember(logs, selectedLevel) {
        if (selectedLevel == null) logs else logs.filter { it.level == selectedLevel }
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Engine Logs", fontWeight = FontWeight.Bold, color = ElegantTextPrimary)
                IconButton(
                    onClick = {
                        val text = logs.joinToString("\n") {
                            "[${timeFormat.format(Date(it.timestamp))}] [${it.level}] [${it.tag}] ${it.message}"
                        }
                        onCopy(text)
                    },
                    modifier = Modifier.testTag("copy_logs_button")
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy logs", tint = ElegantLavenderPrimary)
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    FilterChip(
                        selected = selectedLevel == null,
                        onClick = { selectedLevel = null },
                        label = { Text("All (${logs.size})", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = selectedLevel == LogLevel.ERROR,
                        onClick = { selectedLevel = if (selectedLevel == LogLevel.ERROR) null else LogLevel.ERROR },
                        label = { Text("Error", color = ElegantRed, fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = selectedLevel == LogLevel.INFO,
                        onClick = { selectedLevel = if (selectedLevel == LogLevel.INFO) null else LogLevel.INFO },
                        label = { Text("Info", color = ElegantLavenderPrimary, fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = selectedLevel == LogLevel.DEBUG,
                        onClick = { selectedLevel = if (selectedLevel == LogLevel.DEBUG) null else LogLevel.DEBUG },
                        label = { Text("Debug", fontSize = 11.sp) }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF141218))
                        .padding(10.dp)
                ) {
                    if (filteredLogs.isEmpty()) {
                        Text(
                            text = "No logs available for this filter",
                            color = ElegantTextTertiary,
                            fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(filteredLogs) { log ->
                                val color = when (log.level) {
                                    LogLevel.ERROR -> ElegantRed
                                    LogLevel.WARNING -> ElegantAmber
                                    LogLevel.INFO -> ElegantLavenderPrimary
                                    LogLevel.DEBUG -> ElegantTextTertiary
                                }
                                Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                    Row {
                                        Text(
                                            text = timeFormat.format(Date(log.timestamp)),
                                            color = ElegantTextTertiary,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "[${log.level}]",
                                            color = color,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = log.tag,
                                            color = ElegantTextSecondary,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Text(
                                        text = log.message,
                                        color = ElegantTextPrimary,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = ElegantLavenderPrimary, contentColor = ElegantLavenderOnPrimary)
            ) {
                Text("Close")
            }
        },
        containerColor = ElegantDarkCard
    )
}

@Composable
fun DiagnosticErrorDialog(
    error: EngineDiagnosticError,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit
) {
    var showTechDetails by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = ElegantRed, modifier = Modifier.size(36.dp))
        },
        title = {
            Text(text = error.title, color = ElegantTextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ElegantRed.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = "Reason:", fontWeight = FontWeight.Bold, color = ElegantRed, fontSize = 12.sp)
                        Text(text = error.reason, color = ElegantTextPrimary, fontSize = 13.sp)
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = ElegantLavenderPrimary.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = "Suggested Action:", fontWeight = FontWeight.Bold, color = ElegantLavenderPrimary, fontSize = 12.sp)
                        Text(text = error.suggestedAction, color = ElegantTextPrimary, fontSize = 13.sp)
                    }
                }

                TextButton(
                    onClick = { showTechDetails = !showTechDetails },
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = if (showTechDetails) "Hide technical logs" else "View technical logs",
                        color = ElegantTextSecondary,
                        fontSize = 12.sp
                    )
                }

                if (showTechDetails) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF141218))
                            .padding(8.dp)
                    ) {
                        LazyColumn {
                            item {
                                Text(
                                    text = error.technicalDetails,
                                    color = ElegantTextTertiary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = ElegantLavenderPrimary, contentColor = ElegantLavenderOnPrimary)
            ) {
                Text("Dismiss")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    onCopy("Error: ${error.title}\nReason: ${error.reason}\nAction: ${error.suggestedAction}\nTechnical: ${error.technicalDetails}")
                }
            ) {
                Text("Copy Details")
            }
        },
        containerColor = ElegantDarkCard
    )
}
