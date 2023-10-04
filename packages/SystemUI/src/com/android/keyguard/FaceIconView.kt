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
import android.graphics.ColorFilter
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.SparseArray
import android.view.View
import android.view.ViewTreeObserver.OnPreDrawListener
import android.widget.FrameLayout

import androidx.core.view.isVisible

import com.android.app.animation.Interpolators
import com.android.systemui.R;
import com.android.systemui.statusbar.KeyguardAffordanceView

import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.SimpleColorFilter
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback

class FaceIconView(
    context: Context?, attrs: AttributeSet?
) : FrameLayout(context, attrs) {

    private var faceIcon: FaceIcon? = null

    init {
        setupLottieView()
    }

    private fun setupLottieView() {
        val lottieView = LottieView(context).apply {
            setFailureListener {
                // If lottie files failed to load,
                // It will fallback to normal icon.
                setupIconView()
            }
        }
        updateView(lottieView)
    }

    private fun setupIconView() {
        val iconView = IconView(context)
        updateView(iconView)
    }

    private fun updateView(newView: FaceIcon) {
        faceIcon = newView ?: return
        removeAllViews()
        addView(faceIcon as View)
    }

    fun updateColor(color: Int) {
        faceIcon?.updateColor(color)
    }

    fun updateState(newState: Int) {
        faceIcon?.updateState(newState)
    }

    fun updateVisibility(visible: Boolean): Boolean {
        return faceIcon?.updateVisibility(visible) ?: false
    }

    private interface FaceIcon {
        fun updateColor(color: Int)
        fun updateState(newState: Int)
        fun updateVisibility(visible: Boolean): Boolean
    }

    class LottieView(context: Context) : LottieAnimationView(context), FaceIcon {

        private var oldState: Int = 0
        private var lottieColor: Int = -1 // white

        init {
            // Load a lottie file to find if it's present or not
            setAnimation(R.raw.lottie_face_unlock_running)
        }

        override fun updateColor(color: Int) {
            lottieColor = color
            updateColor()
        }

        override fun updateState(newState: Int) {
            update(newState)
        }

        override fun updateVisibility(visible: Boolean): Boolean {
            if (isVisible == visible) return false
            isVisible = visible
            return true
        }

        private fun updateColor() {
            val filter = SimpleColorFilter(lottieColor)
            val keyPath = KeyPath("**")
            val valueCallback: LottieValueCallback<ColorFilter> = LottieValueCallback(filter)
            addValueCallback(keyPath, LottieProperty.COLOR_FILTER, valueCallback)
        }

        private fun update(newState: Int) {
            if (oldState == newState) return
            oldState = newState
            val lottieRes = getLottieForState(newState)
            setAnimation(lottieRes)
            // Don't repeat for success state
            repeatCount = if (newState == STATE_FACE_SUCCESS) {
                0
            } else {
                LottieDrawable.INFINITE
            }
            // Update color for the new lottie
            // since lottie file has white as default but we want to show wallpaper accent.
            updateColor()
            playAnimation()
        }

        private fun getLottieForState(state: Int): Int {
            val lottieRes: Int = when (state) {
                STATE_FACE_SCANNING -> R.raw.lottie_face_unlock_running
                STATE_FACE_FAILED -> R.raw.lottie_face_unlock_running
                STATE_FACE_SUCCESS -> R.raw.lottie_face_unlock_success
                else -> throw IllegalArgumentException()
            }
            return lottieRes
        }
    }

    class IconView(context: Context) : KeyguardAffordanceView(context), FaceIcon {

        private var iconColor = 0
        private var oldState = 0
        private var state = 0
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

        override fun updateColor(color: Int) {
            updateIconColor(iconColor)
        }

        override fun updateState(newState: Int) {
            update(newState)
        }

        override fun updateVisibility(visible: Boolean): Boolean {
            return updateIconVisibility(visible)
        }

        override fun onConfigurationChanged(newConfig: Configuration?) {
            super.onConfigurationChanged(newConfig)
            drawableCache.clear()
        }

        private fun updateIconVisibility(visible: Boolean): Boolean {
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

        private fun update(newState: Int) {
            oldState = state
            state = newState
            if (!preDrawRegistered) {
                preDrawRegistered = true
                viewTreeObserver.addOnPreDrawListener(onPreDrawListener)
            }
        }

        private fun updateIconColor(iconColor: Int) {
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
                STATE_FACE_SCANNING -> R.drawable.ic_face
                STATE_FACE_FAILED -> R.drawable.ic_face
                STATE_FACE_SUCCESS -> R.drawable.ic_face_unlocked
                else -> throw IllegalArgumentException()
            }
            return iconRes
        }
    }

    companion object {
        const val STATE_FACE_SCANNING = 1
        const val STATE_FACE_FAILED = 2
        const val STATE_FACE_SUCCESS = 3
    }
}
