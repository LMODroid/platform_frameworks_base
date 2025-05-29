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

package com.android.packageinstaller.v2.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.packageinstaller.R;
import com.android.packageinstaller.v2.model.PackageUtil;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * The utility of UI components.
 */
public class UiUtil {

    public static final String LOG_TAG = UiUtil.class.getSimpleName();
    /**
     * If material design is enabled, return the material layout resource id. Otherwise, return the
     * default layout resource id.
     */
    public static int getInstallationLayoutResId(@NonNull Context context) {
        if (PackageUtil.isMaterialDesignEnabled(context)) {
            return R.layout.install_fragment_material_layout;
        } else {
            return R.layout.install_fragment_layout;
        }
    }

    /**
     * Gets the positive button in the {@code dialog}. Returns null if the specified
     * button does not exist or the dialog has not yet been fully created.
     */
    public static Button getAlertDialogPositiveButton(@NonNull Dialog dialog) {
        return getAlertiDialogButton(dialog, DialogInterface.BUTTON_POSITIVE);
    }

    /**
     * Gets one of the buttons used in the {@code dialog}. Returns null if the specified
     * button does not exist or the dialog has not yet been fully created.
     *
     * @param whichButton The identifier of the button that should be returned.
     *            For example, this can be {@link DialogInterface#BUTTON_POSITIVE}.
     * @return The button from the dialog, or null if a button does not exist.
     */
    @Nullable
    private static Button getAlertiDialogButton(@NonNull Dialog dialog, int whichButton) {
        if (dialog instanceof android.app.AlertDialog alertDialog) {
            return alertDialog.getButton(whichButton);
        } else if (dialog instanceof androidx.appcompat.app.AlertDialog alertDialog) {
            return alertDialog.getButton(whichButton);
        } else {
            return null;
        }
    }

    /**
     * If material design is enabled, return the MaterialAlertDialog. Otherwise, return the
     * system AlertDialog.
     */
    public static Dialog getAlertDialog(@NonNull Context context, @NonNull String title,
            @NonNull View contentView, int positiveBtnTextResId, int negativeBtnTextResId,
            @Nullable DialogInterface.OnClickListener positiveBtnListener,
            @Nullable DialogInterface.OnClickListener negativeBtnListener) {
        if (PackageUtil.isMaterialDesignEnabled(context)) {
            return new MaterialAlertDialogBuilder(context)
                    .setTitle(title)
                    .setView(contentView)
                    .setPositiveButton(positiveBtnTextResId, positiveBtnListener)
                    .setNegativeButton(negativeBtnTextResId, negativeBtnListener)
                    .create();
        } else {
            return new AlertDialog.Builder(context)
                    .setTitle(title)
                    .setView(contentView)
                    .setPositiveButton(positiveBtnTextResId, positiveBtnListener)
                    .setNegativeButton(negativeBtnTextResId, negativeBtnListener)
                    .create();
        }
    }
}
