package com.m1xamm.mxtube.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

object HeroSource {
    private val bounds = mutableMapOf<String, Rect>()

    fun put(key: String, rect: Rect) {
        bounds[key] = rect
    }

    fun get(key: String): Rect? = bounds[key]

    fun miniKey(url: String): String = "mini:$url"
}

fun Modifier.heroImage(key: String): Modifier =
    this.onGloballyPositioned { HeroSource.put(key, it.boundsInWindow()) }
