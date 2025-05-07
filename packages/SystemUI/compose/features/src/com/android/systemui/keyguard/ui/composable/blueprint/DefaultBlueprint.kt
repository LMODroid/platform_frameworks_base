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

package com.android.systemui.keyguard.ui.composable.blueprint

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.compose.animation.scene.ContentScope
import com.android.systemui.keyguard.ui.composable.LockscreenLongPress
import com.android.systemui.keyguard.ui.composable.layout.LockscreenSceneLayout
import com.android.systemui.keyguard.ui.composable.section.AmbientIndicationSection
import com.android.systemui.keyguard.ui.composable.section.BottomAreaSection
import com.android.systemui.keyguard.ui.composable.section.DefaultClockSection
import com.android.systemui.keyguard.ui.composable.section.LockSection
import com.android.systemui.keyguard.ui.composable.section.MediaCarouselSection
import com.android.systemui.keyguard.ui.composable.section.NotificationSection
import com.android.systemui.keyguard.ui.composable.section.SettingsMenuSection
import com.android.systemui.keyguard.ui.composable.section.SmartSpaceSection
import com.android.systemui.keyguard.ui.composable.section.StatusBarSection
import com.android.systemui.keyguard.ui.viewmodel.KeyguardClockViewModel
import com.android.systemui.keyguard.ui.viewmodel.LockscreenContentViewModel
import java.util.Optional
import javax.inject.Inject

/**
 * Renders the lockscreen scene when showing with the default layout (e.g. vertical phone form
 * factor).
 */
class DefaultBlueprint
@Inject
constructor(
    private val statusBarSection: StatusBarSection,
    private val lockSection: LockSection,
    private val ambientIndicationSectionOptional: Optional<AmbientIndicationSection>,
    private val bottomAreaSection: BottomAreaSection,
    private val settingsMenuSection: SettingsMenuSection,
    private val notificationSection: NotificationSection,
    private val clockSection: DefaultClockSection,
    private val keyguardClockViewModel: KeyguardClockViewModel,
    private val smartSpaceSection: SmartSpaceSection,
    private val mediaSection: MediaCarouselSection,
) : ComposableLockscreenSceneBlueprint {

    override val id: String = "default"

    @Composable
    override fun ContentScope.Content(viewModel: LockscreenContentViewModel, modifier: Modifier) {
        val isBypassEnabled = viewModel.isBypassEnabled

        if (isBypassEnabled) {
            with(notificationSection) { HeadsUpNotifications() }
        }

        LockscreenLongPress(
            viewModelFactory = viewModel.touchHandlingFactory,
            modifier = modifier,
        ) { onSettingsMenuPlaced ->
            val burnIn = rememberBurnIn(keyguardClockViewModel)

            LockscreenSceneLayout(
                viewModel = viewModel.layout,
                statusBar = {
                    with(statusBarSection) { StatusBar(modifier = Modifier.fillMaxWidth()) }
                },
                smallClock = {
                    with(clockSection) {
                        SmallClock(
                            burnInParams = burnIn.parameters,
                            onTopChanged = burnIn.onSmallClockTopChanged,
                        )
                    }
                },
                largeClock = {
                    with(clockSection) { LargeClock(burnInParams = burnIn.parameters) }
                },
                dateAndWeather = { orientation ->
                    with(smartSpaceSection) { DateAndWeather(orientation) }
                },
                smartSpace = {
                    with(smartSpaceSection) {
                        SmartSpace(
                            burnInParams = burnIn.parameters,
                            onTopChanged = burnIn.onSmartspaceTopChanged,
                            smartSpacePaddingTop = { 0 },
                        )
                    }
                },
                media = {
                    with(mediaSection) {
                        KeyguardMediaCarousel(isShadeLayoutWide = viewModel.isShadeLayoutWide)
                    }
                },
                notifications = {
                    with(notificationSection) {
                        Box(modifier = Modifier.fillMaxHeight()) {
                            AodPromotedNotificationArea()
                            Notifications(areNotificationsVisible = true, burnInParams = null)
                        }
                    }
                },
                lockIcon = { with(lockSection) { LockIcon() } },
                startShortcut = {
                    with(bottomAreaSection) { Shortcut(isStart = true, applyPadding = false) }
                },
                ambientIndication = {
                    if (ambientIndicationSectionOptional.isPresent) {
                        with(ambientIndicationSectionOptional.get()) {
                            AmbientIndication(modifier = Modifier.fillMaxWidth())
                        }
                    }
                },
                bottomIndication = {
                    with(bottomAreaSection) { IndicationArea(modifier = Modifier.fillMaxWidth()) }
                },
                endShortcut = {
                    with(bottomAreaSection) { Shortcut(isStart = false, applyPadding = false) }
                },
                settingsMenu = {
                    with(settingsMenuSection) { SettingsMenu(onPlaced = onSettingsMenuPlaced) }
                },
            )
        }
    }
}
