/*
 * Copyright (C) 2025 The Android Open Source Project
 *
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

package com.android.systemui.statusbar.systemstatusicons.connecteddisplay.ui.viewmodel

import android.content.testableContext
import android.platform.test.annotations.EnableFlags
import android.view.Display.FLAG_SECURE
import android.view.Display.TYPE_EXTERNAL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.display.data.repository.display
import com.android.systemui.display.data.repository.displayRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.res.R
import com.android.systemui.statusbar.systemstatusicons.SystemStatusIconsInCompose
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(SystemStatusIconsInCompose.FLAG_NAME)
class ConnectedDisplayIconViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val keyguardRepository = kosmos.fakeKeyguardRepository

    private val underTest =
        kosmos.connectedDisplayIconViewModelFactory.create(kosmos.testableContext).apply {
            activateIn(kosmos.testScope)
        }

    @Test
    fun icon_displayDisconnected_outputsNull() =
        kosmos.runTest { assertThat(underTest.icon).isNull() }

    @Test
    fun icon_displayConnected_outputsIcon() =
        kosmos.runTest {
            keyguardRepository.setKeyguardShowing(true)
            displayRepository.setDefaultDisplayOff(false)
            displayRepository.addDisplay(display(type = TYPE_EXTERNAL, id = 1))

            assertThat(underTest.icon).isEqualTo(expectedConnectedDisplayIcon)
        }

    @Test
    fun icon_displayConnectedSecure_outputsIcon() =
        kosmos.runTest {
            keyguardRepository.setKeyguardShowing(false)
            displayRepository.setDefaultDisplayOff(false)
            displayRepository.addDisplay(display(type = TYPE_EXTERNAL, flags = FLAG_SECURE, id = 1))

            assertThat(underTest.icon).isEqualTo(expectedConnectedDisplayIcon)
        }

    @Test
    fun icon_updatesWhenDisplayConnectionChanges() =
        kosmos.runTest {
            displayRepository.setDefaultDisplayOff(false)
            assertThat(underTest.icon).isNull()

            keyguardRepository.setKeyguardShowing(true)
            displayRepository.addDisplay(display(type = TYPE_EXTERNAL, id = 1))

            assertThat(underTest.icon).isEqualTo(expectedConnectedDisplayIcon)

            displayRepository.removeDisplay(1)
            assertThat(underTest.icon).isNull()

            keyguardRepository.setKeyguardShowing(false)
            displayRepository.addDisplay(display(type = TYPE_EXTERNAL, flags = FLAG_SECURE, id = 2))
            assertThat(underTest.icon).isEqualTo(expectedConnectedDisplayIcon)

            displayRepository.removeDisplay(2)
            assertThat(underTest.icon).isNull()
        }

    companion object {
        private val expectedConnectedDisplayIcon =
            Icon.Resource(
                res = R.drawable.stat_sys_connected_display,
                contentDescription =
                    ContentDescription.Resource(R.string.connected_display_icon_desc),
            )
    }
}
