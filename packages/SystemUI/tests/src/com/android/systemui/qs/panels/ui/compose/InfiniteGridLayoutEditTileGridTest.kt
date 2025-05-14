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

package com.android.systemui.qs.panels.ui.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.theme.PlatformTheme
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.qs.panels.data.repository.defaultLargeTilesRepository
import com.android.systemui.qs.panels.domain.interactor.iconTilesInteractor
import com.android.systemui.qs.panels.ui.compose.infinitegrid.infiniteGridLayout
import com.android.systemui.qs.panels.ui.viewmodel.dynamicIconTilesViewModel
import com.android.systemui.qs.panels.ui.viewmodel.editModeViewModel
import com.android.systemui.qs.pipeline.domain.interactor.currentTilesInteractor
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class InfiniteGridLayoutEditTileGridTest : SysuiTestCase() {
    @get:Rule val composeRule = createComposeRule()

    private val kosmos =
        testKosmos().useUnconfinedTestDispatcher().apply {
            currentTilesInteractor.setTiles(TestEditTiles)
            editModeViewModel.startEditing()
        }

    private val Kosmos.underTest by Kosmos.Fixture { infiniteGridLayout }

    @Before
    fun setUp() =
        kosmos.run {
            iconTilesInteractor.setLargeTiles(defaultLargeTilesRepository.defaultLargeTiles)
        }

    @Composable
    private fun TestEditTileGrid() {
        PlatformTheme {
            with(kosmos) {
                val tiles by editModeViewModel.tiles.collectAsState(emptyList())
                underTest.EditTileGrid(
                    tiles = tiles,
                    modifier = Modifier.fillMaxSize(),
                    onAddTile = editModeViewModel::addTile,
                    onRemoveTile = editModeViewModel::removeTile,
                    onSetTiles = editModeViewModel::setTiles,
                    onStopEditing = {},
                )
            }
        }
    }

    @Test
    fun moveTiles_correctlySavesSnapshot() =
        kosmos.runTest {
            composeRule.setContent { TestEditTileGrid() }
            composeRule.waitForIdle()

            val stateOnFirstMove =
                listOf(
                    "bt",
                    "internet",
                    "flashlight",
                    "dnd",
                    "alarm",
                    "airplane",
                    "controls",
                    "wallet",
                    "battery",
                    "cast",
                )
            val stateOnSecondMove =
                listOf(
                    "bt",
                    "internet",
                    "alarm",
                    "flashlight",
                    "dnd",
                    "airplane",
                    "controls",
                    "wallet",
                    "battery",
                    "cast",
                )

            // Perform first move
            // Double tap internet
            composeRule.onNodeWithContentDescription("internet").performTouchInput { doubleClick() }
            // Tap on bt to position internet in its spot
            composeRule.onNodeWithContentDescription("bt").performClick()
            composeRule.waitForIdle()

            // Assert the move happened
            assertThat(currentTilesInteractor.currentTilesSpecs.map { it.spec })
                .containsExactlyElementsIn(stateOnFirstMove)
                .inOrder()

            // Perform second move
            // Double tap alarm
            composeRule.onNodeWithContentDescription("alarm").performTouchInput { doubleClick() }
            // Tap on flashlight to position alarm in its spot
            composeRule.onNodeWithContentDescription("flashlight").performClick()
            composeRule.waitForIdle()

            // Assert the second move happened
            assertThat(currentTilesInteractor.currentTilesSpecs.map { it.spec })
                .containsExactlyElementsIn(stateOnSecondMove)
                .inOrder()

            // Perform first undo
            composeRule.onNodeWithContentDescription("Undo").performClick()
            // Assert we're back to the first move state
            assertThat(currentTilesInteractor.currentTilesSpecs.map { it.spec })
                .containsExactlyElementsIn(stateOnFirstMove)
                .inOrder()

            // Perform second undo
            composeRule.onNodeWithContentDescription("Undo").performClick()
            // Assert we're back to the initial state
            assertThat(currentTilesInteractor.currentTilesSpecs.map { it.spec })
                .containsExactlyElementsIn(TestEditTiles.map { it.spec })
                .inOrder()
        }

    @Test
    fun addTiles_correctlySavesSnapshot() =
        kosmos.runTest {
            val latest by collectLastValue(currentTilesInteractor.currentTiles)
            composeRule.setContent { TestEditTileGrid() }
            composeRule.waitForIdle()

            // Perform first addition, rotation is not current
            composeRule
                .onNodeWithTag(AVAILABLE_TILES_GRID_TEST_TAG)
                .performScrollToNode(hasText("rotation"))
            composeRule.onNodeWithText("rotation").performClick()
            composeRule.waitForIdle()

            // Assert the addition happened
            assertThat(latest!!.find { it.tile.tileSpec == "rotation" }).isNotNull()

            // Perform second addition, mictoggle is not current
            composeRule
                .onNodeWithTag(AVAILABLE_TILES_GRID_TEST_TAG)
                .performScrollToNode(hasText("mictoggle"))
            composeRule.onNodeWithText("mictoggle").performClick()
            composeRule.waitForIdle()

            // Assert the addition happened
            assertThat(latest!!.find { it.tile.tileSpec == "mictoggle" }).isNotNull()

            // Perform first undo
            composeRule.onNodeWithContentDescription("Undo").performClick()
            // Assert that mictoggle is no longer current
            assertThat(latest!!.find { it.tile.tileSpec == "mictoggle" }).isNull()

            // Perform second undo
            composeRule.onNodeWithContentDescription("Undo").performClick()
            // Assert that rotation is no longer current
            assertThat(latest!!.find { it.tile.tileSpec == "rotation" }).isNull()
        }

    @Test
    fun removeTiles_correctlySavesSnapshot() =
        kosmos.runTest {
            val latest by collectLastValue(currentTilesInteractor.currentTiles)
            composeRule.setContent { TestEditTileGrid() }
            composeRule.waitForIdle()

            // Perform first removal.
            composeRule.onNodeWithContentDescription("internet").performTouchInput {
                click(position = topRight)
            }
            composeRule.waitForIdle()

            // Assert the removal happened
            assertThat(latest!!.find { it.tile.tileSpec == "internet" }).isNull()

            // Perform second removal
            composeRule.onNodeWithContentDescription("bt").performTouchInput {
                click(position = topRight)
            }
            composeRule.waitForIdle()

            // Assert the removal happened
            assertThat(latest!!.find { it.tile.tileSpec == "bt" }).isNull()

            // Perform first undo
            composeRule.onNodeWithContentDescription("Undo").performClick()
            // Assert that bluetooth is current
            assertThat(latest!!.find { it.tile.tileSpec == "bt" }).isNotNull()

            // Perform second undo
            composeRule.onNodeWithContentDescription("Undo").performClick()
            // Assert that internet is current
            assertThat(latest!!.find { it.tile.tileSpec == "internet" }).isNotNull()
        }

    @Test
    fun resizeTiles_correctlySavesSnapshot() =
        kosmos.runTest {
            composeRule.setContent { TestEditTileGrid() }
            composeRule.waitForIdle()

            // Resize tileA to large
            composeRule
                .onNodeWithContentDescription("internet")
                .performClick() // Select
                .performTouchInput { // Tap on resizing handle
                    click(centerRight)
                }
            composeRule.waitForIdle()

            // Assert the internet is no longer large
            assertLargeTiles(setOf("bt", "dnd", "cast"))

            // Resize flashlight to large
            composeRule
                .onNodeWithContentDescription("flashlight")
                .performClick() // Select
                .performTouchInput { // Tap on resizing handle
                    click(centerRight)
                }
            composeRule.waitForIdle()

            // Assert the resizing happened
            assertLargeTiles(setOf("bt", "dnd", "cast", "flashlight"))

            // Perform first undo
            composeRule.onNodeWithContentDescription("Undo").performClick()
            assertLargeTiles(setOf("bt", "dnd", "cast"))

            // Perform second undo
            composeRule.onNodeWithContentDescription("Undo").performClick()
            assertLargeTiles(setOf("internet", "bt", "dnd", "cast"))
            assertThat(dynamicIconTilesViewModel.largeTilesState.value.map { it.spec })
                .containsExactly("internet", "bt", "dnd", "cast")
        }

    private fun assertLargeTiles(largeSpecs: Set<String>) =
        kosmos.run {
            assertThat(dynamicIconTilesViewModel.largeTilesState.value.map { it.spec })
                .containsExactlyElementsIn(largeSpecs)
        }

    companion object {
        private const val AVAILABLE_TILES_GRID_TEST_TAG = "AvailableTilesGrid"
        private val TestEditTiles =
            listOf(
                TileSpec.create("internet"),
                TileSpec.create("bt"),
                TileSpec.create("flashlight"),
                TileSpec.create("dnd"),
                TileSpec.create("alarm"),
                TileSpec.create("airplane"),
                TileSpec.create("controls"),
                TileSpec.create("wallet"),
                TileSpec.create("battery"),
                TileSpec.create("cast"),
            )
    }
}
