/*
 * Copyright (C) 2023 The Android Open Source Project
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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.ui.composable.blueprint.rememberBurnIn
import com.android.systemui.keyguard.ui.composable.modifier.burnInAware
import com.android.systemui.keyguard.ui.viewmodel.AodBurnInViewModel
import com.android.systemui.keyguard.ui.viewmodel.KeyguardClockViewModel
import com.android.systemui.keyguard.ui.viewmodel.KeyguardRootViewModel
import com.android.systemui.statusbar.notification.promoted.AODPromotedNotification
import com.android.systemui.statusbar.notification.promoted.PromotedNotificationUi
import com.android.systemui.statusbar.notification.promoted.ui.viewmodel.AODPromotedNotificationViewModel
import com.android.systemui.util.ui.isAnimating
import com.android.systemui.util.ui.stopAnimating
import com.android.systemui.util.ui.value
import javax.inject.Inject

@SysUISingleton
class AodPromotedNotificationAreaElement
@Inject
constructor(
    private val aodBurnInViewModel: AodBurnInViewModel,
    private val keyguardRootViewModel: KeyguardRootViewModel,
    private val aodPromotedNotificationViewModelFactory: AODPromotedNotificationViewModel.Factory,
    private val keyguardClockViewModel: KeyguardClockViewModel,
) {

    @Composable
    fun AodPromotedNotificationArea(modifier: Modifier = Modifier) {
        if (!PromotedNotificationUi.isEnabled) {
            return
        }

        val isVisible by
            keyguardRootViewModel.isAodPromotedNotifVisible.collectAsStateWithLifecycle()
        val transitionState = remember { MutableTransitionState(isVisible.value) }
        LaunchedEffect(key1 = isVisible, key2 = transitionState.isIdle) {
            transitionState.targetState = isVisible.value
            if (isVisible.isAnimating && transitionState.isIdle) {
                isVisible.stopAnimating()
            }
        }
        val burnIn = rememberBurnIn(keyguardClockViewModel)

        AnimatedVisibility(
            visibleState = transitionState,
            enter = if (isVisible.isAnimating) fadeIn() else EnterTransition.None,
            exit = if (isVisible.isAnimating) fadeOut() else ExitTransition.None,
            modifier = modifier.burnInAware(aodBurnInViewModel, burnIn.parameters),
        ) {
            AODPromotedNotification(
                viewModelFactory = aodPromotedNotificationViewModelFactory,
                modifier = Modifier.sysuiResTag("aod_promoted_notification_frame"),
            )
        }
    }
}
