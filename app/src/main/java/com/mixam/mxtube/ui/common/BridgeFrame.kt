package com.mixam.mxtube.ui.common

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.delay

@Composable
fun BridgeFrame(
    bridgeFrame: Bitmap?,
    holdMillis: Long = 90,
) {
    var frame by remember(bridgeFrame) { mutableStateOf(bridgeFrame) }
    LaunchedEffect(bridgeFrame) {
        if (bridgeFrame != null) {
            delay(holdMillis)
            frame = null
        }
    }
    frame?.let { bitmap ->
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
