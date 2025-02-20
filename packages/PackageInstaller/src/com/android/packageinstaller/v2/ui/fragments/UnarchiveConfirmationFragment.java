/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_APP_LABEL;
import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_INSTALLER_LABEL;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.android.packageinstaller.R;
import com.android.packageinstaller.v2.model.UnarchiveUserActionRequired;
import com.android.packageinstaller.v2.ui.UnarchiveActionListener;

public class UnarchiveConfirmationFragment extends DialogFragment {

    private static final String LOG_TAG = UnarchiveConfirmationFragment.class.getSimpleName();

    private Dialog mDialog;
    private Button mRestoreButton;
    private UnarchiveUserActionRequired mDialogData;
    private UnarchiveActionListener mUnarchiveActionListener;

    UnarchiveConfirmationFragment() {
        // Required for DialogFragment
    }

    /**
     * Creates a new instance of this fragment with necessary data set as fragment arguments
     *
     * @param dialogData {@link UnarchiveUserActionRequired} object containing data to display
     *         in the dialog
     * @return an instance of the fragment
     */
    public static UnarchiveConfirmationFragment newInstance(
            @NonNull UnarchiveUserActionRequired dialogData) {
        Bundle args = new Bundle();
        args.putString(ARGS_APP_LABEL, dialogData.getAppTitle());
        args.putString(ARGS_INSTALLER_LABEL, dialogData.getInstallerTitle());

        UnarchiveConfirmationFragment dialog = new UnarchiveConfirmationFragment();
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mUnarchiveActionListener = (UnarchiveActionListener) context;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        setDialogData(requireArguments());

        Log.i(LOG_TAG, "Creating " + LOG_TAG + "\n" + mDialogData);

        mDialog = new AlertDialog.Builder(requireActivity())
                .setTitle(
                        String.format(getContext().getString(R.string.unarchive_application_title),
                                mDialogData.getAppTitle(), mDialogData.getInstallerTitle()))
                .setMessage(R.string.unarchive_body_text)
                .setPositiveButton(R.string.restore,
                        (dialog, which) -> mUnarchiveActionListener.beginUnarchive())
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                })
                .create();
        return mDialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mDialog != null) {
            mRestoreButton = ((AlertDialog) mDialog).getButton(DialogInterface.BUTTON_POSITIVE);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mRestoreButton != null) {
            mRestoreButton.setEnabled(false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mRestoreButton != null) {
            mRestoreButton.setEnabled(true);
        }
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (isAdded()) {
            requireActivity().finish();
        }
    }

    private void setDialogData(Bundle args) {
        String appTitle = args.getString(ARGS_APP_LABEL);
        String installerTitle = args.getString(ARGS_INSTALLER_LABEL);

        mDialogData = new UnarchiveUserActionRequired(appTitle, installerTitle);
    }
}
