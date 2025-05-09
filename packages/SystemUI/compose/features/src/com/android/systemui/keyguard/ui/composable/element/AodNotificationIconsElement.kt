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
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.systemui.common.ui.ConfigurationState
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.ui.composable.blueprint.rememberBurnIn
import com.android.systemui.keyguard.ui.composable.modifier.burnInAware
import com.android.systemui.keyguard.ui.viewmodel.AodBurnInViewModel
import com.android.systemui.keyguard.ui.viewmodel.KeyguardClockViewModel
import com.android.systemui.keyguard.ui.viewmodel.KeyguardRootViewModel
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.statusbar.notification.icon.ui.viewbinder.AlwaysOnDisplayNotificationIconViewStore
import com.android.systemui.statusbar.notification.icon.ui.viewbinder.NotificationIconContainerViewBinder
import com.android.systemui.statusbar.notification.icon.ui.viewbinder.StatusBarIconViewBindingFailureTracker
import com.android.systemui.statusbar.notification.icon.ui.viewmodel.NotificationIconContainerAlwaysOnDisplayViewModel
import com.android.systemui.statusbar.phone.NotificationIconContainer
import com.android.systemui.statusbar.ui.SystemBarUtilsState
import com.android.systemui.util.ui.isAnimating
import com.android.systemui.util.ui.stopAnimating
import com.android.systemui.util.ui.value
import javax.inject.Inject
import kotlinx.coroutines.launch

@SysUISingleton
class AodNotificationIconsElement
@Inject
constructor(
    private val aodBurnInViewModel: AodBurnInViewModel,
    private val keyguardRootViewModel: KeyguardRootViewModel,
    @ShadeDisplayAware private val configurationState: ConfigurationState,
    private val iconBindingFailureTracker: StatusBarIconViewBindingFailureTracker,
    private val nicAodViewModel: NotificationIconContainerAlwaysOnDisplayViewModel,
    private val nicAodIconViewStore: AlwaysOnDisplayNotificationIconViewStore,
    private val systemBarUtilsState: SystemBarUtilsState,
    private val keyguardClockViewModel: KeyguardClockViewModel,
) {

    @Composable
    fun AodNotificationIcons(modifier: Modifier = Modifier) {
        val isVisible by
            keyguardRootViewModel.isNotifIconContainerVisible.collectAsStateWithLifecycle()
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
            enter = fadeIn(),
            exit = fadeOut(),
            modifier =
                modifier
                    .height(dimensionResource(R.dimen.notification_shelf_height))
                    .burnInAware(aodBurnInViewModel, burnIn.parameters),
        ) {
            val scope = rememberCoroutineScope()
            AndroidView(
                factory = { context ->
                    NotificationIconContainer(context, null).also { nic ->
                        scope.launch {
                            NotificationIconContainerViewBinder.bind(
                                nic,
                                nicAodViewModel,
                                configurationState,
                                systemBarUtilsState,
                                iconBindingFailureTracker,
                                nicAodIconViewStore,
                            )
                        }
                    }
                }
            )
        }
    }
}
