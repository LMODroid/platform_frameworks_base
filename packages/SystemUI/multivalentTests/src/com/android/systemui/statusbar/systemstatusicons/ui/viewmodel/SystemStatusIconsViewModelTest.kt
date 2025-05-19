/*
 * Copyright (C) 2025 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.systemstatusicons.ui.viewmodel

import android.app.AlarmManager
import android.app.AutomaticZenRule
import android.app.PendingIntent
import android.bluetooth.BluetoothProfile
import android.content.testableContext
import android.media.AudioManager
import android.platform.test.annotations.EnableFlags
import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.settingslib.bluetooth.CachedBluetoothDevice
import com.android.settingslib.notification.modes.TestModeBuilder
import com.android.settingslib.volume.shared.model.RingerMode
import com.android.systemui.SysuiTestCase
import com.android.systemui.display.data.repository.display
import com.android.systemui.display.data.repository.displayRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.statusbar.core.NewStatusBarIcons
import com.android.systemui.statusbar.pipeline.airplane.data.repository.airplaneModeRepository
import com.android.systemui.statusbar.pipeline.shared.data.repository.connectivityRepository
import com.android.systemui.statusbar.pipeline.shared.data.repository.fake
import com.android.systemui.statusbar.pipeline.wifi.data.repository.fakeWifiRepository
import com.android.systemui.statusbar.pipeline.wifi.shared.model.WifiNetworkModel
import com.android.systemui.statusbar.policy.bluetooth.data.repository.bluetoothRepository
import com.android.systemui.statusbar.policy.data.repository.fakeZenModeRepository
import com.android.systemui.statusbar.policy.fakeHotspotController
import com.android.systemui.statusbar.policy.fakeNextAlarmController
import com.android.systemui.statusbar.systemstatusicons.SystemStatusIconsInCompose
import com.android.systemui.statusbar.systemstatusicons.data.repository.statusBarConfigIconSlotNames
import com.android.systemui.testKosmos
import com.android.systemui.volume.data.repository.fakeAudioRepository
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@EnableFlags(SystemStatusIconsInCompose.FLAG_NAME, NewStatusBarIcons.FLAG_NAME)
@SmallTest
@RunWith(AndroidJUnit4::class)
class SystemStatusIconsViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val underTest by lazy {
        kosmos.systemStatusIconsViewModelFactory.create(kosmos.testableContext).apply {
            activateIn(kosmos.testScope)
        }
    }

    private lateinit var slotAirplane: String
    private lateinit var slotBluetooth: String
    private lateinit var slotConnectedDisplay: String
    private lateinit var slotEthernet: String
    private lateinit var slotHotspot: String
    private lateinit var slotMute: String
    private lateinit var slotNextAlarm: String
    private lateinit var slotVibrate: String
    private lateinit var slotWifi: String
    private lateinit var slotZen: String

    @Before
    fun setUp() {
        slotAirplane = context.getString(com.android.internal.R.string.status_bar_airplane)
        slotBluetooth = context.getString(com.android.internal.R.string.status_bar_bluetooth)
        slotConnectedDisplay =
            context.getString(com.android.internal.R.string.status_bar_connected_display)
        slotEthernet = context.getString(com.android.internal.R.string.status_bar_ethernet)
        slotHotspot = context.getString(com.android.internal.R.string.status_bar_hotspot)
        slotMute = context.getString(com.android.internal.R.string.status_bar_mute)
        slotNextAlarm = context.getString(com.android.internal.R.string.status_bar_alarm_clock)
        slotVibrate = context.getString(com.android.internal.R.string.status_bar_volume)
        slotWifi = context.getString(com.android.internal.R.string.status_bar_wifi)
        slotZen = context.getString(com.android.internal.R.string.status_bar_zen)
    }

    @Test
    fun iconViewModels_initiallyEmpty_whenNoIconIsActive() =
        kosmos.runTest { assertThat(underTest.activeSlotNames).isEmpty() }

    @Test
    fun iconViewModels_oneIconActive_showsOneIcon() =
        kosmos.runTest {
            showAirplaneMode()
            assertThat(underTest.activeSlotNames).containsExactly(slotAirplane)
        }

    @Test
    fun iconViewModels_multipleIconsActive_orderIsRespected() =
        kosmos.runTest {
            val customOrder = arrayOf(slotBluetooth, slotZen, slotAirplane)
            statusBarConfigIconSlotNames = (customOrder)

            showZenMode()
            showBluetooth()
            showAirplaneMode()

            assertThat(underTest.activeSlotNames)
                .containsExactly(slotBluetooth, slotZen, slotAirplane)
                .inOrder()
        }

    @Test
    fun iconViewModels_multipleIconsActive_someOrderedSomeNot_unorderedFirstThenOrdered() =
        kosmos.runTest {
            val customOrder = arrayOf(slotMute, slotBluetooth, slotZen)
            statusBarConfigIconSlotNames = customOrder

            showAirplaneMode()
            showBluetooth()
            showEthernet()
            showVibrate()
            showZenMode()

            assertThat(underTest.activeSlotNames)
                .containsExactly(slotAirplane, slotEthernet, slotVibrate, slotBluetooth, slotZen)
                .inOrder()
        }

    @Test
    fun iconViewModels_updatesWhenIndividualIconStateChanges_becomesActive() =
        kosmos.runTest {
            statusBarConfigIconSlotNames = arrayOf(slotAirplane)

            assertThat(underTest.activeSlotNames).isEmpty()

            showAirplaneMode()

            assertThat(underTest.activeSlotNames).containsExactly(slotAirplane)
        }

    @Test
    fun iconViewModels_updatesWhenIndividualIconStateChanges_becomesInactive() =
        kosmos.runTest {
            statusBarConfigIconSlotNames = arrayOf(slotAirplane)
            showAirplaneMode()

            assertThat(underTest.activeSlotNames).containsExactly(slotAirplane)

            hideAirplaneMode()
            assertThat(underTest.activeSlotNames).isEmpty()
        }

    @Test
    fun iconViewModels_orderWithNonExistentSlots_ignoresNonExistent() =
        kosmos.runTest {
            showBluetooth()
            showAirplaneMode()

            val customOrder =
                arrayOf("non_existent_slot_1", slotBluetooth, slotAirplane, "non_existent_slot_2")
            statusBarConfigIconSlotNames = customOrder

            // "non_existent_slot_1" and "non_existent_slot_2" are ignored.
            // bluetooth and airplane are ordered according to their appearance in customOrder.
            assertThat(underTest.activeSlotNames)
                .containsExactly(slotBluetooth, slotAirplane)
                .inOrder()
        }

    @Test
    fun iconViewModels_emptyOrderList_allIconsShownInAlphabeticalOrder() =
        kosmos.runTest {
            statusBarConfigIconSlotNames = emptyArray()

            showZenMode()
            showBluetooth()
            showConnectedDisplay()
            showAirplaneMode()
            showNextAlarm()
            showEthernet()
            showVibrate()
            showHotspot()

            assertThat(underTest.activeSlotNames)
                .containsExactly(
                    slotAirplane,
                    slotBluetooth,
                    slotConnectedDisplay,
                    slotEthernet,
                    slotHotspot,
                    slotNextAlarm,
                    slotVibrate,
                    slotZen,
                )
                .inOrder()

            // The [mute,vibrate] and [ethernet, wifi] icons can not be shown at the same time so we
            // have to test it separately.
            showMute() // This will make vibrate inactive
            showWifi() // This will make ethernet inactive

            assertThat(underTest.activeSlotNames)
                .containsExactly(
                    slotAirplane,
                    slotBluetooth,
                    slotConnectedDisplay,
                    slotHotspot,
                    slotMute,
                    slotNextAlarm,
                    slotWifi,
                    slotZen,
                )
                .inOrder()
        }

    private val SystemStatusIconsViewModel.activeSlotNames: List<String>
        get() = this.iconViewModels.filter { it.icon != null }.map { it.slotName }

    private suspend fun Kosmos.showAirplaneMode() {
        airplaneModeRepository.setIsAirplaneMode(true)
    }

    private suspend fun Kosmos.hideAirplaneMode() {
        airplaneModeRepository.setIsAirplaneMode(false)
    }

    private fun Kosmos.showBluetooth() {
        bluetoothRepository.setConnectedDevices(
            listOf(
                mock<CachedBluetoothDevice>().apply {
                    whenever(isConnected).thenReturn(true)
                    whenever(maxConnectionState).thenReturn(BluetoothProfile.STATE_CONNECTED)
                }
            )
        )
    }

    private suspend fun Kosmos.showConnectedDisplay(isSecure: Boolean = false) {
        fakeKeyguardRepository.setKeyguardShowing(!isSecure)
        displayRepository.setDefaultDisplayOff(false)
        val flags = if (isSecure) Display.FLAG_SECURE else 0
        displayRepository.addDisplay(
            display(
                type = Display.TYPE_EXTERNAL,
                flags = flags,
                id = (displayRepository.displays.value.maxOfOrNull { it.displayId } ?: 0) + 1,
            )
        )
    }

    private fun Kosmos.showEthernet() {
        connectivityRepository.fake.setEthernetConnected(default = true, validated = true)
    }

    private fun Kosmos.showMute() {
        fakeAudioRepository.setRingerMode(RingerMode(AudioManager.RINGER_MODE_SILENT))
    }

    private fun Kosmos.showNextAlarm() {
        val alarmClockInfo = AlarmManager.AlarmClockInfo(1L, mock<PendingIntent>())
        fakeNextAlarmController.setNextAlarm(alarmClockInfo)
    }

    private fun Kosmos.showVibrate() {
        fakeAudioRepository.setRingerMode(RingerMode(AudioManager.RINGER_MODE_VIBRATE))
    }

    private fun Kosmos.showWifi() {
        fakeWifiRepository.setIsWifiEnabled(true)
        val testNetwork =
            WifiNetworkModel.Active.of(isValidated = true, level = 4, ssid = "TestWifi")
        fakeWifiRepository.setWifiNetwork(testNetwork)
        connectivityRepository.fake.setWifiConnected()
    }

    private fun Kosmos.showZenMode() {
        val modeId = "zenModeTestRule"
        val modeName = "Test Zen Mode"
        val mode =
            TestModeBuilder()
                .setId(modeId)
                .setName(modeName)
                .setType(AutomaticZenRule.TYPE_DRIVING)
                .setActive(true)
                .build()
        fakeZenModeRepository.clearModes()
        fakeZenModeRepository.addMode(mode)
    }

    private fun Kosmos.showHotspot() {
        fakeHotspotController.isHotspotEnabled = true
    }
}
