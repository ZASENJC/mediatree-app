package com.zasenjc.mediatree.ui.components

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow

fun topChromeEnterTransition(): EnterTransition =
    fadeIn(animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)) +
        slideInVertically(animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)) { -it / 4 }

fun topChromeExitTransition(): ExitTransition =
    fadeOut(animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)) +
        slideOutVertically(animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)) { -it / 4 }

fun bottomChromeEnterTransition(): EnterTransition =
    fadeIn(animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)) +
        slideInVertically(animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)) { it / 3 }

fun bottomChromeExitTransition(): ExitTransition =
    fadeOut(animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)) +
        slideOutVertically(animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)) { it / 3 }

@Composable
fun SyncChromeWithListScroll(
    state: LazyListState,
    onChromeVisibleChange: (Boolean) -> Unit,
) {
    val onChange by rememberUpdatedState(onChromeVisibleChange)
    LaunchedEffect(state) {
        var previousIndex = state.firstVisibleItemIndex
        var previousOffset = state.firstVisibleItemScrollOffset
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val atTop = index == 0 && offset < 12
                val scrollingIntoContent = index > previousIndex || (index == previousIndex && offset > previousOffset + 8)
                val scrollingTowardTop = index < previousIndex || (index == previousIndex && offset < previousOffset - 8)
                when {
                    atTop -> onChange(true)
                    scrollingIntoContent -> onChange(false)
                    scrollingTowardTop -> onChange(true)
                }
                previousIndex = index
                previousOffset = offset
            }
    }
}

@Composable
fun SyncChromeWithGridScroll(
    state: LazyGridState,
    onChromeVisibleChange: (Boolean) -> Unit,
) {
    val onChange by rememberUpdatedState(onChromeVisibleChange)
    LaunchedEffect(state) {
        var previousIndex = state.firstVisibleItemIndex
        var previousOffset = state.firstVisibleItemScrollOffset
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val atTop = index == 0 && offset < 12
                val scrollingIntoContent = index > previousIndex || (index == previousIndex && offset > previousOffset + 8)
                val scrollingTowardTop = index < previousIndex || (index == previousIndex && offset < previousOffset - 8)
                when {
                    atTop -> onChange(true)
                    scrollingIntoContent -> onChange(false)
                    scrollingTowardTop -> onChange(true)
                }
                previousIndex = index
                previousOffset = offset
            }
    }
}
