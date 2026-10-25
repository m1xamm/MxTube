package com.m1xamm.mxtube.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

@Composable
fun clusterCardColor(): Color {
    val darkTheme = MaterialTheme.colorScheme.surfaceContainerHighest.luminance() < 0.5f
    return if (darkTheme) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        lerp(
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurface,
            0.07f,
        )
    }
}

@Composable
fun clusterInnerColor(): Color {
    val darkTheme = MaterialTheme.colorScheme.surfaceContainerHighest.luminance() < 0.5f
    return if (darkTheme) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
}
