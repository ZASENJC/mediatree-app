package com.zasenjc.mediatree.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape

fun Modifier.shapeAwareClickable(
    shape: Shape,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = clip(shape).clickable(enabled = enabled, onClick = onClick)

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.shapeAwareCombinedClickable(
    shape: Shape,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = clip(shape).combinedClickable(
    enabled = enabled,
    onLongClick = onLongClick,
    onClick = onClick,
)
