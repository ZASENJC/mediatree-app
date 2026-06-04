package com.zasenjc.mediatree

import android.graphics.Color
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.zasenjc.mediatree.data.AppContainer
import com.zasenjc.mediatree.data.ThemeModePreference
import com.zasenjc.mediatree.ui.MediaTreeApp
import com.zasenjc.mediatree.ui.theme.MediaTreeTheme

class MainActivity : ComponentActivity() {
    private var deepLinkData by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        deepLinkData = intent?.data
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            val container = remember { AppContainer(this) }
            val themeMode by container.uiPreferencesStore.themeModeFlow.collectAsStateWithLifecycle(
                initialValue = ThemeModePreference.Light,
            )
            val themeColor by container.uiPreferencesStore.themeColorFlow.collectAsStateWithLifecycle(
                initialValue = com.zasenjc.mediatree.data.DEFAULT_THEME_COLOR,
            )
            val darkTheme = resolveDarkTheme(themeMode)
            LaunchedEffect(darkTheme) {
                applySystemBars(darkTheme)
            }
            LaunchedEffect(container) {
                container.releaseUpdateChecker.checkForUpdates(BuildConfig.VERSION_NAME)
            }
            MediaTreeTheme(darkTheme = darkTheme, themeColorHex = themeColor) {
                MediaTreeApp(container = container, deepLinkData = deepLinkData)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkData = intent.data
    }

    private fun applySystemBars(darkTheme: Boolean) {
        val style = if (darkTheme) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(
            statusBarStyle = style,
            navigationBarStyle = style,
        )
    }
}

@Composable
private fun resolveDarkTheme(preference: ThemeModePreference): Boolean = when (preference) {
    ThemeModePreference.System -> isSystemInDarkTheme()
    ThemeModePreference.Light -> false
    ThemeModePreference.Dark -> true
}
