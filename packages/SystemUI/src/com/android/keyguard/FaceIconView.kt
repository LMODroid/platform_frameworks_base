/*
 * Copyright (C) 2023 The LibreMobileOS Foundation
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
 * limitations under the License
 */

package com.android.keyguard

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.SparseArray
import android.view.ViewTreeObserver.OnPreDrawListener
import com.android.systemui.R;
import com.android.systemui.animation.Interpolators
import com.android.systemui.statusbar.KeyguardAffordanceView

/**
 * Manages the different states of the face unlock icon.
 */
class FaceIconView(
    context: Context?, attrs: AttributeSet?
) : KeyguardAffordanceView(context, attrs) {

    private var iconColor = 0
    private var oldState = 0
    private var state = 0
    private var keyguardJustShown = false
    private var preDrawRegistered = false
    private val drawableCache = SparseArray<Drawable>()

    private val onPreDrawListener: OnPreDrawListener = object : OnPreDrawListener {
        override fun onPreDraw(): Boolean {
            viewTreeObserver.removeOnPreDrawListener(this)
            preDrawRegistered = false
            val newState = state
            val icon = getIcon(newState)
            setImageDrawable(icon, false)
            if (newState == STATE_FACE_SCANNING) {
                announceForAccessibility(
                    resources.getString(
                        R.string.accessibility_scanning_face
                    )
                )
            }
            return true
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        drawableCache.clear()
    }

    /**
     * Update the icon visibility
     * @return true if the visibility changed
     */
    fun updateIconVisibility(visible: Boolean): Boolean {
        val wasVisible = visibility == VISIBLE
        if (visible != wasVisible) {
            visibility = if (visible) VISIBLE else INVISIBLE
            animate().cancel()
            if (visible) {
                scaleX = 0f
                scaleY = 0f
                animate()
                    .setInterpolator(Interpolators.LINEAR_OUT_SLOW_IN)
                    .scaleX(1f)
                    .scaleY(1f)
                    .withLayer()
                    .setDuration(233)
                    .start()
            }
            return true
        }
        return false
    }

    fun update(newState: Int, keyguardJustShown: Boolean) {
        oldState = state
        state = newState
        this.keyguardJustShown = keyguardJustShown
        if (!preDrawRegistered) {
            preDrawRegistered = true
            viewTreeObserver.addOnPreDrawListener(onPreDrawListener)
        }
    }

    fun updateColor(iconColor: Int) {
        if (this.iconColor == iconColor) {
            return
        }
        drawableCache.clear()
        this.iconColor = iconColor
        imageTintList = ColorStateList.valueOf(iconColor)
    }

    private fun getIcon(newState: Int): Drawable {
        val iconRes = getIconForState(newState)
        if (!drawableCache.contains(iconRes)) {
            drawableCache.put(iconRes, context.getDrawable(iconRes))
        }
        return drawableCache[iconRes]
    }

    private fun getIconForState(state: Int): Int {
        val iconRes: Int = when (state) {
            STATE_FACE_SCANNING -> R.drawable.ic_face // update icon later
            STATE_FACE_FAILED -> R.drawable.ic_face // Animate for failed state
            STATE_FACE_SUCCESS -> R.drawable.ic_face_unlocked
            else -> throw IllegalArgumentException()
        }
        return iconRes
    }

    companion object {
        const val STATE_FACE_SCANNING = 1
        const val STATE_FACE_FAILED = 2
        const val STATE_FACE_SUCCESS = 3
    }
}
