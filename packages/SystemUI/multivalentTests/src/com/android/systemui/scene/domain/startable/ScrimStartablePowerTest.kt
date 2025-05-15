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

package com.android.systemui.scene.domain.startable

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setScreenPowerState
import com.android.systemui.power.domain.interactor.powerInteractor
import com.android.systemui.power.shared.model.ScreenPowerState
import com.android.systemui.statusbar.phone.scrimController
import com.android.systemui.testKosmos
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableSceneContainer
class ScrimStartablePowerTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    val underTest = kosmos.scrimStartable

    @Before
    fun setUp() {
        underTest.start()
    }

    @Test
    fun onScreenTurnedOn_calledWhenScreenTurnsOn() =
        kosmos.runTest {
            kosmos.powerInteractor.setScreenPowerState(ScreenPowerState.SCREEN_OFF)
            runCurrent()
            clearInvocations(kosmos.scrimController)

            kosmos.powerInteractor.setScreenPowerState(ScreenPowerState.SCREEN_TURNING_ON)
            runCurrent()
            verify(kosmos.scrimController, never()).onScreenTurnedOn()
            verify(kosmos.scrimController, never()).onScreenTurnedOff()

            kosmos.powerInteractor.setScreenPowerState(ScreenPowerState.SCREEN_ON)
            runCurrent()
            verify(kosmos.scrimController).onScreenTurnedOn()
            verify(kosmos.scrimController, never()).onScreenTurnedOff()
        }

    @Test
    fun onScreenTurnedOff_calledWhenScreenTurnsOff() =
        kosmos.runTest {
            kosmos.powerInteractor.setScreenPowerState(ScreenPowerState.SCREEN_ON)
            runCurrent()
            clearInvocations(kosmos.scrimController)

            kosmos.powerInteractor.setScreenPowerState(ScreenPowerState.SCREEN_TURNING_OFF)
            runCurrent()
            verify(kosmos.scrimController, never()).onScreenTurnedOn()
            verify(kosmos.scrimController, never()).onScreenTurnedOff()

            kosmos.powerInteractor.setScreenPowerState(ScreenPowerState.SCREEN_OFF)
            runCurrent()
            verify(kosmos.scrimController, never()).onScreenTurnedOn()
            verify(kosmos.scrimController).onScreenTurnedOff()
        }
}
