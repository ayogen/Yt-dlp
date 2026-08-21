package com.example.ui.history

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.example.data.model.DownloadHistoryEntity
import com.example.data.model.HistorySortOrder
import com.example.data.model.MediaType
import com.example.data.model.formatBytes
import com.example.ui.MainViewModel
import com.example.ui.NavigationTab
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.MediaTypeBadge
import com.example.ui.theme.ElegantAmber
import com.example.ui.theme.ElegantDarkBackground
import com.example.ui.theme.ElegantDarkBorder
import com.example.ui.theme.ElegantDarkBorderSubtle
import com.example.ui.theme.ElegantDarkCard
import com.example.ui.theme.ElegantDarkSurfaceVariant
import com.example.ui.theme.ElegantLavenderOnPrimary
import com.example.ui.theme.ElegantLavenderPrimary
import com.example.ui.theme.ElegantRed
import com.example.ui.theme.ElegantTextPrimary
import com.example.ui.theme.ElegantTextSecondary
import com.example.ui.theme.ElegantTextTertiary
import com.example.download.StorageUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val historyList by viewModel.filteredHistory.collectAsState()
    val searchQuery by viewModel.historySearchQuery.collectAsState()
    val filterType by viewModel.historyFilterType.collectAsState()
    val sortOrder by viewModel.historySortOrder.collectAsState()

    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<DownloadHistoryEntity?>(null) }
    var deleteFileFromDisk by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Search & Controls Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setHistorySearchQuery(it) },
                placeholder = { Text("Search history...", color = ElegantTextTertiary, fontSize = 13.sp) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = ElegantLavenderPrimary)
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.setHistorySearchQuery("") }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", tint = ElegantTextSecondary)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElegantLavenderPrimary,
                    unfocusedBorderColor = ElegantDarkBorder,
                    focusedContainerColor = ElegantDarkCard,
                    unfocusedContainerColor = ElegantDarkCard,
                    focusedTextColor = ElegantTextPrimary,
                    unfocusedTextColor = ElegantTextPrimary
                ),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("history_search_input")
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Sort Menu
            Box {
                IconButton(
                    onClick = { showSortMenu = true },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(ElegantDarkCard)
                ) {
                    Icon(imageVector = Icons.Default.Sort, contentDescription = "Sort", tint = ElegantLavenderPrimary)
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                    modifier = Modifier.background(ElegantDarkCard)
                ) {
                    HistorySortOrder.values().forEach { order ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = order.displayName,
                                    color = if (sortOrder == order) ElegantLavenderPrimary else ElegantTextPrimary,
                                    fontWeight = if (sortOrder == order) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                viewModel.setHistorySortOrder(order)
                                showSortMenu = false
                            }
                        )
                    }
                }
            }

            if (historyList.isNotEmpty()) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = { showClearConfirmDialog = true },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(ElegantDarkCard)
                ) {
                    Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "Clear All", tint = ElegantRed)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Filter Chips Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                FilterChip(
                    selected = filterType == null,
                    onClick = { viewModel.setHistoryFilterType(null) },
                    label = { Text("All (${historyList.size})", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElegantLavenderPrimary,
                        selectedLabelColor = ElegantLavenderOnPrimary,
                        containerColor = ElegantDarkCard,
                        labelColor = ElegantTextPrimary
                    )
                )
            }
            item {
                FilterChip(
                    selected = filterType == MediaType.VIDEO,
                    onClick = { viewModel.setHistoryFilterType(if (filterType == MediaType.VIDEO) null else MediaType.VIDEO) },
                    label = { Text("Videos", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElegantLavenderPrimary,
                        selectedLabelColor = ElegantLavenderOnPrimary,
                        containerColor = ElegantDarkCard,
                        labelColor = ElegantTextPrimary
                    )
                )
            }
            item {
                FilterChip(
                    selected = filterType == MediaType.AUDIO,
                    onClick = { viewModel.setHistoryFilterType(if (filterType == MediaType.AUDIO) null else MediaType.AUDIO) },
                    label = { Text("Audio Only", fontSize = 12.sp) },
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

        // Content
        if (historyList.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                EmptyStateCard(
                    icon = Icons.Default.History,
                    title = if (searchQuery.isNotBlank()) "No Matching Downloads" else "No Download History",
                    description = if (searchQuery.isNotBlank()) "Try searching for a different keyword or clearing filters." else "Downloaded videos and audio will appear here for easy playback and file management.",
                    actionLabel = if (searchQuery.isNotBlank()) "Clear Search" else "Explore",
                    onAction = {
                        if (searchQuery.isNotBlank()) {
                            viewModel.setHistorySearchQuery("")
                        } else {
                            viewModel.selectTab(NavigationTab.HOME)
                        }
                    }
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(historyList, key = { it.id }) { item ->
                    HistoryItemCard(
                        item = item,
                        formattedDate = dateFormat.format(Date(item.completedTimestamp)),
                        onRedownload = {
                            viewModel.redownloadHistoryItem(item)
                        },
                        onOpen = {
                            if (!item.filePath.isNullOrBlank()) {
                                val openResult = StorageUtils.openMediaFile(context, item.filePath, item.mediaType)
                                if (openResult.isFailure) {
                                    viewModel.showToast("Cannot open file: ${openResult.exceptionOrNull()?.message}")
                                }
                            }
                        },
                        onShare = {
                            StorageUtils.shareMediaFile(
                                context = context,
                                pathOrUri = item.filePath ?: "",
                                mediaType = item.mediaType,
                                title = "${item.title}\n${item.url}"
                            )
                        },
                        onCopyPath = {
                            viewModel.copyToClipboard(item.filePath ?: item.url, "Path / URL")
                        },
                        onDelete = {
                            itemToDelete = item
                            deleteFileFromDisk = false
                        }
                    )
                }
            }
        }
    }

    // Delete Single Item Confirmation Dialog
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(text = "Delete Download Record", color = ElegantTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Are you sure you want to remove \"${item.title.take(30)}...\" from your download history?",
                        color = ElegantTextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { deleteFileFromDisk = !deleteFileFromDisk }
                    ) {
                        Checkbox(
                            checked = deleteFileFromDisk,
                            onCheckedChange = { deleteFileFromDisk = it },
                            colors = CheckboxDefaults.colors(checkedColor = ElegantRed)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Also delete file from device storage", color = ElegantTextPrimary, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteHistory(item.id, item.filePath, deleteFileFromDisk)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElegantRed, contentColor = Color.White)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel", color = ElegantTextSecondary)
                }
            },
            containerColor = ElegantDarkCard
        )
    }

    // Clear All Confirmation Dialog
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text(text = "Clear Entire History", color = ElegantTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "This will remove all entries from your download history list. (Existing files in your download folder will remain intact).",
                    color = ElegantTextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllHistory()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElegantRed, contentColor = Color.White)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel", color = ElegantTextSecondary)
                }
            },
            containerColor = ElegantDarkCard
        )
    }
}

@Composable
fun HistoryItemCard(
    item: DownloadHistoryEntity,
    formattedDate: String,
    onRedownload: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onCopyPath: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = ElegantDarkCard),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.verticalGradient(listOf(ElegantDarkBorder, Color.Transparent))),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(90.dp, 68.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ElegantDarkSurfaceVariant)
                    .clickable { onOpen() }
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
                            .size(30.dp)
                            .align(Alignment.Center)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = ElegantTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = item.uploader.ifBlank { "Media Creator" },
                    fontSize = 11.sp,
                    color = ElegantLavenderPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MediaTypeBadge(mediaType = item.mediaType)
                    Text(
                        text = "${formatBytes(item.fileSize)} • ${item.formatDescription}",
                        fontSize = 11.sp,
                        color = ElegantTextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = formattedDate,
                    fontSize = 10.sp,
                    color = ElegantTextTertiary
                )
            }

            // Actions
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More", tint = ElegantTextSecondary)
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(ElegantDarkCard)
                ) {
                    DropdownMenuItem(
                        text = { Text("Redownload", color = ElegantLavenderPrimary) },
                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = ElegantLavenderPrimary) },
                        onClick = {
                            showMenu = false
                            onRedownload()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Open File", color = ElegantTextPrimary) },
                        leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null, tint = ElegantLavenderPrimary) },
                        onClick = {
                            showMenu = false
                            onOpen()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share Link", color = ElegantTextPrimary) },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = ElegantLavenderPrimary) },
                        onClick = {
                            showMenu = false
                            onShare()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Copy File Path", color = ElegantTextPrimary) },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = ElegantTextSecondary) },
                        onClick = {
                            showMenu = false
                            onCopyPath()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Record", color = ElegantRed) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ElegantRed) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
