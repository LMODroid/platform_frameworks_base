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

package com.android.systemui.qs.panels.ui.compose.infinitegrid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.util.fastMap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.animation.scene.ContentScope
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.grid.ui.compose.VerticalSpannedGrid
import com.android.systemui.haptics.msdl.qs.TileHapticsViewModelFactoryProvider
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.qs.panels.shared.model.SizedTileImpl
import com.android.systemui.qs.panels.ui.compose.EditTileListState
import com.android.systemui.qs.panels.ui.compose.PaginatableGridLayout
import com.android.systemui.qs.panels.ui.compose.TileListener
import com.android.systemui.qs.panels.ui.compose.bounceableInfo
import com.android.systemui.qs.panels.ui.viewmodel.BounceableTileViewModel
import com.android.systemui.qs.panels.ui.viewmodel.DetailsViewModel
import com.android.systemui.qs.panels.ui.viewmodel.EditTileViewModel
import com.android.systemui.qs.panels.ui.viewmodel.IconTilesViewModel
import com.android.systemui.qs.panels.ui.viewmodel.InfiniteGridViewModel
import com.android.systemui.qs.panels.ui.viewmodel.PaginatableGridViewModel
import com.android.systemui.qs.panels.ui.viewmodel.TextFeedbackContentViewModel
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.shared.ui.ElementKeys.toElementKey
import com.android.systemui.res.R
import javax.inject.Inject

@SysUISingleton
class InfiniteGridLayout
@Inject
constructor(
    override val viewModelFactory: InfiniteGridViewModel.Factory,
    private val detailsViewModel: DetailsViewModel,
    private val iconTilesViewModel: IconTilesViewModel,
    private val textFeedbackContentViewModelFactory: TextFeedbackContentViewModel.Factory,
    private val tileHapticsViewModelFactoryProvider: TileHapticsViewModelFactoryProvider,
) : PaginatableGridLayout {

    @Composable
    override fun ContentScope.TileGrid(
        tiles: List<TileViewModel>,
        modifier: Modifier,
        listening: () -> Boolean,
    ) {
        TileGridPage(
            rememberViewModel(traceName = "InfiniteGridLayout.TileGrid") {
                viewModelFactory.create()
            },
            tiles,
            modifier,
            listening,
        )
    }

    @Composable
    override fun ContentScope.TileGridPage(
        viewModel: PaginatableGridViewModel,
        tiles: List<TileViewModel>,
        modifier: Modifier,
        listening: () -> Boolean,
    ) {
        if (viewModel !is InfiniteGridViewModel) return

        val context = LocalContext.current
        val textFeedbackViewModel =
            rememberViewModel(traceName = "InfiniteGridLayout.TileGrid", context) {
                textFeedbackContentViewModelFactory.create(context)
            }

        val columns = viewModel.columnsWithMediaViewModel.columns
        val largeTiles by viewModel.iconTilesViewModel.largeTilesState
        val largeTilesSpan by viewModel.iconTilesViewModel.largeTilesSpanState
        // Tiles or largeTiles may be updated while this is composed, so listen to any changes
        val sizedTiles =
            remember(tiles, largeTiles, largeTilesSpan) {
                tiles.map {
                    SizedTileImpl(it, if (largeTiles.contains(it.spec)) largeTilesSpan else 1)
                }
            }
        val bounceables =
            remember(sizedTiles) { List(sizedTiles.size) { BounceableTileViewModel() } }
        val squishiness by viewModel.squishinessViewModel.squishiness.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        val spans by remember(sizedTiles) { derivedStateOf { sizedTiles.fastMap { it.width } } }

        VerticalSpannedGrid(
            columns = columns,
            columnSpacing = dimensionResource(R.dimen.qs_tile_margin_horizontal),
            rowSpacing = dimensionResource(R.dimen.qs_tile_margin_vertical),
            spans = spans,
            keys = { sizedTiles[it].tile.spec },
        ) { spanIndex, column, isFirstInColumn, isLastInColumn ->
            val it = sizedTiles[spanIndex]

            Element(it.tile.spec.toElementKey(spanIndex), Modifier) {
                Tile(
                    tile = it.tile,
                    iconOnly = !largeTiles.contains(it.tile.spec),
                    squishiness = { squishiness },
                    tileHapticsViewModelFactoryProvider = tileHapticsViewModelFactoryProvider,
                    coroutineScope = scope,
                    bounceableInfo =
                        bounceables.bounceableInfo(
                            it,
                            index = spanIndex,
                            column = column,
                            columns = columns,
                            isFirstInRow = isFirstInColumn,
                            isLastInRow = isLastInColumn,
                        ),
                    detailsViewModel = detailsViewModel,
                    isVisible = listening,
                    requestToggleTextFeedback = textFeedbackViewModel::requestShowFeedback,
                )
            }
        }

        TileListener(tiles, listening)
    }

    @Composable
    override fun EditTileGrid(
        tiles: List<EditTileViewModel>,
        modifier: Modifier,
        onAddTile: (TileSpec, Int) -> Unit,
        onRemoveTile: (TileSpec) -> Unit,
        onSetTiles: (List<TileSpec>) -> Unit,
        onStopEditing: () -> Unit,
    ) {
        val viewModel =
            rememberViewModel(traceName = "InfiniteGridLayout.EditTileGrid") {
                viewModelFactory.create()
            }
        val columnsViewModel =
            rememberViewModel(traceName = "InfiniteGridLayout.EditTileGrid") {
                viewModel.columnsWithMediaViewModelFactory.createWithoutMediaTracking()
            }
        val columns = columnsViewModel.columns
        val largeTiles by viewModel.iconTilesViewModel.largeTilesState
        val largeTilesSpan by viewModel.iconTilesViewModel.largeTilesSpanState

        val currentTiles = tiles.filter { it.isCurrent }
        val listState =
            remember(columns, largeTilesSpan) {
                EditTileListState(
                    currentTiles,
                    largeTiles,
                    columns = columns,
                    largeTilesSpan = largeTilesSpan,
                )
            }
        LaunchedEffect(currentTiles, largeTiles) { listState.updateTiles(currentTiles, largeTiles) }

        DefaultEditTileGrid(
            listState = listState,
            allTiles = tiles,
            modifier = modifier,
            onAddTile = onAddTile,
            onRemoveTile = onRemoveTile,
            onSetTiles = onSetTiles,
            onResize = iconTilesViewModel::resize,
            onStopEditing = onStopEditing,
            onReset = viewModel::showResetDialog,
        )
    }
}
