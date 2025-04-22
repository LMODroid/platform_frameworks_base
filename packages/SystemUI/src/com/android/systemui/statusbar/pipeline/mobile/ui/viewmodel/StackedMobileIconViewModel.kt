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

package com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.statusbar.pipeline.mobile.domain.model.SignalIconModel
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.StackedMobileIconViewModel.DualSim
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

interface StackedMobileIconViewModel {
    val dualSim: DualSim?
    val contentDescription: String?
    val networkTypeIcon: Icon.Resource?
    val isIconVisible: Boolean

    data class DualSim(
        val primary: SignalIconModel.Cellular,
        val secondary: SignalIconModel.Cellular,
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class StackedMobileIconViewModelImpl
@AssistedInject
constructor(
    mobileIconsViewModel: MobileIconsViewModel,
    @ShadeDisplayAware private val context: Context,
) : ExclusiveActivatable(), StackedMobileIconViewModel {
    private val hydrator = Hydrator("StackedMobileIconViewModel")

    private val iconViewModelFlow: Flow<List<MobileIconViewModelCommon>> =
        combine(
            mobileIconsViewModel.mobileSubViewModels,
            mobileIconsViewModel.activeMobileDataSubscriptionId,
        ) { viewModels, activeSubId ->
            // Sort to get the active subscription first, if it's set
            viewModels.sortedByDescending { it.subscriptionId == activeSubId }
        }

    private val _dualSim: Flow<DualSim?> =
        iconViewModelFlow.flatMapLatest { viewModels ->
            combine(viewModels.map { it.icon }) { icons ->
                icons
                    .toList()
                    .filterIsInstance<SignalIconModel.Cellular>()
                    .takeIf { it.size == 2 }
                    ?.let { DualSim(it[0], it[1]) }
            }
        }

    private val _isIconVisible: Flow<Boolean> =
        combine(_dualSim, mobileIconsViewModel.isStackable) { dualSim, isStackable ->
            dualSim != null && isStackable
        }

    override val dualSim: DualSim? by
        hydrator.hydratedStateOf(traceName = "dualSim", source = _dualSim, initialValue = null)

    /** Content description of both icons, starting with the active connection. */
    override val contentDescription: String? by
        hydrator.hydratedStateOf(
            traceName = "contentDescription",
            source =
                flowIfIconIsVisible(
                    iconViewModelFlow.flatMapLatest { viewModels ->
                        combine(viewModels.map { it.contentDescription }) { contentDescriptions ->
                                contentDescriptions.map { it?.loadContentDescription(context) }
                            }
                            .map { loadedStrings ->
                                // Only provide the content description if both icons have one
                                if (loadedStrings.any { it == null }) {
                                    null
                                } else {
                                    // The content description of each icon has the format:
                                    // "[Carrier name], N bars."
                                    // To combine, we simply join them with a space
                                    loadedStrings.joinToString(" ")
                                }
                            }
                    }
                ),
            initialValue = null,
        )

    override val networkTypeIcon: Icon.Resource? by
        hydrator.hydratedStateOf(
            traceName = "networkTypeIcon",
            source =
                flowIfIconIsVisible(
                    iconViewModelFlow.flatMapLatest { viewModels ->
                        viewModels.firstOrNull()?.networkTypeIcon ?: flowOf(null)
                    }
                ),
            initialValue = null,
        )

    override val isIconVisible: Boolean by
        hydrator.hydratedStateOf(
            traceName = "isIconVisible",
            source = _isIconVisible,
            initialValue = false,
        )

    private fun <T> flowIfIconIsVisible(flow: Flow<T>): Flow<T?> {
        return _isIconVisible.flatMapLatest { isVisible ->
            if (isVisible) {
                flow
            } else {
                flowOf(null)
            }
        }
    }

    override suspend fun onActivated(): Nothing {
        hydrator.activate()
    }

    @AssistedFactory
    interface Factory {
        fun create(): StackedMobileIconViewModelImpl
    }
}
