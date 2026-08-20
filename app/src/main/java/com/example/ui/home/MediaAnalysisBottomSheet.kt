package com.example.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FeaturedPlayList
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.AudioFormat
import com.example.data.model.FormatInfo
import com.example.data.model.MediaMetadata
import com.example.data.model.MediaType
import com.example.data.model.OutputContainer
import com.example.ui.components.MediaTypeBadge
import com.example.ui.theme.ElegantAmber
import com.example.ui.theme.ElegantDarkBackground
import com.example.ui.theme.ElegantDarkBorder
import com.example.ui.theme.ElegantDarkBorderSubtle
import com.example.ui.theme.ElegantDarkCard
import com.example.ui.theme.ElegantDarkSurface
import com.example.ui.theme.ElegantDarkSurfaceVariant
import com.example.ui.theme.ElegantGreen
import com.example.ui.theme.ElegantLavenderOnPrimary
import com.example.ui.theme.ElegantLavenderPrimary
import com.example.ui.theme.ElegantTextPrimary
import com.example.ui.theme.ElegantTextSecondary
import com.example.ui.theme.ElegantTextTertiary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MediaAnalysisBottomSheet(
    metadata: MediaMetadata,
    onDismiss: () -> Unit,
    onDownload: (
        format: FormatInfo?,
        mediaType: MediaType,
        container: OutputContainer,
        audioBitrate: Int?,
        embedSubs: Boolean,
        embedThumb: Boolean,
        playlistSelection: Set<Int>
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedMediaType by remember {
        mutableStateOf(if (metadata.isPlaylist) MediaType.PLAYLIST else MediaType.VIDEO)
    }

    val deduplicatedVideoFormats = remember(metadata.formats) {
        deduplicateVideoFormats(metadata.formats)
    }

    var selectedFormat by remember(metadata) {
        mutableStateOf(
            deduplicatedVideoFormats.firstOrNull { it.height != null && it.height <= 1080 }
                ?: deduplicatedVideoFormats.firstOrNull()
                ?: metadata.formats.firstOrNull()
        )
    }

    var selectedContainer by remember { mutableStateOf(OutputContainer.MP4) }
    var selectedAudioFormat by remember { mutableStateOf(AudioFormat.MP3) }
    var selectedAudioBitrate by remember { mutableStateOf(320) }

    var embedSubtitles by remember { mutableStateOf(false) }
    var embedThumbnail by remember { mutableStateOf(true) }

    val selectedPlaylistItems = remember {
        mutableStateOf(metadata.playlistEntries.indices.toMutableSet())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ElegantDarkSurface,
        dragHandle = null,
        modifier = Modifier.testTag("analysis_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MediaTypeBadge(mediaType = selectedMediaType)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (metadata.isPlaylist) "Playlist Detected" else "Extracted Media",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ElegantTextPrimary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = ElegantTextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Media Info Container (Aspect Ratio Preview)
            Card(
                colors = CardDefaults.cardColors(containerColor = ElegantDarkCard),
                shape = RoundedCornerShape(24.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.verticalGradient(listOf(ElegantDarkBorder, Color.Transparent))),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                            .background(Color(0xFF141218))
                    ) {
                        if (metadata.thumbnail.isNotBlank()) {
                            AsyncImage(
                                model = metadata.thumbnail,
                                contentDescription = metadata.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize()
                            )
                        }

                        // Duration Badge
                        if (metadata.durationSeconds > 0) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = metadata.durationFormatted,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = metadata.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ElegantTextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${metadata.uploader.ifBlank { "Media Creator" }} • ${if (metadata.viewCount != null) "${metadata.viewCount / 1000}K Views • " else ""}${metadata.uploadDate.ifBlank { "Recent" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = ElegantTextSecondary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // 2-Column Info Grid for Resolution & Format
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1B1F)),
                                shape = RoundedCornerShape(16.dp),
                                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(ElegantDarkBorder, ElegantDarkBorder))),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(text = "RESOLUTION", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = ElegantTextTertiary, letterSpacing = 1.sp)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = selectedFormat?.displayResolution ?: "Best HD",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ElegantLavenderPrimary
                                    )
                                }
                            }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1B1F)),
                                shape = RoundedCornerShape(16.dp),
                                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(ElegantDarkBorder, ElegantDarkBorder))),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(text = "FORMAT", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = ElegantTextTertiary, letterSpacing = 1.sp)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (selectedMediaType == MediaType.AUDIO) selectedAudioFormat.displayName else selectedContainer.ext.uppercase(),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ElegantLavenderPrimary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Estimated Size & FFmpeg Note
                        Surface(
                            color = Color(0xFF1C1B1F).copy(alpha = 0.6f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "Estimated size: ",
                                    color = ElegantTextSecondary,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = selectedFormat?.displayFileSize ?: "Unknown size",
                                    color = ElegantLavenderPrimary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "• FFmpeg Stream Muxing",
                                    color = ElegantTextTertiary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tab Selector (Video / Audio / Playlist)
            SecondaryTabRow(
                selectedTabIndex = when (selectedMediaType) {
                    MediaType.VIDEO -> 0
                    MediaType.AUDIO -> 1
                    MediaType.PLAYLIST -> 2
                },
                containerColor = ElegantDarkSurfaceVariant,
                contentColor = ElegantLavenderPrimary
            ) {
                Tab(
                    selected = selectedMediaType == MediaType.VIDEO,
                    onClick = { selectedMediaType = MediaType.VIDEO },
                    text = { Text("Video", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedMediaType == MediaType.AUDIO,
                    onClick = { selectedMediaType = MediaType.AUDIO },
                    text = { Text("Audio Only", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Audiotrack, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                if (metadata.isPlaylist) {
                    Tab(
                        selected = selectedMediaType == MediaType.PLAYLIST,
                        onClick = { selectedMediaType = MediaType.PLAYLIST },
                        text = { Text("Playlist", fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.FeaturedPlayList, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Format Choices
            when (selectedMediaType) {
                MediaType.VIDEO -> {
                    Text(
                        text = "Available Video Formats",
                        style = MaterialTheme.typography.labelLarge,
                        color = ElegantTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val videoFormatsToDisplay = if (deduplicatedVideoFormats.isNotEmpty()) {
                            deduplicatedVideoFormats
                        } else {
                            listOf(
                                FormatInfo(
                                    formatId = "best",
                                    ext = "mp4",
                                    resolution = "Best Quality"
                                )
                            )
                        }

                        videoFormatsToDisplay.forEach { format ->
                            val isSelected = selectedFormat?.formatId == format.formatId
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedFormat = format },
                                label = {
                                    Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                        Text(text = format.displayResolution, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "${format.displayFileSize} • ${if (format.fps != null && format.fps > 0) "${format.fps.toInt()}fps" else format.ext.uppercase()}",
                                            fontSize = 10.sp,
                                            color = if (isSelected) ElegantLavenderOnPrimary else ElegantTextSecondary
                                        )
                                    }
                                },
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

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Container",
                        style = MaterialTheme.typography.labelLarge,
                        color = ElegantTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(OutputContainer.MP4, OutputContainer.MKV, OutputContainer.WEBM).forEach { container ->
                            FilterChip(
                                selected = selectedContainer == container,
                                onClick = { selectedContainer = container },
                                label = { Text(container.ext.uppercase(), fontWeight = FontWeight.SemiBold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElegantLavenderPrimary,
                                    selectedLabelColor = ElegantLavenderOnPrimary,
                                    containerColor = ElegantDarkCard,
                                    labelColor = ElegantTextPrimary
                                )
                            )
                        }
                    }
                }

                MediaType.AUDIO -> {
                    Text(
                        text = "Audio Format",
                        style = MaterialTheme.typography.labelLarge,
                        color = ElegantTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(AudioFormat.MP3, AudioFormat.M4A, AudioFormat.OPUS, AudioFormat.FLAC).forEach { fmt ->
                            FilterChip(
                                selected = selectedAudioFormat == fmt,
                                onClick = { selectedAudioFormat = fmt },
                                label = { Text(fmt.displayName, fontWeight = FontWeight.SemiBold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElegantLavenderPrimary,
                                    selectedLabelColor = ElegantLavenderOnPrimary,
                                    containerColor = ElegantDarkCard,
                                    labelColor = ElegantTextPrimary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Bitrate",
                        style = MaterialTheme.typography.labelLarge,
                        color = ElegantTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(320 to "320 kbps (High)", 256 to "256 kbps", 192 to "192 kbps", 128 to "128 kbps").forEach { (rate, label) ->
                            FilterChip(
                                selected = selectedAudioBitrate == rate,
                                onClick = { selectedAudioBitrate = rate },
                                label = { Text(label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElegantLavenderPrimary,
                                    selectedLabelColor = ElegantLavenderOnPrimary,
                                    containerColor = ElegantDarkCard,
                                    labelColor = ElegantTextPrimary
                                )
                            )
                        }
                    }
                }

                MediaType.PLAYLIST -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Videos (${selectedPlaylistItems.value.size}/${metadata.playlistEntries.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = ElegantTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Row {
                            TextButton(onClick = {
                                selectedPlaylistItems.value = metadata.playlistEntries.indices.toMutableSet()
                            }) {
                                Text("Select All", fontSize = 12.sp, color = ElegantLavenderPrimary)
                            }
                            TextButton(onClick = {
                                selectedPlaylistItems.value = mutableSetOf()
                            }) {
                                Text("Clear", fontSize = 12.sp, color = ElegantTextSecondary)
                            }
                        }
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        metadata.playlistEntries.forEachIndexed { index, entry ->
                            val isChecked = selectedPlaylistItems.value.contains(index)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isChecked) ElegantDarkCard else Color.Transparent)
                                    .clickable {
                                        val updated = selectedPlaylistItems.value.toMutableSet()
                                        if (isChecked) updated.remove(index) else updated.add(index)
                                        selectedPlaylistItems.value = updated
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        val updated = selectedPlaylistItems.value.toMutableSet()
                                        if (checked) updated.add(index) else updated.remove(index)
                                        selectedPlaylistItems.value = updated
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = ElegantLavenderPrimary)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = entry.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ElegantTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Divider(color = ElegantDarkBorder)
            Spacer(modifier = Modifier.height(10.dp))

            // Subtitle & Thumbnail toggles
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Embed Subtitles", color = ElegantTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Switch(
                    checked = embedSubtitles,
                    onCheckedChange = { embedSubtitles = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = ElegantLavenderPrimary, checkedTrackColor = ElegantDarkSurfaceVariant)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Embed Thumbnail", color = ElegantTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Switch(
                    checked = embedThumbnail,
                    onCheckedChange = { embedThumbnail = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = ElegantLavenderPrimary, checkedTrackColor = ElegantDarkSurfaceVariant)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action Row: CC / HD Pills + DOWNLOAD NOW Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // CC Badge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(ElegantDarkSurfaceVariant)
                ) {
                    Text(text = "CC", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElegantTextPrimary)
                }

                // HD Badge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(ElegantDarkSurfaceVariant)
                ) {
                    Text(text = "HD", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElegantTextPrimary)
                }

                // DOWNLOAD NOW Button
                Button(
                    onClick = {
                        val container = if (selectedMediaType == MediaType.AUDIO) {
                            OutputContainer.fromExt(selectedAudioFormat.ext)
                        } else {
                            selectedContainer
                        }
                        onDownload(
                            selectedFormat,
                            selectedMediaType,
                            container,
                            if (selectedMediaType == MediaType.AUDIO) selectedAudioBitrate else null,
                            embedSubtitles,
                            embedThumbnail,
                            selectedPlaylistItems.value
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElegantLavenderPrimary, contentColor = ElegantLavenderOnPrimary),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .testTag("start_download_button")
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "DOWNLOAD NOW",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

fun deduplicateVideoFormats(formats: List<FormatInfo>): List<FormatInfo> {
    val videoFormats = formats.filter { !it.isAudioOnly }
    if (videoFormats.isEmpty()) return emptyList()

    val grouped = videoFormats.groupBy { format ->
        when {
            format.height != null && format.height > 0 -> "${format.height}p"
            format.displayResolution.isNotBlank() && format.displayResolution != "null" && format.displayResolution != "Audio Only" -> format.displayResolution
            else -> "Default"
        }
    }

    val bestPerResolution = grouped.map { (_, list) ->
        val chosen = list.maxWithOrNull(
            compareBy<FormatInfo> { it.height ?: 0 }
                .thenBy { it.fps ?: 30.0 }
                .thenBy { it.tbr ?: it.vbr ?: 0.0 }
                .thenBy { it.filesize ?: it.filesizeApprox ?: 0L }
                .thenBy { if (it.ext.lowercase() == "mp4") 1 else 0 }
        ) ?: list.first()

        // Preserve filesize or filesizeApprox from other formats with the same resolution if chosen format has null
        val bestFilesize = chosen.filesize ?: list.firstNotNullOfOrNull { it.filesize }
        val bestFilesizeApprox = chosen.filesizeApprox ?: list.firstNotNullOfOrNull { it.filesizeApprox }

        if (bestFilesize != chosen.filesize || bestFilesizeApprox != chosen.filesizeApprox) {
            chosen.copy(filesize = bestFilesize, filesizeApprox = bestFilesizeApprox)
        } else {
            chosen
        }
    }

    return bestPerResolution.sortedWith(
        compareByDescending<FormatInfo> { it.height ?: 0 }
            .thenByDescending { it.fps ?: 0.0 }
            .thenByDescending { it.tbr ?: it.vbr ?: 0.0 }
    )
}
