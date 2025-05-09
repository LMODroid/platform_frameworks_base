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

import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.compose.animation.scene.ContentScope
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.ui.composable.modifier.burnInAware
import com.android.systemui.keyguard.ui.viewmodel.AodBurnInViewModel
import com.android.systemui.keyguard.ui.viewmodel.BurnInParameters
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.notifications.ui.composable.ConstrainedNotificationStack
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout
import com.android.systemui.statusbar.notification.stack.ui.view.NotificationScrollView
import com.android.systemui.statusbar.notification.stack.ui.view.SharedNotificationContainer
import com.android.systemui.statusbar.notification.stack.ui.viewbinder.SharedNotificationContainerBinder
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.NotificationsPlaceholderViewModel
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.SharedNotificationContainerViewModel
import dagger.Lazy
import javax.inject.Inject

@SysUISingleton
class NotificationElement
@Inject
constructor(
    private val stackScrollView: Lazy<NotificationScrollView>,
    private val viewModelFactory: NotificationsPlaceholderViewModel.Factory,
    private val aodBurnInViewModel: AodBurnInViewModel,
    sharedNotificationContainer: SharedNotificationContainer,
    sharedNotificationContainerViewModel: SharedNotificationContainerViewModel,
    stackScrollLayout: NotificationStackScrollLayout,
    sharedNotificationContainerBinder: SharedNotificationContainerBinder,
) {

    init {
        // This scene container section moves the NSSL to the SharedNotificationContainer.
        // This also requires that SharedNotificationContainer gets moved to the
        // SceneWindowRootView by the SceneWindowRootViewBinder. Prior to Scene Container,
        // NSSL is moved into this container by the NotificationStackScrollLayoutSection.
        // Ensure stackScrollLayout is a child of sharedNotificationContainer.

        if (stackScrollLayout.parent != sharedNotificationContainer) {
            (stackScrollLayout.parent as? ViewGroup)?.removeView(stackScrollLayout)
            sharedNotificationContainer.addNotificationStackScrollLayout(stackScrollLayout)
        }

        sharedNotificationContainerBinder.bind(
            sharedNotificationContainer,
            sharedNotificationContainerViewModel,
        )
    }

    /**
     * @param burnInParams params to make this view adaptive to burn-in, `null` to disable burn-in
     *   adjustment
     */
    @Composable
    fun ContentScope.Notifications(
        areNotificationsVisible: Boolean,
        burnInParams: BurnInParameters?,
        modifier: Modifier = Modifier,
    ) {
        if (!areNotificationsVisible) {
            return
        }

        ConstrainedNotificationStack(
            stackScrollView = stackScrollView.get(),
            viewModel = rememberViewModel("Notifications") { viewModelFactory.create() },
            modifier =
                modifier.fillMaxWidth().let {
                    if (burnInParams == null) {
                        it
                    } else {
                        it.burnInAware(viewModel = aodBurnInViewModel, params = burnInParams)
                    }
                },
        )
    }
}
