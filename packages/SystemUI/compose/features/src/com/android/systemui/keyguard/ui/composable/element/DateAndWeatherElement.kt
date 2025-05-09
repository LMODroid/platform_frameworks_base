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

package com.android.systemui.keyguard.ui.composable.element

import android.widget.FrameLayout
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.systemui.keyguard.ui.viewmodel.KeyguardSmartspaceViewModel
import com.android.systemui.statusbar.lockscreen.LockscreenSmartspaceController
import javax.inject.Inject

class DateAndWeatherElement
@Inject
constructor(
    private val lockscreenSmartspaceController: LockscreenSmartspaceController,
    private val keyguardSmartspaceViewModel: KeyguardSmartspaceViewModel,
) {

    @Composable
    fun DateAndWeather(orientation: Orientation, modifier: Modifier = Modifier) {
        when (orientation) {
            Orientation.Horizontal ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = modifier,
                ) {
                    Date()
                    Weather()
                }

            Orientation.Vertical ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
                    Date()
                    Weather()
                }
        }
    }

    @Composable
    private fun Weather(modifier: Modifier = Modifier) {
        val isVisible by keyguardSmartspaceViewModel.isWeatherVisible.collectAsStateWithLifecycle()
        if (!isVisible) {
            return
        }

        AndroidView(
            factory = { context ->
                FrameLayout(context).apply {
                    addView(
                        lockscreenSmartspaceController
                            .buildAndConnectWeatherView(this, false)
                            .apply {
                                layoutParams =
                                    FrameLayout.LayoutParams(
                                        FrameLayout.LayoutParams.WRAP_CONTENT,
                                        FrameLayout.LayoutParams.WRAP_CONTENT,
                                    )
                            }
                    )
                }
            },
            modifier = modifier,
        )
    }

    @Composable
    private fun Date(modifier: Modifier = Modifier) {
        val isVisible by keyguardSmartspaceViewModel.isDateVisible.collectAsStateWithLifecycle()
        if (!isVisible) {
            return
        }

        AndroidView(
            factory = { context ->
                FrameLayout(context).apply {
                    addView(
                        lockscreenSmartspaceController.buildAndConnectDateView(this, false).apply {
                            layoutParams =
                                FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.WRAP_CONTENT,
                                    FrameLayout.LayoutParams.WRAP_CONTENT,
                                )
                        }
                    )
                }
            },
            modifier = modifier,
        )
    }
}
