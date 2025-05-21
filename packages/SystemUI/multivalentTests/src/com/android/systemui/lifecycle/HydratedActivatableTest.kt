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

package com.android.systemui.lifecycle

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.testScope
import com.android.systemui.testKosmos
import com.android.systemui.ui.viewmodel.FakeHydratedViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class HydratedActivatableTest : SysuiTestCase() {

    @get:Rule val composeRule = createComposeRule()

    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope

    private val underTest = FakeHydratedViewModel()

    @Test
    fun activate() =
        testScope.runTest {
            assertThat(underTest.activationCount).isEqualTo(0)
            assertThat(underTest.cancellationCount).isEqualTo(0)

            underTest.activateIn(testScope)
            runCurrent()
            assertThat(underTest.activationCount).isEqualTo(1)
            assertThat(underTest.cancellationCount).isEqualTo(0)
        }

    @Test
    fun activate_andCancel() =
        testScope.runTest {
            assertThat(underTest.activationCount).isEqualTo(0)
            assertThat(underTest.cancellationCount).isEqualTo(0)

            val job = Job()
            underTest.activateIn(testScope, context = job)
            runCurrent()
            assertThat(underTest.activationCount).isEqualTo(1)
            assertThat(underTest.cancellationCount).isEqualTo(0)

            job.cancel()
            runCurrent()
            assertThat(underTest.activationCount).isEqualTo(1)
            assertThat(underTest.cancellationCount).isEqualTo(1)
        }

    @Test
    fun activate_afterCancellation() =
        testScope.runTest {
            assertThat(underTest.activationCount).isEqualTo(0)
            assertThat(underTest.cancellationCount).isEqualTo(0)

            val job = Job()
            underTest.activateIn(testScope, context = job)
            runCurrent()
            assertThat(underTest.activationCount).isEqualTo(1)
            assertThat(underTest.cancellationCount).isEqualTo(0)

            job.cancel()
            runCurrent()
            assertThat(underTest.activationCount).isEqualTo(1)
            assertThat(underTest.cancellationCount).isEqualTo(1)

            underTest.activateIn(testScope)
            runCurrent()
            assertThat(underTest.activationCount).isEqualTo(2)
            assertThat(underTest.cancellationCount).isEqualTo(1)
        }

    @Test(expected = IllegalStateException::class)
    fun activate_whileActive_throws() =
        testScope.runTest {
            assertThat(underTest.activationCount).isEqualTo(0)
            assertThat(underTest.cancellationCount).isEqualTo(0)

            underTest.activateIn(testScope)
            runCurrent()
            assertThat(underTest.activationCount).isEqualTo(1)
            assertThat(underTest.cancellationCount).isEqualTo(0)

            underTest.activateIn(testScope)
            runCurrent()
        }

    @Test
    fun hydratedStateOf() {
        val keepAliveMutable = mutableStateOf(true)
        val upstreamStateFlow = MutableStateFlow(true)
        val upstreamFlow = upstreamStateFlow.map { !it }
        composeRule.setContent {
            val keepAlive by keepAliveMutable
            if (keepAlive) {
                val viewModel =
                    rememberViewModel("test") {
                        FakeHydratedViewModel(
                            upstreamFlow = upstreamFlow,
                            upstreamStateFlow = upstreamStateFlow,
                        )
                    }

                Column {
                    Text(
                        "upstreamStateFlow=${viewModel.stateBackedByStateFlow}",
                        Modifier.testTag("upstreamStateFlow"),
                    )
                    Text(
                        "upstreamFlow=${viewModel.stateBackedByFlow}",
                        Modifier.testTag("upstreamFlow"),
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule
            .onNode(hasTestTag("upstreamStateFlow"))
            .assertTextEquals("upstreamStateFlow=true")
        composeRule.onNode(hasTestTag("upstreamFlow")).assertTextEquals("upstreamFlow=false")

        composeRule.runOnUiThread { upstreamStateFlow.value = false }
        composeRule.waitForIdle()
        composeRule
            .onNode(hasTestTag("upstreamStateFlow"))
            .assertTextEquals("upstreamStateFlow=false")
        composeRule.onNode(hasTestTag("upstreamFlow")).assertTextEquals("upstreamFlow=true")
    }
}
