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

package com.android.systemui.statusbar.systemstatusicons.wifi.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.statusbar.pipeline.wifi.ui.model.WifiIcon
import com.android.systemui.statusbar.pipeline.wifi.ui.viewmodel.WifiViewModel
import com.android.systemui.statusbar.systemstatusicons.SystemStatusIconsInCompose
import com.android.systemui.statusbar.systemstatusicons.ui.viewmodel.SystemStatusIconViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.map

/**
 * View model for the wifi system status icon. Emits a wifi icon when wifi is enabled and should be
 * shown. This viewModel is active when [SystemStatusIconsInCompose] is enabled and replaces
 * [LocationBasedWifiViewModel].
 */
class WifiIconViewModel
@AssistedInject
constructor(@Assisted private val context: Context, wifiViewModel: WifiViewModel) :
    SystemStatusIconViewModel, ExclusiveActivatable() {

    init {
        SystemStatusIconsInCompose.expectInNewMode()
    }

    private val hydrator = Hydrator("WifiIconViewModel.hydrator")

    override val slotName = context.getString(com.android.internal.R.string.status_bar_wifi)

    override val icon: Icon? by
        hydrator.hydratedStateOf(
            traceName = "SystemStatus.wifiIcon",
            initialValue = null,
            source = wifiViewModel.wifiIcon.map { it.toSystemStatusIcon() },
        )

    private fun WifiIcon.toSystemStatusIcon(): Icon? {
        return when (this) {
            is WifiIcon.Hidden -> null
            is WifiIcon.Visible -> this.icon
        }
    }

    override suspend fun onActivated(): Nothing {
        hydrator.activate()
    }

    @AssistedFactory
    interface Factory {
        fun create(context: Context): WifiIconViewModel
    }
}
