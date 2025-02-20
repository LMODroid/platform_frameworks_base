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

import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_INSTALLER_LABEL;
import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_INSTALLER_PACKAGE;
import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_PENDING_INTENT;
import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_REQUIRED_BYTES;
import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_UNARCHIVAL_STATUS;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageInstaller;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.android.packageinstaller.R;
import com.android.packageinstaller.v2.model.UnarchiveError;
import com.android.packageinstaller.v2.ui.UnarchiveActionListener;

public class UnarchiveErrorFragment extends DialogFragment implements
        DialogInterface.OnClickListener {

    private static final String LOG_TAG = UnarchiveErrorFragment.class.getSimpleName();
    private UnarchiveError mDialogData;
    private UnarchiveActionListener mUnarchiveActionListener;

    public UnarchiveErrorFragment() {
        // Required for DialogFragment
    }

    /**
     * Creates a new instance of this fragment with necessary data set as fragment arguments
     *
     * @param dialogData {@link UnarchiveError} object containing data to display
     *                   in the dialog
     * @return an instance of the fragment
     */
    public static UnarchiveErrorFragment newInstance(UnarchiveError dialogData) {
        Bundle args = new Bundle();
        args.putInt(ARGS_UNARCHIVAL_STATUS, dialogData.getUnarchivalStatus());
        args.putLong(ARGS_REQUIRED_BYTES, dialogData.getRequiredBytes());
        args.putString(ARGS_INSTALLER_LABEL, dialogData.getInstallerAppTitle());
        args.putString(ARGS_INSTALLER_PACKAGE, dialogData.getInstallerPackageName());
        args.putParcelable(ARGS_PENDING_INTENT, dialogData.getPendingIntent());

        UnarchiveErrorFragment dialog = new UnarchiveErrorFragment();
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mUnarchiveActionListener = (UnarchiveActionListener) context;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        setDialogData(requireArguments());
        Log.i(LOG_TAG, "Creating " + LOG_TAG + "\n" + mDialogData);

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(requireActivity());
        setDialogContent(dialogBuilder, mDialogData.getUnarchivalStatus(),
                mDialogData.getInstallerAppTitle(), mDialogData.getRequiredBytes());
        return dialogBuilder.create();
    }

    private void setDialogData(Bundle args) {
        int status = args.getInt(ARGS_UNARCHIVAL_STATUS, -1);
        PendingIntent pendingIntent = args.getParcelable(ARGS_PENDING_INTENT, PendingIntent.class);
        long requiredBytes = args.getLong(ARGS_REQUIRED_BYTES);
        String installerPkgName = args.getString(ARGS_INSTALLER_PACKAGE);
        String installerAppTitle = args.getString(ARGS_INSTALLER_LABEL);

        mDialogData = new UnarchiveError(
                status, installerPkgName, installerAppTitle, requiredBytes, pendingIntent
        );
    }

    private void setDialogContent(AlertDialog.Builder builder, int status, String installerAppTitle,
                                  long requiredBytes) {
        switch (status) {
            case PackageInstaller.UNARCHIVAL_ERROR_USER_ACTION_NEEDED ->
                builder.setTitle(getString(R.string.unarchive_action_required_title))
                    .setMessage(getString(R.string.unarchive_action_required_body))
                    .setPositiveButton(R.string.unarchive_action_required_continue, this)
                    .setNegativeButton(R.string.close, this);

            case PackageInstaller.UNARCHIVAL_ERROR_INSUFFICIENT_STORAGE ->
                builder.setTitle(getString(R.string.unarchive_error_storage_title))
                    .setMessage(String.format(getString(R.string.unarchive_error_storage_body),
                        Formatter.formatShortFileSize(getActivity(), requiredBytes)))
                    .setPositiveButton(R.string.unarchive_clear_storage_button, this)
                    .setNegativeButton(R.string.close, this);

            case PackageInstaller.UNARCHIVAL_ERROR_NO_CONNECTIVITY ->
                builder.setTitle(getString(R.string.unarchive_error_offline_title))
                    .setMessage(getString(R.string.unarchive_error_offline_body))
                    .setPositiveButton(android.R.string.ok, this);

            case PackageInstaller.UNARCHIVAL_ERROR_INSTALLER_DISABLED ->
                builder.setTitle(String.format(
                        getString(R.string.unarchive_error_installer_disabled_title),
                        installerAppTitle))
                    .setMessage(String.format(
                        getString(R.string.unarchive_error_installer_disabled_body),
                        installerAppTitle))
                    .setPositiveButton(R.string.external_sources_settings, this)
                    .setNegativeButton(R.string.close, this);

            case PackageInstaller.UNARCHIVAL_ERROR_INSTALLER_UNINSTALLED ->
                builder.setTitle(String.format(
                        getString(R.string.unarchive_error_installer_uninstalled_title),
                        installerAppTitle))
                    .setMessage(String.format(
                        getString(R.string.unarchive_error_installer_uninstalled_body),
                        installerAppTitle))
                    .setPositiveButton(android.R.string.ok, this);

            case PackageInstaller.UNARCHIVAL_GENERIC_ERROR ->
                builder.setTitle(getString(R.string.unarchive_error_generic_title))
                    .setMessage(getString(R.string.unarchive_error_generic_body))
                    .setPositiveButton(android.R.string.ok, this);

            default ->
                // This should never happen through normal API usage.
                throw new IllegalArgumentException("Invalid unarchive status " + status);
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which != Dialog.BUTTON_POSITIVE) {
            return;
        }
        mUnarchiveActionListener.handleUnarchiveErrorAction(
                mDialogData.getUnarchivalStatus(), mDialogData.getInstallerPackageName(),
                mDialogData.getPendingIntent());
    }


    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        if (isAdded()) {
            getActivity().finish();
        }
    }
}
