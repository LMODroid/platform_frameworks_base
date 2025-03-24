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

package com.android.systemui.common.ui.compose

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.pageLeft
import androidx.compose.ui.semantics.pageRight
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.android.compose.modifiers.width
import com.android.systemui.common.ui.compose.PagerDotsDefaults.SPRING_STIFFNESS
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun PagerDots(
    pagerState: PagerState,
    activeColor: Color,
    nonActiveColor: Color,
    modifier: Modifier = Modifier,
    dotSize: Dp = 6.dp,
    spaceSize: Dp = 4.dp,
) {
    if (pagerState.pageCount < 2) return

    val activeDotWidth = dotSize * 2
    // Active dot + inactive dots + spacing
    val totalWidth =
        activeDotWidth +
            (dotSize * (pagerState.pageCount - 1)) +
            (spaceSize * (pagerState.pageCount - 1))
    val coroutineScope = rememberCoroutineScope()

    // List of animated colors, one per page
    val colors =
        remember(pagerState.pageCount) {
            List(pagerState.pageCount) { page ->
                Animatable(if (page == pagerState.currentPage) activeColor else nonActiveColor)
            }
        }
    LaunchedEffect(pagerState.currentPage, colors) {
        colors.forEachIndexed { index, animatable ->
            val targetColor =
                if (index == pagerState.currentPage) {
                    activeColor
                } else {
                    nonActiveColor
                }
            if (animatable.targetValue != targetColor) {
                launch {
                    animatable.animateTo(
                        targetColor,
                        animationSpec = spring(stiffness = SPRING_STIFFNESS),
                    )
                }
            }
        }
    }

    Canvas(
        modifier
            .width { totalWidth.roundToPx() }
            .height(dotSize)
            .pagerDotsSemantics(pagerState, coroutineScope)
    ) {
        val rtl = layoutDirection == LayoutDirection.Rtl
        scale(if (rtl) -1f else 1f, 1f, Offset(0f, center.y)) {
            // The impacted index is the neighbor of the active index, in the direction dictated
            // from the page offset. The impacted dot will have its width modified
            val impactedIndex =
                if (pagerState.currentPageOffsetFraction >= 0) {
                    pagerState.currentPage + 1
                } else {
                    pagerState.currentPage - 1
                }
            val dotSizePx = dotSize.toPx()
            val activeDotWidthPx = activeDotWidth.toPx()
            val spacingPx = spaceSize.toPx()
            val offsetFraction = abs(pagerState.currentPageOffsetFraction)
            val cornerRadius = CornerRadius(size.height / 2)

            var x = 0f
            repeat(pagerState.pageCount) { page ->
                val width =
                    when (page) {
                        impactedIndex -> dotSizePx + dotSizePx * offsetFraction
                        pagerState.currentPage -> activeDotWidthPx - dotSizePx * offsetFraction
                        else -> dotSizePx
                    }

                drawRoundRect(
                    color = colors[page].value,
                    cornerRadius = cornerRadius,
                    topLeft = Offset(x, 0f),
                    size = Size(width, size.height),
                )
                x += width + spacingPx
            }
        }
    }
}

private fun Modifier.pagerDotsSemantics(
    pagerState: PagerState,
    coroutineScope: CoroutineScope,
): Modifier {
    return then(
        Modifier.semantics {
            pageLeft {
                if (pagerState.canScrollBackward) {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                    }
                    true
                } else {
                    false
                }
            }
            pageRight {
                if (pagerState.canScrollForward) {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                    true
                } else {
                    false
                }
            }
            stateDescription = "Page ${pagerState.settledPage + 1} of ${pagerState.pageCount}"
        }
    )
}

private object PagerDotsDefaults {
    const val SPRING_STIFFNESS = 1600f
}
