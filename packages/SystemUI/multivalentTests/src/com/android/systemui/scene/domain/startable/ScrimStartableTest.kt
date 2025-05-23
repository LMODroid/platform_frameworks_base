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

package com.android.systemui.scene.domain.startable

import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.OverlayKey
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.SysuiTestCase
import com.android.systemui.bouncer.data.repository.fakeKeyguardBouncerRepository
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.keyguard.data.repository.fakeDeviceEntryFingerprintAuthRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.domain.interactor.biometricUnlockInteractor
import com.android.systemui.keyguard.shared.model.BiometricUnlockSource
import com.android.systemui.keyguard.shared.model.DozeStateModel
import com.android.systemui.keyguard.shared.model.DozeTransitionModel
import com.android.systemui.keyguard.shared.model.SuccessFingerprintAuthenticationStatus
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.scene.data.repository.setSceneTransition
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.settings.brightness.domain.interactor.brightnessMirrorShowingInteractor
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.statusbar.domain.interactor.keyguardOcclusionInteractor
import com.android.systemui.statusbar.phone.BiometricUnlockController
import com.android.systemui.statusbar.phone.ScrimState
import com.android.systemui.statusbar.phone.centralSurfaces
import com.android.systemui.statusbar.phone.dozeServiceHost
import com.android.systemui.statusbar.phone.statusBarKeyguardViewManager
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlin.reflect.full.memberProperties
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import platform.test.runner.parameterized.Parameter
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
@EnableSceneContainer
class ScrimStartableTest : SysuiTestCase() {

    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun testSpecs(): List<TestSpec> {
            return listOf(
                TestSpec(
                    id = 0,
                    expectedState = ScrimState.KEYGUARD,
                    Preconditions(
                        isDeviceEntered = false,
                        isAlternateBouncerVisible = true,
                        isTransitioningAwayFromKeyguard = true,
                    ),
                ),
                TestSpec(
                    id = 1,
                    expectedState = null,
                    Preconditions(
                        isDeviceEntered = false,
                        isAlternateBouncerVisible = true,
                        isTransitioningToShadeScene = true,
                    ),
                ),
                TestSpec(
                    id = 2,
                    expectedState = null,
                    Preconditions(
                        isDeviceEntered = false,
                        isAlternateBouncerVisible = true,
                        isTransitioningToShadeOverlay = true,
                    ),
                ),
                TestSpec(
                    id = 3,
                    expectedState = ScrimState.BOUNCER,
                    Preconditions(isDeviceEntered = false, isIdleOnBouncer = true),
                ),
                TestSpec(
                    id = 4,
                    expectedState = ScrimState.BOUNCER_SCRIMMED,
                    Preconditions(
                        isDeviceEntered = false,
                        isIdleOnBouncer = true,
                        isBouncerScrimmingNeeded = true,
                    ),
                ),
                TestSpec(
                    id = 5,
                    expectedState = ScrimState.BRIGHTNESS_MIRROR,
                    Preconditions(isDeviceEntered = false, isBrightnessMirrorVisible = true),
                ),
                TestSpec(
                    id = 6,
                    expectedState = ScrimState.BRIGHTNESS_MIRROR,
                    Preconditions(
                        isDeviceEntered = false,
                        isIdleOnBouncer = true,
                        isBiometricWakeAndUnlock = true,
                        isBrightnessMirrorVisible = true,
                    ),
                ),
                TestSpec(
                    id = 7,
                    expectedState = ScrimState.SHADE_LOCKED,
                    Preconditions(isDeviceEntered = false, isIdleOnShadeScene = true),
                ),
                TestSpec(
                    id = 8,
                    expectedState = ScrimState.SHADE_LOCKED,
                    Preconditions(isDeviceEntered = false, isIdleOnShadeOverlay = true),
                ),
                TestSpec(
                    id = 9,
                    expectedState = ScrimState.PULSING,
                    Preconditions(isDeviceEntered = false, isDozing = true, isPulsing = true),
                ),
                TestSpec(
                    id = 10,
                    expectedState = ScrimState.OFF,
                    Preconditions(isDeviceEntered = false, hasPendingScreenOffCallback = true),
                ),
                TestSpec(
                    id = 11,
                    expectedState = ScrimState.AOD,
                    Preconditions(isDeviceEntered = false, isDozing = true),
                ),
                TestSpec(
                    id = 12,
                    expectedState = ScrimState.GLANCEABLE_HUB,
                    Preconditions(isIdleOnCommunal = true),
                ),
                TestSpec(
                    id = 13,
                    expectedState = ScrimState.GLANCEABLE_HUB_OVER_DREAM,
                    Preconditions(isIdleOnCommunal = true, isDreaming = true),
                ),
                TestSpec(
                    id = 14,
                    expectedState = ScrimState.UNLOCKED,
                    Preconditions(isDeviceEntered = true),
                ),
                TestSpec(
                    id = 15,
                    expectedState = ScrimState.UNLOCKED,
                    Preconditions(isBiometricWakeAndUnlock = true),
                ),
                TestSpec(id = 16, expectedState = ScrimState.KEYGUARD, Preconditions()),
                TestSpec(
                    id = 17,
                    expectedState = ScrimState.DREAMING,
                    Preconditions(isOccluded = true, isDreaming = true),
                ),
                TestSpec(
                    id = 18,
                    expectedState = ScrimState.UNLOCKED,
                    Preconditions(isOccluded = true),
                ),
            )
        }

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            val seenIds = mutableSetOf<Int>()
            testSpecs().forEach { testSpec ->
                assertWithMessage("Duplicate TestSpec id=${testSpec.id}")
                    .that(seenIds)
                    .doesNotContain(testSpec.id)
                seenIds.add(testSpec.id)
            }
        }
    }

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val underTest: ScrimStartable by lazy { kosmos.scrimStartable }

    @JvmField @Parameter(0) var testSpec: TestSpec? = null

    @Before
    fun setUp() {
        kosmos.dozeServiceHost.initialize(
            /* centralSurfaces= */ kosmos.centralSurfaces,
            /* statusBarKeyguardViewManager= */ kosmos.statusBarKeyguardViewManager,
            /* notificationShadeWindowViewController= */ mock(),
            /* ambientIndicationContainer= */ mock(),
        )
        underTest.start()
    }

    @Test
    fun test() =
        kosmos.runTest {
            val observedState by collectLastValue(underTest.scrimState)
            val preconditions = checkNotNull(testSpec).preconditions
            preconditions.assertValid()

            setUpWith(preconditions)

            assertThat(observedState).isEqualTo(checkNotNull(testSpec).expectedState)
        }

    /** Sets up the state to match what's specified in the given [preconditions]. */
    private fun Kosmos.setUpWith(preconditions: Preconditions) {
        whenever(statusBarKeyguardViewManager.primaryBouncerNeedsScrimming())
            .thenReturn(preconditions.isBouncerScrimmingNeeded)

        fakeKeyguardBouncerRepository.setAlternateVisible(preconditions.isAlternateBouncerVisible)

        if (preconditions.isDeviceEntered) {
            fakeDeviceEntryFingerprintAuthRepository.setAuthenticationStatus(
                SuccessFingerprintAuthenticationStatus(0, true)
            )
            whenIdle(on = Scenes.Gone)
        } else {
            whenIdle(on = Scenes.Lockscreen)
        }

        when {
            preconditions.isTransitioningToShadeScene -> {
                enableSingleShade()
                whenTransitioning(from = Scenes.Lockscreen, to = Scenes.Shade)
            }
            preconditions.isTransitioningToShadeOverlay -> {
                enableDualShade()
                whenTransitioning(from = Scenes.Lockscreen, to = Overlays.NotificationsShade)
            }
            preconditions.isTransitioningAwayFromKeyguard ->
                whenTransitioning(from = Scenes.Lockscreen, to = Scenes.Gone)
            preconditions.isIdleOnShadeScene -> {
                enableSingleShade()
                whenIdle(on = Scenes.Shade)
            }
            preconditions.isIdleOnShadeOverlay -> {
                enableDualShade()
                whenIdle(
                    on = if (preconditions.isDeviceEntered) Scenes.Gone else Scenes.Lockscreen,
                    overlays = setOf(Overlays.NotificationsShade),
                )
            }
            preconditions.isIdleOnBouncer ->
                whenIdle(on = Scenes.Lockscreen, overlays = setOf(Overlays.Bouncer))
            preconditions.isIdleOnCommunal -> whenIdle(on = Scenes.Communal)
        }

        keyguardOcclusionInteractor.setWmNotifiedShowWhenLockedActivityOnTop(
            showWhenLockedActivityOnTop = preconditions.isOccluded,
            taskInfo = if (preconditions.isOccluded) mock() else null,
        )

        if (preconditions.isBiometricWakeAndUnlock) {
            biometricUnlockInteractor.setBiometricUnlockState(
                BiometricUnlockController.MODE_WAKE_AND_UNLOCK,
                BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
        }

        brightnessMirrorShowingInteractor.setMirrorShowing(preconditions.isBrightnessMirrorVisible)

        if (preconditions.hasPendingScreenOffCallback) {
            dozeServiceHost.prepareForGentleSleep {}
        } else {
            dozeServiceHost.cancelGentleSleep()
        }

        fakeKeyguardRepository.setIsDozing(preconditions.isDozing)
        if (preconditions.isPulsing) {
            fakeKeyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(to = DozeStateModel.DOZE_PULSING)
            )
        }
        fakeKeyguardRepository.setDreaming(preconditions.isDreaming)
    }

    /** Sets up an idle state on the given [on] scene. */
    private fun Kosmos.whenIdle(on: SceneKey, overlays: Set<OverlayKey> = emptySet()) {
        setSceneTransition(ObservableTransitionState.Idle(on, overlays))
        sceneInteractor.changeScene(on, "")
        for (overlay in overlays) {
            sceneInteractor.showOverlay(overlay, "")
        }
    }

    /** Sets up a transitioning state between the given [from] and [to] scenes. */
    private fun Kosmos.whenTransitioning(from: SceneKey, to: SceneKey, progress: Float = 0.5f) {
        val currentScene = if (progress > 0.5f) to else from
        setSceneTransition(
            ObservableTransitionState.Transition(
                fromScene = from,
                toScene = to,
                progress = flowOf(progress),
                currentScene = flowOf(currentScene),
                isInitiatedByUserInput = true,
                isUserInputOngoing = flowOf(false),
            )
        )
        sceneInteractor.changeScene(currentScene, "")
    }

    /** Sets up a transitioning state between the [from] scene and [to] overlay. */
    private fun Kosmos.whenTransitioning(from: SceneKey, to: OverlayKey, progress: Float = 0.5f) {
        val currentOverlays = if (progress > 0.5f) setOf(to) else emptySet()
        setSceneTransition(
            ObservableTransitionState.Transition.showOverlay(
                overlay = to,
                fromScene = from,
                progress = flowOf(progress),
                currentOverlays = flowOf(currentOverlays),
                isInitiatedByUserInput = true,
                isUserInputOngoing = flowOf(false),
            )
        )
        sceneInteractor.showOverlay(to, "")
    }

    data class Preconditions(
        val isAlternateBouncerVisible: Boolean = false,
        /** Whether any non-shade nor QS scene is transitioning to a shade or QS scene. */
        val isTransitioningToShadeScene: Boolean = false,
        /** Whether any non-shade nor QS scene is transitioning to a shade or QS overlay. */
        val isTransitioningToShadeOverlay: Boolean = false,
        val isOccluded: Boolean = false,
        val isIdleOnBouncer: Boolean = false,
        val isBiometricWakeAndUnlock: Boolean = false,
        /** Whether there's an active transition from lockscreen or bouncer to gone. */
        val isTransitioningAwayFromKeyguard: Boolean = false,
        val isBrightnessMirrorVisible: Boolean = false,
        /** Whether the current scene is a shade or QS scene. */
        val isIdleOnShadeScene: Boolean = false,
        /** Whether the notifications shade or QS shade overlay is in the current overlays. */
        val isIdleOnShadeOverlay: Boolean = false,
        val isDeviceEntered: Boolean = false,
        val isPulsing: Boolean = false,
        val hasPendingScreenOffCallback: Boolean = false,
        val isDozing: Boolean = false,
        val isIdleOnCommunal: Boolean = false,
        val isDreaming: Boolean = false,
        val isBouncerScrimmingNeeded: Boolean = false,
    ) {
        override fun toString(): String {
            // Only include values overridden to true:
            return buildString {
                append("(")
                append(
                    Preconditions::class
                        .memberProperties
                        .filter { it.get(this@Preconditions) == true }
                        .joinToString(", ") { "${it.name}=true" }
                )
                append(")")
            }
        }

        fun assertValid() {
            assertWithMessage("isOccluded cannot be true at the same time as isDeviceEntered")
                .that(!isOccluded || !isDeviceEntered)
                .isTrue()

            assertWithMessage("isIdleOnBouncer cannot be true at the same time as isDeviceEntered")
                .that(!isIdleOnBouncer || !isDeviceEntered)
                .isTrue()

            assertWithMessage("isIdleOnBouncer cannot be true at the same time as isIdleOnCommunal")
                .that(!isIdleOnBouncer || !isIdleOnCommunal)
                .isTrue()

            assertWithMessage(
                    "isIdleOnShadeScene cannot be true at the same time as isIdleOnCommunal"
                )
                .that(!isIdleOnShadeScene || !isIdleOnCommunal)
                .isTrue()

            assertWithMessage(
                    "isIdleOnShadeOverlay cannot be true at the same time as isIdleOnCommunal"
                )
                .that(!isIdleOnShadeOverlay || !isIdleOnCommunal)
                .isTrue()

            assertWithMessage(
                    "isIdleOnShadeScene cannot be true at the same time as isIdleOnShadeOverlay"
                )
                .that(!isIdleOnShadeScene || !isIdleOnShadeOverlay)
                .isTrue()

            assertWithMessage(
                    "isTransitioningToShadeScene cannot be true at the same time as " +
                        "isTransitioningToShadeOverlay"
                )
                .that(!isTransitioningToShadeScene || !isTransitioningToShadeOverlay)
                .isTrue()

            assertWithMessage(
                    "isTransitioningToShadeScene cannot be true at the same time as " +
                        "isIdleOnShadeOverlay"
                )
                .that(!isTransitioningToShadeScene || !isIdleOnShadeOverlay)
                .isTrue()

            assertWithMessage(
                    "isTransitioningToShadeOverlay cannot be true at the same time as " +
                        "isIdleOnShadeScene"
                )
                .that(!isTransitioningToShadeOverlay || !isIdleOnShadeScene)
                .isTrue()

            assertWithMessage("isDeviceEntered cannot be true at the same time as isIdleOnBouncer")
                .that(!isDeviceEntered || !isIdleOnBouncer)
                .isTrue()

            assertWithMessage(
                    "isDeviceEntered cannot be true at the same time as isAlternateBouncerVisible"
                )
                .that(!isDeviceEntered || !isAlternateBouncerVisible)
                .isTrue()

            assertWithMessage("isPulsing cannot be true if both isDozing is false")
                .that(!isPulsing || isDozing)
                .isTrue()
        }
    }

    data class TestSpec(
        val id: Int,
        val expectedState: ScrimState?,
        val preconditions: Preconditions,
    ) {
        override fun toString(): String {
            return "id=$id, expected=$expectedState, preconditions=$preconditions"
        }
    }
}
