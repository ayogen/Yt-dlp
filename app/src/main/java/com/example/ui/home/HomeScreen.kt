package com.example.ui.home

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.data.model.DownloadHistoryEntity
import com.example.data.model.DownloadProfile
import com.example.data.model.MediaType
import com.example.data.model.OutputContainer
import com.example.data.model.formatBytes
import com.example.engine.EngineDiagnosticError
import com.example.ui.AnalysisUiState
import com.example.ui.MainViewModel
import com.example.ui.components.DiagnosticErrorDialog
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
import java.util.UUID

@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    var urlInput by remember { mutableStateOf("") }
    val analysisState by viewModel.analysisState.collectAsState()
    val historyList by viewModel.filteredHistory.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val detectedClipboardUrl by viewModel.detectedClipboardUrl.collectAsState()
    val allProfiles by viewModel.allProfiles.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()

    var activeErrorDialog by remember { mutableStateOf<EngineDiagnosticError?>(null) }
    var showCreateProfileDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.checkClipboardForMediaLink()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Clipboard Link Detection Banner
        AnimatedVisibility(visible = settings.detectClipboardLinks && !detectedClipboardUrl.isNullOrBlank()) {
            detectedClipboardUrl?.let { clipUrl ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = ElegantLavenderPrimary.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(listOf(ElegantLavenderPrimary, ElegantLavenderPrimary.copy(alpha = 0.4f)))
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Assignment,
                            contentDescription = null,
                            tint = ElegantLavenderPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Link detected on clipboard",
                                fontWeight = FontWeight.Bold,
                                color = ElegantTextPrimary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = clipUrl,
                                color = ElegantTextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = {
                                urlInput = clipUrl
                                viewModel.dismissDetectedClipboardUrl()
                                viewModel.analyzeUrl(clipUrl)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElegantLavenderPrimary,
                                contentColor = ElegantLavenderOnPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Extract", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(
                            onClick = { viewModel.dismissDetectedClipboardUrl() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Dismiss",
                                tint = ElegantTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Hero Card in Elegant Dark aesthetic
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF381E72), Color(0xFF2B2930)),
                        radius = 700f
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ElegantLavenderPrimary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = ElegantLavenderOnPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Transcode Engine",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ElegantTextPrimary
                        )
                    }

                    // Ready Badge with glowing green dot
                    Surface(
                        color = Color(0xFF141218),
                        shape = RoundedCornerShape(16.dp),
                        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(ElegantDarkBorder, ElegantDarkBorder)))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(ElegantGreen)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "YT-DLP & FFMPEG READY",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElegantTextSecondary,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "High-fidelity extraction and stream muxing from any yt-dlp supported source. High quality formats, audio extraction, and queue management.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFE8DEF8),
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Preset Profiles Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Download Profiles",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = ElegantTextPrimary
            )
            TextButton(onClick = { showCreateProfileDialog = true }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = ElegantLavenderPrimary)
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Profile", color = ElegantLavenderPrimary, fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(allProfiles) { profile ->
                val isSelected = activeProfile?.id == profile.id
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.selectActiveProfile(profile) },
                    label = {
                        Text(
                            text = profile.name,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp
                        )
                    },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElegantLavenderPrimary,
                        selectedLabelColor = ElegantLavenderOnPrimary,
                        containerColor = ElegantDarkCard,
                        labelColor = ElegantTextPrimary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = if (isSelected) ElegantLavenderPrimary else ElegantDarkBorder
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // URL Input Card
        Card(
            colors = CardDefaults.cardColors(containerColor = ElegantDarkCard),
            shape = RoundedCornerShape(20.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.verticalGradient(listOf(ElegantDarkBorder, Color.Transparent))),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Media URL",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = ElegantTextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    placeholder = { Text("Paste video, audio, or playlist link...", color = ElegantTextTertiary, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Link, contentDescription = null, tint = ElegantLavenderPrimary)
                    },
                    trailingIcon = {
                        Row {
                            if (urlInput.isNotBlank()) {
                                IconButton(onClick = { urlInput = "" }) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", tint = ElegantTextSecondary)
                                }
                            }
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                    if (text.isNotBlank()) {
                                        urlInput = text.trim()
                                    }
                                },
                                modifier = Modifier.testTag("paste_url_button")
                            ) {
                                Icon(imageVector = Icons.Default.ContentPaste, contentDescription = "Paste", tint = ElegantLavenderPrimary)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElegantLavenderPrimary,
                        unfocusedBorderColor = ElegantDarkBorder,
                        focusedContainerColor = ElegantDarkSurfaceVariant,
                        unfocusedContainerColor = ElegantDarkSurfaceVariant,
                        focusedTextColor = ElegantTextPrimary,
                        unfocusedTextColor = ElegantTextPrimary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("url_input_field")
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Analyze Action Button
                Button(
                    onClick = {
                        if (urlInput.isNotBlank()) {
                            viewModel.analyzeUrl(urlInput)
                        }
                    },
                    enabled = urlInput.isNotBlank() && analysisState !is AnalysisUiState.Analyzing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElegantLavenderPrimary,
                        contentColor = ElegantLavenderOnPrimary,
                        disabledContainerColor = ElegantDarkSurfaceVariant,
                        disabledContentColor = ElegantTextTertiary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("analyze_button")
                ) {
                    if (analysisState is AnalysisUiState.Analyzing) {
                        CircularProgressIndicator(
                            color = ElegantLavenderOnPrimary,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Extracting Metadata...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Extract Formats & Info", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }

        // Analysis Error Banner
        if (analysisState is AnalysisUiState.Error) {
            val err = (analysisState as AnalysisUiState.Error).error
            Spacer(modifier = Modifier.height(14.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = ElegantRed.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(14.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(ElegantRed.copy(0.5f), ElegantRed.copy(0.2f)))),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = ElegantRed)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = err.title, fontWeight = FontWeight.Bold, color = ElegantTextPrimary, fontSize = 13.sp)
                        Text(text = err.reason, color = ElegantTextSecondary, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(onClick = { activeErrorDialog = err }) {
                        Text("Details", color = ElegantRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // Supported Sources Quick Badges
        Text(
            text = "Supported Extractors",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = ElegantTextPrimary
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf("YouTube", "Vimeo", "Twitter/X", "TikTok", "Reddit", "Direct Link").forEach { platform ->
                Surface(
                    color = ElegantDarkCard,
                    shape = RoundedCornerShape(10.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(ElegantDarkBorder, ElegantDarkBorder))),
                    modifier = Modifier.clickable {
                        if (platform == "Direct Link") {
                            urlInput = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
                        }
                    }
                ) {
                    Text(
                        text = platform,
                        color = ElegantTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Recent Downloads Row
        if (historyList.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Recent Downloads",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = ElegantTextPrimary
                )
                TextButton(onClick = { viewModel.selectTab(com.example.ui.NavigationTab.HISTORY) }) {
                    Text("View All", color = ElegantLavenderPrimary, fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(historyList.take(6)) { item ->
                    RecentHistoryCard(item = item)
                }
            }
        }
    }

    // Bottom Sheet for Media Analysis
    if (analysisState is AnalysisUiState.Success) {
        val meta = (analysisState as AnalysisUiState.Success).metadata
        MediaAnalysisBottomSheet(
            metadata = meta,
            onDismiss = { viewModel.clearAnalysis() },
            onDownload = { format, mediaType, container, bitrate, embedSubs, embedThumb, playlistSel ->
                val quality = format?.displayResolution ?: activeProfile?.videoQuality ?: "Best"
                viewModel.startDownload(
                    metadata = meta,
                    selectedFormat = format,
                    mediaType = mediaType,
                    targetContainer = container,
                    audioBitrate = bitrate,
                    embedSubs = embedSubs,
                    embedThumbnail = embedThumb,
                    selectedPlaylistIndices = playlistSel,
                    qualityLabel = quality
                )
            }
        )
    }

    // Diagnostic Error Dialog
    activeErrorDialog?.let { err ->
        DiagnosticErrorDialog(
            error = err,
            onDismiss = { activeErrorDialog = null },
            onCopy = { viewModel.copyToClipboard(it, "Error details") }
        )
    }

    // Create Custom Profile Dialog
    if (showCreateProfileDialog) {
        CreateProfileDialog(
            onDismiss = { showCreateProfileDialog = false },
            onSave = { newProf ->
                viewModel.saveCustomProfile(newProf)
                showCreateProfileDialog = false
            }
        )
    }
}

@Composable
fun CreateProfileDialog(
    onDismiss: () -> Unit,
    onSave: (DownloadProfile) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var mediaType by remember { mutableStateOf(MediaType.VIDEO) }
    var quality by remember { mutableStateOf("1080p") }
    var container by remember { mutableStateOf("mp4") }
    var bitrate by remember { mutableStateOf(320) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Download Profile", color = ElegantTextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name") },
                    placeholder = { Text("e.g. High Quality Audio") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mediaType == MediaType.VIDEO,
                        onClick = {
                            mediaType = MediaType.VIDEO
                            container = "mp4"
                        },
                        label = { Text("Video") }
                    )
                    FilterChip(
                        selected = mediaType == MediaType.AUDIO,
                        onClick = {
                            mediaType = MediaType.AUDIO
                            container = "mp3"
                        },
                        label = { Text("Audio Only") }
                    )
                }

                if (mediaType == MediaType.VIDEO) {
                    Text("Target Resolution:", fontSize = 12.sp, color = ElegantTextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("2160p", "1080p", "720p", "480p").forEach { res ->
                            FilterChip(
                                selected = quality == res,
                                onClick = { quality = res },
                                label = { Text(res, fontSize = 11.sp) }
                            )
                        }
                    }
                } else {
                    Text("Audio Bitrate:", fontSize = 12.sp, color = ElegantTextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(320, 256, 192, 128).forEach { rate ->
                            FilterChip(
                                selected = bitrate == rate,
                                onClick = { bitrate = rate },
                                label = { Text("${rate}k", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            DownloadProfile(
                                id = UUID.randomUUID().toString(),
                                name = name.trim(),
                                description = "Custom user preset",
                                mediaType = mediaType,
                                videoQuality = quality,
                                container = container,
                                audioBitrate = bitrate,
                                isPreset = false
                            )
                        )
                    }
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = ElegantLavenderPrimary, contentColor = ElegantLavenderOnPrimary)
            ) {
                Text("Save Profile")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = ElegantTextSecondary)
            }
        }
    )
}

@Composable
fun RecentHistoryCard(item: DownloadHistoryEntity) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ElegantDarkCard),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.verticalGradient(listOf(ElegantDarkBorder, Color.Transparent))),
        modifier = Modifier.width(160.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ElegantDarkSurfaceVariant)
            ) {
                if (item.thumbnail.isNotBlank()) {
                    AsyncImage(
                        model = item.thumbnail,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = ElegantLavenderPrimary,
                        modifier = Modifier
                            .size(32.dp)
                            .align(Alignment.Center)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodySmall,
                color = ElegantTextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = formatBytes(item.fileSize),
                fontSize = 11.sp,
                color = ElegantTextTertiary
            )
        }
    }
}
