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

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.getValue
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.statusbar.systemstatusicons.SystemStatusIconsInCompose
import com.android.systemui.statusbar.systemstatusicons.airplane.ui.viewmodel.AirplaneModeIconViewModel
import com.android.systemui.statusbar.systemstatusicons.bluetooth.ui.viewmodel.BluetoothIconViewModel
import com.android.systemui.statusbar.systemstatusicons.connecteddisplay.ui.viewmodel.ConnectedDisplayIconViewModel
import com.android.systemui.statusbar.systemstatusicons.domain.interactor.OrderedIconSlotNamesInteractor
import com.android.systemui.statusbar.systemstatusicons.ethernet.ui.viewmodel.EthernetIconViewModel
import com.android.systemui.statusbar.systemstatusicons.ringer.ui.viewmodel.MuteIconViewModel
import com.android.systemui.statusbar.systemstatusicons.ringer.ui.viewmodel.VibrateIconViewModel
import com.android.systemui.statusbar.systemstatusicons.wifi.ui.viewmodel.WifiIconViewModel
import com.android.systemui.statusbar.systemstatusicons.zenmode.ui.viewmodel.ZenModeIconViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.map
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
    @Assisted context: Context,
    orderedIconSlotNamesInteractor: OrderedIconSlotNamesInteractor,
    airplaneModeIconViewModelFactory: AirplaneModeIconViewModel.Factory,
    bluetoothIconViewModelFactory: BluetoothIconViewModel.Factory,
    connectedDisplayIconViewModelFactory: ConnectedDisplayIconViewModel.Factory,
    ethernetIconViewModelFactory: EthernetIconViewModel.Factory,
    muteIconViewModelFactory: MuteIconViewModel.Factory,
    vibrateIconViewModelFactory: VibrateIconViewModel.Factory,
    wifiIconViewModelFactory: WifiIconViewModel.Factory,
    zenModeIconViewModelFactory: ZenModeIconViewModel.Factory,
) : ExclusiveActivatable() {

    init {
        SystemStatusIconsInCompose.expectInNewMode()
    }

    private val hydrator = Hydrator("SystemStatusIcons.hydrator")

    private val airplaneModeIcon by lazy { airplaneModeIconViewModelFactory.create(context) }
    private val bluetoothIcon by lazy { bluetoothIconViewModelFactory.create(context) }
    private val connectedDisplayIcon by lazy {
        connectedDisplayIconViewModelFactory.create(context)
    }
    private val ethernetIcon by lazy { ethernetIconViewModelFactory.create(context) }
    private val muteIcon by lazy { muteIconViewModelFactory.create(context) }
    private val vibrateIcon by lazy { vibrateIconViewModelFactory.create(context) }
    private val wifiIcon by lazy { wifiIconViewModelFactory.create(context) }
    private val zenModeIcon by lazy { zenModeIconViewModelFactory.create(context) }

    private val unOrderedIconViewModels: List<SystemStatusIconViewModel> by lazy {
        listOf(
            airplaneModeIcon,
            bluetoothIcon,
            connectedDisplayIcon,
            ethernetIcon,
            muteIcon,
            vibrateIcon,
            wifiIcon,
            zenModeIcon,
        )
    }

    private val viewModelMap: Map<String, SystemStatusIconViewModel> by lazy {
        unOrderedIconViewModels.associateBy { it.slotName }
    }

    @VisibleForTesting
    val iconViewModels by
        hydrator.hydratedStateOf(
            traceName = "iconViewModels",
            initialValue = emptyList(),
            source =
                orderedIconSlotNamesInteractor.orderedIconSlotNames.map { slotNames ->
                    sortViewModelsBySlotNames(slotNames.toSet())
                },
        )

    val icons
        get() = iconViewModels.mapNotNull { it.icon }

    override suspend fun onActivated(): Nothing {
        coroutineScope {
            launch { hydrator.activate() }

            launch { airplaneModeIcon.activate() }
            launch { bluetoothIcon.activate() }
            launch { connectedDisplayIcon.activate() }
            launch { ethernetIcon.activate() }
            launch { muteIcon.activate() }
            launch { vibrateIcon.activate() }
            launch { wifiIcon.activate() }
            launch { zenModeIcon.activate() }
        }
        awaitCancellation()
    }

    private fun sortViewModelsBySlotNames(
        orderedSlotNames: Set<String>
    ): List<SystemStatusIconViewModel> {
        val orderedViewModels = orderedSlotNames.mapNotNull { slotName -> viewModelMap[slotName] }
        val unorderedViewModels =
            unOrderedIconViewModels.filter { viewModel -> viewModel.slotName !in orderedSlotNames }

        // System Status icons which do not specify an ordered slot name should be placed at the
        // start of the list. The highest priority icon is at the end of the list.
        return unorderedViewModels + orderedViewModels
    }

    @AssistedFactory
    interface Factory {
        fun create(context: Context): SystemStatusIconsViewModel
    }
}
