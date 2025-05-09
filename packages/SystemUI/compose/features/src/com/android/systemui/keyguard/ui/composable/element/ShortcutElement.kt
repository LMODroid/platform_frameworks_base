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

package com.android.systemui.keyguard.ui.composable.element

import android.view.View
import android.widget.ImageView
import androidx.annotation.IdRes
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.systemui.animation.view.LaunchableImageView
import com.android.systemui.keyguard.ui.binder.KeyguardQuickAffordanceViewBinder
import com.android.systemui.keyguard.ui.viewmodel.KeyguardQuickAffordanceViewModel
import com.android.systemui.keyguard.ui.viewmodel.KeyguardQuickAffordancesCombinedViewModel
import com.android.systemui.res.R
import com.android.systemui.statusbar.KeyguardIndicationController
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ShortcutElement
@Inject
constructor(
    private val viewModel: KeyguardQuickAffordancesCombinedViewModel,
    private val keyguardQuickAffordanceViewBinder: KeyguardQuickAffordanceViewBinder,
    private val indicationController: KeyguardIndicationController,
) {

    /**
     * Renders a single lockscreen shortcut.
     *
     * @param isStart Whether the shortcut goes on the left (in left-to-right locales).
     * @param applyPadding Whether to apply padding around the shortcut, this is needed if the
     *   shortcut is placed along the edges of the display.
     */
    @Composable
    fun ContentScope.Shortcut(
        isStart: Boolean,
        applyPadding: Boolean,
        modifier: Modifier = Modifier,
    ) {
        Element(
            key = if (isStart) StartButtonElementKey else EndButtonElementKey,
            modifier = modifier,
        ) {
            Shortcut(
                viewId = if (isStart) R.id.start_button else R.id.end_button,
                viewModel = if (isStart) viewModel.startButton else viewModel.endButton,
                transitionAlpha = viewModel.transitionAlpha,
                indicationController = indicationController,
                binder = keyguardQuickAffordanceViewBinder,
                modifier =
                    if (applyPadding) {
                        Modifier.shortcutPadding()
                    } else {
                        Modifier
                    },
            )
        }
    }

    @Composable
    fun shortcutSizeDp(): DpSize {
        return DpSize(
            width = dimensionResource(R.dimen.keyguard_affordance_fixed_width),
            height = dimensionResource(R.dimen.keyguard_affordance_fixed_height),
        )
    }

    @Composable
    private fun Shortcut(
        @IdRes viewId: Int,
        viewModel: Flow<KeyguardQuickAffordanceViewModel>,
        transitionAlpha: Flow<Float>,
        indicationController: KeyguardIndicationController,
        binder: KeyguardQuickAffordanceViewBinder,
        modifier: Modifier = Modifier,
    ) {
        val (binding, setBinding) = mutableStateOf<KeyguardQuickAffordanceViewBinder.Binding?>(null)

        AndroidView(
            factory = { context ->
                val padding =
                    context.resources.getDimensionPixelSize(
                        R.dimen.keyguard_affordance_fixed_padding
                    )
                val view =
                    LaunchableImageView(context, null).apply {
                        id = viewId
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        background =
                            ResourcesCompat.getDrawable(
                                context.resources,
                                R.drawable.keyguard_bottom_affordance_bg,
                                context.theme,
                            )
                        foreground =
                            ResourcesCompat.getDrawable(
                                context.resources,
                                R.drawable.keyguard_bottom_affordance_selected_border,
                                context.theme,
                            )
                        visibility = View.INVISIBLE
                        setPadding(padding, padding, padding, padding)
                    }

                setBinding(
                    binder.bind(view, viewModel, transitionAlpha) {
                        indicationController.showTransientIndication(it)
                    }
                )

                view
            },
            onRelease = { binding?.destroy() },
            modifier =
                modifier.size(width = shortcutSizeDp().width, height = shortcutSizeDp().height),
        )
    }

    @Composable
    private fun Modifier.shortcutPadding(): Modifier {
        return this.padding(
                horizontal = dimensionResource(R.dimen.keyguard_affordance_horizontal_offset)
            )
            .padding(bottom = dimensionResource(R.dimen.keyguard_affordance_vertical_offset))
    }
}

private val StartButtonElementKey = ElementKey("StartButton")
private val EndButtonElementKey = ElementKey("EndButton")
