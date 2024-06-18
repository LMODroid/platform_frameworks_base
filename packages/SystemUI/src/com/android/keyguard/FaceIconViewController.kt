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
 * limitations under the License.
 */

package com.android.keyguard

import android.content.res.Configuration
import android.content.res.Resources
import android.hardware.biometrics.BiometricSourceType
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import com.android.keyguard.FaceIconView.Companion.STATE_FACE_FAILED
import com.android.keyguard.FaceIconView.Companion.STATE_FACE_SCANNING
import com.android.keyguard.FaceIconView.Companion.STATE_FACE_SUCCESS
import com.android.keyguard.KeyguardUpdateMonitor
import com.android.keyguard.KeyguardUpdateMonitorCallback
import com.android.settingslib.Utils
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryFaceAuthInteractor
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.res.R
import com.android.systemui.statusbar.StatusBarState
import com.android.systemui.statusbar.phone.KeyguardBypassController
import com.android.systemui.statusbar.policy.ConfigurationController
import com.android.systemui.statusbar.policy.ConfigurationController.ConfigurationListener
import com.android.systemui.statusbar.policy.KeyguardStateController
import com.android.systemui.util.ViewController

import javax.inject.Inject

/** Controls the [FaceIconView] on the lockscreen.  */
@SysUISingleton
class FaceIconViewController @Inject constructor(
    view: FaceIconView?,
    private val keyguardUpdateMonitor: KeyguardUpdateMonitor,
    private val statusBarStateController: StatusBarStateController,
    private val configurationController: ConfigurationController,
    private val keyguardBypassController: KeyguardBypassController,
    private val keyguardStateController: KeyguardStateController,
    private val deviceEntryFaceAuthInteractor: DeviceEntryFaceAuthInteractor,
    @Main private val resources: Resources,
) : ViewController<FaceIconView?>(view) {

    private var simLocked = false
    private var statusBarState: Int = StatusBarState.SHADE
    private var lastState = 0
    private var dozing = false
    private var faceDetectionRunning = false

    override fun onInit() {
        mView?.setAccessibilityDelegate(accessibilityDelegate)
    }

    override fun onViewAttached() {
        setStatusBarState(statusBarStateController.getState())
        dozing = statusBarStateController.isDozing()
        statusBarStateController.addCallback(statusBarStateListener)
        configurationController.addCallback(configurationListener)
        keyguardUpdateMonitor.registerCallback(updateMonitorCallback)
        keyguardStateController.addCallback(keyguardMonitorCallback)
        simLocked = keyguardUpdateMonitor.isSimPinSecure()
        update()
    }

    override fun onViewDetached() {
        statusBarStateController.removeCallback(statusBarStateListener)
        configurationController.removeCallback(configurationListener)
        keyguardUpdateMonitor.removeCallback(updateMonitorCallback)
        keyguardStateController.removeCallback(keyguardMonitorCallback)
    }

    private fun update(force: Boolean = false) {
        val faceIconView = mView ?: return
        val newState = state
        var shouldUpdate = lastState != newState || force
        // Block updates on success when bypass enabled.
        if (newState == STATE_FACE_SUCCESS && keyguardBypassController.canBypass()) {
            shouldUpdate = false
        }
        if (shouldUpdate && faceIconView.visibility != View.GONE) {
            faceIconView.updateState(newState)
        }
        lastState = newState
        updateIconVisibility()
    }

    private fun setStatusBarState(statusBarState: Int) {
        this.statusBarState = statusBarState
        updateIconVisibility()
    }

    private fun updateIconVisibility(): Boolean {
        val faceIconView = mView ?: return false
        val isKeyguard = keyguardStateController.isShowing()
        val faceAuthAvailable = deviceEntryFaceAuthInteractor.isFaceAuthEnabledAndEnrolled()
                && (faceDetectionRunning || deviceEntryFaceAuthInteractor.isAuthenticated())
        // Make sure it only visible in lockscreen with face auth available.
        val invisible = dozing || !faceAuthAvailable || !isKeyguard
        return faceIconView.updateVisibility(!invisible)
    }

    private fun updateColor() {
        mView?.let { faceIconView ->
            val iconColor = Utils.getColorAttrDefaultColor(
                    faceIconView.context,
                    R.attr.wallpaperTextColorAccent
            )
            faceIconView.updateColor(iconColor)
        }
    }

    fun setAlpha(alpha: Float) {
        mView?.setAlpha(alpha)
    }

    private val state: Int
        get() = if (deviceEntryFaceAuthInteractor.isAuthenticated()
                    && (keyguardStateController.canDismissLockScreen()
                    || !keyguardStateController.isShowing()
                    || keyguardStateController.isKeyguardGoingAway()
                    || keyguardStateController.isKeyguardFadingAway()) && !simLocked
        ) {
            STATE_FACE_SUCCESS
        } else if (deviceEntryFaceAuthInteractor.isRunning()) {
            STATE_FACE_SCANNING
        } else {
            STATE_FACE_FAILED
        }

    private val statusBarStateListener: StatusBarStateController.StateListener =
        object : StatusBarStateController.StateListener {
            override fun onDozingChanged(isDozing: Boolean) {
                if (dozing != isDozing) {
                    dozing = isDozing
                    update()
                }
            }

            override fun onStateChanged(newState: Int) {
                setStatusBarState(newState)
            }
        }

    private val configurationListener: ConfigurationListener = object : ConfigurationListener {

        override fun onDensityOrFontScaleChanged() {
            val faceIconView = mView ?: return
            val lp: ViewGroup.LayoutParams = faceIconView.getLayoutParams()
            lp.width = faceIconView.resources.getDimensionPixelSize(
                R.dimen.keyguard_face_icon_width
            )
            lp.height = faceIconView.resources.getDimensionPixelSize(
                R.dimen.keyguard_face_icon_height
            )
            faceIconView.setLayoutParams(lp)
            update(true /* force */)
        }

        override fun onLocaleListChanged() {
            mView?.let { faceIconView ->
                faceIconView.setContentDescription(
                    faceIconView.resources.getText(R.string.accessibility_unlock_button)
                )
                update(true /* force */)
            }
        }

        override fun onConfigChanged(newConfig: Configuration) {
            updateColor()
        }

        override fun onUiModeChanged() {
            updateColor()
        }

        override fun onThemeChanged() {
            updateColor()
        }

    }

    private val updateMonitorCallback: KeyguardUpdateMonitorCallback =
        object : KeyguardUpdateMonitorCallback() {
            override fun onSimStateChanged(subId: Int, slotId: Int, simState: Int) {
                simLocked = this@FaceIconViewController.keyguardUpdateMonitor.isSimPinSecure()
                update()
            }

            override fun onKeyguardVisibilityChanged(showing: Boolean) {
                update()
            }

            override fun onBiometricAcquired(biometricSourceType: BiometricSourceType, acquireInfo: Int) {
                // Later
            }

            override fun onBiometricAuthenticated(
                userId: Int,
                biometricSourceType: BiometricSourceType,
                isStrongBiometric: Boolean
            ) {
                if (biometricSourceType == BiometricSourceType.FACE) {
                    update()
                }
            }

            override fun onBiometricAuthFailed(biometricSourceType: BiometricSourceType) {
                // Later
            }

            override fun onBiometricRunningStateChanged(
                running: Boolean,
                biometricSourceType: BiometricSourceType?
            ) {
                if (biometricSourceType == BiometricSourceType.FACE) {
                    faceDetectionRunning = running
                    update()
                }
            }

            override fun onStrongAuthStateChanged(userId: Int) {
                update()
            }
        }

    private val keyguardMonitorCallback: KeyguardStateController.Callback =
        object : KeyguardStateController.Callback {
            override fun onKeyguardShowingChanged() {
                update()
            }

            override fun onKeyguardFadingAwayChanged() {
                update()
            }

            override fun onUnlockedChanged() {
                update()
            }
    }

    private val accessibilityDelegate: View.AccessibilityDelegate =
        object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfo
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                if (state == STATE_FACE_SCANNING) {
                    //Avoid 'button' to be spoken for scanning face
                    info.className = FaceIconView::class.java.name
                    info.contentDescription = this@FaceIconViewController.resources.getString(
                        R.string.accessibility_scanning_face
                    )
                }
            }
        }
}
