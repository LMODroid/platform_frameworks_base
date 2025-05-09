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

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

abstract class ClockElement {

    @Composable
    protected fun ClockView(view: View?, modifier: Modifier = Modifier) {
        AndroidView(
            factory = {
                FrameLayout(it).apply {
                    // Clip nothing. The clock views at times render outside their bounds. Compose
                    // does
                    // not clip by default, so only this layer needs clipping to be explicitly
                    // disabled.
                    clipChildren = false
                    clipToPadding = false
                }
            },
            update = { parent ->
                view?.let {
                    parent.removeAllViews()
                    (view.parent as? ViewGroup)?.removeView(view)
                    parent.addView(view)
                } ?: run { parent.removeAllViews() }
            },
            modifier = modifier,
        )
    }
}
