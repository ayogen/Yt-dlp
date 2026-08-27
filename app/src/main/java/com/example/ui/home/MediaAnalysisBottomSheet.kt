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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FeaturedPlayList
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
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
import com.example.ui.theme.ElegantDarkBorder
import com.example.ui.theme.ElegantDarkCard
import com.example.ui.theme.ElegantDarkSurface
import com.example.ui.theme.ElegantDarkSurfaceVariant
import com.example.ui.theme.ElegantLavenderOnPrimary
import com.example.ui.theme.ElegantLavenderPrimary
import com.example.ui.theme.ElegantTextPrimary
import com.example.ui.theme.ElegantTextSecondary
import com.example.ui.theme.ElegantTextTertiary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MediaAnalysisBottomSheet(
    uiModel: AnalysisUiModel,
    onDismiss: () -> Unit,
    onDownload: (
        format: FormatInfo?,
        mediaType: MediaType,
        container: OutputContainer,
        audioBitrate: Int?,
        embedSubs: Boolean,
        embedThumb: Boolean,
        selectedIndices: Set<Int>
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val initialMediaType = remember(uiModel) {
        when {
            uiModel.isCarousel -> MediaType.CAROUSEL
            uiModel.isPlaylist -> MediaType.PLAYLIST
            uiModel.isImage -> MediaType.IMAGE
            uiModel.primaryMediaType == MediaType.AUDIO -> MediaType.AUDIO
            else -> MediaType.VIDEO
        }
    }

    var selectedMediaType by remember(uiModel) { mutableStateOf(initialMediaType) }
    var playlistTargetType by remember { mutableStateOf(MediaType.VIDEO) }

    val deduplicatedVideoFormats = remember(uiModel.formats) {
        deduplicateVideoFormats(uiModel.formats)
    }

    var selectedFormat by remember(uiModel) {
        mutableStateOf(
            deduplicatedVideoFormats.firstOrNull { it.height != null && it.height <= 1080 }
                ?: deduplicatedVideoFormats.firstOrNull()
                ?: uiModel.formats.firstOrNull()
        )
    }

    var selectedContainer by remember {
        mutableStateOf(if (uiModel.isImage) OutputContainer.JPG else OutputContainer.MP4)
    }
    var selectedAudioFormat by remember { mutableStateOf(AudioFormat.MP3) }
    var selectedAudioBitrate by remember { mutableStateOf(320) }

    var embedSubtitles by remember { mutableStateOf(false) }
    var embedThumbnail by remember { mutableStateOf(true) }

    val selectableIndices = remember(uiModel) {
        uiModel.items.filter { it.isSuccess }.map { it.index }.toSet()
    }

    val selectedItems = remember(uiModel) {
        mutableStateOf(selectableIndices.toMutableSet())
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
                    val headerTitle = when {
                        uiModel.isCarousel -> "Carousel Detected (${uiModel.items.size} items)"
                        uiModel.isPlaylist -> "Playlist Detected"
                        uiModel.isImage -> "Image Detected"
                        uiModel.primaryMediaType == MediaType.AUDIO -> "Audio Stream"
                        else -> "Extracted Media"
                    }
                    Text(
                        text = headerTitle,
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

            // Main Media Info Container
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
                        val displayThumb = uiModel.thumbnail.ifBlank {
                            uiModel.items.firstOrNull()?.thumbnail.orEmpty()
                        }
                        if (displayThumb.isNotBlank()) {
                            AsyncImage(
                                model = displayThumb,
                                contentDescription = uiModel.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize()
                            )
                        }

                        // Duration Badge
                        if (uiModel.durationSeconds > 0) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = uiModel.durationFormatted,
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
                            text = uiModel.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ElegantTextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${uiModel.uploader.ifBlank { "Media Creator" }} • ${if (uiModel.viewCount != null) "${uiModel.viewCount / 1000}K Views • " else ""}${uiModel.uploadDate.ifBlank { "Recent" }}",
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
                                    val resText = when {
                                        uiModel.isImage -> uiModel.items.firstOrNull()?.dimensionsFormatted?.ifBlank { "Original HD" } ?: "Original HD"
                                        uiModel.isCarousel -> "${uiModel.items.size} Media Items"
                                        else -> selectedFormat?.displayResolution ?: "Best HD"
                                    }
                                    Text(
                                        text = resText,
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
                                    val fmtText = when {
                                        selectedMediaType == MediaType.IMAGE -> selectedContainer.ext.uppercase()
                                        selectedMediaType == MediaType.CAROUSEL -> "Multi-Item Album"
                                        selectedMediaType == MediaType.AUDIO -> selectedAudioFormat.displayName
                                        selectedMediaType == MediaType.PLAYLIST -> {
                                            if (playlistTargetType == MediaType.AUDIO) "Audio (${selectedAudioFormat.displayName})"
                                            else "Video (${selectedContainer.ext.uppercase()})"
                                        }
                                        else -> selectedContainer.ext.uppercase()
                                    }
                                    Text(
                                        text = fmtText,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ElegantLavenderPrimary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Estimated Size & Engine Note
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
                                val sizeText = when {
                                    uiModel.isImage -> uiModel.displayFileSize
                                    uiModel.isCarousel -> "${uiModel.items.size} items"
                                    else -> selectedFormat?.displayFileSize ?: "Auto Stream"
                                }
                                Text(
                                    text = sizeText,
                                    color = ElegantLavenderPrimary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                val engineNote = when {
                                    uiModel.isImage -> "• Direct Fast Stream"
                                    uiModel.isCarousel -> "• Batch Downloader"
                                    else -> "• Universal Engine"
                                }
                                Text(
                                    text = engineNote,
                                    color = ElegantTextTertiary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tab Selector only for standard Video / Audio / Playlist (Not single Image, Carousel, or pure Audio)
            if (!uiModel.isImage && !uiModel.isCarousel && uiModel.primaryMediaType != MediaType.AUDIO) {
                SecondaryTabRow(
                    selectedTabIndex = when (selectedMediaType) {
                        MediaType.VIDEO -> 0
                        MediaType.AUDIO -> 1
                        MediaType.PLAYLIST -> 2
                        else -> 0
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
                    if (uiModel.isPlaylist) {
                        Tab(
                            selected = selectedMediaType == MediaType.PLAYLIST,
                            onClick = { selectedMediaType = MediaType.PLAYLIST },
                            text = { Text("Playlist", fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Default.FeaturedPlayList, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Format Choices
            when (selectedMediaType) {
                MediaType.IMAGE -> {
                    Text(
                        text = "Image Container Format",
                        style = MaterialTheme.typography.labelLarge,
                        color = ElegantTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(OutputContainer.JPG, OutputContainer.PNG, OutputContainer.WEBM).forEach { container ->
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

                MediaType.CAROUSEL -> {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Carousel Items (${selectedItems.value.size} of ${uiModel.items.size} selected)",
                            style = MaterialTheme.typography.labelLarge,
                            color = ElegantTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = {
                            if (selectedItems.value.size == selectableIndices.size) {
                                selectedItems.value = mutableSetOf()
                            } else {
                                selectedItems.value = selectableIndices.toMutableSet()
                            }
                        }) {
                            Text(
                                text = if (selectedItems.value.size == selectableIndices.size) "Deselect All" else "Select All",
                                color = ElegantLavenderPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        itemsIndexed(uiModel.items) { index, item ->
                            val isSelected = selectedItems.value.contains(item.index)
                            val isClickable = item.isSuccess
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = isClickable) {
                                        val current = selectedItems.value.toMutableSet()
                                        if (isSelected) current.remove(item.index) else current.add(item.index)
                                        selectedItems.value = current
                                    }
                                    .padding(vertical = 4.dp, horizontal = 6.dp)
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    enabled = isClickable,
                                    onCheckedChange = { checked ->
                                        val current = selectedItems.value.toMutableSet()
                                        if (checked) current.add(item.index) else current.remove(item.index)
                                        selectedItems.value = current
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = ElegantLavenderPrimary)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                if (item.thumbnail.isNotBlank()) {
                                    AsyncImage(
                                        model = item.thumbnail,
                                        contentDescription = item.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(ElegantDarkSurfaceVariant)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${index + 1}. ${item.title}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = if (item.isSuccess) ElegantTextPrimary else ElegantTextTertiary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val subText = if (item.isSuccess) {
                                        "${item.mediaKind.name} • ${item.dimensionsFormatted.ifBlank { item.displayFileSize }}"
                                    } else {
                                        "Unavailable (${item.errorMessage ?: "Extraction failed"})"
                                    }
                                    Text(
                                        text = subText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (item.isSuccess) ElegantTextSecondary else MaterialTheme.colorScheme.error,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }

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
                        listOf(AudioFormat.MP3, AudioFormat.M4A, AudioFormat.OPUS, AudioFormat.FLAC, AudioFormat.WAV).forEach { fmt ->
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
                    Text(
                        text = "Playlist Download Type",
                        style = MaterialTheme.typography.labelLarge,
                        color = ElegantTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = playlistTargetType == MediaType.VIDEO,
                            onClick = { playlistTargetType = MediaType.VIDEO },
                            label = { Text("Video (MP4)", fontWeight = FontWeight.SemiBold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElegantLavenderPrimary,
                                selectedLabelColor = ElegantLavenderOnPrimary,
                                containerColor = ElegantDarkCard,
                                labelColor = ElegantTextPrimary
                            )
                        )
                        FilterChip(
                            selected = playlistTargetType == MediaType.AUDIO,
                            onClick = { playlistTargetType = MediaType.AUDIO },
                            label = { Text("Audio Only (MP3)", fontWeight = FontWeight.SemiBold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElegantLavenderPrimary,
                                selectedLabelColor = ElegantLavenderOnPrimary,
                                containerColor = ElegantDarkCard,
                                labelColor = ElegantTextPrimary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Playlist Items (${selectedItems.value.size} of ${uiModel.items.size} selected)",
                            style = MaterialTheme.typography.labelLarge,
                            color = ElegantTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = {
                            if (selectedItems.value.size == selectableIndices.size) {
                                selectedItems.value = mutableSetOf()
                            } else {
                                selectedItems.value = selectableIndices.toMutableSet()
                            }
                        }) {
                            Text(
                                text = if (selectedItems.value.size == selectableIndices.size) "Deselect All" else "Select All",
                                color = ElegantLavenderPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        itemsIndexed(uiModel.items) { index, entry ->
                            val isSelected = selectedItems.value.contains(entry.index)
                            val isClickable = entry.isSuccess
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = isClickable) {
                                        val current = selectedItems.value.toMutableSet()
                                        if (isSelected) current.remove(entry.index) else current.add(entry.index)
                                        selectedItems.value = current
                                    }
                                    .padding(vertical = 4.dp, horizontal = 6.dp)
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    enabled = isClickable,
                                    onCheckedChange = { checked ->
                                        val current = selectedItems.value.toMutableSet()
                                        if (checked) current.add(entry.index) else current.remove(entry.index)
                                        selectedItems.value = current
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = ElegantLavenderPrimary)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                if (entry.thumbnail.isNotBlank()) {
                                    AsyncImage(
                                        model = entry.thumbnail,
                                        contentDescription = entry.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(ElegantDarkSurfaceVariant)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${index + 1}. ${entry.title}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = if (entry.isSuccess) ElegantTextPrimary else ElegantTextTertiary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (entry.isSuccess && entry.durationFormatted.isNotBlank()) {
                                        Text(
                                            text = entry.durationFormatted,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ElegantTextSecondary,
                                            fontSize = 10.sp
                                        )
                                    } else if (!entry.isSuccess) {
                                        Text(
                                            text = "Unavailable (${entry.errorMessage ?: "Extraction failed"})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!uiModel.isImage && !uiModel.isCarousel) {
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
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action Row: CC / HD Pills + DOWNLOAD NOW Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (selectedMediaType == MediaType.IMAGE) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(ElegantDarkSurfaceVariant)
                    ) {
                        Icon(imageVector = Icons.Default.Image, contentDescription = null, tint = ElegantTextPrimary, modifier = Modifier.size(20.dp))
                    }
                } else if (selectedMediaType == MediaType.CAROUSEL) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(ElegantDarkSurfaceVariant)
                    ) {
                        Icon(imageVector = Icons.Default.Layers, contentDescription = null, tint = ElegantTextPrimary, modifier = Modifier.size(20.dp))
                    }
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(ElegantDarkSurfaceVariant)
                    ) {
                        Text(text = "CC", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElegantTextPrimary)
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(ElegantDarkSurfaceVariant)
                    ) {
                        Text(text = "HD", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElegantTextPrimary)
                    }
                }

                val downloadButtonLabel = when {
                    uiModel.isCarousel -> "DOWNLOAD ${selectedItems.value.size} ITEMS"
                    uiModel.isPlaylist -> "DOWNLOAD ${selectedItems.value.size} ITEMS"
                    uiModel.isImage -> "DOWNLOAD IMAGE"
                    selectedMediaType == MediaType.AUDIO -> "DOWNLOAD AUDIO"
                    else -> "DOWNLOAD NOW"
                }

                // DOWNLOAD NOW Button
                Button(
                    onClick = {
                        val targetMediaType = when {
                            uiModel.isCarousel -> MediaType.CAROUSEL
                            uiModel.isPlaylist -> playlistTargetType
                            uiModel.isImage -> MediaType.IMAGE
                            else -> selectedMediaType
                        }

                        val container = when {
                            targetMediaType == MediaType.IMAGE -> selectedContainer
                            targetMediaType == MediaType.AUDIO -> OutputContainer.fromExt(selectedAudioFormat.ext)
                            else -> selectedContainer
                        }

                        val selection = when {
                            uiModel.isMultiItem -> selectedItems.value
                            else -> emptySet()
                        }

                        onDownload(
                            selectedFormat,
                            targetMediaType,
                            container,
                            if (targetMediaType == MediaType.AUDIO) selectedAudioBitrate else null,
                            embedSubtitles,
                            embedThumbnail,
                            selection
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
                        text = downloadButtonLabel,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

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
        selectedIndices: Set<Int>
    ) -> Unit
) {
    val uiModel = remember(metadata) {
        MediaUiMapper.mapMetadataToUiModel(metadata)
    }
    MediaAnalysisBottomSheet(
        uiModel = uiModel,
        onDismiss = onDismiss,
        onDownload = onDownload
    )
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
