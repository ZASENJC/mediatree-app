package com.zasenjc.mediatree.ui.motion

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith

val Md3DefaultEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

const val Md3FadeThroughEnterDurationMillis = 210
const val Md3FadeThroughExitDurationMillis = 90
const val Md3FadeThroughEnterDelayMillis = 90
const val PlayerExitNavigationDelayMillis = 120L

fun md3DefaultEnterTransition(): EnterTransition =
    fadeIn(
        animationSpec = tween(
            durationMillis = Md3FadeThroughEnterDurationMillis,
            delayMillis = Md3FadeThroughEnterDelayMillis,
            easing = Md3DefaultEasing,
        ),
    )

fun md3DefaultExitTransition(): ExitTransition =
    fadeOut(
        animationSpec = tween(
            durationMillis = Md3FadeThroughExitDurationMillis,
            easing = Md3DefaultEasing,
        ),
    )

fun md3DefaultPopEnterTransition(): EnterTransition = md3DefaultEnterTransition()

fun md3DefaultPopExitTransition(): ExitTransition = md3DefaultExitTransition()

@OptIn(ExperimentalAnimationApi::class)
fun md3DefaultContentTransform(): ContentTransform =
    md3DefaultEnterTransition().togetherWith(md3DefaultExitTransition())
