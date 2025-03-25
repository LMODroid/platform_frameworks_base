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

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.UserAction
import com.android.compose.animation.scene.UserActionResult
import com.android.compose.theme.PlatformTheme
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.ui.data.repository.fakeConfigurationRepository
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testCase
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.qs.composefragment.dagger.usingMediaInComposeFragment
import com.android.systemui.qs.panels.domain.interactor.iconTilesInteractor
import com.android.systemui.qs.panels.ui.viewmodel.MockTileViewModel
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.ui.viewmodel.fakeQsSceneAdapter
import com.android.systemui.res.R
import com.android.systemui.scene.sceneContainerViewModelFactory
import com.android.systemui.scene.sceneTransitionsBuilder
import com.android.systemui.scene.shared.model.SceneContainerConfig
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.shared.model.sceneDataSourceDelegator
import com.android.systemui.scene.ui.composable.Scene
import com.android.systemui.scene.ui.composable.SceneContainer
import com.android.systemui.scene.ui.view.sceneJankMonitorFactory
import com.android.systemui.testKosmos
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@SmallTest
@RunWith(AndroidJUnit4::class)
class PaginatedGridLayoutTest : SysuiTestCase() {
    @get:Rule val composeRule = createComposeRule()

    private val kosmos =
        testKosmos().useUnconfinedTestDispatcher().apply {
            usingMediaInComposeFragment = false
            testCase.context.orCreateTestableResources.addOverride(
                R.integer.quick_settings_infinite_grid_num_columns,
                4,
            )
        }
    private val Kosmos.sceneKeys by Fixture { listOf(Scenes.QuickSettings) }
    private val Kosmos.initialSceneKey by Fixture { Scenes.QuickSettings }
    private val Kosmos.sceneContainerConfig by Fixture {
        val navigationDistances = mapOf(Scenes.Lockscreen to 1)
        SceneContainerConfig(
            sceneKeys,
            initialSceneKey,
            emptyList(),
            navigationDistances,
            sceneTransitionsBuilder,
        )
    }
    private val transitionState by lazy {
        MutableStateFlow<ObservableTransitionState>(
            ObservableTransitionState.Idle(kosmos.sceneContainerConfig.initialSceneKey)
        )
    }
    private val view = mock<View>()
    private val sceneContainerViewModel by lazy {
        kosmos.sceneContainerViewModelFactory
            .create(view) {}
            .apply { setTransitionState(transitionState) }
    }
    private val underTest: PaginatedGridLayout = kosmos.paginatedGridLayout

    private class FakeQuickSettings(
        val gridLayout: PaginatedGridLayout,
        tiles: List<TileViewModel> = TestTiles,
    ) : ExclusiveActivatable(), Scene {
        override val key: SceneKey = Scenes.QuickSettings
        override val userActions: Flow<Map<UserAction, UserActionResult>> = flowOf()
        var tiles: List<TileViewModel> by mutableStateOf(tiles)

        @Composable
        override fun ContentScope.Content(modifier: Modifier) {
            with(gridLayout) { TileGrid(tiles, Modifier) { true } }
        }

        override suspend fun onActivated() = awaitCancellation()
    }

    @Test
    fun tileGrid_showsOnePage() =
        kosmos.runTest {
            composeRule.setContent {
                PlatformTheme {
                    SceneContainer(
                        viewModel = rememberViewModel("test") { sceneContainerViewModel },
                        sceneByKey = mapOf(Scenes.QuickSettings to FakeQuickSettings(underTest)),
                        initialSceneKey = Scenes.QuickSettings,
                        transitionsBuilder = kosmos.sceneTransitionsBuilder,
                        overlayByKey = mapOf(),
                        dataSourceDelegator = kosmos.sceneDataSourceDelegator,
                        qsSceneAdapter = { kosmos.fakeQsSceneAdapter },
                        sceneJankMonitorFactory = kosmos.sceneJankMonitorFactory,
                    )
                }
            }
            composeRule.awaitIdle()

            // Assert that only one page is showing, with 4 rows and 4 columns = 16 tiles
            composeRule.assertOnePageIsDisplayed(pageSize = 16)
        }

    @Test
    fun tileGrid_changingColumns_recreatesPages() =
        kosmos.runTest {
            composeRule.setContent {
                PlatformTheme {
                    SceneContainer(
                        viewModel = rememberViewModel("test") { sceneContainerViewModel },
                        sceneByKey = mapOf(Scenes.QuickSettings to FakeQuickSettings(underTest)),
                        initialSceneKey = Scenes.QuickSettings,
                        transitionsBuilder = kosmos.sceneTransitionsBuilder,
                        overlayByKey = mapOf(),
                        dataSourceDelegator = kosmos.sceneDataSourceDelegator,
                        qsSceneAdapter = { kosmos.fakeQsSceneAdapter },
                        sceneJankMonitorFactory = kosmos.sceneJankMonitorFactory,
                    )
                }
            }
            composeRule.awaitIdle()

            // Assert that only one page is showing, with 4 rows and 4 columns = 16 tiles
            composeRule.assertOnePageIsDisplayed(pageSize = 16)

            setResourceOverride(2, R.integer.quick_settings_infinite_grid_num_columns)
            composeRule.awaitIdle()

            // Assert that only one page is showing, with 4 rows and 2 columns = 8 tiles
            composeRule.assertOnePageIsDisplayed(pageSize = 8)
        }

    @Test
    fun tileGrid_changingSizes_recreatesPages() =
        kosmos.runTest {
            composeRule.setContent {
                PlatformTheme {
                    SceneContainer(
                        viewModel = rememberViewModel("test") { sceneContainerViewModel },
                        sceneByKey = mapOf(Scenes.QuickSettings to FakeQuickSettings(underTest)),
                        initialSceneKey = Scenes.QuickSettings,
                        transitionsBuilder = kosmos.sceneTransitionsBuilder,
                        overlayByKey = mapOf(),
                        dataSourceDelegator = kosmos.sceneDataSourceDelegator,
                        qsSceneAdapter = { kosmos.fakeQsSceneAdapter },
                        sceneJankMonitorFactory = kosmos.sceneJankMonitorFactory,
                    )
                }
            }
            composeRule.awaitIdle()

            // Assert that only one page is showing, with 4 rows and 4 columns = 16 tiles
            composeRule.assertOnePageIsDisplayed(pageSize = 16)

            // Set tiles with even spec large
            iconTilesInteractor.setLargeTiles(
                TestTiles.filterIndexed { index, _ -> index % 2 == 0 }.map { it.spec }.toSet()
            )
            composeRule.awaitIdle()

            // Assert that only one page is showing, with 4 rows and 4 columns AND tiles with an
            // even tileSpec resized to large. The grid should be:
            // [ large 0 ] [ 1 ] [x]
            // [ large 2 ] [ 3 ] [x]
            // [ large 4 ] [ 5 ] [x]
            // [ large 6 ] [ 7 ] [x]
            // For a total of 8 tiles, with 4 spacers
            composeRule.assertOnePageIsDisplayed(pageSize = 8)
        }

    @Test
    fun tileGrid_changingTiles_recreatesPages() =
        kosmos.runTest {
            val quickSettings = FakeQuickSettings(underTest)
            composeRule.setContent {
                PlatformTheme {
                    SceneContainer(
                        viewModel = rememberViewModel("test") { sceneContainerViewModel },
                        sceneByKey = mapOf(Scenes.QuickSettings to quickSettings),
                        initialSceneKey = Scenes.QuickSettings,
                        transitionsBuilder = kosmos.sceneTransitionsBuilder,
                        overlayByKey = mapOf(),
                        dataSourceDelegator = kosmos.sceneDataSourceDelegator,
                        qsSceneAdapter = { kosmos.fakeQsSceneAdapter },
                        sceneJankMonitorFactory = kosmos.sceneJankMonitorFactory,
                    )
                }
            }
            composeRule.awaitIdle()

            // Assert that only one page is showing, with 4 rows and 4 columns = 16 tiles
            composeRule.assertOnePageIsDisplayed(pageSize = 16)

            // Remove tiles
            quickSettings.tiles = emptyList()
            composeRule.awaitIdle()

            // Assert that no tiles is showing
            composeRule.assertOnePageIsDisplayed(pageSize = 0)
        }

    private fun ComposeContentTestRule.assertOnePageIsDisplayed(pageSize: Int) {
        // HorizontalPager will compose tiles off screen from adjacent pages.
        onAllNodesWithTestTags(TestTags).apply {
            fetchSemanticsNodes().forEachIndexed { index, _ ->
                if (index < pageSize) {
                    get(index).assertIsDisplayed()
                } else {
                    get(index).assertIsNotDisplayed()
                }
            }
        }
    }

    private fun setResourceOverride(value: Int, id: Int) =
        with(kosmos) {
            testCase.context.orCreateTestableResources.addOverride(id, value)
            fakeConfigurationRepository.onConfigurationChange()
        }

    private companion object {
        const val SMALL_TILE_TEST_TAG = "com.android.systemui:id/qs_tile_small"
        const val LARGE_TILE_TEST_TAG = "com.android.systemui:id/qs_tile_large"
        val TestTags: Set<String> = setOf(SMALL_TILE_TEST_TAG, LARGE_TILE_TEST_TAG)
        val TestTiles: List<TileViewModel> = buildList {
            repeat(32) { add(MockTileViewModel(TileSpec.create("$it"))) }
        }
    }
}
