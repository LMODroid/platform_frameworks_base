/*
* Copyright (C) 2019-2021 crDroid Android Project
* Copyright (C) 2023 The LibreMobileOS Foundation
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*
*/
package com.android.systemui.lmodroid;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import androidx.palette.graphics.Palette;

import com.android.internal.libremobileos.hardware.LineageHardwareManager;

import com.android.settingslib.Utils;
import com.android.systemui.R;
import com.android.systemui.people.PeopleSpaceUtils;

public class NotificationLightsView extends RelativeLayout
        implements Animator.AnimatorListener {

    private static final boolean DEBUG = false;
    private static final String TAG = "NotificationLightsView";

    private View mNotificationAnimView;
    private ValueAnimator mLightAnimator;
    private int color;

    private boolean mOnlyWhenFaceDown = false;
    private LineageHardwareManager mHardware;
    private boolean mHasHbmSupport = false;
    private boolean mHbmEnabled = false;

    public NotificationLightsView(Context context) {
        this(context, null);
    }

    public NotificationLightsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NotificationLightsView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public NotificationLightsView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        if (DEBUG) Log.d(TAG, "new");
        mOnlyWhenFaceDown = context.getResources()
                .getBoolean(R.bool.config_showEdgeLightOnlyWhenFaceDown);
        if (mOnlyWhenFaceDown) {
            setBackgroundColor(Color.BLACK);
            mHardware = LineageHardwareManager.getInstance(context);
            if (mHardware != null) {
                mHasHbmSupport = mHardware.isSupported(
                        LineageHardwareManager.FEATURE_SUNLIGHT_ENHANCEMENT);
            }
        }
    }

    @Override
    public void onAnimationCancel(Animator animation) {
        disableHbm();
    }

    @Override
    public void onAnimationEnd(Animator animation) {
        disableHbm();
    }

    @Override
    public void onAnimationRepeat(Animator animation) {
        // Do nothing
    }

    @Override
    public void onAnimationStart(Animator animation) {
        enableHbm();
    }

    public void animateNotification(String notifPackageName) {
        int customColor = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.PULSE_AMBIENT_LIGHT_COLOR, 0xFF3980FF,
                UserHandle.USER_CURRENT);
        int duration = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.PULSE_AMBIENT_LIGHT_DURATION, 2,
                UserHandle.USER_CURRENT) * 1000;
        int repeat = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.PULSE_AMBIENT_LIGHT_REPEAT_COUNT, 0,
                UserHandle.USER_CURRENT);
        int colorMode = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.PULSE_AMBIENT_LIGHT_COLOR_MODE, 1,
                UserHandle.USER_CURRENT);
        int width = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.PULSE_AMBIENT_LIGHT_WIDTH, 125,
                UserHandle.USER_CURRENT);
        if (colorMode == 0) {
            try {
                Drawable iconDrawable = mContext.getPackageManager()
                        .getApplicationIcon(notifPackageName);
                Bitmap bitmap = PeopleSpaceUtils.convertDrawableToBitmap(iconDrawable);
                if (bitmap != null) {
                    Palette p = Palette.from(bitmap).generate();
                    int iconColor = p.getDominantColor(color);
                    if (color != iconColor)
                        color = iconColor;
                }
            } catch (Exception e) {
                // Nothing to do
            }
        } else if (colorMode == 1) {
            color = Utils.getColorAccentDefaultColor(getContext());
        } else {
            color = customColor;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("animateNotification color ");
        sb.append(Integer.toHexString(color));
        if (DEBUG) Log.e(TAG, sb.toString());
        ImageView leftView = (ImageView) findViewById(R.id.notification_animation_left);
        ImageView rightView = (ImageView) findViewById(R.id.notification_animation_right);
        leftView.setColorFilter(color);
        rightView.setColorFilter(color);
        leftView.getLayoutParams().width = width;
        rightView.getLayoutParams().width = width;
        mLightAnimator = ValueAnimator.ofFloat(new float[]{0.0f, 2.0f});
        mLightAnimator.setDuration(duration);
        mLightAnimator.setRepeatCount(repeat);
        mLightAnimator.setRepeatMode(ValueAnimator.RESTART);
        mLightAnimator.addListener(this);
        mLightAnimator.addUpdateListener(new AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator animation) {
                if (DEBUG) Log.d(TAG, "onAnimationUpdate");
                // onAnimationStart() is being called before waking screen in ambient mode.
                // So HBM don't get enabled since it needs the screen to be on.
                // To fix this, Just try to enable HBM here too.
                enableHbm();
                float progress = ((Float) animation.getAnimatedValue()).floatValue();
                leftView.setScaleY(progress);
                rightView.setScaleY(progress);
                float alpha = 1.0f;
                if (progress <= 0.3f) {
                    alpha = progress / 0.3f;
                } else if (progress >= 1.0f) {
                    alpha = 2.0f - progress;
                }
                leftView.setAlpha(alpha);
                rightView.setAlpha(alpha);
            }
        });
        if (DEBUG) Log.d(TAG, "start");
        mLightAnimator.start();
    }

    private void enableHbm() {
        if (mOnlyWhenFaceDown && mHardware != null && mHasHbmSupport && !mHbmEnabled) {
            // Enable high brightness mode
            mHardware.set(LineageHardwareManager.FEATURE_SUNLIGHT_ENHANCEMENT, true);
            // Update the state.
            mHbmEnabled = mHardware.get(LineageHardwareManager.FEATURE_SUNLIGHT_ENHANCEMENT);
        }
    }

    private void disableHbm() {
        if (mOnlyWhenFaceDown && mHardware != null && mHasHbmSupport) {
            // Disable high brightness mode
            mHardware.set(LineageHardwareManager.FEATURE_SUNLIGHT_ENHANCEMENT, false);
            mHbmEnabled = false;
        }
    }

}
