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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.flashlight.ui.viewmodel.FlashlightSliderViewModel
import com.android.systemui.haptics.slider.SeekableSliderTrackerConfig
import com.android.systemui.haptics.slider.SliderHapticFeedbackConfig
import com.android.systemui.haptics.slider.compose.ui.SliderHapticsViewModel
import com.android.systemui.lifecycle.rememberViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FlashlightSlider(
    levelValue: Int,
    valueRange: IntRange,
    hapticsViewModelFactory: SliderHapticsViewModel.Factory,
    onLevelChanged: (Int) -> Unit,
) {

    var value by remember(levelValue) { mutableIntStateOf(levelValue) }

    val animatedValue by
        animateFloatAsState(targetValue = value.toFloat(), label = "FlashlightSliderAnimatedValue")
    val interactionSource = remember { MutableInteractionSource() }
    val floatValueRange = valueRange.first.toFloat()..valueRange.last.toFloat()

    val hapticsViewModel: SliderHapticsViewModel =
        rememberViewModel(traceName = "SliderHapticsViewModel") {
            hapticsViewModelFactory.create(
                interactionSource,
                floatValueRange,
                Orientation.Horizontal,
                SliderHapticFeedbackConfig(
                    maxVelocityToScale = 1f /* slider progress(from 0 to 1) per sec */
                ),
                SeekableSliderTrackerConfig(),
            )
        }

    Slider(
        value = animatedValue,
        valueRange = floatValueRange,
        onValueChange = {
            hapticsViewModel.onValueChange(it)
            value = it.toInt()
            onLevelChanged(it.toInt())
        },
        onValueChangeFinished = { hapticsViewModel.onValueChangeEnded() },
        interactionSource = interactionSource,
        thumb = {
            SliderDefaults.Thumb(
                interactionSource = interactionSource,
                thumbSize = DpSize(Constants.ThumbWidth, Constants.ThumbHeight),
            )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier.height(40.dp),
                trackCornerSize = Constants.SliderTrackRoundedCorner,
                trackInsideCornerSize = Constants.TrackInsideCornerSize,
                drawStopIndicator = null,
                thumbTrackGapSize = Constants.ThumbTrackGapSize,
            )
        },
    )
}

@Composable
fun FlashlightSliderContainer(viewModel: FlashlightSliderViewModel, modifier: Modifier = Modifier) {
    val currentState = viewModel.currentFlashlightLevel ?: return
    val levelValue =
        if (currentState.enabled) {
            currentState.level
        } else { // flashlight off
            Constants
                .POSITION_ZERO // even if the "level" has been reset to "default" on the backend
        }

    val maxLevel = currentState.max

    Box(modifier = modifier.fillMaxWidth().sysuiResTag("flashlight_slider")) {
        FlashlightSlider(
            levelValue = levelValue,
            valueRange = Constants.POSITION_ZERO..maxLevel,
            hapticsViewModelFactory = viewModel.hapticsViewModelFactory,
            onLevelChanged = { viewModel.setFlashlightLevel(it) },
        )
    }
}

private object Constants {
    val SliderTrackRoundedCorner = 12.dp
    val ThumbTrackGapSize = 6.dp
    val ThumbHeight = 52.dp
    val ThumbWidth = 4.dp
    val TrackInsideCornerSize = 2.dp
    const val POSITION_ZERO = 0
}
