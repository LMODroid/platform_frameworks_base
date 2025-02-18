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
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.android.packageinstaller.v2.model.UnarchiveRepository
import com.android.packageinstaller.v2.viewmodel.UnarchiveViewModel
import com.android.packageinstaller.v2.viewmodel.UnarchiveViewModelFactory

class UnarchiveLaunch: FragmentActivity() {

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        unarchiveRepository = UnarchiveRepository(applicationContext)
        unarchiveViewModel = ViewModelProvider(
            this, UnarchiveViewModelFactory(application, unarchiveRepository!!)
        )[UnarchiveViewModel::class.java]
    }
}
