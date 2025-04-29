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

package com.android.systemui.qs.ui.viewmodel

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags.FLAG_NOTIFICATION_SHADE_BLUR
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.media.controls.data.repository.mediaFilterRepository
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.qs.composefragment.dagger.usingMediaInComposeFragment
import com.android.systemui.scene.domain.startable.sceneContainerStartable
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.testKosmos
import com.android.systemui.window.data.repository.fakeWindowRootViewBlurRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper
@EnableSceneContainer
class QuickSettingsContainerViewModelTest : SysuiTestCase() {

    private val kosmos =
        testKosmos().useUnconfinedTestDispatcher().apply {
            usingMediaInComposeFragment = false // This is not for the compose fragment
        }
    private val testScope = kosmos.testScope

    private val underTest by lazy {
        kosmos.quickSettingsContainerViewModelFactory.create(supportsBrightnessMirroring = false)
    }

    @Before
    fun setUp() {
        kosmos.sceneContainerStartable.start()
        kosmos.enableDualShade()
        underTest.activateIn(testScope)
    }

    @Test
    fun showMedia_activeMedia_true() =
        testScope.runTest {
            kosmos.mediaFilterRepository.addCurrentUserMediaEntry(MediaData(active = true))

            assertThat(underTest.showMedia).isTrue()
        }

    @Test
    fun showMedia_InactiveMedia_true() =
        testScope.runTest {
            kosmos.mediaFilterRepository.addCurrentUserMediaEntry(MediaData(active = false))

            assertThat(underTest.showMedia).isTrue()
        }

    @Test
    fun showMedia_noMedia_false() =
        testScope.runTest {
            kosmos.mediaFilterRepository.addCurrentUserMediaEntry(MediaData(active = true))
            kosmos.mediaFilterRepository.clearCurrentUserMedia()

            assertThat(underTest.showMedia).isFalse()
        }

    @Test
    @DisableFlags(FLAG_NOTIFICATION_SHADE_BLUR)
    fun transparencyEnabled_shadeBlurFlagOff_isDisabled() =
        testScope.runTest {
            kosmos.fakeWindowRootViewBlurRepository.isBlurSupported.value = true

            assertThat(underTest.isTransparencyEnabled).isFalse()
        }

    @Test
    @EnableFlags(FLAG_NOTIFICATION_SHADE_BLUR)
    fun transparencyEnabled_shadeBlurFlagOn_blurSupported_isEnabled() =
        testScope.runTest {
            kosmos.fakeWindowRootViewBlurRepository.isBlurSupported.value = true

            assertThat(underTest.isTransparencyEnabled).isTrue()
        }

    @Test
    @EnableFlags(FLAG_NOTIFICATION_SHADE_BLUR)
    fun transparencyEnabled_shadeBlurFlagOn_blurUnsupported_isDisabled() =
        testScope.runTest {
            kosmos.fakeWindowRootViewBlurRepository.isBlurSupported.value = false

            assertThat(underTest.isTransparencyEnabled).isFalse()
        }
}
