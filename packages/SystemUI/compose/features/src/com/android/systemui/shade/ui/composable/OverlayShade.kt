/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.shade.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.overscroll
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.LowestZIndexContentPicker
import com.android.compose.modifiers.thenIf
import com.android.compose.windowsizeclass.LocalWindowSizeClass
import com.android.mechanics.behavior.VerticalExpandContainerSpec
import com.android.mechanics.behavior.verticalExpandContainerBackground
import com.android.systemui.res.R
import com.android.systemui.shade.ui.ShadeColors.shadePanel
import com.android.systemui.shade.ui.ShadeColors.shadePanelScrimBehind
import com.android.systemui.shade.ui.composable.OverlayShade.rememberShadeExpansionMotion
import kotlin.math.min

/** Renders a lightweight shade UI container, as an overlay. */
@Composable
fun ContentScope.OverlayShade(
    panelElement: ElementKey,
    alignmentOnWideScreens: Alignment,
    enableTransparency: Boolean,
    onScrimClicked: () -> Unit,
    modifier: Modifier = Modifier,
    onBackgroundPlaced: (bounds: Rect, topCornerRadius: Float, bottomCornerRadius: Float) -> Unit =
        { _, _, _ ->
        },
    header: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val isFullWidth = isFullWidthShade()
    val panelSpec = rememberShadeExpansionMotion(isFullWidth)
    val panelCornerRadiusPx = with(LocalDensity.current) { panelSpec.radius.toPx() }
    Box(modifier) {
        Scrim(showBackgroundColor = enableTransparency, onClicked = onScrimClicked)

        Box(
            modifier = Modifier.fillMaxSize().panelContainerPadding(isFullWidth),
            contentAlignment = if (isFullWidth) Alignment.TopCenter else alignmentOnWideScreens,
        ) {
            Panel(
                enableTransparency = enableTransparency,
                spec = panelSpec,
                modifier =
                    Modifier.overscroll(verticalOverscrollEffect)
                        .element(panelElement)
                        .panelWidth(isFullWidth)
                        .onPlaced { coordinates ->
                            val bounds = coordinates.boundsInWindow()
                            val isTopRounded = panelSpec.isFloating
                            val bottomCornerRadius: Float =
                                if (isTopRounded) {
                                    min(panelCornerRadiusPx, bounds.height / 2)
                                } else {
                                    min(panelCornerRadiusPx, bounds.height)
                                }
                            val topCornerRadius = if (isTopRounded) bottomCornerRadius else 0f
                            onBackgroundPlaced(bounds, topCornerRadius, bottomCornerRadius)
                        },
                header = header.takeIf { isFullWidth },
                content = content,
            )
        }

        if (!isFullWidth) {
            header()
        }
    }
}

@Composable
private fun ContentScope.Scrim(
    showBackgroundColor: Boolean,
    onClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrimBackgroundColor = OverlayShade.Colors.ScrimBackground
    Spacer(
        modifier =
            modifier
                .element(OverlayShade.Elements.Scrim)
                .fillMaxSize()
                .thenIf(showBackgroundColor) { Modifier.background(scrimBackgroundColor) }
                .clickable(onClick = onClicked, interactionSource = null, indication = null)
    )
}

@Composable
private fun ContentScope.Panel(
    enableTransparency: Boolean,
    spec: VerticalExpandContainerSpec,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)?,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .disableSwipesWhenScrolling()
                .verticalExpandContainerBackground(
                    backgroundColor = OverlayShade.Colors.panelBackground(enableTransparency),
                    spec = spec,
                )
    ) {
        Column {
            header?.invoke()
            content()
        }
    }
}

@Composable
private fun Modifier.panelWidth(isFullWidthPanel: Boolean): Modifier {
    return if (isFullWidthPanel) {
        fillMaxWidth()
    } else {
        width(dimensionResource(id = R.dimen.shade_panel_width))
    }
}

@Composable
@ReadOnlyComposable
internal fun isFullWidthShade(): Boolean {
    return LocalWindowSizeClass.current.widthSizeClass == WindowWidthSizeClass.Compact
}

@Composable
private fun Modifier.panelContainerPadding(isFullWidthPanel: Boolean): Modifier {
    if (isFullWidthPanel) {
        return this
    }
    val horizontalPaddingDp = dimensionResource(R.dimen.shade_panel_margin_horizontal)
    return windowInsetsPadding(
        WindowInsets.safeContent.union(
            WindowInsets(left = horizontalPaddingDp, right = horizontalPaddingDp)
        )
    )
}

object OverlayShade {
    object Elements {
        val Scrim = ElementKey("OverlayShadeScrim", contentPicker = LowestZIndexContentPicker)
        val Panel =
            ElementKey(
                "OverlayShadePanel",
                contentPicker = LowestZIndexContentPicker,
                placeAllCopies = true,
            )
    }

    object Colors {
        val ScrimBackground: Color
            @Composable
            @ReadOnlyComposable
            get() = Color(shadePanelScrimBehind(LocalContext.current))

        @Composable
        @ReadOnlyComposable
        fun panelBackground(transparencyEnabled: Boolean): Color {
            return Color(
                shadePanel(
                    context = LocalContext.current,
                    blurSupported = transparencyEnabled,
                    withScrim = false,
                )
            )
        }
    }

    object Dimensions {
        val PanelCornerRadius: Dp
            @Composable
            @ReadOnlyComposable
            get() = dimensionResource(R.dimen.overlay_shade_panel_shape_radius)
    }

    @Composable
    fun rememberShadeExpansionMotion(isFullWidth: Boolean): VerticalExpandContainerSpec {
        val radius = Dimensions.PanelCornerRadius
        return remember(radius, isFullWidth) {
            VerticalExpandContainerSpec(isFloating = !isFullWidth, radius = radius)
        }
    }
}
