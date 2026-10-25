@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.m1xamm.mxtube.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.m1xamm.mxtube.R
import com.m1xamm.mxtube.data.youtube.PlaybackInfo
import com.m1xamm.mxtube.ui.common.formatSpeed
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.round

private enum class SettingsPage {
    Quality,
    Speed,
    Audio,
    Subtitles,
}

private enum class MainEntry(
    val page: SettingsPage?,
) {
    Quality(SettingsPage.Quality),
    Speed(SettingsPage.Speed),
    Audio(SettingsPage.Audio),
    Subtitles(SettingsPage.Subtitles),
    More(null),
}

private const val MIN_SPEED = 0.25f
private const val MAX_SPEED = 5f
private const val SPEED_STEP = 0.05f
private val SPEED_PRESETS =
    listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 4f, 5f)

@Composable
fun PlayerSettingsOverlay(
    info: PlaybackInfo,
    selectedQualityIndex: Int,
    selectedAudioTrackIndex: Int,
    selectedSubtitleIndex: Int,
    speed: Float,
    onDismiss: () -> Unit,
    onQualitySelected: (Int) -> Unit,
    onAudioTrackSelected: (Int) -> Unit,
    onSubtitleSelected: (Int) -> Unit,
    onSpeedSelected: (Float) -> Unit,
) {
    var page by remember { mutableStateOf<SettingsPage?>(null) }
    BackHandler(enabled = page != null) { page = null }

    val shape = RoundedCornerShape(30.dp)
    val panelColor = MaterialTheme.colorScheme.surfaceContainerLow
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    val scrimInteraction = remember { MutableInteractionSource() }
    val panelInteraction = remember { MutableInteractionSource() }
    val darkTheme = MaterialTheme.colorScheme.surfaceContainerHighest.luminance() < 0.5f
    val scrimColor = Color.Black.copy(alpha = if (darkTheme) 0.26f else 0.16f)

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .zIndex(4f),
    ) {
        val panelMaxHeight = maxHeight * 0.84f

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(scrimColor)
                    .clickable(
                        interactionSource = scrimInteraction,
                        indication = null,
                        onClick = onDismiss,
                    ),
        )

        AnimatedVisibility(
            visibleState = visibleState,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter =
                slideInVertically(
                    initialOffsetY = { it },
                    animationSpec =
                        spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                ) +
                    fadeIn(tween(160)),
            exit = fadeOut(tween(120)),
        ) {
            Box(
                modifier =
                    Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .widthIn(max = 520.dp)
                        .fillMaxWidth()
                        .heightIn(max = panelMaxHeight)
                        .animateContentSize(
                            animationSpec =
                                spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                        )
                        .clip(shape)
                        .background(panelColor)
                        .clickable(
                            interactionSource = panelInteraction,
                            indication = null,
                            onClick = {},
                        ),
            ) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        if (targetState != null) {
                            (slideInHorizontally { it / 4 } + fadeIn(tween(160))) togetherWith
                                (slideOutHorizontally { -it / 4 } + fadeOut(tween(100)))
                        } else {
                            (slideInHorizontally { -it / 4 } + fadeIn(tween(160))) togetherWith
                                (slideOutHorizontally { it / 4 } + fadeOut(tween(100)))
                        }
                    },
                    label = "settings-page",
                ) { currentPage ->
                    if (currentPage == null) {
                        MainSettings(
                            info = info,
                            selectedQualityIndex = selectedQualityIndex,
                            selectedAudioTrackIndex = selectedAudioTrackIndex,
                            selectedSubtitleIndex = selectedSubtitleIndex,
                            speed = speed,
                            onOpenPage = { page = it },
                        )
                    } else {
                        SettingsPageContent(
                            page = currentPage,
                            info = info,
                            selectedQualityIndex = selectedQualityIndex,
                            selectedAudioTrackIndex = selectedAudioTrackIndex,
                            selectedSubtitleIndex = selectedSubtitleIndex,
                            speed = speed,
                            onBack = { page = null },
                            onQualitySelected = onQualitySelected,
                            onAudioTrackSelected = onAudioTrackSelected,
                            onSubtitleSelected = onSubtitleSelected,
                            onSpeedSelected = onSpeedSelected,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MainSettings(
    info: PlaybackInfo,
    selectedQualityIndex: Int,
    selectedAudioTrackIndex: Int,
    selectedSubtitleIndex: Int,
    speed: Float,
    onOpenPage: (SettingsPage) -> Unit,
) {
    val entries =
        buildList {
            add(MainEntry.Quality)
            add(MainEntry.Speed)
            if (info.audioTracks.size > 1) add(MainEntry.Audio)
            add(MainEntry.Subtitles)
            add(MainEntry.More)
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        entries.forEachIndexed { index, entry ->
            val title =
                when (entry) {
                    MainEntry.Quality -> stringResource(R.string.player_quality)
                    MainEntry.Speed -> stringResource(R.string.player_speed)
                    MainEntry.Audio -> stringResource(R.string.player_audio_track)
                    MainEntry.Subtitles -> stringResource(R.string.player_subtitles)
                    MainEntry.More -> stringResource(R.string.player_more)
                }
            val value =
                when (entry) {
                    MainEntry.Quality -> info.qualities.getOrNull(selectedQualityIndex)?.label.orEmpty()
                    MainEntry.Speed -> formatSpeed(speed)
                    MainEntry.Audio -> info.audioTracks.getOrNull(selectedAudioTrackIndex)?.label.orEmpty()
                    MainEntry.Subtitles ->
                        if (selectedSubtitleIndex >= 0) {
                            info.subtitles.getOrNull(selectedSubtitleIndex)?.label.orEmpty()
                        } else if (info.subtitles.isEmpty()) {
                            stringResource(R.string.player_none)
                        } else {
                            stringResource(R.string.player_subtitles_off)
                        }

                    MainEntry.More -> ""
                }
            NavigationRow(
                title = title,
                value = value,
                topCorner = if (index == 0) 28.dp else 4.dp,
                bottomCorner = if (index == entries.lastIndex) 28.dp else 4.dp,
                onClick = { entry.page?.let(onOpenPage) },
            )
        }
    }
}

@Composable
private fun SettingsPageContent(
    page: SettingsPage,
    info: PlaybackInfo,
    selectedQualityIndex: Int,
    selectedAudioTrackIndex: Int,
    selectedSubtitleIndex: Int,
    speed: Float,
    onBack: () -> Unit,
    onQualitySelected: (Int) -> Unit,
    onAudioTrackSelected: (Int) -> Unit,
    onSubtitleSelected: (Int) -> Unit,
    onSpeedSelected: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        PageHeader(
            title =
                when (page) {
                    SettingsPage.Quality -> stringResource(R.string.player_quality)
                    SettingsPage.Speed -> stringResource(R.string.player_speed)
                    SettingsPage.Audio -> stringResource(R.string.player_audio_track)
                    SettingsPage.Subtitles -> stringResource(R.string.player_subtitles)
                },
            onBack = onBack,
        )

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            when (page) {
                SettingsPage.Quality -> {
                    OptionCluster(count = info.qualities.size) { index, topCorner, bottomCorner ->
                        val quality = info.qualities[index]
                        OptionRow(
                            title = quality.label,
                            supporting = null,
                            selected = index == selectedQualityIndex,
                            topCorner = topCorner,
                            bottomCorner = bottomCorner,
                            onClick = { onQualitySelected(index) },
                        )
                    }
                }

                SettingsPage.Speed -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ) {
                        SpeedPicker(
                            speed = speed,
                            onSpeedSelected = onSpeedSelected,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }

                SettingsPage.Audio -> {
                    OptionCluster(count = info.audioTracks.size) { index, topCorner, bottomCorner ->
                        val track = info.audioTracks[index]
                        OptionRow(
                            title = track.label,
                            supporting =
                                if (track.isOriginal) {
                                    stringResource(R.string.player_audio_original)
                                } else {
                                    null
                                },
                            selected = index == selectedAudioTrackIndex,
                            topCorner = topCorner,
                            bottomCorner = bottomCorner,
                            onClick = { onAudioTrackSelected(index) },
                        )
                    }
                }

                SettingsPage.Subtitles -> {
                    if (info.subtitles.isEmpty()) {
                        Text(
                            text = stringResource(R.string.player_subtitles_empty),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        OptionCluster(count = info.subtitles.size + 1) { index, topCorner, bottomCorner ->
                            if (index == 0) {
                                OptionRow(
                                    title = stringResource(R.string.player_subtitles_off),
                                    supporting = null,
                                    selected = selectedSubtitleIndex == -1,
                                    topCorner = topCorner,
                                    bottomCorner = bottomCorner,
                                    onClick = { onSubtitleSelected(-1) },
                                )
                            } else {
                                val subtitle = info.subtitles[index - 1]
                                OptionRow(
                                    title = subtitle.label,
                                    supporting =
                                        if (subtitle.isAutoGenerated) {
                                            stringResource(R.string.player_subtitles_auto)
                                        } else {
                                            null
                                        },
                                    selected = index - 1 == selectedSubtitleIndex,
                                    topCorner = topCorner,
                                    bottomCorner = bottomCorner,
                                    onClick = { onSubtitleSelected(index - 1) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageHeader(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back_rounded),
                contentDescription = stringResource(R.string.player_back),
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = title,
            modifier = Modifier.padding(start = 2.dp),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NavigationRow(
    title: String,
    value: String,
    topCorner: Dp,
    bottomCorner: Dp,
    onClick: () -> Unit,
) {
    ClusterSurface(
        topCorner = topCorner,
        bottomCorner = bottomCorner,
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 17.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (value.isNotBlank()) {
                Text(
                    text = value,
                    modifier =
                        Modifier
                            .widthIn(max = 120.dp)
                            .padding(start = 12.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_expand_more_rounded),
                contentDescription = null,
                modifier =
                    Modifier
                        .padding(start = 8.dp)
                        .size(20.dp)
                        .rotate(-90f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OptionRow(
    title: String,
    supporting: String?,
    selected: Boolean,
    topCorner: Dp,
    bottomCorner: Dp,
    onClick: () -> Unit,
) {
    ClusterSurface(
        topCorner = topCorner,
        bottomCorner = bottomCorner,
        onClick = onClick,
        containerColor =
            if (selected) {
                MaterialTheme.colorScheme.surfaceContainerHighest
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                )
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.ic_check_rounded),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ClusterSurface(
    topCorner: Dp,
    bottomCorner: Dp,
    onClick: () -> Unit,
    containerColor: Color,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val animatedTopCorner by
        animateDpAsState(
            targetValue = if (pressed) 28.dp else topCorner,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            label = "settings-top-corner",
        )
    val animatedBottomCorner by
        animateDpAsState(
            targetValue = if (pressed) 28.dp else bottomCorner,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            label = "settings-bottom-corner",
        )

    Surface(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(
                topStart = animatedTopCorner,
                topEnd = animatedTopCorner,
                bottomEnd = animatedBottomCorner,
                bottomStart = animatedBottomCorner,
            ),
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        content()
    }
}

@Composable
private fun OptionCluster(
    count: Int,
    content: @Composable (index: Int, topCorner: Dp, bottomCorner: Dp) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(count) { index ->
            content(
                index,
                if (index == 0) 28.dp else 4.dp,
                if (index == count - 1) 28.dp else 4.dp,
            )
        }
    }
}

@Composable
private fun SpeedPicker(
    speed: Float,
    onSpeedSelected: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val toggleColors =
        ToggleButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            checkedContainerColor = MaterialTheme.colorScheme.primary,
            checkedContentColor = MaterialTheme.colorScheme.onPrimary,
        )
    val sliderColors =
        SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.24f),
        )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = formatSpeed(speed),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { onSpeedSelected(stepSpeed(speed, -SPEED_STEP)) },
                enabled = speed > MIN_SPEED,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_remove_rounded),
                    contentDescription = stringResource(R.string.player_speed_down),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Slider(
                value = speedToFraction(speed),
                onValueChange = { onSpeedSelected(fractionToSpeed(it)) },
                modifier = Modifier.weight(1f),
                colors = sliderColors,
            )

            IconButton(
                onClick = { onSpeedSelected(stepSpeed(speed, SPEED_STEP)) },
                enabled = speed < MAX_SPEED,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add_rounded),
                    contentDescription = stringResource(R.string.player_speed_up),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SPEED_PRESETS.forEach { preset ->
                ToggleButton(
                    checked = speed == preset,
                    onCheckedChange = { checked ->
                        if (checked) onSpeedSelected(preset)
                    },
                    colors = toggleColors,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = formatSpeed(preset),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

private fun speedToFraction(speed: Float): Float {
    val clamped = speed.coerceIn(MIN_SPEED, MAX_SPEED)
    return (ln(clamped / MIN_SPEED) / ln(MAX_SPEED / MIN_SPEED)).coerceIn(0f, 1f)
}

private fun fractionToSpeed(fraction: Float): Float {
    val raw = MIN_SPEED * (MAX_SPEED / MIN_SPEED).pow(fraction.coerceIn(0f, 1f))
    return snapSpeed(raw)
}

private fun stepSpeed(speed: Float, delta: Float): Float = snapSpeed(speed + delta)

private fun snapSpeed(value: Float): Float =
    (round(value / SPEED_STEP) * SPEED_STEP).coerceIn(MIN_SPEED, MAX_SPEED)
