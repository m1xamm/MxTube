package com.mixam.mxtube.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LightSurfaceColorsTest {
    @Test
    fun lightContainersAreBrighterThanTheBackgroundAndTextRemainsReadable() {
        val colors = lightSurfaceColors(LightColors)
        val containers = listOf(
            colors.surfaceContainerLowest,
            colors.surfaceContainerLow,
            colors.surfaceContainer,
            colors.surfaceContainerHigh,
            colors.surfaceContainerHighest,
        )
        assertTrue(containers.all { it.luminance() > colors.background.luminance() })
        assertTrue(containers.zipWithNext().all { (outer, inner) -> outer.luminance() > inner.luminance() })
        assertTrue(colors.surfaceContainerLow.luminance() - colors.surfaceContainerHigh.luminance() >= 0.04f)
        (containers + colors.background + colors.surface).forEach { surface ->
            assertTrue(contrast(surface, colors.onSurface) >= 4.5f)
            assertTrue(contrast(surface, colors.onSurfaceVariant) >= 4.5f)
        }
    }

    @Test
    fun dynamicNeutralColorsAndAccentsArePreserved() {
        val dynamic = LightColors.copy(
            surfaceDim = Color(0xFFDED8D2),
            surfaceContainer = Color(0xFFF0EAE4),
            surfaceContainerLowest = Color.White,
            primary = Color(0xFF765B36),
            primaryContainer = Color(0xFFFFDEAE),
            secondaryContainer = Color(0xFFF1DFC4),
        )
        val colors = lightSurfaceColors(dynamic)
        assertEquals(dynamic.surfaceDim, colors.background)
        assertEquals(dynamic.surface, colors.surface)
        assertEquals(dynamic.surfaceContainerLowest, colors.surfaceContainerLowest)
        assertEquals(dynamic.surfaceContainerLow, colors.surfaceContainerLow)
        assertEquals(dynamic.surfaceContainer, colors.surfaceContainer)
        assertEquals(dynamic.surfaceContainerHigh, colors.surfaceContainerHigh)
        assertEquals(dynamic.surfaceContainerHighest, colors.surfaceContainerHighest)
        assertEquals(dynamic.primary, colors.primary)
        assertEquals(dynamic.primaryContainer, colors.primaryContainer)
        assertEquals(dynamic.secondaryContainer, colors.secondaryContainer)
        assertEquals(dynamic.onSurface, colors.onSurface)
        assertNeutralContainersAreBrighter(colors)
    }

    private fun assertNeutralContainersAreBrighter(colors: ColorScheme) {
        listOf(colors.surfaceContainerLow, colors.surfaceContainer, colors.surfaceContainerHigh, colors.surfaceContainerHighest)
            .forEach { assertTrue(it.luminance() > colors.background.luminance()) }
    }

    private fun contrast(surface: Color, text: Color): Float =
        (surface.luminance() + 0.05f) / (text.luminance() + 0.05f)
}
