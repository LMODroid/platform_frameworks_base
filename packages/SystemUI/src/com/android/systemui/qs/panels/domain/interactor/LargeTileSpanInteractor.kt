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

package com.android.systemui.qs.panels.domain.interactor

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.qs.panels.data.repository.LargeTileSpanRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@SysUISingleton
class LargeTileSpanInteractor
@Inject
constructor(
    @Application scope: CoroutineScope,
    private val repo: LargeTileSpanRepository,
    columnsInteractor: QSColumnsInteractor,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    val span: StateFlow<Int> =
        repo.useExtraLargeTiles
            .flatMapLatest { useExtraLargeTiles ->
                if (useExtraLargeTiles) {
                    combine(columnsInteractor.columns, repo.tileMaxWidth, ::largeTileWidth)
                } else {
                    flowOf(repo.defaultTileMaxWidth)
                }
            }
            .stateIn(
                scope,
                SharingStarted.WhileSubscribed(),
                initialLargeTileWidth(
                    repo.currentUseExtraLargeTiles,
                    columnsInteractor.columns.value,
                    repo.currentTileMaxWidth,
                ),
            )

    private fun initialLargeTileWidth(
        useExtraLargeTiles: Boolean,
        columns: Int,
        largeTileMaxWidth: Int,
    ): Int {
        return if (useExtraLargeTiles) {
            largeTileWidth(columns, largeTileMaxWidth)
        } else {
            repo.defaultTileMaxWidth
        }
    }

    private fun largeTileWidth(columns: Int, largeTileMaxWidth: Int): Int {
        return if (columns > largeTileMaxWidth) columns / 2 else columns
    }
}
