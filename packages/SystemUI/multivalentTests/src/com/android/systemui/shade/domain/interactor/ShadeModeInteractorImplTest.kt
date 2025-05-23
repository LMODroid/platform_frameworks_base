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

package com.android.systemui.shade.domain.interactor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableSceneContainer
class ShadeModeInteractorImplTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val underTest: ShadeModeInteractor by lazy { kosmos.shadeModeInteractor }

    @Test
    fun legacyShadeMode_narrowScreen_singleShade() =
        kosmos.runTest {
            val shadeMode by collectLastValue(underTest.shadeMode)
            enableSingleShade()

            assertThat(shadeMode).isEqualTo(ShadeMode.Single)
        }

    @Test
    fun legacyShadeMode_wideScreen_splitShade() =
        kosmos.runTest {
            val shadeMode by collectLastValue(underTest.shadeMode)
            enableSplitShade()

            assertThat(shadeMode).isEqualTo(ShadeMode.Split)
        }

    @Test
    fun shadeMode_wideScreen_isDual() =
        kosmos.runTest {
            val shadeMode by collectLastValue(underTest.shadeMode)
            enableDualShade(wideLayout = true)

            assertThat(shadeMode).isEqualTo(ShadeMode.Dual)
        }

    @Test
    fun shadeMode_narrowScreen_isDual() =
        kosmos.runTest {
            val shadeMode by collectLastValue(underTest.shadeMode)
            enableDualShade(wideLayout = false)

            assertThat(shadeMode).isEqualTo(ShadeMode.Dual)
        }

    @Test
    fun isDualShade_settingEnabledSceneContainerEnabled_returnsTrue() =
        kosmos.runTest {
            // TODO(b/391578667): Add a test case for user switching once the bug is fixed.
            val shadeMode by collectLastValue(underTest.shadeMode)
            enableDualShade()

            assertThat(shadeMode).isEqualTo(ShadeMode.Dual)
            assertThat(underTest.isDualShade).isTrue()
        }

    @Test
    fun isDualShade_settingDisabled_returnsFalse() =
        kosmos.runTest {
            val shadeMode by collectLastValue(underTest.shadeMode)
            disableDualShade()

            assertThat(shadeMode).isNotEqualTo(ShadeMode.Dual)
            assertThat(underTest.isDualShade).isFalse()
        }
}
