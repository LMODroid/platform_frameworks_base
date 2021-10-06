/*
 * Copyright (C) 2026 The LibreMobileOS Foundation
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

package com.android.systemui.libremobileos.qs.footer.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.systemui.libremobileos.qs.footer.domain.interactor.QSFooterDataUsageInteractor
import com.android.systemui.lifecycle.ExclusiveActivatable
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** View model for the daily data usage shown in the QS footer. */
class QSFooterDataUsageViewModel
@AssistedInject
constructor(private val interactor: QSFooterDataUsageInteractor) : ExclusiveActivatable() {

    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)

    /** The data usage text to show, or `null` if it shouldn't be shown. */
    var dataUsage: String? by mutableStateOf(null)
        private set

    /** Reloads the data usage, e.g. when QS becomes visible. */
    fun refresh() {
        refreshRequests.trySend(Unit)
    }

    override suspend fun onActivated(): Nothing {
        coroutineScope {
            launch {
                val refreshes = refreshRequests.receiveAsFlow().onStart { emit(Unit) }
                combine(interactor.isEnabled, refreshes) { enabled, _ -> enabled }
                    .collectLatest { enabled ->
                        dataUsage = if (enabled) interactor.getDataUsage() else null
                    }
            }
            awaitCancellation()
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(): QSFooterDataUsageViewModel
    }
}
