/*
 * Copyright (C) 2023 droid-ng
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

package com.android.systemui.navigationbar.buttons;

import android.util.Log;
import android.view.View;

import com.android.systemui.R;

public class ClipboardButtonDispatcher extends ButtonDispatcher {
    private KeyButtonDrawable mImageDrawable1;
    private KeyButtonDrawable mImageDrawable2;

    public ClipboardButtonDispatcher() {
        super(R.id.clipboard);
    }

    @Override
    public void addView(View view) {
        super.addView(view);

        if (view instanceof ClipboardView) {
            if (mImageDrawable1 != null && mImageDrawable2 != null) {
                ((ClipboardView) view).setImageDrawables(mImageDrawable1, mImageDrawable2);
            }
        }
    }

    public void setImageDrawables(KeyButtonDrawable drawable1, KeyButtonDrawable drawable2) {
        mImageDrawable1 = drawable1;
        mImageDrawable2 = drawable2;
        final int N = mViews.size();
        for (int i = 0; i < N; i++) {
            if (mViews.get(i) instanceof ClipboardView) {
                ((ClipboardView) mViews.get(i)).setImageDrawables(mImageDrawable1, mImageDrawable2);
            }
        }
        if (mImageDrawable1 != null) {
            mImageDrawable1.setCallback(mCurrentView);
        }
        if (mImageDrawable2 != null) {
            mImageDrawable2.setCallback(mCurrentView);
        }
    }

    @Override
    public void setCurrentView(View currentView) {
        super.setCurrentView(currentView);
        if (mImageDrawable1 != null) {
            mImageDrawable1.setCallback(mCurrentView);
        }
        if (mImageDrawable2 != null) {
            mImageDrawable2.setCallback(mCurrentView);
        }
    }

    public KeyButtonDrawable getImageDrawable1() {
        return mImageDrawable1;
    }

    public KeyButtonDrawable getImageDrawable2() {
        return mImageDrawable2;
    }
}
