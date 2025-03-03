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

package com.android.systemui.flashlight.ui.viewmodel

import androidx.compose.runtime.getValue
import com.android.systemui.flashlight.domain.interactor.FlashlightInteractor
import com.android.systemui.flashlight.shared.logger.FlashlightLogger
import com.android.systemui.flashlight.shared.model.FlashlightModel
import com.android.systemui.haptics.slider.compose.ui.SliderHapticsViewModel
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map

/** View Model for a flashlight slider. Only used when flashlight supports levels. */
class FlashlightSliderViewModel
@AssistedInject
constructor(
    val hapticsViewModelFactory: SliderHapticsViewModel.Factory,
    private val flashlightInteractor: FlashlightInteractor,
    private val logger: FlashlightLogger,
) : ExclusiveActivatable() {
    private val hydrator = Hydrator("FlashlightSliderViewModel.hydrator")

    val currentFlashlightLevel: FlashlightModel.Available.Level? by
        hydrator.hydratedStateOf(
            "currentFlashlightLevel",
            flashlightInteractor.state.value as? FlashlightModel.Available.Level,
            // TODO (b/413736768): disable slider if flashlight becomes un-adjustable mid-slide!
            flashlightInteractor.state.filterIsInstance(FlashlightModel.Available.Level::class),
        )

    private val isFlashlightAdjustable: Boolean by
        hydrator.hydratedStateOf(
            "isFlashlightAdjustable",
            flashlightInteractor.state.value is FlashlightModel.Available.Level,
            flashlightInteractor.state.map { it is FlashlightModel.Available.Level },
        )

    override suspend fun onActivated(): Nothing {
        hydrator.activate()
    }

    fun setFlashlightLevel(value: Int) {
        if (!isFlashlightAdjustable) {
            logger.w(
                "FlashlightSliderViewModel attempted to set level to $value when state was" +
                    " not adjustable"
            )
            return
        }

        if (value == 0) {
            flashlightInteractor.setEnabled(false)
        } else {
            try {
                flashlightInteractor.setLevel(value)
            } catch (ex: IllegalArgumentException) {
                logger.w("FlashlightSliderViewModel#setFlashlightLevel: $ex")
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(): FlashlightSliderViewModel
    }
}
