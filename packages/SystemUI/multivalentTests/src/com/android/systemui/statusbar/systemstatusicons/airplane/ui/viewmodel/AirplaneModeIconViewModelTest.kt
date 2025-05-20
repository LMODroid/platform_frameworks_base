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

package com.android.systemui.statusbar.systemstatusicons.airplane.ui.viewmodel

import android.content.testableContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.res.R
import com.android.systemui.statusbar.pipeline.airplane.data.repository.airplaneModeRepository
import com.android.systemui.statusbar.pipeline.shared.data.model.ConnectivitySlot
import com.android.systemui.statusbar.pipeline.shared.data.repository.connectivityRepository
import com.android.systemui.statusbar.pipeline.shared.data.repository.fake
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class AirplaneModeIconViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val fakeConnectivityRepository = kosmos.connectivityRepository.fake
    private val expectedAirplaneIcon =
        Icon.Resource(
            res = com.android.internal.R.drawable.ic_qs_airplane,
            contentDescription = ContentDescription.Resource(R.string.accessibility_airplane_mode),
        )

    private val underTest =
        kosmos.airplaneModeIconViewModelFactory.create(kosmos.testableContext).apply {
            activateIn(kosmos.testScope)
        }

    @Test
    fun icon_notAirplaneMode_outputsNull() =
        kosmos.runTest {
            fakeConnectivityRepository.setForceHiddenIcons(setOf())
            airplaneModeRepository.setIsAirplaneMode(false)

            assertThat(underTest.icon).isNull()
        }

    @Test
    fun icon_forceHidden_outputsNull() =
        kosmos.runTest {
            fakeConnectivityRepository.setForceHiddenIcons(setOf(ConnectivitySlot.AIRPLANE))
            airplaneModeRepository.setIsAirplaneMode(true)

            assertThat(underTest.icon).isNull()
        }

    @Test
    fun icon_isAirplaneModeAndNotForceHidden_outputsIcon() =
        kosmos.runTest {
            fakeConnectivityRepository.setForceHiddenIcons(setOf())
            airplaneModeRepository.setIsAirplaneMode(true)

            val expectedIcon =
                Icon.Resource(
                    res = com.android.internal.R.drawable.ic_qs_airplane,
                    contentDescription =
                        ContentDescription.Resource(R.string.accessibility_airplane_mode),
                )
            assertThat(underTest.icon).isEqualTo(expectedIcon)
        }

    @Test
    fun icon_updatesWhenAirplaneModeChanges() =
        kosmos.runTest {
            fakeConnectivityRepository.setForceHiddenIcons(setOf())

            // Start not in airplane mode
            airplaneModeRepository.setIsAirplaneMode(false)
            assertThat(underTest.icon).isNull()

            // Turn on airplane mode
            airplaneModeRepository.setIsAirplaneMode(true)

            assertThat(underTest.icon).isEqualTo(expectedAirplaneIcon)

            // Turn off airplane mode
            airplaneModeRepository.setIsAirplaneMode(false)
            assertThat(underTest.icon).isNull()
        }

    @Test
    fun icon_updatesWhenForceHiddenChanges() =
        kosmos.runTest {
            airplaneModeRepository.setIsAirplaneMode(true)

            // Start not hidden
            fakeConnectivityRepository.setForceHiddenIcons(setOf())

            assertThat(underTest.icon).isEqualTo(expectedAirplaneIcon)

            // Force hide
            fakeConnectivityRepository.setForceHiddenIcons(setOf(ConnectivitySlot.AIRPLANE))
            assertThat(underTest.icon).isNull()

            // Un-hide
            fakeConnectivityRepository.setForceHiddenIcons(setOf())
            assertThat(underTest.icon).isEqualTo(expectedAirplaneIcon)
        }
}
