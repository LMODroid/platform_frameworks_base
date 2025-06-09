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

package com.android.systemui.flashlight.ui.composable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.flashlight.ui.viewmodel.FlashlightSliderViewModel
import com.android.systemui.util.ui.compose.DualIconSlider

@Composable
fun FlashlightSliderContainer(viewModel: FlashlightSliderViewModel, modifier: Modifier = Modifier) {
    val currentState = viewModel.currentFlashlightLevel ?: return
    val levelValue =
        if (currentState.enabled) {
            currentState.level
        } else { // flashlight off
            0 // even if the "level" has been reset to "default" on the backend
        }

    Box(modifier = modifier.fillMaxWidth().sysuiResTag("flashlight_slider")) {
        DualIconSlider(
            levelValue = levelValue,
            valueRange = 0..currentState.max,
            iconResProvider = FlashlightSliderViewModel::getIconForPercentage,
            imageLoader = viewModel::loadImage,
            hapticsViewModelFactory = viewModel.hapticsViewModelFactory,
            onDrag = viewModel::setFlashlightLevel,
            isEnabled = viewModel.isFlashlightAdjustable,
        )
    }
}
