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

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.animation.scene.ContentScope
import com.android.compose.modifiers.padding
import com.android.systemui.customization.R as clocksR
import com.android.systemui.keyguard.ui.composable.blueprint.ClockElementKeys.smallClockElementKey
import com.android.systemui.keyguard.ui.composable.modifier.burnInAware
import com.android.systemui.keyguard.ui.composable.modifier.onTopPlacementChanged
import com.android.systemui.keyguard.ui.viewmodel.AodBurnInViewModel
import com.android.systemui.keyguard.ui.viewmodel.BurnInParameters
import com.android.systemui.keyguard.ui.viewmodel.KeyguardClockViewModel
import javax.inject.Inject

/** Provides small clock and large clock composables for the default clock face. */
class SmallClockElement
@Inject
constructor(
    private val viewModel: KeyguardClockViewModel,
    private val aodBurnInViewModel: AodBurnInViewModel,
) : ClockElement() {

    @Composable
    fun ContentScope.SmallClock(
        burnInParams: BurnInParameters,
        onTopChanged: (top: Float?) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        val currentClock by viewModel.currentClock.collectAsStateWithLifecycle()
        val smallTopMargin by
            viewModel.smallClockTopMargin.collectAsStateWithLifecycle(
                viewModel.getSmallClockTopMargin()
            )
        if (currentClock?.smallClock?.view == null) {
            return
        }

        ClockView(
            checkNotNull(currentClock).smallClock.view,
            modifier =
                modifier
                    .height(dimensionResource(clocksR.dimen.small_clock_height))
                    .padding(horizontal = dimensionResource(clocksR.dimen.clock_padding_start))
                    .padding(top = { smallTopMargin })
                    .onTopPlacementChanged(onTopChanged)
                    .burnInAware(viewModel = aodBurnInViewModel, params = burnInParams)
                    .element(smallClockElementKey),
        )
    }
}
