/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.systemstatusicons.connecteddisplay.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.display.domain.interactor.ConnectedDisplayInteractor
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.res.R
import com.android.systemui.statusbar.systemstatusicons.SystemStatusIconsInCompose
import com.android.systemui.statusbar.systemstatusicons.ui.viewmodel.SystemStatusIconViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.map

/**
 * View model for the connected display system status icon. Emits a connected display icon when an
 * external display is connected. Null icon otherwise.
 */
class ConnectedDisplayIconViewModel
@AssistedInject
constructor(@Assisted private val context: Context, interactor: ConnectedDisplayInteractor) :
    SystemStatusIconViewModel, ExclusiveActivatable() {
    init {
        SystemStatusIconsInCompose.expectInNewMode()
    }

    private val hydrator = Hydrator("ConnectedDisplayIconViewModel.hydrator")

    override val slotName =
        context.getString(com.android.internal.R.string.status_bar_connected_display)

    override val icon: Icon? by
        hydrator.hydratedStateOf(
            traceName = null,
            initialValue = null,
            source = interactor.connectedDisplayState.map { it.toUiState() },
        )

    override suspend fun onActivated(): Nothing = hydrator.activate()

    private fun ConnectedDisplayInteractor.State.toUiState(): Icon? =
        when (this) {
            ConnectedDisplayInteractor.State.CONNECTED,
            ConnectedDisplayInteractor.State.CONNECTED_SECURE ->
                Icon.Resource(
                    res = R.drawable.stat_sys_connected_display,
                    contentDescription =
                        ContentDescription.Resource(R.string.connected_display_icon_desc),
                )
            ConnectedDisplayInteractor.State.DISCONNECTED -> null
        }

    @AssistedFactory
    interface Factory {
        fun create(context: Context): ConnectedDisplayIconViewModel
    }
}
