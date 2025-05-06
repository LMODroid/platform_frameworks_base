/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.packageinstaller.v2.ui.fragments;

import static android.text.format.Formatter.formatFileSize;

import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_APP_DATA_SIZE;
import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_APP_SNIPPET;
import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_BUTTON_TEXT;
import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_MESSAGE;
import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_TITLE;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.android.packageinstaller.R;
import com.android.packageinstaller.v2.model.PackageUtil.AppSnippet;
import com.android.packageinstaller.v2.model.UninstallUserActionRequired;
import com.android.packageinstaller.v2.ui.UninstallActionListener;

/**
 * Dialog to show while requesting user confirmation for uninstalling an app.
 */
public class UninstallConfirmationFragment extends DialogFragment {

    private static final String LOG_TAG = UninstallConfirmationFragment.class.getSimpleName();
    private UninstallUserActionRequired mDialogData;
    private UninstallActionListener mUninstallActionListener;
    private CheckBox mKeepData;

    public UninstallConfirmationFragment() {
        // Required for DialogFragment
    }

    /**
     * Create a new instance of this fragment with necessary data set as fragment arguments
     *
     * @param dialogData {@link UninstallUserActionRequired} object containing data to
     *                   display in the dialog
     * @return an instance of the fragment
     */
    public static UninstallConfirmationFragment newInstance(
            @NonNull UninstallUserActionRequired dialogData) {
        Bundle args = new Bundle();
        args.putString(ARGS_TITLE, dialogData.getTitle());
        args.putString(ARGS_MESSAGE, dialogData.getMessage());
        args.putString(ARGS_BUTTON_TEXT, dialogData.getButtonText());
        args.putLong(ARGS_APP_DATA_SIZE, dialogData.getAppDataSize());
        args.putParcelable(ARGS_APP_SNIPPET, dialogData.getAppSnippet());

        UninstallConfirmationFragment fragment = new UninstallConfirmationFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mUninstallActionListener = (UninstallActionListener) context;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        setDialogData(requireArguments());

        Log.i(LOG_TAG, "Creating " + LOG_TAG + "\n" + mDialogData);

        View dialogView = getLayoutInflater().inflate(R.layout.uninstall_fragment_layout, null);
        dialogView.requireViewById(R.id.app_snippet).setVisibility(View.VISIBLE);
        ((ImageView) dialogView.requireViewById(R.id.app_icon))
            .setImageDrawable(mDialogData.getAppIcon());
        ((TextView) dialogView.requireViewById(R.id.app_label)).setText(mDialogData.getAppLabel());

        if (mDialogData.getMessage() != null) {
            TextView customMessage = dialogView.requireViewById(R.id.custom_message);
            customMessage.setText(mDialogData.getMessage());
            customMessage.setVisibility(View.VISIBLE);
        }

        long appDataSize = mDialogData.getAppDataSize();
        if (appDataSize != 0) {
            View keepDataLayout = dialogView.requireViewById(R.id.keep_data_layout);
            keepDataLayout.setVisibility(View.VISIBLE);

            TextView keepDataBytes = keepDataLayout.requireViewById(R.id.keep_data_bytes);
            keepDataBytes.setText(formatFileSize(getContext(), appDataSize));

            mKeepData = keepDataLayout.requireViewById(R.id.keep_data_checkbox);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
                .setTitle(mDialogData.getTitle())
                .setView(dialogView)
                .setPositiveButton(mDialogData.getButtonText(),
                    (dialogInt, which) -> mUninstallActionListener.onPositiveResponse(
                        mKeepData != null && mKeepData.isChecked()))
                .setNegativeButton(R.string.button_cancel,
                    (dialogInt, which) -> mUninstallActionListener.onNegativeResponse());

        return builder.create();
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        super.onCancel(dialog);
        mUninstallActionListener.onNegativeResponse();
    }

    private void setDialogData(Bundle args) {
        long appDataSize = args.getLong(ARGS_APP_DATA_SIZE);
        String buttonText = args.getString(ARGS_BUTTON_TEXT);
        String message = args.getString(ARGS_MESSAGE);
        AppSnippet appSnippet = args.getParcelable(ARGS_APP_SNIPPET, AppSnippet.class);
        String title = args.getString(ARGS_TITLE);

        mDialogData = new UninstallUserActionRequired(title, message, buttonText, appDataSize,
            appSnippet);
    }
}
