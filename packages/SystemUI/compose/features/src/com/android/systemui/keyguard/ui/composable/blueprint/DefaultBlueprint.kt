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
import com.android.systemui.keyguard.ui.composable.element.AmbientIndicationElement
import com.android.systemui.keyguard.ui.composable.element.AodPromotedNotificationAreaElement
import com.android.systemui.keyguard.ui.composable.element.DateAndWeatherElement
import com.android.systemui.keyguard.ui.composable.element.HeadsUpNotificationsElement
import com.android.systemui.keyguard.ui.composable.element.IndicationAreaElement
import com.android.systemui.keyguard.ui.composable.element.LargeClockElement
import com.android.systemui.keyguard.ui.composable.element.LockElement
import com.android.systemui.keyguard.ui.composable.element.MediaCarouselElement
import com.android.systemui.keyguard.ui.composable.element.NotificationElement
import com.android.systemui.keyguard.ui.composable.element.SettingsMenuElement
import com.android.systemui.keyguard.ui.composable.element.ShortcutElement
import com.android.systemui.keyguard.ui.composable.element.SmallClockElement
import com.android.systemui.keyguard.ui.composable.element.SmartSpaceElement
import com.android.systemui.keyguard.ui.composable.element.StatusBarElement
import com.android.systemui.keyguard.ui.composable.layout.LockscreenSceneLayout
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
    private val statusBarElement: StatusBarElement,
    private val lockElement: LockElement,
    private val ambientIndicationElementOptional: Optional<AmbientIndicationElement>,
    private val shortcutElement: ShortcutElement,
    private val indicationAreaElement: IndicationAreaElement,
    private val settingsMenuElement: SettingsMenuElement,
    private val headsUpNotificationsElement: HeadsUpNotificationsElement,
    private val notificationsElement: NotificationElement,
    private val aodPromotedNotificationAreaElement: AodPromotedNotificationAreaElement,
    private val smallClockElement: SmallClockElement,
    private val largeClockElement: LargeClockElement,
    private val keyguardClockViewModel: KeyguardClockViewModel,
    private val smartSpaceElement: SmartSpaceElement,
    private val dateAndWeatherElement: DateAndWeatherElement,
    private val mediaCarouselElement: MediaCarouselElement,
) : ComposableLockscreenSceneBlueprint {

    override val id: String = "default"

    @Composable
    override fun ContentScope.Content(viewModel: LockscreenContentViewModel, modifier: Modifier) {
        val isBypassEnabled = viewModel.isBypassEnabled

        if (isBypassEnabled) {
            with(headsUpNotificationsElement) { HeadsUpNotifications() }
        }

        LockscreenLongPress(
            viewModelFactory = viewModel.touchHandlingFactory,
            modifier = modifier,
        ) { onSettingsMenuPlaced ->
            val burnIn = rememberBurnIn(keyguardClockViewModel)

            LockscreenSceneLayout(
                viewModel = viewModel.layout,
                statusBar = {
                    with(statusBarElement) { StatusBar(modifier = Modifier.fillMaxWidth()) }
                },
                smallClock = {
                    with(smallClockElement) {
                        SmallClock(
                            burnInParams = burnIn.parameters,
                            onTopChanged = burnIn.onSmallClockTopChanged,
                        )
                    }
                },
                largeClock = {
                    with(largeClockElement) { LargeClock(burnInParams = burnIn.parameters) }
                },
                dateAndWeather = { orientation ->
                    with(dateAndWeatherElement) { DateAndWeather(orientation) }
                },
                smartSpace = {
                    with(smartSpaceElement) {
                        SmartSpace(
                            burnInParams = burnIn.parameters,
                            onTopChanged = burnIn.onSmartspaceTopChanged,
                            smartSpacePaddingTop = { 0 },
                        )
                    }
                },
                media = {
                    with(mediaCarouselElement) {
                        KeyguardMediaCarousel(isShadeLayoutWide = viewModel.isShadeLayoutWide)
                    }
                },
                notifications = {
                    Box(modifier = Modifier.fillMaxHeight()) {
                        with(aodPromotedNotificationAreaElement) { AodPromotedNotificationArea() }
                        with(notificationsElement) {
                            Notifications(areNotificationsVisible = true, burnInParams = null)
                        }
                    }
                },
                lockIcon = { with(lockElement) { LockIcon() } },
                startShortcut = {
                    with(shortcutElement) { Shortcut(isStart = true, applyPadding = false) }
                },
                ambientIndication = {
                    if (ambientIndicationElementOptional.isPresent) {
                        with(ambientIndicationElementOptional.get()) {
                            AmbientIndication(modifier = Modifier.fillMaxWidth())
                        }
                    }
                },
                bottomIndication = {
                    with(indicationAreaElement) {
                        IndicationArea(modifier = Modifier.fillMaxWidth())
                    }
                },
                endShortcut = {
                    with(shortcutElement) { Shortcut(isStart = false, applyPadding = false) }
                },
                settingsMenu = {
                    with(settingsMenuElement) { SettingsMenu(onPlaced = onSettingsMenuPlaced) }
                },
            )
        }
    }
}
