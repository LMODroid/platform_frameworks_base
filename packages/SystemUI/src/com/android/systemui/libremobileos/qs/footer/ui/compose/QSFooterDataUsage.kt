/*
 * Copyright (C) 2026 The LibreMobileOS Foundation
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

package com.android.systemui.libremobileos.qs.footer.ui.compose

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.android.systemui.libremobileos.qs.footer.ui.viewmodel.QSFooterDataUsageViewModel
import com.android.systemui.lifecycle.rememberViewModel

/** Shows the daily data usage in the QS footer, if enabled. */
@Composable
fun QSFooterDataUsage(
    viewModelFactory: QSFooterDataUsageViewModel.Factory,
    isVisible: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val viewModel =
        rememberViewModel(traceName = "QSFooterDataUsage") { viewModelFactory.create() }

    // Refresh the usage every time QS is shown.
    val visible = isVisible()
    LaunchedEffect(visible) {
        if (visible) {
            viewModel.refresh()
        }
    }

    val dataUsage = viewModel.dataUsage
    if (dataUsage != null) {
        Text(
            text = dataUsage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier =
                modifier
                    .wrapContentWidth()
                    // Keep scrolling while QS is visible so long usage texts can be read. The
                    // marquee restarts every time QS is shown.
                    .then(
                        if (visible) {
                            Modifier.basicMarquee(
                                iterations = Int.MAX_VALUE,
                                initialDelayMillis = 1000,
                            )
                        } else {
                            Modifier
                        }
                    ),
        )
    } else {
        Spacer(modifier)
    }
}
