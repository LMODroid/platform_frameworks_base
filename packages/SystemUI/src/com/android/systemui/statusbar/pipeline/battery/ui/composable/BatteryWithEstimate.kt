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

package com.android.systemui.statusbar.pipeline.battery.ui.composable

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.statusbar.phone.domain.interactor.IsAreaDark
import com.android.systemui.statusbar.pipeline.battery.ui.viewmodel.BatteryViewModel

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BatteryWithEstimate(
    viewModelFactory: BatteryViewModel.Factory,
    isDark: IsAreaDark,
    showEstimate: Boolean,
    modifier: Modifier = Modifier,
) {
    val viewModel =
        rememberViewModel(traceName = "BatteryWithEstimate") { viewModelFactory.create() }

    val batteryHeight =
        with(LocalDensity.current) { BatteryViewModel.STATUS_BAR_BATTERY_HEIGHT.toDp() }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        UnifiedBattery(
            viewModelFactory = viewModelFactory,
            isDark = isDark,
            modifier =
                Modifier.height(batteryHeight)
                    .align(Alignment.CenterVertically)
                    .aspectRatio(BatteryViewModel.ASPECT_RATIO),
        )
        if (showEstimate) {
            viewModel.batteryTimeRemainingEstimate?.let {
                Spacer(modifier.width(4.dp))
                Text(text = it, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
