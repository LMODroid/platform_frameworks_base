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

package com.android.systemui.scene.domain.interactor

import android.content.pm.UserInfo
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.OverlayKey
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.advanceTimeBy
import com.android.systemui.kosmos.currentValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.scene.domain.model.DualShadeEducationModel
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.shade.domain.interactor.disableDualShade
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.testKosmos
import com.android.systemui.user.data.repository.fakeUserRepository
import com.android.systemui.user.domain.interactor.selectedUserInteractor
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
@EnableSceneContainer
class DualShadeEducationInteractorTest(private val forOverlay: OverlayKey) : SysuiTestCase() {
    private val kosmos = testKosmos()
    private lateinit var underTest: DualShadeEducationInteractor

    @Before
    fun setUp() {
        kosmos.enableDualShade()
        kosmos.fakeUserRepository.setUserInfos(USER_INFOS)
        underTest = kosmos.dualShadeEducationInteractor
        kosmos.runCurrent()
    }

    @Test fun initiallyNull() = kosmos.runTest { assertEducation(DualShadeEducationModel.None) }

    @Test
    fun happyPath() =
        kosmos.runTest {
            val otherOverlay = otherOverlay(forOverlay)

            showOverlay(otherOverlay)
            // No education before the delay.
            assertEducation(DualShadeEducationModel.None)

            // SHOW THE HINT
            advanceTimeBy(DualShadeEducationInteractor.HINT_APPEARANCE_DELAY_MS - 1)
            // No education because didn't wait quite long enough yet.
            assertEducation(DualShadeEducationModel.None)
            advanceTimeBy(1)
            // Expected hint after the full delay.
            assertEducation(expectedHint(forOverlay))

            // SHOW THE TOOLTIP
            advanceTimeBy(DualShadeEducationInteractor.TOOLTIP_APPEARANCE_DELAY_MS - 1)
            // Still showing the hint because didn't wait quite long enough yet.
            assertEducation(expectedHint(forOverlay))
            advanceTimeBy(1)
            // Expected tooltip after the full delay.
            assertEducation(expectedTooltip(forOverlay))

            // UI reports impression and dismissal of the tooltip.
            when (forOverlay) {
                Overlays.NotificationsShade -> {
                    underTest.recordNotificationsShadeTooltipImpression()
                    underTest.dismissNotificationsShadeTooltip()
                }
                Overlays.QuickSettingsShade -> {
                    underTest.recordQuickSettingsShadeTooltipImpression()
                    underTest.dismissQuickSettingsShadeTooltip()
                }
            }
            assertEducation(DualShadeEducationModel.None)

            // Hide and reshow overlay to try and trigger it again it shouldn't show again because
            // it was already shown once.
            hideOverlay(otherOverlay)
            assertEducation(DualShadeEducationModel.None)
            showOverlay(otherOverlay)
            advanceTimeBy(DualShadeEducationInteractor.HINT_APPEARANCE_DELAY_MS)
            // No hint as it was already shown to the user.
            assertEducation(DualShadeEducationModel.None)
            advanceTimeBy(DualShadeEducationInteractor.TOOLTIP_APPEARANCE_DELAY_MS)
            // No hint or tooltip as it was already shown to the user.
            assertEducation(DualShadeEducationModel.None)
        }

    @Test
    fun otherOverlayHiddenBeforeHint_noEducation() =
        kosmos.runTest {
            val otherOverlay = otherOverlay(forOverlay)
            showOverlay(otherOverlay)
            // No tooltip before the delay.
            assertEducation(DualShadeEducationModel.None)

            advanceTimeBy(DualShadeEducationInteractor.HINT_APPEARANCE_DELAY_MS - 1)
            // No hint because didn't wait quite long enough yet.
            assertEducation(DualShadeEducationModel.None)

            // Overlay hidden before the delay elapses.
            hideOverlay(otherOverlay)
            assertEducation(DualShadeEducationModel.None)

            advanceTimeBy(1)
            // Waited the entire delay, but the overlay was already hidden.
            assertEducation(DualShadeEducationModel.None)
            // Even waiting for the tooltip doesn't show anything.
            advanceTimeBy(DualShadeEducationInteractor.TOOLTIP_APPEARANCE_DELAY_MS)
            assertEducation(DualShadeEducationModel.None)
        }

    @Test
    fun otherOverlayHiddenBeforeTooltipDelay_noEducation() =
        kosmos.runTest {
            val otherOverlay = otherOverlay(forOverlay)
            showOverlay(otherOverlay)
            // No tooltip before the delay.
            assertEducation(DualShadeEducationModel.None)

            advanceTimeBy(DualShadeEducationInteractor.HINT_APPEARANCE_DELAY_MS)
            // Showing hint.
            assertEducation(expectedHint(forOverlay))

            advanceTimeBy(DualShadeEducationInteractor.TOOLTIP_APPEARANCE_DELAY_MS - 1)
            // Still showing hint as the tooltip delay isn't over.
            assertEducation(expectedHint(forOverlay))

            // Overlay hidden before the tooltip delay elapses.
            hideOverlay(otherOverlay)
            assertEducation(DualShadeEducationModel.None)

            advanceTimeBy(1)
            // Waited the entire delay, but the overlay was already hidden.
            assertEducation(DualShadeEducationModel.None)
        }

    @Test
    fun notDualShadeMode_noEducation() =
        kosmos.runTest {
            disableDualShade()
            showOverlay(otherOverlay(forOverlay))
            advanceTimeBy(DualShadeEducationInteractor.HINT_APPEARANCE_DELAY_MS)
            assertEducation(DualShadeEducationModel.None)
            advanceTimeBy(DualShadeEducationInteractor.TOOLTIP_APPEARANCE_DELAY_MS)
            assertEducation(DualShadeEducationModel.None)
        }

    @Test
    fun reshowsEducationAndTooltip_afterUserChanged() =
        kosmos.runTest {
            val otherOverlay = otherOverlay(forOverlay)
            showOverlay(otherOverlay)
            advanceTimeBy(DualShadeEducationInteractor.HINT_APPEARANCE_DELAY_MS)
            assertEducation(expectedHint(forOverlay))
            advanceTimeBy(DualShadeEducationInteractor.TOOLTIP_APPEARANCE_DELAY_MS)
            assertEducation(expectedTooltip(forOverlay))
            when (forOverlay) {
                Overlays.NotificationsShade -> {
                    underTest.recordNotificationsShadeTooltipImpression()
                    underTest.dismissNotificationsShadeTooltip()
                }
                Overlays.QuickSettingsShade -> {
                    underTest.recordQuickSettingsShadeTooltipImpression()
                    underTest.dismissQuickSettingsShadeTooltip()
                }
            }
            assertEducation(DualShadeEducationModel.None)
            hideOverlay(otherOverlay)

            selectUser(USER_INFOS[1])

            showOverlay(otherOverlay)
            advanceTimeBy(DualShadeEducationInteractor.HINT_APPEARANCE_DELAY_MS)
            // New user, hint shown again.
            assertEducation(expectedHint(forOverlay))
            advanceTimeBy(DualShadeEducationInteractor.TOOLTIP_APPEARANCE_DELAY_MS)
            // New user, tooltip shown again.
            assertEducation(expectedTooltip(forOverlay))
        }

    /**
     * Returns the complementary overlay for [forOverlay]; the one that, when shown, the tooltip
     * will show for [forOverlay].
     */
    private fun otherOverlay(forOverlay: OverlayKey): OverlayKey {
        return when (forOverlay) {
            Overlays.NotificationsShade -> Overlays.QuickSettingsShade
            Overlays.QuickSettingsShade -> Overlays.NotificationsShade
            else -> error("Test isn't expecting forOverlay of ${forOverlay.debugName}")
        }
    }

    private fun Kosmos.assertEducation(expected: DualShadeEducationModel) {
        runCurrent()
        assertThat(underTest.education).isEqualTo(expected)
    }

    private fun expectedHint(forOverlay: OverlayKey): DualShadeEducationModel {
        return when (forOverlay) {
            Overlays.NotificationsShade -> DualShadeEducationModel.HintForNotificationsShade
            Overlays.QuickSettingsShade -> DualShadeEducationModel.HintForQuickSettingsShade
            else -> DualShadeEducationModel.None
        }
    }

    private fun expectedTooltip(forOverlay: OverlayKey): DualShadeEducationModel {
        return when (forOverlay) {
            Overlays.NotificationsShade -> DualShadeEducationModel.TooltipForNotificationsShade
            Overlays.QuickSettingsShade -> DualShadeEducationModel.TooltipForQuickSettingsShade
            else -> DualShadeEducationModel.None
        }
    }

    private fun Kosmos.showOverlay(overlay: OverlayKey) {
        sceneInteractor.showOverlay(overlay, "")
        assertThat(currentValue(sceneInteractor.currentOverlays)).contains(overlay)
    }

    private fun Kosmos.hideOverlay(overlay: OverlayKey) {
        sceneInteractor.hideOverlay(overlay, "")
        assertThat(currentValue(sceneInteractor.currentOverlays)).doesNotContain(overlay)
    }

    private suspend fun Kosmos.selectUser(userInfo: UserInfo) {
        fakeUserRepository.setSelectedUserInfo(userInfo)
        assertThat(selectedUserInteractor.getSelectedUserId()).isEqualTo(userInfo.id)
    }

    companion object {
        private val USER_INFOS =
            listOf(UserInfo(10, "Initial user", 0), UserInfo(11, "Other user", 0))

        @JvmStatic
        @Parameters(name = "{0}")
        fun testParameters(): List<OverlayKey> {
            return listOf(Overlays.NotificationsShade, Overlays.QuickSettingsShade)
        }
    }
}
