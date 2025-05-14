/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.qs.panels.ui.compose.toolbar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.development.ui.compose.BuildNumber
import com.android.systemui.development.ui.viewmodel.BuildNumberViewModel
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.qs.footer.ui.compose.IconButton
import com.android.systemui.qs.panels.ui.compose.toolbar.Toolbar.TransitionKeys.SecurityInfoKey
import com.android.systemui.qs.panels.ui.viewmodel.TextFeedbackContentViewModel
import com.android.systemui.qs.panels.ui.viewmodel.TextFeedbackViewModel
import com.android.systemui.qs.panels.ui.viewmodel.toolbar.ToolbarViewModel

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Toolbar(viewModel: ToolbarViewModel, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        val securityInfoCollapsed = viewModel.securityInfoShowCollapsed

        SharedTransitionLayout(modifier = Modifier.weight(1f)) {
            AnimatedContent(
                targetState = securityInfoCollapsed,
                contentAlignment =
                    if (securityInfoCollapsed) {
                        Alignment.CenterStart
                    } else {
                        Alignment.Center
                    },
                label = "Toolbar.CollapsedSecurityInfo",
            ) { securityInfoCollapsed ->
                if (securityInfoCollapsed) {
                    StandardToolbarLayout(animatedContentScope = this@AnimatedContent, viewModel)
                } else {
                    SecurityInfo(
                        viewModel = viewModel.securityInfoViewModel,
                        showCollapsed = false,
                        modifier =
                            Modifier.sharedElement(
                                rememberSharedContentState(key = SecurityInfoKey),
                                animatedVisibilityScope = this@AnimatedContent,
                            ),
                    )
                }
            }
        }

        IconButton(
            { viewModel.powerButtonViewModel },
            useModifierBasedExpandable = true,
            Modifier.sysuiResTag("pm_lite").minimumInteractiveComponentSize(),
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.StandardToolbarLayout(
    animatedContentScope: AnimatedContentScope,
    viewModel: ToolbarViewModel,
    modifier: Modifier = Modifier,
) {
    Row(modifier) {
        // User switcher button
        IconButton(
            model = { viewModel.userSwitcherViewModel },
            useModifierBasedExpandable = true,
            Modifier.sysuiResTag("multi_user_switch").minimumInteractiveComponentSize(),
        )

        // Edit mode button
        // TODO(b/410843063): Support the tooltip in DualShade
        val editModeButtonViewModel =
            rememberViewModel("Toolbar") { viewModel.editModeButtonViewModelFactory.create() }
        EditModeButton(editModeButtonViewModel, tooltipEnabled = false)

        // Settings button
        IconButton(
            model = viewModel.settingsButtonViewModel,
            useModifierBasedExpandable = true,
            Modifier.sysuiResTag("settings_button_container").minimumInteractiveComponentSize(),
        )

        // Security info button
        SecurityInfo(
            viewModel = viewModel.securityInfoViewModel,
            showCollapsed = true,
            modifier =
                Modifier.sharedElement(
                    rememberSharedContentState(key = SecurityInfoKey),
                    animatedVisibilityScope = animatedContentScope,
                ),
        )

        // Text feedback chip / build number
        ToolbarTextFeedback(
            viewModelFactory = viewModel.textFeedbackContentViewModelFactory,
            buildNumberViewModelFactory = viewModel.buildNumberViewModelFactory,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ToolbarTextFeedback(
    viewModelFactory: TextFeedbackContentViewModel.Factory,
    buildNumberViewModelFactory: BuildNumberViewModel.Factory,
    modifier: Modifier,
) {
    Box(modifier = modifier) {
        val context = LocalContext.current
        val viewModel =
            rememberViewModel("Toolbar.TextFeedbackViewModel", context) {
                viewModelFactory.create(context)
            }
        val hasTextFeedback = viewModel.textFeedback !is TextFeedbackViewModel.NoFeedback

        Crossfade(
            targetState = hasTextFeedback,
            modifier = Modifier.align(Alignment.Center),
            label = "Toolbar.ShowTextFeedback",
        ) { showTextFeedback ->
            if (showTextFeedback) {
                TextFeedback(model = viewModel.textFeedback)
            } else {
                BuildNumber(viewModelFactory = buildNumberViewModelFactory)
            }
        }
    }
}

private object Toolbar {
    object TransitionKeys {
        const val SecurityInfoKey = "SecurityInfo"
    }
}
