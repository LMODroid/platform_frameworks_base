package com.android.systemui.keyguard.ui.view.layout.sections

import android.content.res.Resources
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.android.keyguard.FaceIconView
import com.android.keyguard.FaceIconViewController
import com.android.keyguard.KeyguardUpdateMonitor
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryFaceAuthInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.shared.model.KeyguardSection
import com.android.systemui.keyguard.ui.viewmodel.DreamingToLockscreenTransitionViewModel
import com.android.systemui.lifecycle.repeatWhenAttached
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.res.R
import com.android.systemui.statusbar.phone.KeyguardBypassController
import com.android.systemui.statusbar.policy.ConfigurationController
import com.android.systemui.statusbar.policy.KeyguardStateController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.android.systemui.dagger.SysUISingleton

@SysUISingleton
open class FaceIconSection
@Inject
constructor(
    private val layoutInflater: LayoutInflater,
    private val keyguardUpdateMonitor: KeyguardUpdateMonitor,
    private val statusBarStateController: StatusBarStateController,
    private val configurationController: ConfigurationController,
    private val keyguardBypassController: KeyguardBypassController,
    private val keyguardStateController: KeyguardStateController,
    private val deviceEntryFaceAuthInteractor: DeviceEntryFaceAuthInteractor,
    private val keyguardInteractor: KeyguardInteractor,
    private val dreamingToLockscreenTransitionViewModel: DreamingToLockscreenTransitionViewModel,
    @Main private val resources: Resources,
) : KeyguardSection() {

    private lateinit var faceIconView: FaceIconView
    private var controller: FaceIconViewController? = null

    override fun addViews(constraintLayout: ConstraintLayout) {
        faceIconView = layoutInflater.inflate(
            R.layout.keyguard_face_icon_view,
            constraintLayout,
            false
        ) as FaceIconView
        constraintLayout.addView(faceIconView)
    }

    override fun bindData(constraintLayout: ConstraintLayout) {
        controller = FaceIconViewController(
            faceIconView,
            keyguardUpdateMonitor,
            statusBarStateController,
            configurationController,
            keyguardBypassController,
            keyguardStateController,
            deviceEntryFaceAuthInteractor,
            resources
        ).apply { init() }

        faceIconView.repeatWhenAttached {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    dreamingToLockscreenTransitionViewModel.lockscreenAlpha.collect { alphaValue ->
                        faceIconView.alpha = alphaValue
                    }
                }
            }
        }
    }

    override fun applyConstraints(constraintSet: ConstraintSet) {
        val faceIconId = R.id.face_icon_view
        val statusBarHeight = resources.getDimensionPixelSize(
            com.android.internal.R.dimen.status_bar_height
        )

        constraintSet.apply {
            connect(faceIconId, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, statusBarHeight)
            connect(faceIconId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            connect(faceIconId, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)

            constrainWidth(faceIconId, resources.getDimensionPixelSize(R.dimen.keyguard_face_icon_width))
            constrainHeight(faceIconId, resources.getDimensionPixelSize(R.dimen.keyguard_face_icon_height))
        }
    }

    override fun removeViews(constraintLayout: ConstraintLayout) {
        constraintLayout.removeView(faceIconView)
        controller = null
    }
}
