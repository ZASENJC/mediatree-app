package com.zasenjc.mediatree.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
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
import com.zasenjc.mediatree.ui.components.MediaTreePageBackground
import com.zasenjc.mediatree.ui.components.bottomChromeEnterTransition
import com.zasenjc.mediatree.ui.components.bottomChromeExitTransition
import com.zasenjc.mediatree.ui.motion.md3DefaultEnterTransition
import com.zasenjc.mediatree.ui.motion.md3DefaultExitTransition
import com.zasenjc.mediatree.ui.motion.md3DefaultPopEnterTransition
import com.zasenjc.mediatree.ui.motion.md3DefaultPopExitTransition
import com.zasenjc.mediatree.ui.navigation.TopDestination
import com.zasenjc.mediatree.ui.navigation.topDestinations
import com.zasenjc.mediatree.ui.screens.BrowseScreen
import com.zasenjc.mediatree.ui.screens.DetailScreen
import com.zasenjc.mediatree.ui.screens.FavoritesScreen
import com.zasenjc.mediatree.ui.screens.HomeScreen
import com.zasenjc.mediatree.ui.screens.SettingsScreen
import com.zasenjc.mediatree.ui.screens.SmbBrowseScreen
import com.zasenjc.mediatree.ui.screens.SmbPlayerScreen
import com.zasenjc.mediatree.ui.screens.WebDavBrowseScreen
import com.zasenjc.mediatree.ui.screens.WebDavPlayerScreen
import kotlinx.coroutines.launch
import kotlin.math.abs

private val Md3StandardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

@Composable
fun MediaTreeApp(container: AppContainer, deepLinkData: Uri? = null) {
    var session by remember { mutableStateOf<Session?>(null) }
    var startupError by remember { mutableStateOf<Throwable?>(null) }

    LaunchedEffect(container) {
        runCatching {
            container.sessionStore.sessionFlow.collect { session = it }
        }.onFailure { startupError = it }
    }

    if (startupError != null) {
        CredentialStorageErrorPane(startupError!!)
        return
    }

    val currentSession = session
    if (currentSession == null) {
        LoadingPane()
        return
    }
    MainShell(container = container, session = currentSession, deepLinkData = deepLinkData)
}

@Composable
private fun CredentialStorageErrorPane(error: Throwable) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = error.message ?: "加密凭据存储不可用，无法安全保存登录凭据",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainShell(container: AppContainer, session: Session, deepLinkData: Uri? = null) {
    val navController = rememberNavController()
    val initialMovieId = remember(deepLinkData) { detailMovieIdFromUri(deepLinkData) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route.orEmpty()
    val pagerState = rememberPagerState(
        initialPage = initialTopDestinationPage(session),
        pageCount = { topDestinations.size },
    )
    var browseFolder by remember { mutableStateOf("") }
    var browseViewMode by rememberSaveable { mutableStateOf("compact") }
    var browseRecursiveVideos by remember { mutableStateOf(false) }
    var chromeVisible by remember { mutableStateOf(true) }

    LaunchedEffect(currentRoute) {
        chromeVisible = true
    }

    LaunchedEffect(session.activeProviderType, session.activeLibrary) {
        browseFolder = ""
        browseRecursiveVideos = false
    }

    fun navigateTopDestination(route: String) {
        val page = topDestinations.indexOfFirst { it.route == route }
        if (page >= 0) {
            scope.launch {
                pagerState.animateScrollToPage(
                    page = page,
                    animationSpec = tween(durationMillis = 360, easing = Md3StandardEasing),
                )
            }
        }
    }

    fun navigateToReconnectSettings() {
        navController.popBackStack("main", inclusive = false)
        navigateTopDestination("settings")
    }

    val onError: (Throwable) -> Unit = { err ->
        scope.launch {
            val result = handleConnectionError(session, err)
            if (result.clearToken) {
                container.sessionStore.clearToken()
            }
            if (result.navigateRoute == "settings") {
                navigateToReconnectSettings()
            }
            snackbarHostState.showSnackbar(result.message)
        }
    }

    fun browseParentFolder(): String = browseFolder
        .trimEnd('/')
        .substringBeforeLast("/", missingDelimiterValue = "")

    fun handleAppNavigate(route: String) {
        when {
            route.startsWith("detail/") -> navController.navigate(route) { launchSingleTop = true }
            route == "browse" -> {
                browseFolder = ""
                browseRecursiveVideos = false
                navigateTopDestination("browse")
            }
            route.startsWith("browse?folder=") -> {
                val query = route.substringAfter("browse?", missingDelimiterValue = "")
                val params = Uri.parse("mediatree://local/browse?$query")
                browseFolder = params.getQueryParameter("folder").orEmpty()
                browseRecursiveVideos = params.getQueryParameter("recursiveVideos") == "true"
                navigateTopDestination("browse")
            }
            topDestinations.any { it.route == route } -> navigateTopDestination(route)
            else -> navController.navigate(route) { launchSingleTop = true }
        }
    }

    BackHandler(enabled = pagerState.currentPage == topDestinations.indexOfFirst { it.route == "browse" } && browseFolder.isNotBlank()) {
        browseFolder = browseParentFolder()
        browseRecursiveVideos = false
        navigateTopDestination("browse")
    }

    LaunchedEffect(initialMovieId, session.serverUrl) {
        if (initialMovieId != null) {
            if (shouldLoadRemoteContent(session)) {
                navController.navigate("detail/$initialMovieId") {
                    launchSingleTop = true
                }
            } else {
                navigateToReconnectSettings()
            }
        }
    }

    MediaTreePageBackground {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = "main",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding),
                enterTransition = { md3DefaultEnterTransition() },
                exitTransition = { md3DefaultExitTransition() },
                popEnterTransition = { md3DefaultPopEnterTransition() },
                popExitTransition = { md3DefaultPopExitTransition() },
            ) {
                composable("main") {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 0,
                        key = { page -> topDestinations[page].route },
                    ) { page ->
                        val route = topDestinations[page].route
                        val pageActive = currentRoute == "main" && page == pagerState.currentPage
                        when (route) {
                            "home" -> HomeScreen(
                                container = container,
                                session = session,
                                onNavigate = ::handleAppNavigate,
                                onError = onError,
                                active = pageActive,
                                browseViewMode = browseViewMode,
                                onBrowseViewModeChange = { browseViewMode = it },
                                chromeVisible = chromeVisible,
                                onChromeVisibleChange = { chromeVisible = it },
                            )
                            "browse" -> BrowseScreen(
                                container = container,
                                session = session,
                                onNavigate = ::handleAppNavigate,
                                onError = onError,
                                active = pageActive,
                                initialFolder = browseFolder,
                                recursiveVideosOnly = browseRecursiveVideos,
                                viewMode = browseViewMode,
                                onViewModeChange = { browseViewMode = it },
                                chromeVisible = chromeVisible,
                                onChromeVisibleChange = { chromeVisible = it },
                            )
                            "favorites" -> FavoritesScreen(
                                container = container,
                                session = session,
                                onNavigate = ::handleAppNavigate,
                                onError = onError,
                                active = pageActive,
                                chromeVisible = chromeVisible,
                                onChromeVisibleChange = { chromeVisible = it },
                            )
                            "settings" -> SettingsScreen(
                                container = container,
                                session = session,
                                onError = onError,
                                active = pageActive,
                            )
                        }
                    }
                }
                composable(
                    route = "detail/{movieId}?providerItemId={providerItemId}",
                    arguments = listOf(
                        navArgument("movieId") { type = NavType.IntType },
                        navArgument("providerItemId") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
                    enterTransition = { md3DefaultEnterTransition() },
                    exitTransition = { md3DefaultExitTransition() },
                    popEnterTransition = { md3DefaultPopEnterTransition() },
                    popExitTransition = { md3DefaultPopExitTransition() },
                ) { entry ->
                    DetailScreen(
                        container = container,
                        session = session,
                        movieId = entry.arguments?.getInt("movieId") ?: 0,
                        providerItemId = entry.arguments?.getString("providerItemId").orEmpty(),
                        onBack = { navController.popBackStack() },
                        onNavigate = ::handleAppNavigate,
                        onError = onError,
                        onChromeVisibleChange = { chromeVisible = it },
                    )
                }
                composable(
                    route = "webdav/{sourceId}?path={path}",
                    arguments = listOf(
                        navArgument("sourceId") { type = NavType.StringType },
                        navArgument("path") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
                    enterTransition = { md3DefaultEnterTransition() },
                    exitTransition = { md3DefaultExitTransition() },
                    popEnterTransition = { md3DefaultPopEnterTransition() },
                    popExitTransition = { md3DefaultPopExitTransition() },
                ) { entry ->
                    WebDavBrowseScreen(
                        container = container,
                        sourceId = entry.arguments?.getString("sourceId").orEmpty(),
                        path = entry.arguments?.getString("path").orEmpty(),
                        onBack = { navController.popBackStack() },
                        onNavigate = ::handleAppNavigate,
                        onError = onError,
                    )
                }
                composable(
                    route = "webdavPlayer/{sourceId}?path={path}",
                    arguments = listOf(
                        navArgument("sourceId") { type = NavType.StringType },
                        navArgument("path") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
                    enterTransition = { md3DefaultEnterTransition() },
                    exitTransition = { md3DefaultExitTransition() },
                    popEnterTransition = { md3DefaultPopEnterTransition() },
                    popExitTransition = { md3DefaultPopExitTransition() },
                ) { entry ->
                    WebDavPlayerScreen(
                        container = container,
                        sourceId = entry.arguments?.getString("sourceId").orEmpty(),
                        path = entry.arguments?.getString("path").orEmpty(),
                        onBack = { navController.popBackStack() },
                        onError = onError,
                    )
                }
                composable(
                    route = "smb/{sourceId}?path={path}",
                    arguments = listOf(
                        navArgument("sourceId") { type = NavType.StringType },
                        navArgument("path") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
                    enterTransition = { md3DefaultEnterTransition() },
                    exitTransition = { md3DefaultExitTransition() },
                    popEnterTransition = { md3DefaultPopEnterTransition() },
                    popExitTransition = { md3DefaultPopExitTransition() },
                ) { entry ->
                    SmbBrowseScreen(
                        container = container,
                        sourceId = entry.arguments?.getString("sourceId").orEmpty(),
                        path = entry.arguments?.getString("path").orEmpty(),
                        onBack = { navController.popBackStack() },
                        onNavigate = ::handleAppNavigate,
                        onError = onError,
                    )
                }
                composable(
                    route = "smbPlayer/{sourceId}?path={path}",
                    arguments = listOf(
                        navArgument("sourceId") { type = NavType.StringType },
                        navArgument("path") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
                    enterTransition = { md3DefaultEnterTransition() },
                    exitTransition = { md3DefaultExitTransition() },
                    popEnterTransition = { md3DefaultPopEnterTransition() },
                    popExitTransition = { md3DefaultPopExitTransition() },
                ) { entry ->
                    SmbPlayerScreen(
                        container = container,
                        sourceId = entry.arguments?.getString("sourceId").orEmpty(),
                        path = entry.arguments?.getString("path").orEmpty(),
                        onBack = { navController.popBackStack() },
                        onError = onError,
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = chromeVisible && !currentRoute.startsWith("detail") && !currentRoute.endsWith("Player/{sourceId}?path={path}"),
            enter = bottomChromeEnterTransition(),
            exit = bottomChromeExitTransition(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            DesignBottomNavigationBar(
                currentPage = pagerState.currentPage,
                pageOffsetFraction = pagerState.currentPageOffsetFraction,
                onNavigate = ::navigateTopDestination,
            )
        }
    }
}

@Composable
private fun DesignBottomNavigationBar(
    currentPage: Int,
    pageOffsetFraction: Float,
    onNavigate: (String) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 14.dp),
        shape = RoundedCornerShape(38.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            topDestinations.forEachIndexed { index, item ->
                val pageDistance = (currentPage - index) + pageOffsetFraction
                val selectedAmount = (1f - abs(pageDistance)).coerceIn(0f, 1f)
                BottomNavItem(
                    item = item,
                    selectedAmount = selectedAmount,
                    onClick = { onNavigate(item.route) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    item: TopDestination,
    selectedAmount: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = tween(durationMillis = if (pressed) 70 else 120, easing = Md3StandardEasing),
        label = "bottomNavScale",
    )
    val indicatorWidthFraction = 0.58f + 0.42f * selectedAmount

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .height(44.dp)
                .fillMaxWidth(indicatorWidthFraction)
                .clip(RoundedCornerShape(28.dp))
                .graphicsLayer {
                    alpha = selectedAmount
                }
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f + 0.74f * selectedAmount)),
        )
        BottomNavItemContent(
            selectedAmount = selectedAmount,
            label = item.label,
            selectedIcon = item.selectedIcon,
            unselectedIcon = item.unselectedIcon,
        )
    }
}

@Composable
private fun BottomNavItemContent(
    selectedAmount: Float,
    label: String,
    selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    val selectedColor = MaterialTheme.colorScheme.onPrimary
    val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val contentColor = lerp(unselectedColor, selectedColor, selectedAmount)
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Icon(
                unselectedIcon,
                contentDescription = label,
                tint = unselectedColor.copy(alpha = 1f - selectedAmount),
                modifier = Modifier.fillMaxSize(),
            )
            Icon(
                selectedIcon,
                contentDescription = label,
                tint = selectedColor.copy(alpha = selectedAmount),
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            label,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
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

data class ConnectionErrorResult(
    val session: Session,
    val clearToken: Boolean,
    val navigateRoute: String?,
    val message: String,
)

fun initialTopDestinationPage(session: Session): Int {
    if (session.serverUrl.isNotBlank()) return 0
    return topDestinations.indexOfFirst { it.route == "settings" }.coerceAtLeast(0)
}

fun shouldLoadRemoteContent(session: Session): Boolean = session.serverUrl.isNotBlank()

fun handleConnectionError(session: Session, throwable: Throwable): ConnectionErrorResult {
    if (throwable is ApiException && throwable.statusCode == 401) {
        return ConnectionErrorResult(
            session = session.copy(token = ""),
            clearToken = true,
            navigateRoute = "settings",
            message = "登录已过期，请重新登录",
        )
    }
    return ConnectionErrorResult(
        session = session,
        clearToken = false,
        navigateRoute = null,
        message = throwable.message ?: "请求失败",
    )
}
