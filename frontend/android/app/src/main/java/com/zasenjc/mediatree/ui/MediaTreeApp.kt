package com.zasenjc.mediatree.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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

    LaunchedEffect(initialMovieId) {
        if (initialMovieId != null) {
            navController.navigate("detail/$initialMovieId") {
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            AnimatedVisibility(
                visible = !currentRoute.startsWith("detail"),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.82f),
                    tonalElevation = 3.dp,
                    shadowElevation = 2.dp,
                ) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.0f),
                        tonalElevation = 0.dp,
                    ) {
                        topDestinations.forEach { item ->
                            val selected = currentRoute == item.route || (currentRoute.isBlank() && item.route == "home")
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo("home")
                                        launchSingleTop = true
                                    }
                                },
                                icon = { Icon(if (selected) item.selectedIcon else item.unselectedIcon, contentDescription = item.label) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
        ) {
            composable("home") {
                HomeScreen(container, session, onNavigate = { navController.navigate(it) { launchSingleTop = true } }, onError)
            }
            composable("browse") {
                BrowseScreen(container, session, onNavigate = { navController.navigate(it) { launchSingleTop = true } }, onError, "")
            }
            composable("browse?folder={folder}") { entry ->
                val folder = entry.arguments?.getString("folder").orEmpty()
                BrowseScreen(container, session, onNavigate = { navController.navigate(it) { launchSingleTop = true } }, onError, folder)
            }
            composable("favorites") {
                FavoritesScreen(container, session, onNavigate = { navController.navigate(it) { launchSingleTop = true } }, onError)
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
