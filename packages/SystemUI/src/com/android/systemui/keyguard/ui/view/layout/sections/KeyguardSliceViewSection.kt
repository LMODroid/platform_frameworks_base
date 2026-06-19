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
 *
 */

package com.android.systemui.keyguard.ui.view.layout.sections

import android.content.res.Resources
import android.os.Handler
import android.view.LayoutInflater
import androidx.constraintlayout.widget.Barrier
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.android.keyguard.KeyguardSliceView
import com.android.keyguard.KeyguardSliceViewController
import com.android.systemui.customization.R as customR
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.dump.DumpManager
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.shared.model.KeyguardSection
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.power.domain.interactor.PowerInteractor
import com.android.systemui.res.R
import com.android.systemui.settings.DisplayTracker
import com.android.systemui.statusbar.lockscreen.LockscreenSmartspaceController
import com.android.systemui.statusbar.policy.ConfigurationController
import javax.inject.Inject

class KeyguardSliceViewSection
@Inject
constructor(
    val smartspaceController: LockscreenSmartspaceController,
    val layoutInflater: LayoutInflater,
    @Main val handler: Handler,
    @Background val bgHandler: Handler,
    val activityStarter: ActivityStarter,
    val configurationController: ConfigurationController,
    val dumpManager: DumpManager,
    val displayTracker: DisplayTracker,
    val keyguardInteractor: KeyguardInteractor,
    val powerInteractor: PowerInteractor,
    @Main private val resources: Resources,
) : KeyguardSection() {
    private lateinit var sliceView: KeyguardSliceView

    override fun addViews(constraintLayout: ConstraintLayout) {
        if (smartspaceController.isEnabled) return

        sliceView =
            layoutInflater.inflate(R.layout.keyguard_slice_view, null, false) as KeyguardSliceView
        constraintLayout.addView(sliceView)
    }

    override fun bindData(constraintLayout: ConstraintLayout) {
        if (smartspaceController.isEnabled) return

        val controller =
            KeyguardSliceViewController(
                handler,
                bgHandler,
                sliceView,
                activityStarter,
                configurationController,
                dumpManager,
                displayTracker,
                keyguardInteractor,
                powerInteractor,
            )
        controller.init()
    }

    override fun applyConstraints(constraintSet: ConstraintSet) {
        if (smartspaceController.isEnabled) return

        val sliceViewId = R.id.keyguard_slice_view
        val faceIconId = R.id.face_icon_view
        val smallClockId = customR.id.lockscreen_clock_view

        constraintSet.apply {
            clear(sliceViewId, ConstraintSet.TOP)

            connect(
                sliceViewId,
                ConstraintSet.START,
                ConstraintSet.PARENT_ID,
                ConstraintSet.START,
            )
            connect(
                sliceViewId,
                ConstraintSet.END,
                ConstraintSet.PARENT_ID,
                ConstraintSet.END,
            )
            constrainHeight(sliceViewId, ConstraintSet.WRAP_CONTENT)

            val isSmallClockVisible = constraintSet.getVisibility(smallClockId) == ConstraintSet.VISIBLE

            if (isSmallClockVisible) {
                connect(
                    sliceViewId,
                    ConstraintSet.TOP,
                    smallClockId,
                    ConstraintSet.BOTTOM,
                )
            } else {
                connect(
                    sliceViewId,
                    ConstraintSet.TOP,
                    faceIconId,
                    ConstraintSet.BOTTOM,
                )
            }

            createBarrier(
                R.id.smart_space_barrier_bottom,
                Barrier.BOTTOM,
                0,
                *intArrayOf(sliceViewId),
            )
        }
    }

    override fun removeViews(constraintLayout: ConstraintLayout) {
        if (smartspaceController.isEnabled) return

        constraintLayout.removeView(R.id.keyguard_slice_view)
    }
}
