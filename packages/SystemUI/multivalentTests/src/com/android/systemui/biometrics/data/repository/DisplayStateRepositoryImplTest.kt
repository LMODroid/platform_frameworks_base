/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.biometrics.data.repository

import android.content.res.Configuration
import android.util.DisplayMetrics
import android.util.Size
import android.view.Display
import android.view.Display.DEFAULT_DISPLAY
import android.view.DisplayInfo
import android.view.Surface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.biometrics.shared.model.DisplayRotation
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.display.data.repository.DeviceStateRepository.DeviceState
import com.android.systemui.display.data.repository.FakeDeviceStateRepository
import com.android.systemui.display.data.repository.FakeDisplayRepository
import com.android.systemui.util.mockito.any
import com.android.systemui.util.mockito.mock
import com.android.systemui.util.mockito.whenever
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.spy

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class DisplayStateRepositoryImplTest : SysuiTestCase() {
    private val display = mock<Display>()
    private val testScope = TestScope(StandardTestDispatcher())
    private val fakeDeviceStateRepository = FakeDeviceStateRepository()
    private val fakeDisplayRepository = FakeDisplayRepository()
    private val configuration = Configuration()

    private lateinit var underTest: DisplayStateRepository

    @Before
    fun setUp() {
        mContext.orCreateTestableResources.addOverride(
            com.android.internal.R.bool.config_reverseDefaultRotation,
            false,
        )

        // Set densityDpi such that pixels and DP are the same; Makes it easier to read and write
        // tests.
        configuration.densityDpi = DisplayMetrics.DENSITY_DEFAULT

        mContext = spy(mContext)
        whenever(mContext.display).thenReturn(display)
        whenever(mContext.resources.configuration).thenReturn(configuration)

        underTest =
            DisplayStateRepositoryImpl(
                testScope.backgroundScope,
                mContext,
                fakeDeviceStateRepository,
                fakeDisplayRepository,
            )
    }

    @Test
    fun updatesIsInRearDisplayMode_whenRearDisplayStateChanges() =
        testScope.runTest {
            val isInRearDisplayMode by collectLastValue(underTest.isInRearDisplayMode)
            runCurrent()

            fakeDeviceStateRepository.emit(DeviceState.FOLDED)
            assertThat(isInRearDisplayMode).isFalse()

            fakeDeviceStateRepository.emit(DeviceState.REAR_DISPLAY)
            assertThat(isInRearDisplayMode).isTrue()
        }

    @Test
    fun updatesCurrentRotation_whenDisplayStateChanges() =
        testScope.runTest {
            val currentRotation by collectLastValue(underTest.currentRotation)
            runCurrent()

            whenever(display.getDisplayInfo(any())).then {
                val info = it.getArgument<DisplayInfo>(0)
                info.rotation = Surface.ROTATION_90
                return@then true
            }

            fakeDisplayRepository.emitDisplayChangeEvent(DEFAULT_DISPLAY)
            assertThat(currentRotation).isEqualTo(DisplayRotation.ROTATION_90)

            whenever(display.getDisplayInfo(any())).then {
                val info = it.getArgument<DisplayInfo>(0)
                info.rotation = Surface.ROTATION_180
                return@then true
            }

            fakeDisplayRepository.emitDisplayChangeEvent(DEFAULT_DISPLAY)
            assertThat(currentRotation).isEqualTo(DisplayRotation.ROTATION_180)
        }

    @Test
    fun updatesCurrentSize_whenDisplayStateChanges() =
        testScope.runTest {
            val currentSize by collectLastValue(underTest.currentDisplaySize)
            runCurrent()

            whenever(display.getDisplayInfo(any())).then {
                val info = it.getArgument<DisplayInfo>(0)
                info.rotation = Surface.ROTATION_0
                info.logicalWidth = 100
                info.logicalHeight = 200
                return@then true
            }
            fakeDisplayRepository.emitDisplayChangeEvent(DEFAULT_DISPLAY)
            assertThat(currentSize).isEqualTo(Size(100, 200))

            whenever(display.getDisplayInfo(any())).then {
                val info = it.getArgument<DisplayInfo>(0)
                info.rotation = Surface.ROTATION_90
                info.logicalWidth = 100
                info.logicalHeight = 200
                return@then true
            }
            fakeDisplayRepository.emitDisplayChangeEvent(DEFAULT_DISPLAY)
            assertThat(currentSize).isEqualTo(Size(200, 100))
        }

    @Test
    fun updatesIsLargeScreen_whenDisplayStateChanges() =
        testScope.runTest {
            val isLargeScreen by collectLastValue(underTest.isLargeScreen)
            runCurrent()

            whenever(display.getDisplayInfo(any())).then {
                val info = it.getArgument<DisplayInfo>(0)
                info.rotation = Surface.ROTATION_0
                info.logicalWidth = 100
                info.logicalHeight = 700
                return@then true
            }
            fakeDisplayRepository.emitDisplayChangeEvent(DEFAULT_DISPLAY)
            assertThat(isLargeScreen).isFalse()

            whenever(display.getDisplayInfo(any())).then {
                val info = it.getArgument<DisplayInfo>(0)
                info.rotation = Surface.ROTATION_0
                info.logicalWidth = 800
                info.logicalHeight = 700
                return@then true
            }
            fakeDisplayRepository.emitDisplayChangeEvent(DEFAULT_DISPLAY)
            assertThat(isLargeScreen).isTrue()
        }

    @Test
    fun updatesIsWideScreen_whenDisplayStateChanges() =
        testScope.runTest {
            val isWideScreen by collectLastValue(underTest.isWideScreen)
            runCurrent()

            whenever(display.getDisplayInfo(any())).then {
                val info = it.getArgument<DisplayInfo>(0)
                info.rotation = Surface.ROTATION_0
                info.logicalWidth = 200
                info.logicalHeight = 700
                return@then true
            }
            fakeDisplayRepository.emitDisplayChangeEvent(DEFAULT_DISPLAY)
            assertThat(isWideScreen).isFalse()

            whenever(display.getDisplayInfo(any())).then {
                val info = it.getArgument<DisplayInfo>(0)
                info.rotation = Surface.ROTATION_90
                info.logicalWidth = 700
                info.logicalHeight = 200
                return@then true
            }
            fakeDisplayRepository.emitDisplayChangeEvent(DEFAULT_DISPLAY)
            assertThat(isWideScreen).isTrue()
        }
}
