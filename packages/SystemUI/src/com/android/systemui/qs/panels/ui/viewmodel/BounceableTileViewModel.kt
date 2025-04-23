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

package com.android.systemui.qs.panels.ui.viewmodel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.compose.animation.Bounceable

class BounceableTileViewModel : Bounceable {
    private val animatableContainerBounce = Animatable(0.dp, Dp.VectorConverter)
    private val animatableIconBounceScale = Animatable(1f)
    private val animatableTextBounceScale = Animatable(1f)

    override val bounce: Dp
        get() = animatableContainerBounce.value

    val iconBounceScale: Float
        get() = animatableIconBounceScale.value

    val textBounceScale: Float
        get() = animatableTextBounceScale.value

    suspend fun animateContainerBounce() {
        animatableContainerBounce.animateToBounce(BounceSize)
        animatableContainerBounce.animateToRest(ContainerBounceAtRest)
    }

    suspend fun animateContentBounce(iconOnly: Boolean) {
        if (iconOnly) {
            animateIconBounce()
        } else {
            animateTextBounce()
        }
    }

    private suspend fun animateIconBounce() {
        animatableIconBounceScale.animateToBounce(ICON_BOUNCE_SCALE)
        animatableIconBounceScale.animateToRest(SCALE_BOUNCE_AT_REST)
    }

    private suspend fun animateTextBounce() {
        animatableTextBounceScale.animateToBounce(TEXT_BOUNCE_SCALE)
        animatableTextBounceScale.animateToRest(SCALE_BOUNCE_AT_REST)
    }

    private suspend fun <T, V : AnimationVector> Animatable<T, V>.animateToBounce(targetValue: T) {
        animateTo(
            targetValue,
            tween(durationMillis = BOUNCE_DURATION_MILLIS, easing = BounceStartEasing),
        )
    }

    private suspend fun <T, V : AnimationVector> Animatable<T, V>.animateToRest(targetValue: T) {
        animateTo(
            targetValue,
            tween(durationMillis = BOUNCE_DURATION_MILLIS, easing = BounceEndEasing),
        )
    }

    private companion object {
        val BounceSize = 8.dp
        val BounceStartEasing = CubicBezierEasing(.05f, 0f, 0f, 1f)
        val BounceEndEasing = CubicBezierEasing(1f, 0f, .95f, 1f)
        val ContainerBounceAtRest = 0.dp
        const val ICON_BOUNCE_SCALE = 1.1f
        const val TEXT_BOUNCE_SCALE = 1.06f
        const val SCALE_BOUNCE_AT_REST = 1f
        const val BOUNCE_DURATION_MILLIS = 167
    }
}
