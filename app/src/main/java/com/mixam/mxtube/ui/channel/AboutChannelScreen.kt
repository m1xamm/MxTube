@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mixam.mxtube.ui.channel

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mixam.mxtube.AppLocale
import com.mixam.mxtube.R
import com.mixam.mxtube.data.youtube.ChannelAbout
import com.mixam.mxtube.data.youtube.ChannelHeader
import com.mixam.mxtube.data.youtube.YouTubeRepository
import com.mixam.mxtube.ui.common.clusterCardColor
import com.mixam.mxtube.ui.common.compactCount
import com.mixam.mxtube.ui.player.ChannelAvatar
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.text.NumberFormat

@Composable
fun AboutChannelScreen(
    header: ChannelHeader,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haze = rememberHazeState()
    val context = LocalContext.current
    var about by remember { mutableStateOf(ChannelAbout()) }
    LaunchedEffect(header.url) {
        about = YouTubeRepository().channelAbout(header.url)
    }

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().hazeSource(haze).windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            ),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 72.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "about_header") {
                AboutCard(topCorner = 28.dp, bottomCorner = 4.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        ChannelAvatar(url = header.avatarUrl, name = header.name, size = 64.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                header.name,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            header.handle?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            item(key = "about_stats") {
                AboutCard(topCorner = 4.dp, bottomCorner = 4.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.about_stats), style = MaterialTheme.typography.titleMedium)
                        AboutStatRow(stringResource(R.string.channel_subscribers, compactCount(header.subscriberCount)))
                        if (header.videoCount >= 0) AboutStatRow(stringResource(R.string.channel_video_count, compactCount(header.videoCount)))
                        about.totalViews?.toLongOrNull()?.let {
                            AboutStatRow(
                                stringResource(R.string.about_views) + ": " +
                                    NumberFormat.getIntegerInstance(AppLocale.locale).format(it),
                            )
                        }
                    }
                }
            }

            if (about.country != null || about.joinedDate != null) {
                item(key = "about_details") {
                    AboutCard(topCorner = 4.dp, bottomCorner = 4.dp) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            about.country?.let { AboutDetailRow(stringResource(R.string.about_country), it) }
                            about.joinedDate?.let { AboutDetailRow(stringResource(R.string.about_joined), it) }
                        }
                    }
                }
            }

            if (about.links.isNotEmpty()) {
                item(key = "about_links") {
                    AboutCard(topCorner = 4.dp, bottomCorner = 4.dp) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.about_links), style = MaterialTheme.typography.titleMedium)
                            about.links.forEach { link ->
                                Text(
                                    link.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).heightIn(min = 32.dp)
                                        .clickable {
                                            runCatching {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(link.url)))
                                            }
                                        }
                                        .padding(vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }

            if (header.description.isNotBlank()) {
                item(key = "about_description") {
                    AboutCard(topCorner = 4.dp, bottomCorner = 28.dp) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.about_description), style = MaterialTheme.typography.titleMedium)
                            SelectionContainer {
                                Text(header.description, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(8.dp).height(48.dp).clip(CircleShape)
                .hazeBlur(input = HazeInput.Sources(haze), style = HazeMaterials.ultraThin())
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.25f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back_rounded), stringResource(R.string.player_back), Modifier.size(24.dp))
            }
            Text(
                stringResource(R.string.channel_about),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AboutCard(topCorner: Dp, bottomCorner: Dp, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = topCorner, topEnd = topCorner, bottomStart = bottomCorner, bottomEnd = bottomCorner),
        color = clusterCardColor(),
    ) { content() }
}

@Composable
private fun AboutStatRow(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun AboutDetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
