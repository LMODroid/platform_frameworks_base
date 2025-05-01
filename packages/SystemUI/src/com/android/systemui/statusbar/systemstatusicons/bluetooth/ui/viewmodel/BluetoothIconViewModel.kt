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

package com.android.systemui.statusbar.systemstatusicons.bluetooth.ui.viewmodel

import androidx.compose.runtime.getValue
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.res.R
import com.android.systemui.statusbar.policy.bluetooth.domain.interactor.BluetoothConnectionStatusInteractor
import com.android.systemui.statusbar.systemstatusicons.SystemStatusIconsInCompose
import com.android.systemui.statusbar.systemstatusicons.ui.viewmodel.SystemStatusIconViewModel
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.map

/**
 * View model for the bluetooth connected system status icon. Emits a bluetooth connected icon when
 * a bluetooth device is connected. Null icon otherwise.
 */
class BluetoothIconViewModel
@AssistedInject
constructor(interactor: BluetoothConnectionStatusInteractor) :
    SystemStatusIconViewModel, ExclusiveActivatable() {
    init {
        SystemStatusIconsInCompose.expectInNewMode()
    }

    private val hydrator = Hydrator("BluetoothIconViewModel.hydrator")

    override val icon: Icon? by
        hydrator.hydratedStateOf(
            traceName = null,
            initialValue = null,
            source = interactor.isBluetoothConnected.map { it.toUiState() },
        )

    override suspend fun onActivated(): Nothing {
        hydrator.activate()
    }

    private fun Boolean.toUiState(): Icon? =
        if (this) {
            Icon.Resource(
                res = R.drawable.ic_bluetooth_connected,
                contentDescription =
                    ContentDescription.Resource(R.string.accessibility_bluetooth_connected),
            )
        } else {
            null
        }

    @AssistedFactory
    interface Factory {
        fun create(): BluetoothIconViewModel
    }
}
