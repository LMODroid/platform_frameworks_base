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
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.theme.PlatformTheme
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.qs.panels.ui.compose.infinitegrid.DefaultEditTileGrid
import com.android.systemui.qs.panels.ui.model.GridCell
import com.android.systemui.qs.panels.ui.model.TileGridCell
import com.android.systemui.qs.panels.ui.viewmodel.EditTileViewModel
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.shared.model.TileCategory
import com.android.systemui.res.R
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class ResizingTest : SysuiTestCase() {
    @get:Rule val composeRule = createComposeRule()

    @Composable
    private fun EditTileGridUnderTest(listState: EditTileListState) {
        val largeTilesSpecs = TestLargeTilesSpecs.toMutableSet()
        PlatformTheme {
            DefaultEditTileGrid(
                listState = listState,
                otherTiles = listOf(),
                modifier = Modifier.fillMaxSize(),
                onAddTile = { _, _ -> },
                onRemoveTile = {},
                onSetTiles = {},
                onResize = { spec, toIcon ->
                    if (toIcon) largeTilesSpecs.remove(spec) else largeTilesSpecs.add(spec)
                    listState.updateTiles(TestEditTiles, largeTilesSpecs)
                },
                onStopEditing = {},
                onReset = null,
            )
        }
    }

    @Test
    fun toggleIconTileWithA11yAction_shouldBeLarge() {
        val listState =
            EditTileListState(TestEditTiles, TestLargeTilesSpecs, columns = 4, largeTilesSpan = 2)
        composeRule.setContent { EditTileGridUnderTest(listState) }
        composeRule.waitForIdle()

        composeRule
            .onAllNodesWithText("tileA")
            .onFirst()
            .performCustomAccessibilityActionWithLabel(
                context.getString(R.string.accessibility_qs_edit_toggle_tile_size_action)
            )

        assertTileHasWidth(listState.tiles, "tileA", 2)
    }

    @Test
    fun toggleLargeTileWithA11yAction_shouldBeIcon() {
        val listState =
            EditTileListState(TestEditTiles, TestLargeTilesSpecs, columns = 4, largeTilesSpan = 2)
        composeRule.setContent { EditTileGridUnderTest(listState) }
        composeRule.waitForIdle()

        composeRule
            .onAllNodesWithText("tileD_large")
            .onFirst()
            .performCustomAccessibilityActionWithLabel(
                context.getString(R.string.accessibility_qs_edit_toggle_tile_size_action)
            )

        assertTileHasWidth(listState.tiles, "tileD_large", 1)
    }

    @Test
    fun tapOnIconResizingHandle_shouldBeLarge() {
        val listState =
            EditTileListState(TestEditTiles, TestLargeTilesSpecs, columns = 4, largeTilesSpan = 2)
        composeRule.setContent { EditTileGridUnderTest(listState) }
        composeRule.waitForIdle()

        composeRule
            .onAllNodesWithText("tileA")
            .onFirst()
            .performClick() // Select
            .performTouchInput { // Tap on resizing handle
                click(centerRight)
            }
        composeRule.waitForIdle()

        assertTileHasWidth(listState.tiles, "tileA", 2)
    }

    @Test
    fun tapOnLargeResizingHandle_shouldBeIcon() {
        val listState =
            EditTileListState(TestEditTiles, TestLargeTilesSpecs, columns = 4, largeTilesSpan = 2)
        composeRule.setContent { EditTileGridUnderTest(listState) }
        composeRule.waitForIdle()

        composeRule
            .onAllNodesWithText("tileD_large")
            .onFirst()
            .performClick() // Select
            .performTouchInput { // Tap on resizing handle
                click(centerRight)
            }
        composeRule.waitForIdle()

        assertTileHasWidth(listState.tiles, "tileD_large", 1)
    }

    @Test
    fun resizedIcon_shouldBeLarge() {
        val listState =
            EditTileListState(TestEditTiles, TestLargeTilesSpecs, columns = 4, largeTilesSpan = 2)
        composeRule.setContent { EditTileGridUnderTest(listState) }
        composeRule.waitForIdle()

        composeRule
            .onAllNodesWithText("tileA")
            .onFirst()
            .performClick() // Select
            .performTouchInput { // Resize up
                swipeRight(startX = right, endX = right * 2)
            }
        composeRule.waitForIdle()

        assertTileHasWidth(listState.tiles, "tileA", 2)
    }

    @Test
    fun resizedLarge_shouldBeIcon() {
        val listState =
            EditTileListState(TestEditTiles, TestLargeTilesSpecs, columns = 4, largeTilesSpan = 2)
        composeRule.setContent { EditTileGridUnderTest(listState) }
        composeRule.waitForIdle()

        composeRule
            .onAllNodesWithText("tileD_large")
            .onFirst()
            .performClick() // Select
            .performTouchInput { // Resize down
                swipeLeft()
            }
        composeRule.waitForIdle()

        assertTileHasWidth(listState.tiles, "tileD_large", 1)
    }

    private fun assertTileHasWidth(tiles: List<GridCell>, spec: String, expectedWidth: Int) {
        val tile =
            tiles.find { it is TileGridCell && it.tile.tileSpec.spec == spec } as TileGridCell
        assertThat(tile.width).isEqualTo(expectedWidth)
    }

    companion object {
        private fun createEditTile(tileSpec: String): EditTileViewModel {
            return EditTileViewModel(
                tileSpec = TileSpec.create(tileSpec),
                icon =
                    Icon.Resource(android.R.drawable.star_on, ContentDescription.Loaded(tileSpec)),
                label = AnnotatedString(tileSpec),
                appName = null,
                isCurrent = true,
                availableEditActions = emptySet(),
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
