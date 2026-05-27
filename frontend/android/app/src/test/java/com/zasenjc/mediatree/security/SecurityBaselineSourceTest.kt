package com.zasenjc.mediatree.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SecurityBaselineSourceTest {
    private val appRoot = File(System.getProperty("user.dir") ?: ".")

    @Test
    fun manifestAllowsLocalAndLanCleartextTraffic() {
        val manifest = appRoot.resolve("src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("""android:usesCleartextTraffic="true""""))
        assertTrue(manifest.contains("""android:networkSecurityConfig="@xml/network_security_config""""))
    }

    @Test
    fun networkSecurityConfigAllowsLocalAndLanHttpServers() {
        val config = appRoot.resolve("src/main/res/xml/network_security_config.xml").readText()

        assertTrue(config.contains("""<base-config cleartextTrafficPermitted="true" />"""))
        assertFalse(config.contains("""<base-config cleartextTrafficPermitted="false""""))
    }

    @Test
    fun playerLogsDoNotPrintUrlsTokensOrHeaders() {
        val playerSource = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/player/MediaTreePlayer.kt")
            .readText()

        assertFalse(playerSource.contains("Log.d(TAG, \"Creating ExoPlayer for $"))
        assertFalse(playerSource.contains("Log.d(TAG, \"Preparing media source: $"))
        assertFalse(playerSource.contains("Log.e(TAG, \"Player error: ${'$'}{error.message}\", error)"))
        assertFalse(playerSource.contains("Bearer ${'$'}token"))
    }
}
