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

import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_ACTION_REASON;
import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_APP_SNIPPET;
import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_SOURCE_PKG;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
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
import com.android.packageinstaller.v2.model.InstallUserActionRequired;
import com.android.packageinstaller.v2.model.PackageUtil.AppSnippet;
import com.android.packageinstaller.v2.ui.InstallActionListener;

/**
 * Dialog to show when the installing app is an unknown source and needs AppOp grant to install
 * other apps.
 */
public class ExternalSourcesBlockedFragment extends DialogFragment {

    private static final String LOG_TAG = ExternalSourcesBlockedFragment.class.getSimpleName();
    @NonNull
    private InstallUserActionRequired mDialogData;
    @NonNull
    private InstallActionListener mInstallActionListener;
    @NonNull
    private AlertDialog mDialog;

    public ExternalSourcesBlockedFragment() {
        // Required for DialogFragment
    }

    /**
     * Creates a new instance of this fragment with necessary data set as fragment arguments
     *
     * @param dialogData {@link InstallUserActionRequired} object containing data to display
     *                   in the dialog
     * @return an instance of the fragment
     */
    public static ExternalSourcesBlockedFragment newInstance(
            @NonNull InstallUserActionRequired dialogData) {
        Bundle args = new Bundle();
        args.putInt(ARGS_ACTION_REASON, dialogData.getActionReason());
        args.putParcelable(ARGS_APP_SNIPPET, dialogData.getAppSnippet());
        args.putString(ARGS_SOURCE_PKG, dialogData.getUnknownSourcePackageName());

        ExternalSourcesBlockedFragment fragment = new ExternalSourcesBlockedFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mInstallActionListener = (InstallActionListener) context;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        setDialogData(requireArguments());

        View dialogView = getLayoutInflater().inflate(R.layout.install_fragment_layout, null);

        dialogView.requireViewById(R.id.app_snippet).setVisibility(View.VISIBLE);
        ((ImageView) dialogView.requireViewById(R.id.app_icon))
                .setImageDrawable(mDialogData.getAppIcon());
        ((TextView) dialogView.requireViewById(R.id.app_label)).setText(mDialogData.getAppLabel());

        TextView customMessage = dialogView.requireViewById(R.id.custom_message);
        customMessage.setText(R.string.message_external_source_blocked);
        customMessage.setVisibility(View.VISIBLE);

        Log.i(LOG_TAG, "Creating " + LOG_TAG + "\n" + mDialogData);
        mDialog = new AlertDialog.Builder(
                    requireContext(), R.style.Theme_MaterialAlertDialog_Variant)
                .setTitle(R.string.title_unknown_source_blocked)
                .setView(dialogView)
                .setPositiveButton(R.string.external_sources_settings,
                        (dialog, which) -> mInstallActionListener.sendUnknownAppsIntent(
                            mDialogData.getUnknownSourcePackageName()))
                .setNegativeButton(R.string.cancel,
                        (dialog, which) -> mInstallActionListener.onNegativeResponse(
                                mDialogData.getStageCode()))
                .create();
        return mDialog;
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        super.onCancel(dialog);
        mInstallActionListener.onNegativeResponse(mDialogData.getStageCode());
    }

    @Override
    public void onStart() {
        super.onStart();
        mDialog.getButton(DialogInterface.BUTTON_POSITIVE).setFilterTouchesWhenObscured(true);
    }

    @Override
    public void onPause() {
        super.onPause();
        // This prevents tapjacking since an overlay activity started in front of Pia will
        // cause Pia to be paused.
        mDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
    }

    @Override
    public void onResume() {
        super.onResume();
        mDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
    }

    private void setDialogData(Bundle args) {
        int actionReason = args.getInt(ARGS_ACTION_REASON);
        AppSnippet appSnippet = args.getParcelable(ARGS_APP_SNIPPET, AppSnippet.class);
        String sourcePkg = args.getString(ARGS_SOURCE_PKG);

        mDialogData = new InstallUserActionRequired(actionReason, appSnippet, false, null, null,
            sourcePkg);
    }
}
