package com.zasenjc.mediatree.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.zasenjc.mediatree.data.ProviderType

data class TopDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

val topDestinations = listOf(
    TopDestination("home", "首页", Icons.Filled.Home, Icons.Outlined.Home),
    TopDestination("browse", "浏览", Icons.Filled.Folder, Icons.Outlined.Folder),
    TopDestination("favorites", "收藏", Icons.Filled.Bookmarks, Icons.Outlined.Bookmarks),
    TopDestination("settings", "设置", Icons.Filled.Settings, Icons.Outlined.Settings),
)

fun topDestinationsFor(providerType: ProviderType): List<TopDestination> =
    if (providerType == ProviderType.M3U) {
        topDestinations.filterNot { it.route == "browse" }
    } else {
        topDestinations
    }
