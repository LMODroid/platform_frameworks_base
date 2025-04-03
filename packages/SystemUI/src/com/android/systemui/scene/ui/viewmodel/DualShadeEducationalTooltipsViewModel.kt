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

package com.android.systemui.scene.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.res.R
import com.android.systemui.scene.domain.interactor.DualShadeEducationInteractor
import com.android.systemui.scene.domain.model.DualShadeEducationModel
import com.android.systemui.statusbar.ui.SystemBarUtilsState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class DualShadeEducationalTooltipsViewModel
@AssistedInject
constructor(
    systemBarUtilsState: SystemBarUtilsState,
    private val interactor: DualShadeEducationInteractor,
    @Assisted private val context: Context,
) : ExclusiveActivatable() {

    private val hydrator = Hydrator("DualShadeEducationalTooltipsViewModel.hydrator")

    private val statusBarHeight: Int by
        hydrator.hydratedStateOf(
            traceName = "statusBarHeight",
            source = systemBarUtilsState.statusBarHeight,
            initialValue = 0,
        )

    /**
     * The tooltip to show, or `null` if none should be shown.
     *
     * Please call [DualShadeEducationalTooltipViewModel.onShown] and
     * [DualShadeEducationalTooltipViewModel.onDismissed] when the tooltip is shown or hidden,
     * respectively.
     */
    val visibleTooltip: DualShadeEducationalTooltipViewModel?
        get() =
            when (interactor.education) {
                DualShadeEducationModel.TooltipForNotificationsShade ->
                    notificationsTooltip(statusBarHeight)
                DualShadeEducationModel.TooltipForQuickSettingsShade ->
                    quickSettingsTooltip(statusBarHeight)
                else -> null
            }

    override suspend fun onActivated(): Nothing = coroutineScope {
        launch { hydrator.activate() }
        awaitCancellation()
    }

    private fun notificationsTooltip(statusBarHeight: Int): DualShadeEducationalTooltipViewModel {
        return object : DualShadeEducationalTooltipViewModel {
            override val text: String =
                context.getString(R.string.dual_shade_educational_tooltip_notifs)

            override val isAlignedToStart: Boolean = true

            override val anchorBottomY: Int = statusBarHeight

            override val onShown: () -> Unit = interactor::recordNotificationsShadeTooltipImpression

            override val onDismissed: () -> Unit = interactor::dismissNotificationsShadeTooltip
        }
    }

    private fun quickSettingsTooltip(statusBarHeight: Int): DualShadeEducationalTooltipViewModel {
        return object : DualShadeEducationalTooltipViewModel {
            override val text: String =
                context.getString(R.string.dual_shade_educational_tooltip_qs)

            override val isAlignedToStart: Boolean = false

            override val anchorBottomY: Int = statusBarHeight

            override val onShown: () -> Unit = interactor::recordQuickSettingsShadeTooltipImpression

            override val onDismissed: () -> Unit = interactor::dismissQuickSettingsShadeTooltip
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(context: Context): DualShadeEducationalTooltipsViewModel
    }
}
