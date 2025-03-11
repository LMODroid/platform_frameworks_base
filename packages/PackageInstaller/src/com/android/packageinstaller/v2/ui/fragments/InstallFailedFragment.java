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
import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_LEGACY_CODE;
import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_MESSAGE;
import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_RESULT_INTENT;
import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_SHOULD_RETURN_RESULT;
import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_STATUS_CODE;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.android.packageinstaller.R;
import com.android.packageinstaller.v2.model.InstallFailed;
import com.android.packageinstaller.v2.model.PackageUtil.AppSnippet;
import com.android.packageinstaller.v2.ui.InstallActionListener;

/**
 * Dialog to show when the installation failed. Depending on the failure code, an appropriate
 * message would be shown to the user. This dialog is shown only when the caller does not want the
 * install result back.
 */
public class InstallFailedFragment extends DialogFragment {

    private static final String LOG_TAG = InstallFailedFragment.class.getSimpleName();
    private InstallFailed mDialogData;
    private InstallActionListener mInstallActionListener;

    public InstallFailedFragment() {
        // Required for DialogFragment
    }

    /**
     * Creates a new instance of this fragment with necessary data set as fragment arguments
     *
     * @param dialogData {@link InstallFailed} object containing data to display in the
     *                   dialog
     * @return an instance of the fragment
     */
    public static InstallFailedFragment newInstance(@NonNull InstallFailed dialogData) {
        Bundle args = new Bundle();
        args.putParcelable(ARGS_APP_SNIPPET, dialogData.getAppSnippet());
        args.putInt(ARGS_LEGACY_CODE, dialogData.getLegacyCode());
        args.putInt(ARGS_STATUS_CODE, dialogData.getStatusCode());
        args.putString(ARGS_MESSAGE, dialogData.getMessage());
        args.putBoolean(ARGS_SHOULD_RETURN_RESULT, dialogData.getShouldReturnResult());
        args.putParcelable(ARGS_RESULT_INTENT, dialogData.getResultIntent());

        InstallFailedFragment fragment = new InstallFailedFragment();
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

        Log.i(LOG_TAG, "Creating " + LOG_TAG + "\n" + mDialogData);

        View dialogView = getLayoutInflater().inflate(R.layout.install_fragment_layout, null);
        dialogView.requireViewById(R.id.custom_message).setVisibility(View.VISIBLE);
        dialogView.requireViewById(R.id.app_snippet).setVisibility(View.VISIBLE);
        ((ImageView) dialogView.requireViewById(R.id.app_icon))
            .setImageDrawable(mDialogData.getAppIcon());
        ((TextView) dialogView.requireViewById(R.id.app_label)).setText(mDialogData.getAppLabel());


        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setNegativeButton(R.string.button_close,
                    (dialogInt, which) -> mInstallActionListener.onNegativeResponse(
                        mDialogData.getStageCode()));
        setDialogStringsFromErrorCode(mDialogData.getLegacyCode(), dialogBuilder, dialogView);

        return dialogBuilder.create();
    }

    /**
     * Set corresponding dialog title and message for the failure statusCode.
     *
     * @param statusCode The status code from the package installer.
     */
    private void setDialogStringsFromErrorCode(int statusCode, AlertDialog.Builder builder,
                                               View dialogView) {
        Log.i(LOG_TAG, "Installation status code: " + statusCode);

        TextView customMessage = dialogView.requireViewById(R.id.custom_message);
        switch (statusCode) {
            case PackageInstaller.STATUS_FAILURE_BLOCKED -> {
                customMessage.setText(R.string.message_install_failed_blocked);
                builder.setTitle(R.string.title_install_failed_blocked);
            }
            case PackageInstaller.STATUS_FAILURE_CONFLICT -> {
                customMessage.setText(R.string.message_install_failed_conflict);
                builder.setTitle(R.string.title_cant_install_app);
            }
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> {
                customMessage.setText(R.string.message_install_failed_incompatible);
                builder.setTitle(R.string.title_install_failed_incompatible);
            }
            case PackageInstaller.STATUS_FAILURE_INVALID -> {
                customMessage.setText(R.string.message_install_failed_invalid);
                builder.setTitle(R.string.title_cant_install_app);
            }
            case PackageInstaller.STATUS_FAILURE_STORAGE -> {
                customMessage.setText(
                        Html.fromHtml(
                            getString(R.string.message_install_failed_less_storage, 0),
                            Html.FROM_HTML_MODE_LEGACY));
                builder.setTitle(R.string.title_install_failed_less_storage)
                        .setPositiveButton(R.string.button_manage_apps, (dialog, which) ->
                            mInstallActionListener.sendManageAppsIntent());
            }
            default -> {
                customMessage.setVisibility(View.GONE);
                builder.setTitle(R.string.title_install_failed_not_installed);
            }
        }
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        super.onCancel(dialog);
        mInstallActionListener.onNegativeResponse(mDialogData.getStageCode());
    }

    private void setDialogData(Bundle args) {
        AppSnippet appSnippet = args.getParcelable(ARGS_APP_SNIPPET, AppSnippet.class);
        int legacyCode = args.getInt(ARGS_LEGACY_CODE);
        int statusCode = args.getInt(ARGS_STATUS_CODE);
        String message = args.getString(ARGS_MESSAGE);
        boolean shouldReturnResult = args.getBoolean(ARGS_SHOULD_RETURN_RESULT);
        Intent resultIntent = args.getParcelable(ARGS_RESULT_INTENT, Intent.class);

        mDialogData = new InstallFailed(appSnippet, legacyCode, statusCode, message,
            shouldReturnResult, resultIntent);
    }
}
