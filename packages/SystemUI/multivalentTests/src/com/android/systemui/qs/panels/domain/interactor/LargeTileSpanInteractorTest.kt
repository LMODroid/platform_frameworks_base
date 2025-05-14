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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.ui.data.repository.fakeConfigurationRepository
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testCase
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.res.R
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class LargeTileSpanInteractorTest : SysuiTestCase() {
    private val kosmos =
        testKosmos().useUnconfinedTestDispatcher().apply {
            testCase.context.orCreateTestableResources.addOverride(
                R.integer.quick_settings_infinite_grid_num_columns,
                4,
            )
            testCase.context.orCreateTestableResources.addOverride(
                R.integer.quick_settings_infinite_grid_tile_max_width,
                4,
            )
        }
    private val Kosmos.underTest by Kosmos.Fixture { largeTileSpanInteractor }

    @Test
    fun span_normalTiles_ignoreColumns() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.span)
            setExtraLargeTiles(useExtraLargeTiles = false)

            // Not using extra large tiles means that we stay to the default width of 2, regardless
            // of columns
            assertThat(latest).isEqualTo(2)

            setColumns(10)
            assertThat(latest).isEqualTo(2)
        }

    @Test
    fun span_extraLargeTiles_tracksColumns() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.span)
            setExtraLargeTiles(useExtraLargeTiles = true)

            // Using extra large tiles with a max width of 4 means that we change the width to the
            // same as the columns if equal or under 4, otherwise we divide it in half
            assertThat(latest).isEqualTo(4)

            setColumns(2)
            assertThat(latest).isEqualTo(2)

            setColumns(6)
            assertThat(latest).isEqualTo(3)

            setColumns(8)
            assertThat(latest).isEqualTo(4)
        }

    @Test
    fun span_extraLargeTiles_tracksMaxWidth() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.span)
            setExtraLargeTiles(useExtraLargeTiles = true)

            // Using extra large tiles with 4 columns means that we change the width to be 4, unless
            // we're using a max width lower than 4 in which case divide it in half
            assertThat(latest).isEqualTo(4)

            setMaxWidth(3)
            assertThat(latest).isEqualTo(2)

            setMaxWidth(6)
            assertThat(latest).isEqualTo(4)

            setMaxWidth(8)
            assertThat(latest).isEqualTo(4)
        }

    @Test
    fun span_tracksExtraLargeTiles() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.span)

            setExtraLargeTiles(useExtraLargeTiles = false)
            assertThat(latest).isEqualTo(2)

            setExtraLargeTiles(useExtraLargeTiles = true)
            assertThat(latest).isEqualTo(4)
        }

    private fun setExtraLargeTiles(useExtraLargeTiles: Boolean) {
        with(kosmos) {
            val fontScale = if (useExtraLargeTiles) 2f else 1f
            testCase.context.resources.configuration.fontScale = fontScale
            fakeConfigurationRepository.onConfigurationChange()
        }
    }

    private fun setColumns(columns: Int) =
        setValueInConfig(columns, R.integer.quick_settings_infinite_grid_num_columns)

    private fun setMaxWidth(width: Int) =
        setValueInConfig(width, R.integer.quick_settings_infinite_grid_tile_max_width)

    private fun setValueInConfig(value: Int, id: Int) =
        with(kosmos) {
            testCase.context.orCreateTestableResources.addOverride(id, value)
            fakeConfigurationRepository.onConfigurationChange()
        }
}
