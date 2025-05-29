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
import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_EXISTING_OWNER;
import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_IS_UPDATING;
import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_NEW_OWNER;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.android.packageinstaller.R;
import com.android.packageinstaller.v2.model.InstallUserActionRequired;
import com.android.packageinstaller.v2.model.PackageUtil.AppSnippet;
import com.android.packageinstaller.v2.ui.InstallActionListener;
import com.android.packageinstaller.v2.ui.UiUtil;

/**
 * Dialog to show when the requesting user confirmation for installing an app.
 */
public class InstallConfirmationFragment extends DialogFragment {

    public static final String LOG_TAG = InstallConfirmationFragment.class.getSimpleName();
    private InstallUserActionRequired mDialogData;
    @NonNull
    private InstallActionListener mInstallActionListener;
    @NonNull
    private Dialog mDialog;

    public InstallConfirmationFragment() {
        // Required for DialogFragment
    }

    /**
     * Creates a new instance of this fragment with necessary data set as fragment arguments
     *
     * @param dialogData {@link InstallUserActionRequired} object containing data to display
     *                   in the dialog
     * @return an instance of the fragment
     */
    public static InstallConfirmationFragment newInstance(
            @NonNull InstallUserActionRequired dialogData) {
        Bundle args = new Bundle();
        args.putInt(ARGS_ACTION_REASON, dialogData.getActionReason());
        args.putParcelable(ARGS_APP_SNIPPET, dialogData.getAppSnippet());
        args.putBoolean(ARGS_IS_UPDATING, dialogData.isAppUpdating());
        args.putCharSequence(ARGS_EXISTING_OWNER, dialogData.getExistingUpdateOwnerLabel());
        args.putCharSequence(ARGS_NEW_OWNER, dialogData.getRequestedUpdateOwnerLabel());

        InstallConfirmationFragment fragment = new InstallConfirmationFragment();
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

        View dialogView = getLayoutInflater().inflate(
                UiUtil.getInstallationLayoutResId(requireContext()), null);
        dialogView.requireViewById(R.id.app_snippet).setVisibility(View.VISIBLE);
        ((ImageView) dialogView.requireViewById(R.id.app_icon))
            .setImageDrawable(mDialogData.getAppIcon());
        ((TextView) dialogView.requireViewById(R.id.app_label)).setText(mDialogData.getAppLabel());

        int positiveBtnTextRes;
        String title;
        if (mDialogData.isAppUpdating()) {
            if (mDialogData.getExistingUpdateOwnerLabel() != null
                    && mDialogData.getRequestedUpdateOwnerLabel() != null) {
                title = getString(R.string.title_update_ownership_change,
                    mDialogData.getRequestedUpdateOwnerLabel());
                positiveBtnTextRes = R.string.button_update_anyway;

                TextView customMessage = dialogView.requireViewById(R.id.custom_message);
                customMessage.setVisibility(View.VISIBLE);
                String updateOwnerString = getString(R.string.message_update_owner_change,
                        mDialogData.getExistingUpdateOwnerLabel());
                customMessage.setText(Html.fromHtml(updateOwnerString, Html.FROM_HTML_MODE_LEGACY));
                customMessage.setMovementMethod(new ScrollingMovementMethod());
            } else {
                title = getString(R.string.title_update);
                positiveBtnTextRes = R.string.button_update;
            }
        } else {
            title = getString(R.string.title_install);
            positiveBtnTextRes = R.string.button_install;
        }

        mDialog = UiUtil.getAlertDialog(requireContext(), title, dialogView,
                positiveBtnTextRes, R.string.button_cancel,
                (dialogInt, which) -> mInstallActionListener.onPositiveResponse(
                        InstallUserActionRequired.USER_ACTION_REASON_INSTALL_CONFIRMATION),
                (dialogInt, which) -> mInstallActionListener.onNegativeResponse(
                                mDialogData.getStageCode()));

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

    private void setDialogData(Bundle args) {
        int actionReason = args.getInt(ARGS_ACTION_REASON);
        AppSnippet appSnippet = args.getParcelable(ARGS_APP_SNIPPET, AppSnippet.class);
        boolean isUpdating = args.getBoolean(ARGS_IS_UPDATING);
        CharSequence existingOwner = args.getCharSequence(ARGS_EXISTING_OWNER);
        CharSequence newOwner = args.getCharSequence(ARGS_NEW_OWNER);

        mDialogData = new InstallUserActionRequired(actionReason, appSnippet, isUpdating,
            existingOwner, newOwner, null);
    }
}
