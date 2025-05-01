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

package com.android.systemui.keyguard.ui.composable.layout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.VerticalAlignmentLine
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastRoundToInt
import com.android.compose.modifiers.padding
import com.android.compose.windowsizeclass.LocalWindowSizeClass
import kotlin.math.max
import kotlin.math.min

/**
 * Models the UI state of the [LockscreenSceneLayout]. Properties should be backed by snapshot
 * state.
 */
@Immutable
interface LockscreenLayoutViewModel {
    /** Whether the clock can switch sizes dynamically or forever remain small. */
    val isDynamicClockEnabled: Boolean
    /**
     * Whether date and weather should be drawn by the [LockscreenSceneLayout] when the clock is
     * large.
     *
     * A large clock that already draws the date and weather on its own (like a weather clock)
     * should have this set to `false`.
     *
     * This value is meaningless if [isDynamicClockEnabled] is `false`.
     */
    val isDateAndWeatherVisibleWithLargeClock: Boolean
    /** Whether smart space should currently be showing. */
    val isSmartSpaceVisible: Boolean
    /** Whether media should currently be showing. */
    val isMediaVisible: Boolean
    /**
     * Whether notifications should currently be showing (inside the lock screen layout; should be
     * `false` if only heads-up notifications are showing).
     */
    val isNotificationsVisible: Boolean
    /** Whether the ambient indication UI should currently be showing. */
    val isAmbientIndicationVisible: Boolean
    /** Amount of horizontal translation that should be applied to elements in the scene. */
    val unfoldTranslations: UnfoldTranslations
}

@Immutable
interface UnfoldTranslations {

    /**
     * Amount of horizontal translation to apply to elements that are aligned to the start side
     * (left in left-to-right layouts). Can also be used as horizontal padding for elements that
     * need horizontal padding on both side. In pixels.
     */
    val start: Float

    /**
     * Amount of horizontal translation to apply to elements that are aligned to the end side (right
     * in left-to-right layouts). In pixels.
     */
    val end: Float
}

/**
 * Encapsulates alignment lines produced by the lock icon element.
 *
 * Because the lock icon is also the same element as the under-display fingerprint sensor (UDFPS),
 * [LockscreenSceneLayout] uses the lock icon provided alignment lines to make sure that other
 * elements on screen do not overlap with the lock icon.
 */
object LockIconAlignmentLines {

    /** The left edge of the lock icon. */
    val Left =
        VerticalAlignmentLine(
            merger = { old, new ->
                // When two left alignment line values are provided, choose the leftmost one:
                min(old, new)
            }
        )

    /** The top edge of the lock icon. */
    val Top =
        HorizontalAlignmentLine(
            merger = { old, new ->
                // When two top alignment line values are provided, choose the topmost one:
                min(old, new)
            }
        )

    /** The right edge of the lock icon. */
    val Right =
        VerticalAlignmentLine(
            merger = { old, new ->
                // When two right alignment line values are provided, choose the rightmost one:
                max(old, new)
            }
        )

    /** The bottom edge of the lock icon. */
    val Bottom =
        HorizontalAlignmentLine(
            merger = { old, new ->
                // When two bottom alignment line values are provided, choose the bottommost
                // one:
                max(old, new)
            }
        )
}

/**
 * Arranges the layout for the lockscreen scene.
 *
 * Takes care of figuring out the correct layout configuration based on the device form factor,
 * orientation, and the current UI state.
 *
 * Notes about some non-obvious parameters:
 * - [dateAndWeather] is drawn either with the small clock or with the large clock. It's only drawn
 *   with the large clock if `isDateAndWeatherVisibleWithLargeClock` in the view-model is `true`
 * - [lockIcon] is drawn according to the [LockIconAlignmentLines] that it must supply. The layout
 *   logic uses those alignment lines to make sure other elements don't overlap with the lock icon
 *   as it may be drawn on top of the UDFPS (under display fingerprint sensor)
 * - the [ambientIndication] is drawn between the start-side and end-side shortcuts, showing ambient
 *   information like the song that's currently being detected
 * - the [bottomIndication] is drawn between the start-side and end-side shortcuts, along the bottom
 */
@Composable
fun LockscreenSceneLayout(
    viewModel: LockscreenLayoutViewModel,
    statusBar: @Composable () -> Unit,
    smallClock: @Composable () -> Unit,
    largeClock: @Composable () -> Unit,
    dateAndWeather: @Composable (Orientation) -> Unit,
    smartSpace: @Composable () -> Unit,
    media: @Composable () -> Unit,
    notifications: @Composable () -> Unit,
    lockIcon: @Composable () -> Unit,
    startShortcut: @Composable () -> Unit,
    ambientIndication: @Composable () -> Unit,
    bottomIndication: @Composable () -> Unit,
    endShortcut: @Composable () -> Unit,
    settingsMenu: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = layout(viewModel)

    /**
     * Convenience function to draw the content column without needing to pass a lot of parameters.
     *
     * Also draws a placeholder if the content column shouldn't be visible.
     */
    @Composable
    fun contentColumn(modifier: Modifier = Modifier) {
        if (layout.isContentColumnVisible) {
            ContentColumn(
                isSmallClockVisible = layout.isSmallClockVisible,
                smallClock = smallClock,
                dateAndWeather = dateAndWeather,
                isSmartSpaceVisible = viewModel.isSmartSpaceVisible,
                smartSpace = smartSpace,
                isMediaVisible = viewModel.isMediaVisible,
                media = media,
                isNotificationsVisible = viewModel.isNotificationsVisible,
                notifications = notifications,
                modifier = modifier,
            )
        } else {
            Box(modifier)
        }
    }

    /**
     * Convenience function to draw the large clock without needing to pass a lot of parameters.
     *
     * Also draws a placeholder if the large clock shouldn't be visible.
     */
    @Composable
    fun largeClock(modifier: Modifier = Modifier) {
        if (layout.isLargeClockVisible) {
            LargeClockWithDateAndWeather(
                largeClock = largeClock,
                isDateAndWeatherVisible = viewModel.isDateAndWeatherVisibleWithLargeClock,
                dateAndWeather = dateAndWeather,
                modifier = modifier,
            )
        } else {
            Box(modifier)
        }
    }

    /** Convenience function to draw the bottom area without needing to pass a lot of parameters. */
    @Composable
    fun bottomArea(modifier: Modifier = Modifier) {
        BottomArea(
            startShortcut = startShortcut,
            isAmbientIndicationVisible = viewModel.isAmbientIndicationVisible,
            ambientIndication = ambientIndication,
            bottomIndication = bottomIndication,
            endShortcut = endShortcut,
            unfoldTranslations = viewModel.unfoldTranslations,
            modifier = modifier.navigationBarsPadding(),
        )
    }

    val density = LocalDensity.current
    val spacingAboveLockIconPx = with(density) { 64.dp.roundToPx() }
    val spacingBetweenColumnsPx = with(density) { 32.dp.roundToPx() }

    Layout(
        content = {
            Box(
                Modifier.padding(
                    horizontal = { viewModel.unfoldTranslations.start.fastRoundToInt() }
                )
            ) {
                statusBar()
            }
            Box(Modifier.graphicsLayer { translationX = viewModel.unfoldTranslations.start }) {
                contentColumn()
            }
            Box(Modifier.graphicsLayer { translationX = viewModel.unfoldTranslations.end }) {
                largeClock()
            }
            bottomArea()
            lockIcon()
            settingsMenu()
        },
        modifier = modifier,
    ) { measurables, constraints ->
        check(measurables.size == 6)
        val statusBarMeasurable = measurables[0]
        val contentColumnMeasurable = measurables[1]
        val largeClockMeasurable = measurables[2]
        val bottomAreaMeasurable = measurables[3]
        val lockIconMeasurable = measurables[4]
        val settingsMenuMeasurable = measurables[5]

        val statusBarPlaceable =
            statusBarMeasurable.measure(constraints = Constraints.fixedWidth(constraints.maxWidth))

        val lockIconPlaceable =
            lockIconMeasurable.measure(constraints.copy(minWidth = 0, minHeight = 0))

        // The width of a single column in a two-column layout.
        val oneColumnWidthInTwoColumnLayout = (constraints.maxWidth - spacingBetweenColumnsPx) / 2
        // Height available between the bottom of the status bar and either the top of the UDFPS
        // icon (if one is showing) or the bottom of the screen, if no UDFPS icon is showing.
        val lockIconBounds =
            IntRect(
                left = lockIconPlaceable[LockIconAlignmentLines.Left],
                top = lockIconPlaceable[LockIconAlignmentLines.Top],
                right = lockIconPlaceable[LockIconAlignmentLines.Right],
                bottom = lockIconPlaceable[LockIconAlignmentLines.Bottom],
            )
        val lockIconConstrainedMaxHeight = lockIconBounds.top - spacingAboveLockIconPx

        val largeClockPlaceableOrNull =
            when {
                layout.isOnlyLargeClockVisible ->
                    largeClockMeasurable.measure(
                        Constraints(
                            minWidth = 0,
                            maxWidth = constraints.maxWidth.coerceAtLeast(0),
                            minHeight = 0,
                            maxHeight =
                                if (viewModel.isDateAndWeatherVisibleWithLargeClock) {
                                        // Only constrain the height of the standalone large clock
                                        // so it doesn't overlap with the UDFPS icon if the large
                                        // clock also needs the date and weather to be drawn by this
                                        // layout; which means that the large clock does not take up
                                        // the entire available space.
                                        lockIconConstrainedMaxHeight
                                    } else {
                                        constraints.maxHeight - statusBarPlaceable.measuredHeight
                                    }
                                    .coerceAtLeast(0),
                        )
                    )
                layout.isTwoColumns && layout.isLargeClockVisible ->
                    largeClockMeasurable.measure(
                        Constraints(
                            minWidth = 0,
                            maxWidth = oneColumnWidthInTwoColumnLayout.coerceAtLeast(0),
                            minHeight = 0,
                            // When the large clock is shown as part of a two-column layout, it's
                            // allowed to vertically overlap with the UDFPS icon.
                            maxHeight =
                                (constraints.maxHeight - statusBarPlaceable.measuredHeight)
                                    .coerceAtLeast(0),
                        )
                    )
                else -> null
            }

        val contentColumnPlaceableOrNull =
            when {
                layout.isOnlyContentColumnVisible ->
                    contentColumnMeasurable.measure(
                        Constraints(
                            minWidth = 0,
                            maxWidth = constraints.maxWidth.coerceAtLeast(0),
                            minHeight = 0,
                            maxHeight = lockIconConstrainedMaxHeight.coerceAtLeast(0),
                        )
                    )
                layout.isTwoColumns ->
                    contentColumnMeasurable.measure(
                        Constraints(
                            minWidth = 0,
                            maxWidth = oneColumnWidthInTwoColumnLayout.coerceAtLeast(0),
                            minHeight = 0,
                            maxHeight = lockIconConstrainedMaxHeight.coerceAtLeast(0),
                        )
                    )
                else -> null
            }

        val bottomAreaPlaceable =
            bottomAreaMeasurable.measure(constraints = Constraints.fixedWidth(constraints.maxWidth))

        val settingsMenuPleaceable = settingsMenuMeasurable.measure(constraints)

        layout(constraints.maxWidth, constraints.maxHeight) {
            statusBarPlaceable.place(0, 0)
            contentColumnPlaceableOrNull?.placeRelative(0, statusBarPlaceable.measuredHeight)
            largeClockPlaceableOrNull?.placeRelative(
                x =
                    if (layout.isOnlyLargeClockVisible) {
                        // When the large clock is shown by itself, center it horizontally.
                        (constraints.maxWidth - largeClockPlaceableOrNull.measuredWidth) / 2
                    } else {
                        // When the large clock is shown inside its own column, center it inside its
                        // own column, towards the end-side of the layout.
                        oneColumnWidthInTwoColumnLayout +
                            spacingBetweenColumnsPx +
                            (oneColumnWidthInTwoColumnLayout -
                                largeClockPlaceableOrNull.measuredWidth) / 2
                    },
                y =
                    if (
                        layout.isOnlyLargeClockVisible &&
                            viewModel.isDateAndWeatherVisibleWithLargeClock
                    ) {
                        // When the large clock is shown by itself but needs to not overlap
                        // vertically with the UDFPS icon, position it above the UDFPS icon.
                        lockIconBounds.top -
                            spacingAboveLockIconPx -
                            largeClockPlaceableOrNull.measuredHeight
                    } else {
                        // In all other cases, center the large clock vertically in its allotted
                        // space.
                        (constraints.maxHeight -
                            statusBarPlaceable.measuredHeight -
                            largeClockPlaceableOrNull.measuredHeight) / 2
                    },
            )

            bottomAreaPlaceable.place(0, constraints.maxHeight - bottomAreaPlaceable.measuredHeight)
            lockIconPlaceable.place(x = lockIconBounds.left, y = lockIconBounds.top)
            settingsMenuPleaceable.placeRelative(
                x = (constraints.maxWidth - settingsMenuPleaceable.measuredWidth) / 2,
                y = constraints.maxHeight - settingsMenuPleaceable.measuredHeight,
            )
        }
    }
}

/** Draws the bottom area of the layout. */
@Composable
private fun BottomArea(
    startShortcut: @Composable () -> Unit,
    isAmbientIndicationVisible: Boolean,
    ambientIndication: @Composable () -> Unit,
    bottomIndication: @Composable () -> Unit,
    endShortcut: @Composable () -> Unit,
    unfoldTranslations: UnfoldTranslations,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        if (isAmbientIndicationVisible) {
            ambientIndication()
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.graphicsLayer { translationX = unfoldTranslations.start }) {
                startShortcut()
            }
            Box(Modifier.weight(1f)) { bottomIndication() }
            Box(Modifier.graphicsLayer { translationX = unfoldTranslations.end }) { endShortcut() }
        }
    }
}

/**
 * Draws the content column.
 *
 * This is the vertical stack that contains many elements like the small clock, smart space, media,
 * and notifications.
 */
@Composable
private fun ContentColumn(
    isSmallClockVisible: Boolean,
    smallClock: @Composable () -> Unit,
    dateAndWeather: @Composable (Orientation) -> Unit,
    isSmartSpaceVisible: Boolean,
    smartSpace: @Composable () -> Unit,
    isMediaVisible: Boolean,
    media: @Composable () -> Unit,
    isNotificationsVisible: Boolean,
    notifications: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(top = 32.dp)) {
        if (isSmallClockVisible) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 24.dp),
            ) {
                smallClock()
                dateAndWeather(Orientation.Vertical)
            }
        }

        AnimatedVisibility(isSmartSpaceVisible) {
            Box(Modifier.padding(bottom = 24.dp)) { smartSpace() }
        }

        AnimatedVisibility(isMediaVisible) { Box(Modifier.padding(bottom = 24.dp)) { media() } }

        AnimatedVisibility(isNotificationsVisible) { notifications() }
    }
}

/** Draws the large clock. */
@Composable
private fun LargeClockWithDateAndWeather(
    largeClock: @Composable () -> Unit,
    isDateAndWeatherVisible: Boolean,
    dateAndWeather: @Composable (Orientation) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        if (isDateAndWeatherVisible) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                modifier = Modifier.align(Alignment.Center),
            ) {
                largeClock()
                dateAndWeather(Orientation.Horizontal)
            }
        } else {
            Box(Modifier.align(Alignment.Center)) { largeClock() }
        }
    }
}

/**
 * Returns a [Layout] representing the layout configuration that should be used to display the
 * lockscreen scene.
 */
@Composable
private fun layout(viewModel: LockscreenLayoutViewModel): Layout {
    val isAbleToShowLargeClock = viewModel.isDynamicClockEnabled

    val isContentColumnVisible =
        when {
            !isAbleToShowLargeClock -> {
                // If the large clock cannot be shown, the small clock is always shown and it must
                // be shown as part of the content column.
                true
            }

            viewModel.isMediaVisible || viewModel.isNotificationsVisible -> {
                // If there's content, the content column must be shown.
                true
            }

            // If the large clock can be shown and there's no content to be shown otherwise, the
            // content column is hidden.
            else -> false
        }

    val windowSizeClass = LocalWindowSizeClass.current
    val isLargeScreen =
        windowSizeClass.widthSizeClass >= WindowWidthSizeClass.Medium &&
            windowSizeClass.heightSizeClass >= WindowHeightSizeClass.Medium

    val isTwoColumns = isLargeScreen && isContentColumnVisible
    return Layout(
        isTwoColumns = isTwoColumns,
        isLargeClockVisible = isAbleToShowLargeClock && (isTwoColumns || !isContentColumnVisible),
    )
}

/** Models the particular layout to use for the lockscreen scene. */
private data class Layout(
    /** Whether the UI should be laid out as two side-by-side columns or just as one. */
    val isTwoColumns: Boolean,
    /**
     * Whether the large clock should be visible. If `false`, the small clock will be visible
     * instead.
     */
    val isLargeClockVisible: Boolean,
) {
    /** Whether only the large clock is visible, without the content column. */
    val isOnlyLargeClockVisible: Boolean = !isTwoColumns && isLargeClockVisible
    /** Whether the small clock should be visible instead of the large clock. */
    val isSmallClockVisible: Boolean = !isLargeClockVisible
    /** Whether the content column is visible; the large might be visible or invisible. */
    val isContentColumnVisible: Boolean = isTwoColumns || !isLargeClockVisible
    /** Whether only the content column is visible but the large clock isn't. */
    val isOnlyContentColumnVisible: Boolean = !isTwoColumns && !isLargeClockVisible
}
