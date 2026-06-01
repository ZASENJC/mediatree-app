package com.zasenjc.mediatree.ui.motion

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

val MediaTreeEmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
val MediaTreeExitEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

const val FolderEnterDurationMillis = 300
const val FolderExitDurationMillis = 210
const val PlayerRouteScrimFadeInMillis = 170
const val PlayerRouteScrimFadeOutMillis = 260
const val PlayerExitNavigationDelayMillis = 170L

fun folderForwardOffset(fullWidth: Int): Int = (fullWidth * 0.08f).toInt()
fun folderBackOffset(fullWidth: Int): Int = -(fullWidth * 0.08f).toInt()

@OptIn(ExperimentalAnimationApi::class)
fun folderForwardContentTransform(): ContentTransform =
    (
        fadeIn(animationSpec = tween(FolderEnterDurationMillis, easing = MediaTreeEmphasizedEasing)) +
            slideInHorizontally(
                animationSpec = tween(FolderEnterDurationMillis, easing = MediaTreeEmphasizedEasing),
                initialOffsetX = ::folderForwardOffset,
            )
        ).togetherWith(
        fadeOut(animationSpec = tween(FolderExitDurationMillis, easing = MediaTreeExitEasing)) +
            slideOutHorizontally(
                animationSpec = tween(FolderExitDurationMillis, easing = MediaTreeExitEasing),
                targetOffsetX = ::folderBackOffset,
            ),
    )

@OptIn(ExperimentalAnimationApi::class)
fun folderBackContentTransform(): ContentTransform =
    (
        fadeIn(animationSpec = tween(FolderEnterDurationMillis, easing = MediaTreeEmphasizedEasing)) +
            slideInHorizontally(
                animationSpec = tween(FolderEnterDurationMillis, easing = MediaTreeEmphasizedEasing),
                initialOffsetX = ::folderBackOffset,
            )
        ).togetherWith(
        fadeOut(animationSpec = tween(FolderExitDurationMillis, easing = MediaTreeExitEasing)) +
            slideOutHorizontally(
                animationSpec = tween(FolderExitDurationMillis, easing = MediaTreeExitEasing),
                targetOffsetX = ::folderForwardOffset,
            ),
    )

@OptIn(ExperimentalAnimationApi::class)
fun folderContentTransform(fromPath: String, toPath: String): ContentTransform =
    if (isFolderBackNavigation(fromPath, toPath)) {
        folderBackContentTransform()
    } else {
        folderForwardContentTransform()
    }

fun isFolderBackNavigation(fromPath: String, toPath: String): Boolean {
    val from = fromPath.trim('/')
    val to = toPath.trim('/')
    if (from.isBlank()) return false
    if (to.isBlank()) return true
    return from.startsWith("$to/")
}
