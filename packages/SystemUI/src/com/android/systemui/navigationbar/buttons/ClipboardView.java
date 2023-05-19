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

package com.android.systemui.navigationbar.buttons;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ClipboardManager.OnPrimaryClipChangedListener;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import com.android.systemui.R;

public class ClipboardView extends KeyButtonView implements OnPrimaryClipChangedListener {

    private static final int TARGET_COLOR = 0x4dffffff;
    private final ClipboardManager mClipboardManager;
    private ClipData mCurrentClip;
    private Drawable mClipboardEmpty;
    private Drawable mClipboardFull;

    public ClipboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setClickable(true);
        mClipboardManager = context.getSystemService(ClipboardManager.class);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startListening();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopListening();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN && mCurrentClip != null) {
            startPocketDrag();
            return true;
        }
        return super.onTouchEvent(ev);
    }

    @Override
    public boolean onDragEvent(DragEvent event) {
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_ENTERED:
                setBackgroundDragTarget(true);
                break;
            case DragEvent.ACTION_DROP:
                mClipboardManager.setPrimaryClip(event.getClipData());
            case DragEvent.ACTION_DRAG_EXITED:
            case DragEvent.ACTION_DRAG_ENDED:
                setBackgroundDragTarget(false);
                setForceDisableOverview(false);
                break;
        }
        return true;
    }

    private void setBackgroundDragTarget(boolean isTarget) {
        setBackgroundColor(isTarget ? TARGET_COLOR : 0);
    }

    private void startPocketDrag() {
        setForceDisableOverview(true);
        startDragAndDrop(mCurrentClip, new View.DragShadowBuilder(this), null,
                View.DRAG_FLAG_GLOBAL);
    }

    private void startListening() {
        mClipboardManager.addPrimaryClipChangedListener(this);
        onPrimaryClipChanged();
    }

    private void stopListening() {
        mClipboardManager.removePrimaryClipChangedListener(this);
    }

    public void setImageDrawables(Drawable imageDrawable1, Drawable imageDrawable2) {
        mClipboardEmpty = imageDrawable1;
        mClipboardFull = imageDrawable2;
        onPrimaryClipChanged(); // update drawable
    }

    @Override
    public void onPrimaryClipChanged() {
        mCurrentClip = mClipboardManager.getPrimaryClip();
        setImageDrawable(mCurrentClip != null ? mClipboardFull : mClipboardEmpty);
    }
}
