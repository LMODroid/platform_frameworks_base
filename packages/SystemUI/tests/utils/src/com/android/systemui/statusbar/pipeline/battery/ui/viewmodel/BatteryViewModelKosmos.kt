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

package com.android.systemui.statusbar.pipeline.battery.ui.viewmodel

import android.content.testableContext
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.statusbar.pipeline.battery.domain.interactor.batteryInteractor

val Kosmos.batteryViewModelBasedOnSetting by
    Kosmos.Fixture { BatteryViewModel.BasedOnUserSetting(batteryInteractor, testableContext) }

val Kosmos.batteryViewModelBasedOnSettingFactory by
    Kosmos.Fixture {
        BatteryViewModel.BasedOnUserSetting.Factory { batteryViewModelBasedOnSetting }
    }

val Kosmos.batteryViewModelShowWhenChargingOrSetting by
    Kosmos.Fixture {
        BatteryViewModel.ShowPercentWhenChargingOrSetting(batteryInteractor, testableContext)
    }

val Kosmos.batteryViewModelShowWhenChargingOrSettingFactory by
    Kosmos.Fixture {
        BatteryViewModel.ShowPercentWhenChargingOrSetting.Factory {
            batteryViewModelShowWhenChargingOrSetting
        }
    }

val Kosmos.batteryViewModelAlwaysShowPercent by
    Kosmos.Fixture { BatteryViewModel.AlwaysShowPercent(batteryInteractor, testableContext) }

val Kosmos.batteryViewModelAlwaysShowPercentFactory by
    Kosmos.Fixture {
        BatteryViewModel.AlwaysShowPercent.Factory { batteryViewModelAlwaysShowPercent }
    }

val Kosmos.batteryWithPercentViewModel by
    Kosmos.Fixture { BatteryNextToPercentViewModel(batteryInteractor, testableContext) }

val Kosmos.batteryWithPercentViewModelFactory by
    Kosmos.Fixture {
        object : BatteryNextToPercentViewModel.Factory {
            override fun create(): BatteryNextToPercentViewModel = batteryWithPercentViewModel
        }
    }
