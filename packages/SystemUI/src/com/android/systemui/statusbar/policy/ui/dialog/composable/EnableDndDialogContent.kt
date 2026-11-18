/*
 * Copyright (C) 2026 The LibreMobileOS Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.policy.ui.dialog.composable

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.compose.PlatformButton
import com.android.compose.PlatformOutlinedButton
import com.android.compose.dialog.AlertDialogContent
import com.android.settingslib.R as SettingsLibR
import com.android.systemui.res.R
import com.android.systemui.statusbar.policy.ui.dialog.EnableDndDialogState

/**
 * The "Turn on Do Not Disturb" dialog of quick settings, drawn like the modes dialog it opens from:
 * the interruption level as three tiles in a row and the duration as a column of tiles, the chosen
 * ones filled like an active mode.
 */
@Composable
fun EnableDndDialogContent(
    state: EnableDndDialogState,
    onCancel: () -> Unit,
    onTurnOn: () -> Unit,
) {
    AlertDialogContent(
        title = {
            Text(stringResource(SettingsLibR.string.zen_mode_settings_turn_on_dialog_title))
        },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(TILE_SPACING)) {
                Row(
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(TILE_SPACING),
                ) {
                    LEVELS.forEach { level ->
                        LevelTile(
                            level = level,
                            selected = state.zenMode == level.mode,
                            onClick = { state.selectZenMode(level.mode) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(TILE_SPACING))
                Column(
                    modifier = Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(TILE_SPACING),
                ) {
                    DurationTile(
                        icon = R.drawable.ic_do_not_disturb,
                        title = stringResource(com.android.internal.R.string.zen_mode_forever),
                        subtitle = null,
                        selected = state.selected == EnableDndDialogState.FOREVER,
                        onClick = { state.select(EnableDndDialogState.FOREVER) },
                    )
                    val countdown = state.countdownCondition
                    DurationTile(
                        icon = R.drawable.ic_statusbar_clock,
                        title = countdown.line1?.ifEmpty { null } ?: countdown.summary,
                        subtitle = countdown.line2,
                        selected = state.selected == EnableDndDialogState.COUNTDOWN,
                        onClick = { state.select(EnableDndDialogState.COUNTDOWN) },
                        trailing = {
                            Stepper(
                                canShorten = state.canShorten,
                                canExtend = state.canExtend,
                                onStep = state::step,
                            )
                        },
                    )
                    state.nextAlarmCondition?.let { alarm ->
                        DurationTile(
                            icon = R.drawable.ic_alarm,
                            title = alarm.line1?.ifEmpty { null } ?: alarm.summary,
                            subtitle = alarm.line2,
                            selected = state.selected == EnableDndDialogState.UNTIL_ALARM,
                            onClick = { state.select(EnableDndDialogState.UNTIL_ALARM) },
                        )
                    }
                }
                state.alarmWarning()?.let { warning ->
                    Text(
                        warning,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        },
        neutralButton = {
            PlatformOutlinedButton(onClick = onCancel) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        positiveButton = {
            PlatformButton(onClick = onTurnOn) {
                Text(stringResource(SettingsLibR.string.zen_mode_enable_dialog_turn_on))
            }
        },
    )
}

private class Level(val mode: Int, val icon: Int, val label: Int, val description: Int)

private val LEVELS =
    listOf(
        Level(
            Settings.Global.ZEN_MODE_NO_INTERRUPTIONS,
            R.drawable.ic_volume_off,
            SettingsLibR.string.interruption_level_none_twoline,
            SettingsLibR.string.interruption_level_none_with_warning,
        ),
        Level(
            Settings.Global.ZEN_MODE_ALARMS,
            R.drawable.ic_alarm,
            SettingsLibR.string.interruption_level_alarms_twoline,
            SettingsLibR.string.interruption_level_alarms,
        ),
        Level(
            Settings.Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS,
            R.drawable.ic_star,
            SettingsLibR.string.interruption_level_priority_twoline,
            SettingsLibR.string.interruption_level_priority,
        ),
    )

private val TILE_SPACING = 8.dp
private val TILE_SHAPE = RoundedCornerShape(16.dp)

/** The colors of a mode tile (ModeTile): filled when chosen, tonal otherwise. */
@Composable
private fun tileColors(selected: Boolean): Pair<Color, Color> {
    val container by
        animateColorAsState(
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant
        )
    val content by
        animateColorAsState(
            if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    return container to content
}

@Composable
private fun LevelTile(level: Level, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val (container, content) = tileColors(selected)
    val description = stringResource(level.description)
    CompositionLocalProvider(LocalContentColor provides content) {
        Surface(color = container, shape = TILE_SHAPE, modifier = modifier) {
            Column(
                modifier =
                    Modifier.selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = onClick,
                        )
                        .semantics { contentDescription = description }
                        .padding(horizontal = 8.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(painterResource(level.icon), contentDescription = null, Modifier.size(24.dp))
                Text(
                    stringResource(level.label),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    minLines = 2,
                )
            }
        }
    }
}

@Composable
private fun DurationTile(
    icon: Int,
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val (container, content) = tileColors(selected)
    CompositionLocalProvider(LocalContentColor provides content) {
        Surface(color = container, shape = TILE_SHAPE, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier =
                    Modifier.selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = onClick,
                        )
                        .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(painterResource(icon), contentDescription = null, Modifier.size(24.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge)
                    if (!subtitle.isNullOrEmpty()) {
                        Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                trailing?.invoke()
            }
        }
    }
}

@Composable
private fun Stepper(canShorten: Boolean, canExtend: Boolean, onStep: (Boolean) -> Unit) {
    val colors =
        IconButtonDefaults.iconButtonColors(
            contentColor = LocalContentColor.current,
            disabledContentColor = LocalContentColor.current.copy(alpha = 0.38f),
        )
    Row {
        IconButton(onClick = { onStep(false) }, enabled = canShorten, colors = colors) {
            Icon(
                painterResource(R.drawable.ic_remove),
                contentDescription =
                    stringResource(SettingsLibR.string.accessibility_manual_zen_less_time),
            )
        }
        IconButton(onClick = { onStep(true) }, enabled = canExtend, colors = colors) {
            Icon(
                painterResource(R.drawable.ic_add),
                contentDescription =
                    stringResource(SettingsLibR.string.accessibility_manual_zen_more_time),
            )
        }
    }
}
