package com.zasenjc.mediatree.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zasenjc.mediatree.data.ApiException
import com.zasenjc.mediatree.data.AppContainer
import com.zasenjc.mediatree.data.Session
import com.zasenjc.mediatree.ui.components.LoadingPane
import com.zasenjc.mediatree.ui.components.bottomChromeEnterTransition
import com.zasenjc.mediatree.ui.components.bottomChromeExitTransition
import com.zasenjc.mediatree.ui.navigation.topDestinations
import com.zasenjc.mediatree.ui.screens.BrowseScreen
import com.zasenjc.mediatree.ui.screens.DetailScreen
import com.zasenjc.mediatree.ui.screens.FavoritesScreen
import com.zasenjc.mediatree.ui.screens.HomeScreen
import com.zasenjc.mediatree.ui.screens.LoginScreen
import com.zasenjc.mediatree.ui.screens.SettingsScreen
import kotlinx.coroutines.launch

@Composable
fun MediaTreeApp(deepLinkData: Uri? = null) {
    val context = LocalContext.current
    val container = remember { AppContainer(context) }
    val session by container.sessionStore.sessionFlow.collectAsStateWithLifecycle(initialValue = Session())
    var checkingAuth by remember(session.serverUrl, session.token) { mutableStateOf(session.serverUrl.isNotBlank() && session.token.isBlank()) }
    var needsLogin by remember(session.serverUrl, session.token) { mutableStateOf(session.serverUrl.isBlank()) }

    LaunchedEffect(session.serverUrl, session.token) {
        if (session.serverUrl.isBlank()) {
            checkingAuth = false
            needsLogin = true
        } else if (session.token.isBlank()) {
            checkingAuth = true
            needsLogin = runCatching { container.api.authStatus(session.serverUrl).needAuth }.getOrDefault(true)
            checkingAuth = false
        } else {
            checkingAuth = false
            needsLogin = false
        }
    }

    when {
        checkingAuth -> LoadingPane()
        needsLogin -> LoginScreen(container = container, initialServerUrl = session.serverUrl)
        else -> MainShell(container = container, session = session, deepLinkData = deepLinkData)
    }
}

@Composable
private fun MainShell(container: AppContainer, session: Session, deepLinkData: Uri? = null) {
    val navController = rememberNavController()
    val initialMovieId = remember(deepLinkData) { detailMovieIdFromUri(deepLinkData) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route.orEmpty()
    var chromeVisible by remember { mutableStateOf(true) }
    val onError: (Throwable) -> Unit = { err ->
        scope.launch {
            val message = if (err is ApiException && err.statusCode == 401) {
                container.sessionStore.clearToken()
                "登录已过期，请重新登录"
            } else {
                err.message ?: "请求失败"
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(currentRoute) {
        chromeVisible = true
    }

    LaunchedEffect(initialMovieId) {
        if (initialMovieId != null) {
            navController.navigate("detail/$initialMovieId") {
                launchSingleTop = true
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            ) {
                composable("home") {
                    HomeScreen(
                        container = container,
                        session = session,
                        onNavigate = { navController.navigate(it) { launchSingleTop = true } },
                        onError = onError,
                        chromeVisible = chromeVisible,
                        onChromeVisibleChange = { chromeVisible = it },
                    )
                }
                composable("browse") {
                    BrowseScreen(
                        container = container,
                        session = session,
                        onNavigate = { navController.navigate(it) { launchSingleTop = true } },
                        onError = onError,
                        initialFolder = "",
                        chromeVisible = chromeVisible,
                        onChromeVisibleChange = { chromeVisible = it },
                    )
                }
                composable("browse?folder={folder}") { entry ->
                    val folder = entry.arguments?.getString("folder").orEmpty()
                    BrowseScreen(
                        container = container,
                        session = session,
                        onNavigate = { navController.navigate(it) { launchSingleTop = true } },
                        onError = onError,
                        initialFolder = folder,
                        chromeVisible = chromeVisible,
                        onChromeVisibleChange = { chromeVisible = it },
                    )
                }
                composable("favorites") {
                    FavoritesScreen(
                        container = container,
                        session = session,
                        onNavigate = { navController.navigate(it) { launchSingleTop = true } },
                        onError = onError,
                        chromeVisible = chromeVisible,
                        onChromeVisibleChange = { chromeVisible = it },
                    )
                }
                composable("settings") {
                    SettingsScreen(container, session, onError)
                }
                composable(
                    route = "detail/{movieId}",
                    arguments = listOf(navArgument("movieId") { type = NavType.IntType }),
                ) { entry ->
                    DetailScreen(
                        container = container,
                        session = session,
                        movieId = entry.arguments?.getInt("movieId") ?: 0,
                        onBack = { navController.popBackStack() },
                        onNavigate = { navController.navigate(it) { launchSingleTop = true } },
                        onError = onError,
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = chromeVisible && !currentRoute.startsWith("detail"),
            enter = bottomChromeEnterTransition(),
            exit = bottomChromeExitTransition(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            FrostedBottomNavigationBar(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo("home")
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}

@Composable
private fun FrostedBottomNavigationBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 14.dp),
        shape = RoundedCornerShape(38.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.86f),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            topDestinations.forEach { item ->
                val selected = currentRoute == item.route || (currentRoute.isBlank() && item.route == "home")
                val contentColor = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                val baseModifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()

                if (selected) {
                    Surface(
                        modifier = baseModifier.clickable { onNavigate(item.route) },
                        shape = RoundedCornerShape(32.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                        tonalElevation = 2.dp,
                        shadowElevation = 4.dp,
                    ) {
                        BottomNavItemContent(
                            selected = true,
                            label = item.label,
                            icon = item.selectedIcon,
                            contentColor = contentColor,
                        )
                    }
                } else {
                    BottomNavItemContent(
                        modifier = baseModifier
                            .clip(RoundedCornerShape(32.dp))
                            .clickable { onNavigate(item.route) },
                        selected = false,
                        label = item.label,
                        icon = item.unselectedIcon,
                        contentColor = contentColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomNavItemContent(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = label, tint = contentColor)
        Text(
            label,
            color = contentColor,
            style = if (selected) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
        )
    }
}

private fun detailMovieIdFromUri(uri: Uri?): Int? {
    if (uri == null || uri.scheme != "mediatree") return null
    val segments = uri.pathSegments
    return when {
        uri.host == "detail" -> segments.firstOrNull()?.toIntOrNull()
        segments.firstOrNull() == "detail" -> segments.getOrNull(1)?.toIntOrNull()
        else -> null
    }
}
