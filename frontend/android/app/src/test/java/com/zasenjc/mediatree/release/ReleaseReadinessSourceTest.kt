package com.zasenjc.mediatree.release

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReleaseReadinessSourceTest {
    private val appRoot = File(System.getProperty("user.dir") ?: ".")

    @Test
    fun releaseBuildShrinksCodeResourcesAndKeepsOnlyArm64NativeLibs() {
        val buildGradle = appRoot.resolve("build.gradle").readText()

        assertTrue(buildGradle.contains("minifyEnabled true"))
        assertTrue(buildGradle.contains("shrinkResources true"))
        assertTrue(buildGradle.contains("""abiFilters "arm64-v8a""""))
        assertTrue(buildGradle.contains("signingConfigs"))
        assertTrue(buildGradle.contains("hasReleaseKeystore"))
        assertTrue(buildGradle.contains("ANDROID_KEYSTORE_PASSWORD"))
        assertTrue(buildGradle.contains("ANDROID_KEY_ALIAS"))
        assertFalse(buildGradle.contains("takeIf"))
    }

    @Test
    fun manifestDisablesUserDataBackupForReleaseSafety() {
        val manifest = appRoot.resolve("src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("""android:allowBackup="false""""))
        assertFalse(manifest.contains("""android:allowBackup="true""""))
    }

    @Test
    fun proguardKeepsMpvJniBridgeCallbacks() {
        val proguardRules = appRoot.resolve("proguard-rules.pro").readText()

        assertTrue(proguardRules.contains("-keep class is.xyz.mpv.MPVLib"))
        assertTrue(proguardRules.contains("-keepclasseswithmembernames class *"))
        assertTrue(proguardRules.contains("native <methods>;"))
        assertTrue(proguardRules.contains("-dontwarn com.google.errorprone.annotations.**"))
        assertTrue(proguardRules.contains("-dontwarn javax.el.**"))
        assertTrue(proguardRules.contains("-dontwarn org.ietf.jgss.**"))
    }

    @Test
    fun releaseWorkflowBuildsSignedReleaseArtifactsWithoutForcePushingTags() {
        val workflow = appRoot.resolve("../../../.github/workflows/release-tag.yml").readText()

        assertTrue(workflow.contains("workflow_dispatch:"))
        assertTrue(workflow.contains("ANDROID_KEYSTORE_BASE64"))
        assertTrue(workflow.contains("assembleRelease"))
        assertTrue(workflow.contains("app-release.apk"))
        assertTrue(workflow.contains("sha256sum"))
        assertTrue(workflow.contains("release-notes:start"))
        assertTrue(workflow.contains("release-notes:end"))
        assertFalse(workflow.contains("assembleDebug"))
        assertFalse(workflow.contains("app-debug.apk"))
        assertFalse(workflow.contains("--force"))
    }

    @Test
    fun buildScriptCanBuildDebugOrReleaseVariants() {
        val script = appRoot.resolve("../../scripts/build-android.sh").readText()

        assertTrue(script.contains("""VARIANT="${'$'}{1:-debug}""""))
        assertTrue(script.contains("assembleDebug"))
        assertTrue(script.contains("assembleRelease"))
    }

    @Test
    fun androidBuildEntrypointsPreferBundledJbrOverHomebrewOpenJdk() {
        val script = appRoot.resolve("../../scripts/build-android.sh").readText()
        val javaHomeScript = appRoot.resolve("../../scripts/android-java-home.sh").readText()
        val wrapper = appRoot.resolve("../../android/gradlew").readText()
        val readme = appRoot.resolve("../../android/README.md").readText()

        assertTrue(script.contains("resolve_android_java_home"))
        assertTrue(script.contains("android-java-home.sh"))

        assertTrue(javaHomeScript.contains("Android Studio.app/Contents/jbr/Contents/Home"))
        assertTrue(javaHomeScript.contains("openjdk@21"))
        assertFalse(javaHomeScript.contains("/opt/homebrew/opt/openjdk \\"))

        assertTrue(wrapper.contains("resolve_android_java_home"))
        assertTrue(wrapper.contains("android-java-home.sh"))

        assertTrue(readme.contains("Android Studio 自带 JBR 21"))
        assertFalse(readme.contains("export JAVA_HOME=/opt/homebrew/opt/openjdk"))
    }

    @Test
    fun changelogsExposeCurrentAndroidVersionSectionForReleaseNotes() {
        val english = appRoot.resolve("../../../CHANGELOG.md").readText()
        val chinese = appRoot.resolve("../../../CHANGELOG_zh-CN.md").readText()

        assertTrue(english.contains("## 0.1.05"))
        assertTrue(chinese.contains("## 0.1.05"))
        assertTrue(english.contains("Updated Android `versionCode` to `6` and `versionName` to `0.1.05`"))
        assertTrue(chinese.contains("将 Android `versionCode` 更新为 `6`，`versionName` 更新为 `0.1.05`"))
        assertFalse(english.substringBefore("## 0.1.05").contains("Updated Android `versionCode`"))
        assertFalse(chinese.substringBefore("## 0.1.05").contains("将 Android `versionCode` 更新为"))
    }

    @Test
    fun releaseNotesBlockStaysUserFacingAndFunctionalOnly() {
        val english = appRoot.resolve("../../../CHANGELOG.md").readText()
        val chinese = appRoot.resolve("../../../CHANGELOG_zh-CN.md").readText()
        val englishReleaseNotes = releaseNotesBlock(english, "0.1.05")
        val chineseReleaseNotes = releaseNotesBlock(chinese, "0.1.05")

        assertTrue(englishReleaseNotes.contains("Detail stills"))
        assertTrue(englishReleaseNotes.contains("Fullscreen playback"))
        assertTrue(chineseReleaseNotes.contains("详情页剧照"))
        assertTrue(chineseReleaseNotes.contains("全屏播放"))
        assertFalse(englishReleaseNotes.contains("versionCode"))
        assertFalse(englishReleaseNotes.contains(".codex"))
        assertFalse(chineseReleaseNotes.contains("versionCode"))
        assertFalse(chineseReleaseNotes.contains(".codex"))
    }

    private fun releaseNotesBlock(changelog: String, version: String): String {
        val versionSection = changelog.substringAfter("## $version").substringBefore("\n## ")
        return versionSection
            .substringAfter("<!-- release-notes:start -->")
            .substringBefore("<!-- release-notes:end -->")
    }
}
