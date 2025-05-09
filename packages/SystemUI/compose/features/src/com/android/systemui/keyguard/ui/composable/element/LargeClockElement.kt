/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.content.res.Resources
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.animation.scene.ContentScope
import com.android.systemui.keyguard.ui.composable.blueprint.ClockElementKeys.largeClockElementKey
import com.android.systemui.keyguard.ui.composable.blueprint.ClockScenes.largeClockScene
import com.android.systemui.keyguard.ui.composable.blueprint.ClockScenes.splitShadeLargeClockScene
import com.android.systemui.keyguard.ui.composable.modifier.burnInAware
import com.android.systemui.keyguard.ui.viewmodel.AodBurnInViewModel
import com.android.systemui.keyguard.ui.viewmodel.BurnInParameters
import com.android.systemui.keyguard.ui.viewmodel.KeyguardClockViewModel
import javax.inject.Inject

/** Provides small clock and large clock composables for the default clock face. */
class LargeClockElement
@Inject
constructor(
    private val viewModel: KeyguardClockViewModel,
    private val aodBurnInViewModel: AodBurnInViewModel,
) : ClockElement() {

    @Composable
    fun ContentScope.LargeClock(burnInParams: BurnInParameters, modifier: Modifier = Modifier) {
        val currentClock by viewModel.currentClock.collectAsStateWithLifecycle()
        if (currentClock?.largeClock?.view == null) {
            return
        }

        // Centering animation for clocks that have custom position animations.
        LaunchedEffect(layoutState.currentTransition?.progress) {
            val transition = layoutState.currentTransition ?: return@LaunchedEffect
            if (currentClock?.largeClock?.config?.hasCustomPositionUpdatedAnimation != true) {
                return@LaunchedEffect
            }

            // If we are not doing the centering animation, do not animate.
            val progress =
                if (transition.isTransitioningBetween(largeClockScene, splitShadeLargeClockScene)) {
                    transition.progress
                } else {
                    1f
                }

            val dir = if (transition.toContent == splitShadeLargeClockScene) -1f else 1f
            val distance = dir * getClockCenteringDistance()
            val largeClock = checkNotNull(currentClock).largeClock
            largeClock.animations.onPositionUpdated(distance = distance, fraction = progress)
        }

        Element(key = largeClockElementKey, modifier = modifier) {
            ClockView(
                checkNotNull(currentClock).largeClock.view,
                modifier =
                    Modifier.fillMaxSize()
                        .burnInAware(
                            viewModel = aodBurnInViewModel,
                            params = burnInParams,
                            isClock = true,
                        ),
            )
        }
    }

    companion object {
        private fun getClockCenteringDistance(): Float {
            return Resources.getSystem().displayMetrics.widthPixels / 4f
        }
    }
}
