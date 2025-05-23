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

package com.android.systemui.shade.domain.startable

import android.platform.test.flag.junit.FlagsParameterization
import android.testing.TestableLooper.RunWithLooper
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.SysuiTestCase
import com.android.systemui.authentication.data.repository.fakeAuthenticationRepository
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.flags.parameterizeSceneContainerFlag
import com.android.systemui.keyguard.data.repository.fakeDeviceEntryFingerprintAuthRepository
import com.android.systemui.keyguard.shared.model.SuccessFingerprintAuthenticationStatus
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.shared.model.fakeSceneDataSource
import com.android.systemui.shade.ShadeExpansionChangeEvent
import com.android.systemui.shade.ShadeExpansionListener
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.shade.domain.interactor.enableSplitShade
import com.android.systemui.shade.domain.interactor.shadeMode
import com.android.systemui.shade.domain.interactor.shadeModeInteractor
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.statusbar.notification.stack.notificationStackScrollLayoutController
import com.android.systemui.statusbar.notificationShadeDepthController
import com.android.systemui.statusbar.phone.scrimController
import com.android.systemui.testKosmos
import com.android.systemui.util.mockito.any
import com.android.systemui.util.mockito.mock
import com.android.systemui.util.mockito.whenever
import com.google.common.truth.Truth.assertThat
import kotlin.math.max
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.verify
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWithLooper(setAsMainLooper = true)
@RunWith(ParameterizedAndroidJunit4::class)
class ShadeStartableTest(flags: FlagsParameterization) : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val underTest: ShadeStartable by lazy { kosmos.shadeStartable }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return parameterizeSceneContainerFlag()
        }
    }

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    @Before
    fun setUp() {
        kosmos.shadeExpansionStateManager.addExpansionListener(
            kosmos.notificationShadeDepthController
        )
    }

    @Test
    fun hydrateShadeMode_dualShadeDisabled() =
        kosmos.runTest {
            val shadeMode by collectLastValue(shadeMode)
            val isShadeLayoutWide by collectLastValue(shadeModeInteractor.isShadeLayoutWide)
            underTest.start()

            enableSingleShade()
            assertThat(shadeMode).isEqualTo(ShadeMode.Single)
            assertThat(isShadeLayoutWide).isFalse()

            enableSplitShade()
            assertThat(shadeMode).isEqualTo(ShadeMode.Split)
            assertThat(isShadeLayoutWide).isTrue()

            enableSingleShade()
            assertThat(shadeMode).isEqualTo(ShadeMode.Single)
            assertThat(isShadeLayoutWide).isFalse()
        }

    @Test
    @EnableSceneContainer
    fun hydrateShadeMode_dualShadeEnabled() =
        kosmos.runTest {
            val shadeMode by collectLastValue(shadeMode)
            val isShadeLayoutWide by collectLastValue(shadeModeInteractor.isShadeLayoutWide)
            underTest.start()

            enableDualShade(wideLayout = false)
            assertThat(shadeMode).isEqualTo(ShadeMode.Dual)
            assertThat(isShadeLayoutWide).isFalse()

            enableDualShade(wideLayout = true)
            assertThat(shadeMode).isEqualTo(ShadeMode.Dual)
            assertThat(isShadeLayoutWide).isTrue()

            enableDualShade(wideLayout = false)
            assertThat(shadeMode).isEqualTo(ShadeMode.Dual)
            assertThat(isShadeLayoutWide).isFalse()
        }

    @Test
    @EnableSceneContainer
    fun hydrateShadeExpansionStateManager() =
        kosmos.runTest {
            enableSingleShade()
            val expansionListener = mock<ShadeExpansionListener>()
            var latestChangeEvent: ShadeExpansionChangeEvent? = null
            whenever(expansionListener.onPanelExpansionChanged(any())).thenAnswer {
                latestChangeEvent = it.arguments[0] as ShadeExpansionChangeEvent
                Unit
            }
            shadeExpansionStateManager.addExpansionListener(expansionListener)

            underTest.start()

            fakeAuthenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.Pin)
            fakeDeviceEntryFingerprintAuthRepository.setAuthenticationStatus(
                SuccessFingerprintAuthenticationStatus(0, true)
            )
            val transitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Idle(Scenes.Gone)
                )
            sceneInteractor.setTransitionState(transitionState)

            changeScene(Scenes.Gone, transitionState)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            assertThat(currentScene).isEqualTo(Scenes.Gone)

            assertThat(latestChangeEvent)
                .isEqualTo(
                    ShadeExpansionChangeEvent(fraction = 0f, expanded = false, tracking = false)
                )

            changeScene(Scenes.Shade, transitionState) { progress ->
                assertThat(latestChangeEvent?.fraction).isEqualTo(progress)
                assertThat(notificationShadeDepthController.qsPanelExpansion).isZero()
                assertThat(notificationShadeDepthController.shadeExpansion).isEqualTo(progress)
                assertThat(notificationShadeDepthController.transitionToFullShadeProgress)
                    .isEqualTo(progress)
            }
            assertThat(currentScene).isEqualTo(Scenes.Shade)

            changeScene(Scenes.QuickSettings, transitionState) { progress ->
                assertThat(latestChangeEvent?.fraction).isEqualTo(1 - progress)
                assertThat(notificationShadeDepthController.qsPanelExpansion).isEqualTo(progress)
                assertThat(notificationShadeDepthController.shadeExpansion).isEqualTo(1 - progress)
                assertThat(notificationShadeDepthController.transitionToFullShadeProgress)
                    .isEqualTo(
                        max(
                            notificationShadeDepthController.qsPanelExpansion,
                            notificationShadeDepthController.shadeExpansion,
                        )
                    )
            }
            assertThat(currentScene).isEqualTo(Scenes.QuickSettings)

            changeScene(Scenes.Lockscreen, transitionState) { progress ->
                assertThat(latestChangeEvent?.fraction).isZero()
                assertThat(notificationShadeDepthController.qsPanelExpansion)
                    .isEqualTo(1 - progress)
                assertThat(notificationShadeDepthController.shadeExpansion).isZero()
                assertThat(notificationShadeDepthController.transitionToFullShadeProgress)
                    .isEqualTo(1 - progress)
            }
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
        }

    @Test
    @EnableSceneContainer
    fun hydrateFullWidth_singleShade() =
        kosmos.runTest {
            enableSingleShade()
            underTest.start()

            verify(notificationStackScrollLayoutController).setIsFullWidth(true)
            assertThat(scrimController.clipQsScrim).isFalse()
        }

    @Test
    @EnableSceneContainer
    fun hydrateFullWidth_splitShade() =
        kosmos.runTest {
            enableSplitShade()
            underTest.start()

            verify(notificationStackScrollLayoutController).setIsFullWidth(false)
            assertThat(scrimController.clipQsScrim).isFalse()
        }

    @Test
    @EnableSceneContainer
    fun hydrateFullWidth_dualShade_narrowScreen() =
        kosmos.runTest {
            enableDualShade(wideLayout = false)
            underTest.start()

            verify(notificationStackScrollLayoutController).setIsFullWidth(true)
            assertThat(scrimController.clipQsScrim).isFalse()
        }

    @Test
    @EnableSceneContainer
    fun hydrateFullWidth_dualShade_wideScreen() =
        kosmos.runTest {
            enableDualShade(wideLayout = true)
            underTest.start()

            verify(notificationStackScrollLayoutController).setIsFullWidth(false)
            assertThat(scrimController.clipQsScrim).isFalse()
        }

    private fun Kosmos.changeScene(
        toScene: SceneKey,
        transitionState: MutableStateFlow<ObservableTransitionState>,
        assertDuringProgress: ((progress: Float) -> Unit) = {},
    ) {
        val currentScene by collectLastValue(sceneInteractor.currentScene)
        val progressFlow = MutableStateFlow(0f)
        transitionState.value =
            ObservableTransitionState.Transition(
                fromScene = checkNotNull(currentScene),
                toScene = toScene,
                currentScene = flowOf(checkNotNull(currentScene)),
                progress = progressFlow,
                isInitiatedByUserInput = true,
                isUserInputOngoing = flowOf(true),
            )
        assertDuringProgress(progressFlow.value)

        progressFlow.value = 0.2f
        assertDuringProgress(progressFlow.value)

        progressFlow.value = 0.6f
        assertDuringProgress(progressFlow.value)

        progressFlow.value = 1f
        assertDuringProgress(progressFlow.value)

        transitionState.value = ObservableTransitionState.Idle(toScene)
        fakeSceneDataSource.changeScene(toScene)
        assertDuringProgress(progressFlow.value)

        assertThat(currentScene).isEqualTo(toScene)
    }
}
