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
 *
 */

package com.android.systemui.shade.ui.composable

import android.view.ContextThemeWrapper
import android.view.ViewGroup
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.LowestZIndexContentPicker
import com.android.compose.animation.scene.ValueKey
import com.android.compose.animation.scene.animateElementFloatAsState
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.compose.modifiers.thenIf
import com.android.compose.theme.colorAttr
import com.android.settingslib.Utils
import com.android.systemui.Flags
import com.android.systemui.battery.BatteryMeterView
import com.android.systemui.battery.BatteryMeterViewController
import com.android.systemui.common.ui.compose.windowinsets.CutoutLocation
import com.android.systemui.common.ui.compose.windowinsets.LocalDisplayCutout
import com.android.systemui.common.ui.compose.windowinsets.LocalScreenCornerRadius
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.privacy.OngoingPrivacyChip
import com.android.systemui.res.R
import com.android.systemui.scene.shared.model.DualShadeEducationElement
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.ui.composable.ShadeHeader.Dimensions.ChipPaddingHorizontal
import com.android.systemui.shade.ui.composable.ShadeHeader.Dimensions.ChipPaddingVertical
import com.android.systemui.shade.ui.composable.ShadeHeader.Values.ClockScale
import com.android.systemui.shade.ui.viewmodel.ShadeHeaderViewModel
import com.android.systemui.statusbar.core.NewStatusBarIcons
import com.android.systemui.statusbar.phone.StatusBarLocation
import com.android.systemui.statusbar.phone.StatusIconContainer
import com.android.systemui.statusbar.pipeline.mobile.ui.view.ModernShadeCarrierGroupMobileView
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.MobileIconsViewModelKairosComposeWrapper
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.ShadeCarrierGroupMobileIconViewModel
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.ShadeCarrierGroupMobileIconViewModelKairos
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.composeWrapper
import com.android.systemui.statusbar.policy.Clock
import com.android.systemui.util.composable.kairos.ActivatedKairosSpec
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

object ShadeHeader {
    object Elements {
        val ExpandedContent = ElementKey("ShadeHeaderExpandedContent")
        val CollapsedContentStart = ElementKey("ShadeHeaderCollapsedContentStart")
        val CollapsedContentEnd = ElementKey("ShadeHeaderCollapsedContentEnd")
        val PrivacyChip = ElementKey("PrivacyChip", contentPicker = LowestZIndexContentPicker)
        val Clock = ElementKey("ShadeHeaderClock", contentPicker = LowestZIndexContentPicker)
        val ShadeCarrierGroup = ElementKey("ShadeCarrierGroup")
    }

    object Values {
        val ClockScale = ValueKey("ShadeHeaderClockScale")
    }

    object Dimensions {
        @Deprecated(
            "Approximation of the collapsed shade header height, used in legacy shade transitions.",
            replaceWith = ReplaceWith("StatusBarHeight"),
        )
        val CollapsedHeightForTransitions = 48.dp
        val ExpandedHeight = 120.dp
        val ChipPaddingHorizontal = 6.dp
        val ChipPaddingVertical = 4.dp

        val StatusBarHeight: Dp
            // TODO(b/414737230): This is a temporary workaround, until we fix the zero padding
            //  issue given by WindowInsets.statusBars.asPaddingValues().calculateTopPadding().
            @Composable get() = dimensionResource(R.dimen.status_bar_height)
    }

    object TestTags {
        const val Root = "shade_header_root"
    }

    /** Represents the background highlighting of a header icons chip. */
    sealed interface ChipHighlight {
        val backgroundColor: Color
            @Composable @ReadOnlyComposable get

        val foregroundColor: Color
            @Composable @ReadOnlyComposable get

        data object Weak : ChipHighlight {
            override val backgroundColor: Color
                @Composable get() = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)

            override val foregroundColor: Color
                @Composable get() = MaterialTheme.colorScheme.onSurface
        }

        data object Strong : ChipHighlight {
            override val backgroundColor: Color
                @Composable get() = MaterialTheme.colorScheme.secondary

            override val foregroundColor: Color
                @Composable get() = MaterialTheme.colorScheme.onSecondary
        }
    }
}

/** The status bar that appears above the Shade scene on small screens */
@Composable
fun ContentScope.CollapsedShadeHeader(
    viewModel: ShadeHeaderViewModel,
    isSplitShade: Boolean,
    modifier: Modifier = Modifier,
) {
    val cutoutLocation = LocalDisplayCutout.current.location
    val horizontalPadding =
        max(LocalScreenCornerRadius.current / 2f, Shade.Dimensions.HorizontalPadding)

    val useExpandedTextFormat by
        remember(cutoutLocation) {
            derivedStateOf {
                cutoutLocation != CutoutLocation.CENTER ||
                    shouldUseExpandedFormat(layoutState.transitionState)
            }
        }

    val isPrivacyChipVisible by viewModel.isPrivacyChipVisible.collectAsStateWithLifecycle()

    // This layout assumes it is globally positioned at (0, 0) and is the same size as the screen.
    CutoutAwareShadeHeader(
        modifier = modifier,
        startContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.padding(horizontal = horizontalPadding),
            ) {
                Clock(onClick = viewModel::onClockClicked)
                VariableDayDate(
                    longerDateText = viewModel.longerDateText,
                    shorterDateText = viewModel.shorterDateText,
                    textColor = colorAttr(android.R.attr.textColorPrimary),
                    modifier = Modifier.element(ShadeHeader.Elements.CollapsedContentStart),
                )
            }
        },
        endContent = {
            if (isPrivacyChipVisible) {
                Box(modifier = Modifier.fillMaxSize().padding(horizontal = horizontalPadding)) {
                    PrivacyChip(
                        viewModel = viewModel,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier.element(ShadeHeader.Elements.CollapsedContentEnd)
                            .padding(horizontal = horizontalPadding),
                ) {
                    if (isSplitShade) {
                        ShadeCarrierGroup(viewModel = viewModel)
                    }
                    SystemIconChip(
                        onClick = viewModel::onSystemIconChipClicked.takeIf { isSplitShade }
                    ) {
                        val paddingEnd =
                            with(LocalDensity.current) {
                                (if (NewStatusBarIcons.isEnabled) 3.sp else 6.sp).toDp()
                            }
                        StatusIcons(
                            viewModel = viewModel,
                            useExpandedFormat = useExpandedTextFormat,
                            modifier = Modifier.padding(end = paddingEnd).weight(1f, fill = false),
                        )
                        BatteryIcon(
                            createBatteryMeterViewController =
                                viewModel.createBatteryMeterViewController,
                            useExpandedFormat = useExpandedTextFormat,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
            }
        },
    )
}

/** The status bar that appears above the Quick Settings scene on small screens */
@Composable
fun ContentScope.ExpandedShadeHeader(
    viewModel: ShadeHeaderViewModel,
    modifier: Modifier = Modifier,
) {
    val useExpandedFormat by remember {
        derivedStateOf { shouldUseExpandedFormat(layoutState.transitionState) }
    }

    val isPrivacyChipVisible by viewModel.isPrivacyChipVisible.collectAsStateWithLifecycle()

    Box(modifier = modifier.sysuiResTag(ShadeHeader.TestTags.Root)) {
        if (isPrivacyChipVisible) {
            Box(modifier = Modifier.height(ShadeHeader.Dimensions.StatusBarHeight).fillMaxWidth()) {
                PrivacyChip(viewModel = viewModel, modifier = Modifier.align(Alignment.CenterEnd))
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(space = 16.dp, alignment = Alignment.Bottom),
            modifier =
                Modifier.fillMaxWidth()
                    .defaultMinSize(minHeight = ShadeHeader.Dimensions.ExpandedHeight),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Clock(
                    onClick = viewModel::onClockClicked,
                    modifier = Modifier.align(Alignment.CenterStart),
                    scale = 2.57f,
                )
                Box(
                    modifier =
                        Modifier.element(ShadeHeader.Elements.ShadeCarrierGroup).fillMaxWidth()
                ) {
                    ShadeCarrierGroup(
                        viewModel = viewModel,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.element(ShadeHeader.Elements.ExpandedContent).fillMaxWidth(),
            ) {
                VariableDayDate(
                    longerDateText = viewModel.longerDateText,
                    shorterDateText = viewModel.shorterDateText,
                    textColor = colorAttr(android.R.attr.textColorPrimary),
                    modifier = Modifier.widthIn(max = 90.dp),
                )
                SystemIconChip {
                    val paddingEnd =
                        with(LocalDensity.current) {
                            (if (NewStatusBarIcons.isEnabled) 3.sp else 6.sp).toDp()
                        }
                    StatusIcons(
                        viewModel = viewModel,
                        useExpandedFormat = useExpandedFormat,
                        modifier = Modifier.padding(end = paddingEnd).weight(1f, fill = false),
                    )
                    BatteryIcon(
                        useExpandedFormat = useExpandedFormat,
                        createBatteryMeterViewController =
                            viewModel.createBatteryMeterViewController,
                    )
                }
            }
        }
    }
}

/**
 * The status bar that appears above both the Notifications and Quick Settings shade overlays when
 * overlay shade is enabled.
 */
@Composable
fun ContentScope.OverlayShadeHeader(
    viewModel: ShadeHeaderViewModel,
    notificationsHighlight: ShadeHeader.ChipHighlight,
    quickSettingsHighlight: ShadeHeader.ChipHighlight,
    showClock: Boolean,
    modifier: Modifier = Modifier,
) {
    val horizontalPadding =
        max(LocalScreenCornerRadius.current / 2f, Shade.Dimensions.HorizontalPadding)

    val isPrivacyChipVisible by viewModel.isPrivacyChipVisible.collectAsStateWithLifecycle()

    // This layout assumes it is globally positioned at (0, 0) and is the same size as the screen.
    CutoutAwareShadeHeader(
        modifier = modifier,
        startContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.padding(horizontal = horizontalPadding),
            ) {
                if (showClock) {
                    Clock(
                        onClick = viewModel::onClockClicked,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                NotificationsChip(
                    onClick = viewModel::onNotificationIconChipClicked,
                    backgroundColor = notificationsHighlight.backgroundColor,
                    modifier =
                        Modifier.bouncy(
                            isEnabled = viewModel.animateNotificationsChipBounce,
                            onBoundsChange = { bounds ->
                                viewModel.onDualShadeEducationElementBoundsChange(
                                    element = DualShadeEducationElement.Notifications,
                                    bounds = bounds,
                                )
                            },
                        ),
                ) {
                    VariableDayDate(
                        longerDateText = viewModel.longerDateText,
                        shorterDateText = viewModel.shorterDateText,
                        textColor = notificationsHighlight.foregroundColor,
                    )
                }
            }
        },
        endContent = {
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = horizontalPadding),
            ) {
                SystemIconChip(
                    backgroundColor = quickSettingsHighlight.backgroundColor,
                    onClick = viewModel::onSystemIconChipClicked,
                    modifier =
                        Modifier.bouncy(
                            isEnabled = viewModel.animateSystemIconChipBounce,
                            onBoundsChange = { bounds ->
                                viewModel.onDualShadeEducationElementBoundsChange(
                                    element = DualShadeEducationElement.QuickSettings,
                                    bounds = bounds,
                                )
                            },
                        ),
                ) {
                    val paddingEnd =
                        with(LocalDensity.current) {
                            (if (NewStatusBarIcons.isEnabled) 3.sp else 6.sp).toDp()
                        }
                    val isHighlighted = quickSettingsHighlight is ShadeHeader.ChipHighlight.Strong
                    StatusIcons(
                        viewModel = viewModel,
                        useExpandedFormat = false,
                        modifier = Modifier.padding(end = paddingEnd).weight(1f, fill = false),
                        isHighlighted = isHighlighted,
                    )
                    BatteryIcon(
                        createBatteryMeterViewController =
                            viewModel.createBatteryMeterViewController,
                        useExpandedFormat = false,
                        isHighlighted = isHighlighted,
                    )
                }
                if (isPrivacyChipVisible) {
                    Box(modifier = Modifier.fillMaxSize().padding(horizontal = horizontalPadding)) {
                        PrivacyChip(
                            viewModel = viewModel,
                            modifier = Modifier.align(Alignment.CenterEnd),
                        )
                    }
                }
            }
        },
    )
}

/** The header that appears at the top of the Quick Settings shade overlay. */
@Composable
fun QuickSettingsOverlayHeader(viewModel: ShadeHeaderViewModel, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        ShadeCarrierGroup(viewModel = viewModel)
        BatteryIcon(
            createBatteryMeterViewController = viewModel.createBatteryMeterViewController,
            useExpandedFormat = true,
        )
    }
}

/*
 * Places startContent and endContent according to the location of the display cutout.
 * Assumes it is globally positioned at (0, 0) and the same size as the screen.
 */
@Composable
private fun CutoutAwareShadeHeader(
    modifier: Modifier = Modifier,
    startContent: @Composable () -> Unit,
    endContent: @Composable () -> Unit,
) {
    val cutoutWidth = LocalDisplayCutout.current.width()
    val cutoutHeight = LocalDisplayCutout.current.height()
    val cutoutTop = LocalDisplayCutout.current.top
    val cutoutLocation = LocalDisplayCutout.current.location
    val statusBarHeight = ShadeHeader.Dimensions.StatusBarHeight

    Layout(
        modifier = modifier.sysuiResTag(ShadeHeader.TestTags.Root),
        contents = listOf(startContent, endContent),
    ) { measurables, constraints ->
        check(constraints.hasBoundedWidth)
        check(measurables.size == 2)
        check(measurables[0].size == 1)
        check(measurables[1].size == 1)

        val screenWidth = constraints.maxWidth
        val cutoutWidthPx = cutoutWidth.roundToPx()
        val height = max(cutoutHeight + (cutoutTop * 2), statusBarHeight).roundToPx()
        val childConstraints = Constraints.fixed((screenWidth - cutoutWidthPx) / 2, height)

        val startMeasurable = measurables[0][0]
        val endMeasurable = measurables[1][0]

        val startPlaceable = startMeasurable.measure(childConstraints)
        val endPlaceable = endMeasurable.measure(childConstraints)

        layout(screenWidth, height) {
            when (cutoutLocation) {
                CutoutLocation.NONE,
                CutoutLocation.RIGHT -> {
                    startPlaceable.placeRelative(x = 0, y = 0)
                    endPlaceable.placeRelative(x = startPlaceable.width, y = 0)
                }
                CutoutLocation.CENTER -> {
                    startPlaceable.placeRelative(x = 0, y = 0)
                    endPlaceable.placeRelative(x = startPlaceable.width + cutoutWidthPx, y = 0)
                }
                CutoutLocation.LEFT -> {
                    startPlaceable.placeRelative(x = cutoutWidthPx, y = 0)
                    endPlaceable.placeRelative(x = startPlaceable.width + cutoutWidthPx, y = 0)
                }
            }
        }
    }
}

@Composable
private fun ContentScope.Clock(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
) {
    val layoutDirection = LocalLayoutDirection.current

    ElementWithValues(key = ShadeHeader.Elements.Clock, modifier = modifier) {
        val animatedScale by animateElementFloatAsState(scale, ClockScale, canOverflow = false)

        content {
            AndroidView(
                factory = { context ->
                    Clock(
                        ContextThemeWrapper(context, R.style.Theme_SystemUI_QuickSettings_Header),
                        null,
                    )
                },
                modifier =
                    modifier
                        // use graphicsLayer instead of Modifier.scale to anchor transform to the
                        // (start, top) corner
                        .graphicsLayer {
                            scaleX = animatedScale
                            scaleY = animatedScale
                            transformOrigin =
                                TransformOrigin(
                                    when (layoutDirection) {
                                        LayoutDirection.Ltr -> 0f
                                        LayoutDirection.Rtl -> 1f
                                    },
                                    0.5f,
                                )
                        }
                        .clickable { onClick() },
            )
        }
    }
}

@Composable
private fun BatteryIcon(
    createBatteryMeterViewController: (ViewGroup, StatusBarLocation) -> BatteryMeterViewController,
    useExpandedFormat: Boolean,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
) {
    val localContext = LocalContext.current
    val themedContext =
        ContextThemeWrapper(localContext, R.style.Theme_SystemUI_QuickSettings_Header)
    val primaryColor =
        Utils.getColorAttrDefaultColor(themedContext, android.R.attr.textColorPrimary)
    val inverseColor =
        Utils.getColorAttrDefaultColor(themedContext, android.R.attr.textColorPrimaryInverse)

    val cutoutLocation = LocalDisplayCutout.current.location

    AndroidView(
        factory = { context ->
            val batteryIcon = BatteryMeterView(context, null)
            batteryIcon.setPercentShowMode(BatteryMeterView.MODE_ON)

            // [BatteryMeterView.updateColors] is an old method that was built to distinguish
            // between dual-tone colors and single-tone. The current icon is only single-tone, so
            // the final [fg] is the only one we actually need
            batteryIcon.updateColors(primaryColor, inverseColor, primaryColor)

            val batteryMaterViewController =
                createBatteryMeterViewController(batteryIcon, StatusBarLocation.QS)
            batteryMaterViewController.init()
            batteryMaterViewController.ignoreTunerUpdates()

            batteryIcon
        },
        update = { batteryIcon ->
            batteryIcon.setPercentShowMode(
                if (useExpandedFormat || cutoutLocation != CutoutLocation.CENTER) {
                    BatteryMeterView.MODE_ESTIMATE
                } else {
                    BatteryMeterView.MODE_ON
                }
            )
            // TODO(b/397223606): Get the actual spec for this.
            batteryIcon.updateColors(
                primaryColor,
                inverseColor,
                if (isHighlighted) inverseColor else primaryColor,
            )
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalKairosApi::class)
@Composable
private fun ShadeCarrierGroup(viewModel: ShadeHeaderViewModel, modifier: Modifier = Modifier) {
    if (Flags.statusBarMobileIconKairos()) {
        ShadeCarrierGroupKairos(viewModel, modifier)
        return
    }

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        for (subId in viewModel.mobileSubIds) {
            AndroidView(
                factory = { context ->
                    ModernShadeCarrierGroupMobileView.constructAndBind(
                            context = context,
                            logger = viewModel.mobileIconsViewModel.logger,
                            slot = "mobile_carrier_shade_group",
                            viewModel =
                                (viewModel.mobileIconsViewModel.viewModelForSub(
                                    subId,
                                    StatusBarLocation.SHADE_CARRIER_GROUP,
                                ) as ShadeCarrierGroupMobileIconViewModel),
                        )
                        .also { it.setOnClickListener { viewModel.onShadeCarrierGroupClicked() } }
                }
            )
        }
    }
}

@ExperimentalKairosApi
@Composable
private fun ShadeCarrierGroupKairos(
    viewModel: ShadeHeaderViewModel,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        ActivatedKairosSpec(
            buildSpec = viewModel.mobileIconsViewModelKairos.get().composeWrapper(),
            kairosNetwork = viewModel.kairosNetwork,
        ) { iconsViewModel: MobileIconsViewModelKairosComposeWrapper ->
            for ((subId, icon) in iconsViewModel.icons) {
                Spacer(modifier = Modifier.width(5.dp))
                val scope = rememberCoroutineScope()
                AndroidView(
                    factory = { context ->
                        ModernShadeCarrierGroupMobileView.constructAndBind(
                                context = context,
                                logger = iconsViewModel.logger,
                                slot = "mobile_carrier_shade_group",
                                viewModel =
                                    ShadeCarrierGroupMobileIconViewModelKairos(
                                        icon,
                                        icon.iconInteractor,
                                    ),
                                scope = scope,
                                subscriptionId = subId,
                                location = StatusBarLocation.SHADE_CARRIER_GROUP,
                                kairosNetwork = viewModel.kairosNetwork,
                            )
                            .first
                            .also {
                                it.setOnClickListener { viewModel.onShadeCarrierGroupClicked() }
                            }
                    }
                )
            }
        }
    }
}

@Composable
private fun ContentScope.StatusIcons(
    viewModel: ShadeHeaderViewModel,
    useExpandedFormat: Boolean,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
) {
    val localContext = LocalContext.current
    val themedContext =
        ContextThemeWrapper(localContext, R.style.Theme_SystemUI_QuickSettings_Header)
    val primaryColor =
        Utils.getColorAttrDefaultColor(themedContext, android.R.attr.textColorPrimary)
    val inverseColor =
        Utils.getColorAttrDefaultColor(themedContext, android.R.attr.textColorPrimaryInverse)

    val carrierIconSlots =
        listOf(
            stringResource(id = com.android.internal.R.string.status_bar_mobile),
            stringResource(id = com.android.internal.R.string.status_bar_stacked_mobile),
        )
    val cameraSlot = stringResource(id = com.android.internal.R.string.status_bar_camera)
    val micSlot = stringResource(id = com.android.internal.R.string.status_bar_microphone)
    val locationSlot = stringResource(id = com.android.internal.R.string.status_bar_location)

    val isSingleCarrier by viewModel.isSingleCarrier.collectAsStateWithLifecycle()
    val isPrivacyChipEnabled by viewModel.isPrivacyChipEnabled.collectAsStateWithLifecycle()
    val isMicCameraIndicationEnabled by
        viewModel.isMicCameraIndicationEnabled.collectAsStateWithLifecycle()
    val isLocationIndicationEnabled by
        viewModel.isLocationIndicationEnabled.collectAsStateWithLifecycle()

    val iconContainer = remember { StatusIconContainer(themedContext, null) }
    val iconManager = remember {
        viewModel.createTintedIconManager(iconContainer, StatusBarLocation.QS)
    }

    // TODO(408001821): Use composable system status icons here instead.
    AndroidView(
        factory = {
            iconManager.setTint(primaryColor, inverseColor)
            viewModel.statusBarIconController.addIconGroup(iconManager)

            iconContainer
        },
        update = { iconContainer ->
            iconContainer.setQsExpansionTransitioning(
                layoutState.isTransitioningBetween(Scenes.Shade, Scenes.QuickSettings)
            )
            if (isSingleCarrier || !useExpandedFormat) {
                iconContainer.removeIgnoredSlots(carrierIconSlots)
            } else {
                iconContainer.addIgnoredSlots(carrierIconSlots)
            }

            if (isPrivacyChipEnabled) {
                if (isMicCameraIndicationEnabled) {
                    iconContainer.addIgnoredSlot(cameraSlot)
                    iconContainer.addIgnoredSlot(micSlot)
                } else {
                    iconContainer.removeIgnoredSlot(cameraSlot)
                    iconContainer.removeIgnoredSlot(micSlot)
                }
                if (isLocationIndicationEnabled) {
                    iconContainer.addIgnoredSlot(locationSlot)
                } else {
                    iconContainer.removeIgnoredSlot(locationSlot)
                }
            } else {
                iconContainer.removeIgnoredSlot(cameraSlot)
                iconContainer.removeIgnoredSlot(micSlot)
                iconContainer.removeIgnoredSlot(locationSlot)
            }

            // TODO(b/397223606): Get the actual spec for this.
            if (isHighlighted) {
                iconManager.setTint(inverseColor, primaryColor)
            } else {
                iconManager.setTint(primaryColor, inverseColor)
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun NotificationsChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier =
            modifier
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .background(backgroundColor, RoundedCornerShape(25.dp))
                .padding(horizontal = ChipPaddingHorizontal, vertical = ChipPaddingVertical)
    ) {
        content()
    }
}

@Composable
private fun SystemIconChip(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .clip(RoundedCornerShape(25.dp))
                .thenIf(onClick != null) { Modifier.clickable(onClick = { onClick?.invoke() }) }
                .thenIf(backgroundColor != Color.Unspecified) {
                    Modifier.background(backgroundColor)
                        .padding(horizontal = ChipPaddingHorizontal, vertical = ChipPaddingVertical)
                },
        content = content,
    )
}

@Composable
private fun ContentScope.PrivacyChip(
    viewModel: ShadeHeaderViewModel,
    modifier: Modifier = Modifier,
) {
    val privacyList by viewModel.privacyItems.collectAsStateWithLifecycle()

    AndroidView(
        factory = { context ->
            val view =
                OngoingPrivacyChip(context, null).also { privacyChip ->
                    privacyChip.privacyList = privacyList
                    privacyChip.setOnClickListener { viewModel.onPrivacyChipClicked(privacyChip) }
                }
            view
        },
        update = { it.privacyList = privacyList },
        modifier = modifier.element(ShadeHeader.Elements.PrivacyChip),
    )
}

/** Modifies the given [Modifier] such that it shows a looping vertical bounce animation. */
@Composable
private fun Modifier.bouncy(
    isEnabled: Boolean,
    onBoundsChange: (bounds: IntRect) -> Unit,
): Modifier {
    val density = LocalDensity.current
    val animatable = remember { Animatable(0f) }
    LaunchedEffect(isEnabled) {
        if (isEnabled) {
            while (true) {
                // Lifts the element up to the first peak.
                animatable.animateTo(
                    targetValue = with(density) { -(10.dp).toPx() },
                    animationSpec =
                        tween(
                            durationMillis = 200,
                            easing = CubicBezierEasing(0.15f, 0f, 0.23f, 1f),
                        ),
                )
                // Drops the element back to the ground from the first peak.
                animatable.animateTo(
                    targetValue = 0f,
                    animationSpec =
                        tween(
                            durationMillis = 167,
                            easing = CubicBezierEasing(0.74f, 0f, 0.22f, 1f),
                        ),
                )
                // Lifts the element up again, this time to the second, smaller peak.
                animatable.animateTo(
                    targetValue = with(density) { -(5.dp).toPx() },
                    animationSpec =
                        tween(
                            durationMillis = 150,
                            easing = CubicBezierEasing(0.62f, 0f, 0.35f, 1f),
                        ),
                )
                // Drops the element back to the ground from the second peak.
                animatable.animateTo(
                    targetValue = 0f,
                    animationSpec =
                        tween(
                            durationMillis = 117,
                            easing = CubicBezierEasing(0.67f, 0f, 0.51f, 1f),
                        ),
                )
                // Wait for a moment before repeating it.
                delay(1000)
            }
        } else {
            animatable.animateTo(targetValue = 0f, animationSpec = tween(durationMillis = 500))
        }
    }

    return this.thenIf(isEnabled) {
        Modifier.offset { IntOffset(x = 0, y = animatable.value.roundToInt()) }
            .onGloballyPositioned { coordinates ->
                val offset = coordinates.positionInWindow()
                onBoundsChange(
                    IntRect(
                        offset = IntOffset(x = offset.x.roundToInt(), y = offset.y.roundToInt()),
                        size = coordinates.size,
                    )
                )
            }
    }
}

private fun shouldUseExpandedFormat(state: TransitionState): Boolean {
    return when (state) {
        is TransitionState.Idle -> state.currentScene == Scenes.QuickSettings
        is TransitionState.Transition -> {
            (state.isTransitioning(to = Scenes.QuickSettings) && state.progress >= 0.5) ||
                (state.isTransitioning(from = Scenes.QuickSettings) && state.progress <= 0.5)
        }
    }
}
