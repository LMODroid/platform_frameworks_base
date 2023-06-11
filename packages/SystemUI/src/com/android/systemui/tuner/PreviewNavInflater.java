/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.tuner;

import android.annotation.DrawableRes;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;

import com.android.settingslib.Utils;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.navigationbar.NavigationBarInflaterView;
import com.android.systemui.navigationbar.buttons.ButtonInterface;
import com.android.systemui.navigationbar.buttons.ClipboardView;
import com.android.systemui.navigationbar.buttons.KeyButtonDrawable;
import com.android.systemui.shared.system.QuickStepContract;

import java.util.HashMap;

public class PreviewNavInflater extends NavigationBarInflaterView {

    private static final String TAG = "PreviewNavInflater";

    private Context mLightContext;
    private final HashMap<String, Drawable> mDrawableMap = new HashMap<>();
    private boolean mShowSwipeUpUi;

    public PreviewNavInflater(Context context, AttributeSet attrs) {
        super(new ContextThemeWrapper(context, R.style.Theme_SystemUI), attrs);
        final Context darkContext = new ContextThemeWrapper(getContext(),
            Utils.getThemeAttr(getContext(), R.attr.darkIconTheme));
        mLightContext = new ContextThemeWrapper(getContext(),
            Utils.getThemeAttr(getContext(), R.attr.lightIconTheme));
        setIconColors(Utils.getColorAttrDefaultColor(mLightContext, R.attr.singleToneColor),
                Utils.getColorAttrDefaultColor(darkContext, R.attr.singleToneColor));
        loadDrawables();
        updateAlternativeOrder();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateAlternativeOrder();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);

        final boolean newVertical = w > 0 && h > w
                && !QuickStepContract.isGesturalMode(mNavBarMode);
        setVertical(newVertical);

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public void setVertical(boolean vertical) {
        if (vertical != mIsVertical) {
            super.setVertical(vertical);
            loadDrawables();
        }
    }

    private void updateAlternativeOrder() {
        setAlternativeOrder(getContext().getDisplay().getRotation() == Surface.ROTATION_90);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Immediately remove tuner listening, since this is a preview, all values will be injected
        // manually.
        Dependency.get(TunerService.class).removeTunable(this);
        mShowSwipeUpUi = QuickStepContract.isSwipeUpMode(mNavBarMode);
        loadDrawables();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // Only a preview, not interactable.
        return true;
    }

    @Override
    public void onNavigationModeChanged(int mode) {
        mShowSwipeUpUi = QuickStepContract.isSwipeUpMode(mode);
        loadDrawables();
        super.onNavigationModeChanged(mode);
    }

    @Override
    public View createView(String buttonSpec, ViewGroup parent, LayoutInflater inflater) {
        View v = super.createView(buttonSpec, parent, inflater);
        final String button = extractButton(buttonSpec);
        if (v instanceof ButtonInterface) {
            if (mDrawableMap.containsKey(button)) {
                if (v instanceof ClipboardView) {
                    ((ClipboardView) v).setImageDrawables(mDrawableMap.get(button), mDrawableMap.get(button));
                } else {
                    ((ButtonInterface) v).setImageDrawable(mDrawableMap.get(button));
                }
            }
            ((ButtonInterface) v).setDarkIntensity(0f);
        } else {
            Log.w(TAG, "Key " + button + " is not ButtonInterface but " + v.getClass().getName());
        }
        return v;
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        if (NAV_BAR_VIEWS.equals(key)) {
            // Since this is a preview we might get a bunch of random stuff, validate before sending
            // for inflation.
            if (isValidLayout(newValue)) {
                super.onTuningChanged(key, newValue);
            }
        } else {
            super.onTuningChanged(key, newValue);
        }
    }

    private KeyButtonDrawable getDrawable(@DrawableRes int icon) {
        return KeyButtonDrawable.create(mLightContext, mLightIconColor, mDarkIconColor, icon,
                true /* hasShadow */, null /* ovalBackgroundColor */);
    }

    private boolean isValidLayout(String newValue) {
        if (newValue == null) {
            return true;
        }
        int separatorCount = 0;
        int lastGravitySeparator = 0;
        for (int i = 0; i < newValue.length(); i++) {
            if (newValue.charAt(i) == GRAVITY_SEPARATOR.charAt(0)) {
                if (i == 0 || (i - lastGravitySeparator) == 1) {
                    return false;
                }
                lastGravitySeparator = i;
                separatorCount++;
            }
        }
        return separatorCount == 2 && (newValue.length() - lastGravitySeparator) != 1;
    }

    private void loadDrawables() {
        mDrawableMap.clear();
        if (!QuickStepContract.isGesturalMode(mNavBarMode)) {
            mDrawableMap.put(HOME, getHomeDrawable());
            mDrawableMap.put(BACK, getBackDrawable());
        }
        if (QuickStepContract.isLegacyMode(mNavBarMode)) {
            mDrawableMap.put(RECENT, getDrawable(R.drawable.ic_sysbar_recent));
        }
        mDrawableMap.put(POWER, getDrawable(R.drawable.ic_sysbar_power));
        mDrawableMap.put(VOLUME_UP, getDrawable(R.drawable.ic_sysbar_volume_plus));
        mDrawableMap.put(VOLUME_DOWN, getDrawable(R.drawable.ic_sysbar_volume_minus));
        mDrawableMap.put(CLIPBOARD, getDrawable(R.drawable.clipboard_empty));
        forceReinflate();
    }

    /* copied from/based on NavigationBarView */

    private void orientHomeButton(KeyButtonDrawable drawable) {
        drawable.setRotation(mIsVertical ? 90 : 0);
    }

    private @DrawableRes int chooseNavigationIconDrawableRes(@DrawableRes int icon,
            @DrawableRes int quickStepIcon) {
        return mShowSwipeUpUi ? quickStepIcon : icon;
    }

    public KeyButtonDrawable getBackDrawable() {
        KeyButtonDrawable drawable = getDrawable(getBackDrawableRes());
        // orientBackButton(drawable); not needed for preview
        return drawable;
    }

    public @DrawableRes int getBackDrawableRes() {
        return chooseNavigationIconDrawableRes(R.drawable.ic_sysbar_back,
                R.drawable.ic_sysbar_back_quick_step);
    }

    public KeyButtonDrawable getHomeDrawable() {
        KeyButtonDrawable drawable = mShowSwipeUpUi
                ? getDrawable(R.drawable.ic_sysbar_home_quick_step)
                : getDrawable(R.drawable.ic_sysbar_home);
        orientHomeButton(drawable);
        return drawable;
    }
}
