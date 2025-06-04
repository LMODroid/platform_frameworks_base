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

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.android.packageinstaller.R;
import com.android.packageinstaller.v2.model.InstallStage;
import com.android.packageinstaller.v2.model.InstallUserActionRequired;
import com.android.packageinstaller.v2.ui.InstallActionListener;
import com.android.packageinstaller.v2.ui.UiUtil;

/**
 * Dialog to show when the source of apk can not be identified.
 */
public class AnonymousSourceFragment extends DialogFragment {

    public static final String LOG_TAG = AnonymousSourceFragment.class.getSimpleName();
    @NonNull
    private InstallActionListener mInstallActionListener;
    @NonNull
    private Dialog mDialog;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mInstallActionListener = (InstallActionListener) context;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Log.i(LOG_TAG, "Creating " + LOG_TAG);

        View dialogView = getLayoutInflater().inflate(
                UiUtil.getInstallationLayoutResId(requireContext()), null);
        TextView customMessage = dialogView.requireViewById(R.id.custom_message);
        customMessage.setText(R.string.message_anonymous_source_warning);
        customMessage.setVisibility(View.VISIBLE);

        mDialog = UiUtil.getAlertDialog(requireContext(),
                getString(R.string.title_anonymous_source_warning), dialogView,
                R.string.button_continue, R.string.button_cancel,
                ((dialog, which) -> mInstallActionListener.onPositiveResponse(
                        InstallUserActionRequired.USER_ACTION_REASON_ANONYMOUS_SOURCE)),
                ((dialog, which) -> mInstallActionListener.onNegativeResponse(
                        InstallStage.STAGE_USER_ACTION_REQUIRED)),
                UiUtil.getTextButtonThemeResId(requireContext()));
        return mDialog;
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        super.onCancel(dialog);
        mInstallActionListener.onNegativeResponse(InstallStage.STAGE_USER_ACTION_REQUIRED);
    }

    @Override
    public void onStart() {
        super.onStart();
        Button button = UiUtil.getAlertDialogPositiveButton(mDialog);
        if (button != null) {
            button.setFilterTouchesWhenObscured(true);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // This prevents tapjacking since an overlay activity started in front of Pia will
        // cause Pia to be paused.
        Button button = UiUtil.getAlertDialogPositiveButton(mDialog);
        if (button != null) {
            button.setEnabled(false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Button button = UiUtil.getAlertDialogPositiveButton(mDialog);
        if (button != null) {
            button.setEnabled(true);
        }
    }
}
