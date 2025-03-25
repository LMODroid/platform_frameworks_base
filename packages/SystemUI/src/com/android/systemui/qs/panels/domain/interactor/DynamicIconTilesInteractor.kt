/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.LogLevel
import com.android.systemui.qs.panels.shared.model.PanelsLog
import com.android.systemui.qs.pipeline.domain.interactor.CurrentTilesInteractor
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.util.kotlin.pairwise
import com.android.systemui.util.kotlin.sample
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach

/** Interactor to resize QS tiles down to icons when removed from the current tiles. */
class DynamicIconTilesInteractor
@AssistedInject
constructor(
    private val iconTilesInteractor: IconTilesInteractor,
    private val currentTilesInteractor: CurrentTilesInteractor,
    @PanelsLog private val logBuffer: LogBuffer,
) : ExclusiveActivatable() {

    override suspend fun onActivated(): Nothing {
        currentTilesInteractor.userAndTiles
            .pairwise()
            .filter { !it.newValue.userChange } // Only compare tile changes for the same user
            .sample(iconTilesInteractor.largeTilesSpecs) { tilesData, largeTilesSpecs ->
                // Only current tiles can be resized, so remove deleted tiles from the set of
                // large tiles
                val deletedTiles = tilesData.previousValue.tiles - tilesData.newValue.tiles.toSet()
                largeTilesSpecs to deletedTiles.toSet()
            }
            .onEach { logChange(it.first, it.second) }
            .collect { (currentLargeTiles, deletedTiles) ->
                val newLargeTiles = currentLargeTiles - deletedTiles
                iconTilesInteractor.setLargeTiles(newLargeTiles)
            }
        awaitCancellation()
    }

    @AssistedFactory
    interface Factory {
        fun create(): DynamicIconTilesInteractor
    }

    private fun logChange(largeSpecs: Set<TileSpec>, deletedSpecs: Set<TileSpec>) {
        logBuffer.log(
            LOG_BUFFER_DELETED_TILES_RESIZED_TAG,
            LogLevel.DEBUG,
            {
                str1 = deletedSpecs.toString()
                str2 = largeSpecs.toString()
            },
            { "Deleted tiles=$str1, current large tiles specs=$str2" },
        )
    }

    private companion object {
        const val LOG_BUFFER_DELETED_TILES_RESIZED_TAG = "DeletedTilesResized"
    }
}
