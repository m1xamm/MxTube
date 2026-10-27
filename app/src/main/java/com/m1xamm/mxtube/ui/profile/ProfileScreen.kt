@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.m1xamm.mxtube.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m1xamm.mxtube.R
import com.m1xamm.mxtube.data.network.NetworkManager
import com.m1xamm.mxtube.data.network.NetworkMode
import com.m1xamm.mxtube.data.network.NetworkSettings
import com.m1xamm.mxtube.data.network.ProxyType
import com.m1xamm.mxtube.ui.common.clusterCardColor
import com.m1xamm.mxtube.ui.common.clusterInnerColor
import kotlinx.coroutines.launch
import java.util.Locale

private sealed interface ConnectionState {
    data object Idle : ConnectionState

    data object Testing : ConnectionState

    data class Done(
        val result: NetworkManager.ConnectionTest,
    ) : ConnectionState
}

@Composable
fun ProfileScreen(
    onNetworkChanged: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved by NetworkManager.settings.collectAsStateWithLifecycle()
    var draft by remember(saved) { mutableStateOf(saved) }
    var invalid by remember { mutableStateOf(false) }
    var testState by remember { mutableStateOf<ConnectionState>(ConnectionState.Idle) }
    var ipv6 by remember { mutableStateOf<Boolean?>(null) }
    val locale = LocalConfiguration.current.locales[0]
    val languageName =
        remember(locale) {
            locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
        }
    val versionName =
        remember(context) {
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
            }.getOrDefault("")
        }

    LaunchedEffect(Unit) {
        ipv6 = NetworkManager.ipv6Available()
    }

    val applySettings: (NetworkSettings) -> Boolean = { settings ->
        val savedNow = NetworkManager.save(settings)
        if (savedNow) onNetworkChanged()
        savedNow
    }

    val runTest: () -> Unit = {
        if (draft.mode == NetworkMode.Proxy && !draft.proxyValid) {
            invalid = true
        } else {
            invalid = false
            applySettings(draft)
            testState = ConnectionState.Testing
            scope.launch {
                testState = ConnectionState.Done(NetworkManager.testConnection())
            }
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.nav_settings),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(14.dp))

        SectionTitle(stringResource(R.string.settings_network))
        NetworkSection(
            draft = draft,
            ipv6Available = ipv6,
            invalid = invalid,
            testState = testState,
            onDraftChange = {
                draft = it
                invalid = false
                testState = ConnectionState.Idle
            },
            onModeChange = { mode ->
                draft = draft.copy(mode = mode)
                invalid = false
                testState = ConnectionState.Idle
                if (mode != NetworkMode.Proxy || draft.proxyValid) {
                    applySettings(draft)
                }
            },
            onSaveProxy = {
                if (applySettings(draft)) {
                    invalid = false
                    testState = ConnectionState.Idle
                } else {
                    invalid = true
                }
            },
            onTest = runTest,
        )

        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.settings_app))
        AppSection(
            languageName = languageName,
            versionName = versionName,
            onLanguageClick = {
                val appLocaleIntent =
                    Intent(
                        Settings.ACTION_APP_LOCALE_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    )
                val intent =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        appLocaleIntent.resolveActivity(context.packageManager) != null
                    ) {
                        appLocaleIntent
                    } else {
                        Intent(Settings.ACTION_LOCALE_SETTINGS)
                    }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
            },
        )

        Spacer(Modifier.height(110.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun NetworkSection(
    draft: NetworkSettings,
    ipv6Available: Boolean?,
    invalid: Boolean,
    testState: ConnectionState,
    onDraftChange: (NetworkSettings) -> Unit,
    onModeChange: (NetworkMode) -> Unit,
    onSaveProxy: () -> Unit,
    onTest: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = clusterCardColor(),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ModeButton(
                    label = stringResource(R.string.settings_mode_direct),
                    selected = draft.mode == NetworkMode.Direct,
                    onClick = { onModeChange(NetworkMode.Direct) },
                    modifier = Modifier.weight(1f),
                )
                ModeButton(
                    label = stringResource(R.string.settings_mode_ipv6),
                    selected = draft.mode == NetworkMode.PreferIpv6,
                    onClick = { onModeChange(NetworkMode.PreferIpv6) },
                    modifier = Modifier.weight(1f),
                )
                ModeButton(
                    label = stringResource(R.string.settings_mode_proxy),
                    selected = draft.mode == NetworkMode.Proxy,
                    onClick = { onModeChange(NetworkMode.Proxy) },
                    modifier = Modifier.weight(1f),
                )
            }

            if (draft.mode == NetworkMode.Proxy) {
                ProxyForm(
                    draft = draft,
                    invalid = invalid,
                    onDraftChange = onDraftChange,
                    onSave = onSaveProxy,
                )
            }

            Text(
                text =
                    when (draft.mode) {
                        NetworkMode.Direct -> stringResource(R.string.settings_status_direct)
                        NetworkMode.PreferIpv6 -> stringResource(R.string.settings_status_ipv6)
                        NetworkMode.Proxy ->
                            if (draft.proxyValid) {
                                stringResource(
                                    R.string.settings_status_proxy,
                                    draft.proxyHost,
                                    draft.proxyPort,
                                )
                            } else {
                                stringResource(R.string.settings_proxy_invalid)
                            }
                    },
                modifier = Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (draft.mode != NetworkMode.Proxy) {
                if (ipv6Available != null) {
                    Text(
                        text =
                            stringResource(
                                if (ipv6Available) {
                                    R.string.settings_ipv6_available
                                } else {
                                    R.string.settings_ipv6_unavailable
                                },
                            ),
                        modifier = Modifier.padding(horizontal = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = stringResource(R.string.settings_ipv6_hint),
                    modifier = Modifier.padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            FilledTonalButton(
                onClick = onTest,
                enabled = testState != ConnectionState.Testing,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                if (testState == ConnectionState.Testing) {
                    LoadingIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_testing))
                } else {
                    Text(stringResource(R.string.settings_test))
                }
            }

            when (val state = testState) {
                is ConnectionState.Done -> TestResult(state.result)
                else -> Unit
            }
        }
    }
}

@Composable
private fun TestResult(result: NetworkManager.ConnectionTest) {
    val reason =
        result.message?.takeIf { it.isNotBlank() }
            ?: result.code?.let { "HTTP $it" }
            ?: "—"
    Text(
        text =
            if (result.success) {
                stringResource(R.string.settings_test_ok, result.elapsedMs)
            } else {
                stringResource(R.string.settings_test_fail, reason)
            },
        modifier = Modifier.padding(horizontal = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color =
            if (result.success) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ProxyForm(
    draft: NetworkSettings,
    invalid: Boolean,
    onDraftChange: (NetworkSettings) -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ModeButton(
                label = stringResource(R.string.settings_proxy_http),
                selected = draft.proxyType == ProxyType.Http,
                onClick = { onDraftChange(draft.copy(proxyType = ProxyType.Http)) },
                modifier = Modifier.weight(1f),
            )
            ModeButton(
                label = stringResource(R.string.settings_proxy_socks),
                selected = draft.proxyType == ProxyType.Socks,
                onClick = { onDraftChange(draft.copy(proxyType = ProxyType.Socks)) },
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsField(
                value = draft.proxyHost,
                onValueChange = {
                    onDraftChange(draft.copy(proxyHost = it.filterNot(Char::isWhitespace)))
                },
                label = stringResource(R.string.settings_proxy_host),
                modifier = Modifier.weight(1f),
            )
            SettingsField(
                value = if (draft.proxyPort == 0) "" else draft.proxyPort.toString(),
                onValueChange = { value ->
                    onDraftChange(
                        draft.copy(
                            proxyPort =
                                value.filter(Char::isDigit).take(5).toIntOrNull() ?: 0,
                        ),
                    )
                },
                label = stringResource(R.string.settings_proxy_port),
                keyboardType = KeyboardType.Number,
                modifier = Modifier.width(104.dp),
            )
        }

        SettingsField(
            value = draft.proxyUser,
            onValueChange = { onDraftChange(draft.copy(proxyUser = it)) },
            label = stringResource(R.string.settings_proxy_user),
            modifier = Modifier.fillMaxWidth(),
        )
        SettingsField(
            value = draft.proxyPassword,
            onValueChange = { onDraftChange(draft.copy(proxyPassword = it)) },
            label = stringResource(R.string.settings_proxy_password),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = stringResource(R.string.settings_proxy_note),
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (invalid) {
            Text(
                text = stringResource(R.string.settings_proxy_invalid),
                modifier = Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = onSave,
            enabled = draft.proxyValid,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Text(stringResource(R.string.settings_proxy_save))
        }
    }
}

@Composable
private fun SettingsField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
            ),
    )
}

@Composable
private fun ModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color by
        animateColorAsState(
            targetValue =
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    clusterInnerColor()
                },
            animationSpec = tween(180),
            label = "settings-mode-color",
        )
    val contentColor =
        if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(24.dp),
        color = color,
        contentColor = contentColor,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AppSection(
    languageName: String,
    versionName: String,
    onLanguageClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = clusterCardColor(),
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            SettingsRow(
                title = stringResource(R.string.settings_language),
                value = languageName,
                onClick = onLanguageClick,
            )
            Spacer(Modifier.height(2.dp))
            SettingsRow(
                title = stringResource(R.string.settings_version),
                value = versionName.ifBlank { "—" },
                onClick = null,
            )
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    value: String,
    onClick: (() -> Unit)?,
) {
    val content: @Composable () -> Unit = {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                modifier = Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (onClick == null) {
        content()
    } else {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            color = Color.Transparent,
        ) {
            content()
        }
    }
}
