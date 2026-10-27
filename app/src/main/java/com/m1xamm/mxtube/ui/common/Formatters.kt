package com.m1xamm.mxtube.ui.common

import android.icu.text.CompactDecimalFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.m1xamm.mxtube.R
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

fun formatTime(totalSeconds: Long): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0)
    val hours = safeSeconds / 3600
    val minutes = (safeSeconds % 3600) / 60
    val seconds = safeSeconds % 60

    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

fun formatDuration(seconds: Long): String = if (seconds > 0) formatTime(seconds) else ""

@Composable
fun compactCount(count: Long): String {
    if (count < 1_000) return count.toString()

    val formatter =
        CompactDecimalFormat.getInstance(
            LocalConfiguration.current.locales[0],
            CompactDecimalFormat.CompactStyle.SHORT,
        )
    formatter.maximumFractionDigits = 1
    return formatter.format(count)
}

@Composable
fun viewsText(count: Long): String {
    val safeCount = count.coerceAtLeast(0)
    return if (safeCount < 1_000) {
        pluralStringResource(R.plurals.video_views, safeCount.toInt(), safeCount)
    } else {
        stringResource(R.string.video_views_compact, compactCount(safeCount))
    }
}

@Composable
fun formatSpeed(speed: Float): String {
    val formatter =
        DecimalFormat(
            "0.##",
            DecimalFormatSymbols(LocalConfiguration.current.locales[0]),
        )
    return "${formatter.format(speed)}×"
}

@Composable
fun formatRelativeDate(epochMillis: Long): String {
    val elapsedMillis = System.currentTimeMillis() - epochMillis
    if (elapsedMillis < 0) return ""

    val minutes = elapsedMillis / 60_000
    val hours = elapsedMillis / 3_600_000
    val days = elapsedMillis / 86_400_000

    return when {
        minutes < 1 -> stringResource(R.string.relative_just_now)
        minutes < 60 -> stringResource(R.string.relative_minutes, minutes)
        hours < 24 -> stringResource(R.string.relative_hours, hours)
        days < 7 -> stringResource(R.string.relative_days, days)
        days < 30 -> stringResource(R.string.relative_weeks, days / 7)
        days < 365 -> stringResource(R.string.relative_months, days / 30)
        else -> stringResource(R.string.relative_years, days / 365)
    }
}
