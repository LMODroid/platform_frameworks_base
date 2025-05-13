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

package com.android.systemui.statusbar.systemstatusicons.ui.viewmodel

import com.android.systemui.common.shared.model.Icon
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.statusbar.systemstatusicons.SystemStatusIconsInCompose
import com.android.systemui.statusbar.systemstatusicons.airplane.ui.viewmodel.AirplaneModeIconViewModel
import com.android.systemui.statusbar.systemstatusicons.bluetooth.ui.viewmodel.BluetoothIconViewModel
import com.android.systemui.statusbar.systemstatusicons.ethernet.ui.viewmodel.EthernetIconViewModel
import com.android.systemui.statusbar.systemstatusicons.ringer.ui.viewmodel.MuteIconViewModel
import com.android.systemui.statusbar.systemstatusicons.ringer.ui.viewmodel.VibrateIconViewModel
import com.android.systemui.statusbar.systemstatusicons.zenmode.ui.viewmodel.ZenModeIconViewModel
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * ViewModel for managing and displaying a list of system status icons.
 *
 * This ViewModel is responsible for orchestrating the display of various system status icons.
 * Exposes a consolidated list of icons.
 */
class SystemStatusIconsViewModel
@AssistedInject
constructor(
    airplaneModeIconViewModelFactory: AirplaneModeIconViewModel.Factory,
    bluetoothIconViewModelFactory: BluetoothIconViewModel.Factory,
    ethernetIconViewModelFactory: EthernetIconViewModel.Factory,
    zenModeIconViewModelFactory: ZenModeIconViewModel.Factory,
    muteIconViewModelFactory: MuteIconViewModel.Factory,
    vibrateIconViewModelFactory: VibrateIconViewModel.Factory,
) : ExclusiveActivatable() {

    init {
        SystemStatusIconsInCompose.expectInNewMode()
    }

    private val airplaneModeIcon by lazy { airplaneModeIconViewModelFactory.create() }
    private val ethernetIcon by lazy { ethernetIconViewModelFactory.create() }
    private val bluetoothIcon by lazy { bluetoothIconViewModelFactory.create() }
    private val zenModeIcon by lazy { zenModeIconViewModelFactory.create() }
    private val muteIcon by lazy { muteIconViewModelFactory.create() }
    private val vibrateIcon by lazy { vibrateIconViewModelFactory.create() }

    private val iconViewModels: List<SystemStatusIconViewModel> by lazy {
        listOf(bluetoothIcon, zenModeIcon, vibrateIcon, muteIcon, ethernetIcon, airplaneModeIcon)
    }

    val icons: List<Icon>
        get() = iconViewModels.mapNotNull { viewModel -> viewModel.icon }

    override suspend fun onActivated(): Nothing {
        coroutineScope {
            launch { zenModeIcon.activate() }
            launch { ethernetIcon.activate() }
            launch { bluetoothIcon.activate() }
            launch { airplaneModeIcon.activate() }
            launch { muteIcon.activate() }
            launch { vibrateIcon.activate() }
        }
        awaitCancellation()
    }

    @AssistedFactory
    interface Factory {
        fun create(): SystemStatusIconsViewModel
    }
}
