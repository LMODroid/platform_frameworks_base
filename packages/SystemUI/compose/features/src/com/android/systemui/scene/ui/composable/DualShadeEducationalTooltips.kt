/*
 * Copyright (C) 2025 The Android Open Source Project
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

@file:OptIn(ExperimentalMaterial3Api::class)

package com.android.systemui.scene.ui.composable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.android.compose.modifiers.height
import com.android.compose.theme.LocalAndroidColorScheme
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.scene.ui.viewmodel.DualShadeEducationalTooltipsViewModel

@Composable
fun DualShadeEducationalTooltips(viewModelFactory: DualShadeEducationalTooltipsViewModel.Factory) {
    val context = LocalContext.current
    val viewModel =
        rememberViewModel(traceName = "DualShadeEducationalTooltips") {
            viewModelFactory.create(context)
        }

    val visibleTooltip = viewModel.visibleTooltip ?: return

    val anchorBottomY = visibleTooltip.anchorBottomY
    // This Box represents the bounds of the top edge that the user can swipe down on to reveal
    // either of the dual shade overlays. It's used as a convenient way to position the anchor for
    // each of the tooltips that can be shown. As such, this Box is the same size as the status bar.
    Box(
        contentAlignment =
            if (visibleTooltip.isAlignedToStart) {
                Alignment.CenterStart
            } else {
                Alignment.CenterEnd
            },
        modifier = Modifier.fillMaxWidth().height { anchorBottomY }.padding(horizontal = 24.dp),
    ) {
        AnchoredTooltip(
            text = visibleTooltip.text,
            onShown = visibleTooltip.onShown,
            onDismissed = visibleTooltip.onDismissed,
        )
    }
}

@Composable
private fun AnchoredTooltip(
    text: String,
    onShown: () -> Unit,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tooltipState = rememberTooltipState(initialIsVisible = true, isPersistent = true)

    LaunchedEffect(Unit) { onShown() }

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
        tooltip = {
            RichTooltip(
                colors =
                    TooltipDefaults.richTooltipColors(
                        containerColor = LocalAndroidColorScheme.current.tertiaryFixed,
                        contentColor = LocalAndroidColorScheme.current.onTertiaryFixed,
                    ),
                caretSize = TooltipDefaults.caretSize,
                shadowElevation = 2.dp,
            ) {
                Text(text = text, modifier = Modifier.padding(8.dp))
            }
        },
        state = tooltipState,
        onDismissRequest = onDismissed,
        modifier = modifier,
    ) {
        Spacer(modifier = Modifier.width(48.dp).fillMaxHeight())
    }
}
