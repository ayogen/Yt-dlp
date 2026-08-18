package com.example.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DownloadStatus
import com.example.ui.downloads.DownloadsScreen
import com.example.ui.history.HistoryScreen
import com.example.ui.home.HomeScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.ElegantDarkBackground
import com.example.ui.theme.ElegantDarkBorder
import com.example.ui.theme.ElegantDarkSurface
import com.example.ui.theme.ElegantDarkSurfaceVariant
import com.example.ui.theme.ElegantGreen
import com.example.ui.theme.ElegantLavenderOnPrimary
import com.example.ui.theme.ElegantLavenderPrimary
import com.example.ui.theme.ElegantTextPrimary
import com.example.ui.theme.ElegantTextSecondary
import com.example.ui.theme.ElegantTextTertiary

data class NavItem(
    val tab: NavigationTab,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun MainApp(viewModel: MainViewModel) {
    val currentTab by viewModel.currentTab.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val activeDownloadsCount = tasks.count { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearToast()
        }
    }

    val navItems = listOf(
        NavItem(NavigationTab.HOME, Icons.Filled.Home, Icons.Outlined.Home),
        NavItem(NavigationTab.DOWNLOADS, Icons.Filled.Download, Icons.Outlined.Download),
        NavItem(NavigationTab.HISTORY, Icons.Filled.History, Icons.Outlined.History),
        NavItem(NavigationTab.SETTINGS, Icons.Filled.Settings, Icons.Outlined.Settings)
    )

    Scaffold(
        topBar = {
            // Elegant Dark Header
            Surface(
                color = ElegantDarkBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ElegantLavenderPrimary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = ElegantLavenderOnPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Transcode",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElegantTextPrimary,
                            letterSpacing = (-0.5).sp
                        )
                    }

                    // yt-dlp ready pill badge with glowing indicator
                    Surface(
                        color = ElegantDarkSurface,
                        shape = RoundedCornerShape(20.dp),
                        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(ElegantDarkBorder, ElegantDarkBorder)))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(ElegantGreen)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "YT-DLP READY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElegantTextSecondary,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            // Navigation Bar with Elegant Dark styling
            NavigationBar(
                containerColor = ElegantDarkSurface,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(72.dp)
                    .testTag("bottom_nav_bar")
            ) {
                navItems.forEach { item ->
                    val isSelected = currentTab == item.tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { viewModel.selectTab(item.tab) },
                        icon = {
                            BadgedBox(badge = {
                                if (item.tab == NavigationTab.DOWNLOADS && activeDownloadsCount > 0) {
                                    Badge(containerColor = ElegantLavenderPrimary, contentColor = ElegantLavenderOnPrimary) {
                                        Text("$activeDownloadsCount", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }) {
                                Icon(
                                    imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.tab.title,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        },
                        label = {
                            Text(
                                text = item.tab.title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ElegantLavenderPrimary,
                            selectedTextColor = ElegantLavenderPrimary,
                            unselectedIconColor = ElegantTextTertiary,
                            unselectedTextColor = ElegantTextTertiary,
                            indicatorColor = ElegantDarkSurfaceVariant
                        ),
                        modifier = Modifier.testTag("nav_tab_${item.tab.name.lowercase()}")
                    )
                }
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        containerColor = ElegantDarkBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Crossfade(targetState = currentTab, label = "tab_crossfade") { tab ->
                when (tab) {
                    NavigationTab.HOME -> HomeScreen(viewModel = viewModel)
                    NavigationTab.DOWNLOADS -> DownloadsScreen(viewModel = viewModel)
                    NavigationTab.HISTORY -> HistoryScreen(viewModel = viewModel)
                    NavigationTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
                }
            }
        }
    }
}
