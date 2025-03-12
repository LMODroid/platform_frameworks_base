/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.packageinstaller.v2.ui.fragments;

import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_APP_SNIPPET;
import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_INSTALL_TYPE;
import static com.android.packageinstaller.v2.model.PackageUtil.INSTALL_TYPE_NEW;
import static com.android.packageinstaller.v2.model.PackageUtil.INSTALL_TYPE_REINSTALL;
import static com.android.packageinstaller.v2.model.PackageUtil.INSTALL_TYPE_UPDATE;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.android.packageinstaller.R;
import com.android.packageinstaller.v2.model.InstallInstalling;
import com.android.packageinstaller.v2.model.PackageUtil.AppSnippet;

/**
 * Dialog to show when an install is in progress.
 */
public class InstallInstallingFragment extends DialogFragment {

    private static final String LOG_TAG = InstallInstallingFragment.class.getSimpleName();
    private InstallInstalling mDialogData;
    private AlertDialog mDialog;

    public InstallInstallingFragment() {
        // Required for DialogFragment
    }

    /**
     * Creates a new instance of this fragment with necessary data set as fragment arguments
     *
     * @param dialogData {@link InstallInstalling} object containing data to display in the
     *                   dialog
     * @return an instance of the fragment
     */
    public static InstallInstallingFragment newInstance(@NonNull InstallInstalling dialogData) {
        Bundle args = new Bundle();
        args.putParcelable(ARGS_APP_SNIPPET, dialogData.getAppSnippet());
        args.putInt(ARGS_INSTALL_TYPE, dialogData.getInstallType());

        InstallInstallingFragment fragment = new InstallInstallingFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        setDialogData(requireArguments());

        Log.i(LOG_TAG, "Creating " + LOG_TAG + "\n" + mDialogData);

        View dialogView = getLayoutInflater().inflate(R.layout.install_fragment_layout, null);
        dialogView.requireViewById(R.id.progress_bar).setVisibility(View.VISIBLE);
        dialogView.requireViewById(R.id.app_snippet).setVisibility(View.VISIBLE);
        ((ImageView) dialogView.requireViewById(R.id.app_icon))
            .setImageDrawable(mDialogData.getAppIcon());
        ((TextView) dialogView.requireViewById(R.id.app_label)).setText(mDialogData.getAppLabel());

        int titleRes = 0;
        switch (mDialogData.getInstallType()) {
            case INSTALL_TYPE_NEW -> titleRes = R.string.title_installing;
            case INSTALL_TYPE_UPDATE -> titleRes = R.string.title_updating;
            case INSTALL_TYPE_REINSTALL -> titleRes = R.string.title_reinstalling;
        }

        mDialog = new AlertDialog.Builder(requireContext())
            .setTitle(titleRes)
            .setView(dialogView)
            .create();

        this.setCancelable(false);

        return mDialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        mDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setEnabled(false);
    }

    private void setDialogData(Bundle args) {
        AppSnippet appSnippet = args.getParcelable(ARGS_APP_SNIPPET, AppSnippet.class);
        int installType = args.getInt(ARGS_INSTALL_TYPE);
        mDialogData = new InstallInstalling(appSnippet, installType);
    }
}
