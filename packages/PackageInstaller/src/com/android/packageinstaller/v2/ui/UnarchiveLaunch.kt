/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.packageinstaller.v2.ui

import android.os.Bundle
import android.os.Process
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import com.android.packageinstaller.v2.model.UnarchiveAborted
import com.android.packageinstaller.v2.model.UnarchiveRepository
import com.android.packageinstaller.v2.model.UnarchiveStage
import com.android.packageinstaller.v2.model.UnarchiveUserActionRequired
import com.android.packageinstaller.v2.ui.fragments.UnarchiveConfirmationFragment
import com.android.packageinstaller.v2.viewmodel.UnarchiveViewModel
import com.android.packageinstaller.v2.viewmodel.UnarchiveViewModelFactory

class UnarchiveLaunch : FragmentActivity(), UnarchiveActionListener {

    companion object {
        @JvmField val EXTRA_CALLING_PKG_UID: String =
            UnarchiveLaunch::class.java.packageName + ".callingPkgUid"
        @JvmField val EXTRA_CALLING_PKG_NAME: String =
            UnarchiveLaunch::class.java.packageName + ".callingPkgName"
        val TAG: String = UnarchiveLaunch::class.java.simpleName
        private const val TAG_DIALOG = "dialog"
    }

    private var unarchiveViewModel: UnarchiveViewModel? = null
    private var unarchiveRepository: UnarchiveRepository? = null
    private var fragmentManager: FragmentManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        window.addSystemFlags(
            WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS
        )
        super.onCreate(savedInstanceState)

        fragmentManager = supportFragmentManager

        unarchiveRepository = UnarchiveRepository(applicationContext)
        unarchiveViewModel = ViewModelProvider(
            this, UnarchiveViewModelFactory(application, unarchiveRepository!!)
        )[UnarchiveViewModel::class.java]

        val info = UnarchiveRepository.CallerInfo(
            intent.getStringExtra(EXTRA_CALLING_PKG_NAME),
            intent.getIntExtra(EXTRA_CALLING_PKG_UID, Process.INVALID_UID)
        )

        unarchiveViewModel!!.preprocessIntent(intent, info)
        unarchiveViewModel!!.currentUnarchiveStage.observe(this) { stage: UnarchiveStage ->
            onUnarchiveStageChange(stage)
        }
    }

    private fun onUnarchiveStageChange(stage: UnarchiveStage) {
        when (stage.stageCode) {
            UnarchiveStage.STAGE_ABORTED -> {
                val aborted = stage as UnarchiveAborted
                setResult(aborted.activityResultCode)
                finish()
            }

            UnarchiveStage.STAGE_USER_ACTION_REQUIRED -> {
                val uar = stage as UnarchiveUserActionRequired
                val confirmationDialog = UnarchiveConfirmationFragment.newInstance(uar)
                showDialogInner(confirmationDialog)
            }
        }
    }

    override fun beginUnarchive() {
        unarchiveViewModel!!.beginUnarchive()
    }

    /**
     * Replace any visible dialog by the dialog returned by UnarchiveRepository
     *
     * @param newDialog The new dialog to display
     */
    private fun showDialogInner(newDialog: DialogFragment?) {
        val currentDialog = fragmentManager!!.findFragmentByTag(TAG_DIALOG) as DialogFragment?
        currentDialog?.dismissAllowingStateLoss()
        newDialog?.show(fragmentManager!!, TAG_DIALOG)
    }
}
