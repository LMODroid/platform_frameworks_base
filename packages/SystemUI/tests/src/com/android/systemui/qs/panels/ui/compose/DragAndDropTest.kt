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

package com.android.systemui.qs.panels.ui.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.FlakyTest
import androidx.test.filters.SmallTest
import com.android.compose.theme.PlatformTheme
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.qs.panels.shared.model.SizedTileImpl
import com.android.systemui.qs.panels.ui.compose.infinitegrid.DefaultEditTileGrid
import com.android.systemui.qs.panels.ui.model.TileGridCell
import com.android.systemui.qs.panels.ui.viewmodel.AvailableEditActions
import com.android.systemui.qs.panels.ui.viewmodel.EditTileViewModel
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.shared.model.TileCategory
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@FlakyTest(bugId = 360351805)
@SmallTest
@RunWith(AndroidJUnit4::class)
class DragAndDropTest : SysuiTestCase() {
    @get:Rule val composeRule = createComposeRule()

    // TODO(ostonge): Investigate why drag isn't detected when using performTouchInput
    @Composable
    private fun EditTileGridUnderTest(listState: EditTileListState) {
        PlatformTheme {
            DefaultEditTileGrid(
                listState = listState,
                otherTiles = listOf(),
                modifier = Modifier.fillMaxSize(),
                onAddTile = { _, _ -> },
                onRemoveTile = {},
                onSetTiles = {
                    listState.updateTiles(
                        it.map { tileSpec -> createEditTile(tileSpec.spec) },
                        TestLargeTilesSpecs,
                    )
                },
                onResize = { _, _ -> },
                onStopEditing = {},
                onReset = null,
            )
        }
    }

    @Test
    fun draggedTile_shouldDisappear() {
        val listState =
            EditTileListState(TestEditTiles, TestLargeTilesSpecs, columns = 4, largeTilesSpan = 2)
        composeRule.setContent { EditTileGridUnderTest(listState) }
        composeRule.waitForIdle()

        listState.onStarted(listState.tiles[0] as TileGridCell, DragType.Move)

        // Tile is being dragged, it should be replaced with a placeholder
        composeRule.onNodeWithContentDescription("tileA").assertDoesNotExist()

        // Available tiles should disappear
        composeRule.onNodeWithTag(AVAILABLE_TILES_GRID_TEST_TAG).assertDoesNotExist()

        // Remove drop zone should appear
        composeRule.onNodeWithText("Remove").assertExists()

        // Every other tile should still be in the same order
        composeRule.assertGridContainsExactly(
            CURRENT_TILES_GRID_TEST_TAG,
            listOf("tileB", "tileC", "tileD_large", "tileE"),
        )
    }

    @Test
    fun nonRemovableDraggedTile_removeHeaderShouldNotExist() {
        val nonRemovableTile = createEditTile("tileA", isRemovable = false)
        val listState =
            EditTileListState(
                listOf(nonRemovableTile),
                TestLargeTilesSpecs,
                columns = 4,
                largeTilesSpan = 2,
            )
        composeRule.setContent { EditTileGridUnderTest(listState) }
        composeRule.waitForIdle()

        val sizedTile = SizedTileImpl(nonRemovableTile, width = 1)
        listState.onStarted(sizedTile, DragType.Move)

        // Tile is being dragged, it should be replaced with a placeholder
        composeRule.onNodeWithContentDescription("tileA").assertDoesNotExist()

        // Remove drop zone should not appear
        composeRule.onNodeWithText("Remove").assertDoesNotExist()
    }

    @Test
    fun droppedNonRemovableDraggedTile_shouldStayInGrid() {
        val nonRemovableTile = createEditTile("tileA", isRemovable = false)
        val listState =
            EditTileListState(
                listOf(nonRemovableTile),
                TestLargeTilesSpecs,
                columns = 4,
                largeTilesSpan = 2,
            )
        composeRule.setContent { EditTileGridUnderTest(listState) }
        composeRule.waitForIdle()

        val sizedTile = SizedTileImpl(nonRemovableTile, width = 1)
        listState.onStarted(sizedTile, DragType.Move)

        // Tile is being dragged, it should be replaced with a placeholder
        composeRule.onNodeWithContentDescription("tileA").assertDoesNotExist()

        // Remove drop zone should not appear
        composeRule.onNodeWithText("Remove").assertDoesNotExist()

        // Drop tile outside of the grid
        listState.movedOutOfBounds()
        listState.onDrop()

        // Tile A is still in the grid
        composeRule.assertGridContainsExactly(CURRENT_TILES_GRID_TEST_TAG, listOf("tileA"))
    }

    @Test
    fun draggedTile_shouldChangePosition() {
        val listState =
            EditTileListState(TestEditTiles, TestLargeTilesSpecs, columns = 4, largeTilesSpan = 2)
        composeRule.setContent { EditTileGridUnderTest(listState) }
        composeRule.waitForIdle()

        listState.onStarted(listState.tiles[0] as TileGridCell, DragType.Move)

        // Remove drop zone should appear
        composeRule.onNodeWithText("Remove").assertExists()

        listState.onTargeting(1, false)
        listState.onDrop()

        // Available tiles should re-appear
        composeRule.onNodeWithTag(AVAILABLE_TILES_GRID_TEST_TAG).assertExists()

        // Remove drop zone should disappear
        composeRule.onNodeWithText("Remove").assertDoesNotExist()

        // Tile A and B should swap places
        composeRule.assertGridContainsExactly(
            CURRENT_TILES_GRID_TEST_TAG,
            listOf("tileB", "tileA", "tileC", "tileD_large", "tileE"),
        )
    }

    @Test
    fun draggedTileOut_shouldBeRemoved() {
        val listState =
            EditTileListState(TestEditTiles, TestLargeTilesSpecs, columns = 4, largeTilesSpan = 2)
        composeRule.setContent { EditTileGridUnderTest(listState) }
        composeRule.waitForIdle()

        listState.onStarted(listState.tiles[0] as TileGridCell, DragType.Move)

        // Remove drop zone should appear
        composeRule.onNodeWithText("Remove").assertExists()

        listState.movedOutOfBounds()
        listState.onDrop()

        // Available tiles should re-appear
        composeRule.onNodeWithTag(AVAILABLE_TILES_GRID_TEST_TAG).assertExists()

        // Remove drop zone should disappear
        composeRule.onNodeWithText("Remove").assertDoesNotExist()

        // Tile A is gone
        composeRule.assertGridContainsExactly(
            CURRENT_TILES_GRID_TEST_TAG,
            listOf("tileB", "tileC", "tileD_large", "tileE"),
        )
    }

    @Test
    fun draggedNewTileIn_shouldBeAdded() {
        val listState =
            EditTileListState(TestEditTiles, TestLargeTilesSpecs, columns = 4, largeTilesSpan = 2)
        composeRule.setContent { EditTileGridUnderTest(listState) }
        composeRule.waitForIdle()

        val sizedTile = SizedTileImpl(createEditTile("tile_new", isRemovable = false), width = 1)
        listState.onStarted(sizedTile, DragType.Add)

        // Remove drop zone should appear
        composeRule.onNodeWithText("Remove").assertExists()

        // Insert after tileD, which is at index 4
        // [ a ] [ b ] [ c ] [ empty ]
        // [ tile d ] [ e ]
        listState.onTargeting(4, insertAfter = true)
        listState.onDrop()

        // Available tiles should re-appear
        composeRule.onNodeWithTag(AVAILABLE_TILES_GRID_TEST_TAG).assertExists()

        // Remove drop zone should disappear
        composeRule.onNodeWithText("Remove").assertDoesNotExist()

        // tile_new is added after tileD
        composeRule.assertGridContainsExactly(
            CURRENT_TILES_GRID_TEST_TAG,
            listOf("tileA", "tileB", "tileC", "tileD_large", "tile_new", "tileE"),
        )
    }

    companion object {
        private const val CURRENT_TILES_GRID_TEST_TAG = "CurrentTilesGrid"
        private const val AVAILABLE_TILES_GRID_TEST_TAG = "AvailableTilesGrid"

        private fun createEditTile(
            tileSpec: String,
            isRemovable: Boolean = true,
        ): EditTileViewModel {
            return EditTileViewModel(
                tileSpec = TileSpec.create(tileSpec),
                icon =
                    Icon.Resource(android.R.drawable.star_on, ContentDescription.Loaded(tileSpec)),
                label = AnnotatedString(tileSpec),
                appName = null,
                isCurrent = true,
                availableEditActions =
                    if (isRemovable) setOf(AvailableEditActions.REMOVE) else emptySet(),
                category = TileCategory.UNKNOWN,
            )
        }

        private val TestEditTiles =
            listOf(
                createEditTile("tileA"),
                createEditTile("tileB"),
                createEditTile("tileC"),
                createEditTile("tileD_large"),
                createEditTile("tileE"),
            )
        private val TestLargeTilesSpecs =
            TestEditTiles.filter { it.tileSpec.spec.endsWith("large") }.map { it.tileSpec }.toSet()
    }
}
