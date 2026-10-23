package com.m1xamm.mxtube.ui.common

import androidx.compose.runtime.Composable
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

fun compactCount(count: Long): String {
    if (count < 1_000) return count.toString()

    val value =
        when {
            count >= 1_000_000_000 -> count / 1_000_000_000.0 to "млрд"
            count >= 1_000_000 -> count / 1_000_000.0 to "млн"
            else -> count / 1_000.0 to "тыс."
        }

    val number = String.format(Locale.forLanguageTag("ru"), "%.1f", value.first).removeSuffix(",0")
    return "$number ${value.second}"
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

fun formatSpeed(speed: Float): String {
    val formatter = DecimalFormat("0.##", DecimalFormatSymbols(Locale.forLanguageTag("ru")))
    return "${formatter.format(speed)}×"
}

fun formatRelativeDate(epochMillis: Long): String {
    val elapsedMillis = System.currentTimeMillis() - epochMillis
    if (elapsedMillis < 0) return ""

    val minutes = elapsedMillis / 60_000
    val hours = elapsedMillis / 3_600_000
    val days = elapsedMillis / 86_400_000

    return when {
        minutes < 1 -> "только что"
        minutes < 60 -> "$minutes мин. назад"
        hours < 24 -> "$hours ч. назад"
        days < 7 -> "$days дн. назад"
        days < 30 -> "${days / 7} нед. назад"
        days < 365 -> "${days / 30} мес. назад"
        else -> "${days / 365} г. назад"
    }
}
