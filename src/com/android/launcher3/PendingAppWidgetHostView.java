/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.launcher3;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources.Theme;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;

public class PendingAppWidgetHostView extends LauncherAppWidgetHostView implements OnClickListener {

    private static Theme sPreloaderTheme;

    private final Rect mRect = new Rect();
    private View mDefaultView;
    private OnClickListener mClickListener;
    private final LauncherAppWidgetInfo mInfo;
    private final int mStartState;
    private final Intent mIconLookupIntent;

    private Bitmap mIcon;
    private PreloadIconDrawable mDrawable;

    private Drawable mCenterDrawable;
    private Drawable mTopCornerDrawable;

    private boolean mDrawableSizeChanged;

    public PendingAppWidgetHostView(Context context, LauncherAppWidgetInfo info) {
        super(context);
        mInfo = info;
        mStartState = info.restoreStatus;
        mIconLookupIntent = new Intent().setComponent(info.providerName);

        setBackgroundResource(R.drawable.quantum_panel_dark);
        setWillNotDraw(false);
    }

    @Override
    public void updateAppWidgetSize(Bundle newOptions, int minWidth, int minHeight, int maxWidth,
            int maxHeight) {
        // No-op
    }

    @Override
    protected View getDefaultView() {
        if (mDefaultView == null) {
            mDefaultView = mInflater.inflate(R.layout.appwidget_not_ready, this, false);
            mDefaultView.setOnClickListener(this);
            applyState();
        }
        return mDefaultView;
    }

    @Override
    public void setOnClickListener(OnClickListener l) {
        mClickListener = l;
    }

    @Override
    public boolean isReinflateRequired() {
        // Re inflate is required any time the widget restore status changes
        return mStartState != mInfo.restoreStatus;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mDrawableSizeChanged = true;
    }

    public void updateIcon(IconCache cache) {
        Bitmap icon = cache.getIcon(mIconLookupIntent, mInfo.user);
        if (mIcon == icon) {
            return;
        }
        mIcon = icon;
        if (mDrawable != null) {
            mDrawable.setCallback(null);
            mDrawable = null;
        }
        if (mIcon != null) {
            // The view displays two modes, one with a setup icon and another with a preload icon
            // in the center.
            if (isReadyForClickSetup()) {
                mCenterDrawable = getResources().getDrawable(R.drawable.ic_setting);
                mTopCornerDrawable = new FastBitmapDrawable(mIcon);
            } else {
                if (sPreloaderTheme == null) {
                    sPreloaderTheme = getResources().newTheme();
                    sPreloaderTheme.applyStyle(R.style.PreloadIcon, true);
                }

                FastBitmapDrawable drawable = Utilities.createIconDrawable(mIcon);
                mDrawable = new PreloadIconDrawable(drawable, sPreloaderTheme);
                mDrawable.setCallback(this);
                applyState();
            }
            mDrawableSizeChanged = true;
        }
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return (who == mDrawable) || super.verifyDrawable(who);
    }

    public void applyState() {
        if (mDrawable != null) {
            mDrawable.setLevel(mInfo.installProgress);
        }
    }

    @Override
    public void onClick(View v) {
        // AppWidgetHostView blocks all click events on the root view. Instead handle click events
        // on the content and pass it along.
        if (mClickListener != null) {
            mClickListener.onClick(this);
        }
    }

    public boolean isReadyForClickSetup() {
        return (mInfo.restoreStatus & LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY) == 0
                && (mInfo.restoreStatus & LauncherAppWidgetInfo.FLAG_UI_NOT_READY) != 0;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mDrawable != null) {
            if (mDrawableSizeChanged) {
                int maxSize = LauncherAppState.getInstance().getDynamicGrid()
                        .getDeviceProfile().iconSizePx + 2 * mDrawable.getOutset();
                int size = Math.min(maxSize, Math.min(
                        getWidth() - getPaddingLeft() - getPaddingRight(),
                        getHeight() - getPaddingTop() - getPaddingBottom()));

                mRect.set(0, 0, size, size);
                mRect.inset(mDrawable.getOutset(), mDrawable.getOutset());
                mRect.offsetTo((getWidth() - mRect.width()) / 2, (getHeight() - mRect.height()) / 2);
                mDrawable.setBounds(mRect);
                mDrawableSizeChanged = false;
            }

            mDrawable.draw(canvas);
        } else if ((mCenterDrawable != null) && (mTopCornerDrawable != null)) {
            if (mDrawableSizeChanged) {
                int iconSize = getResources().getDimensionPixelSize(R.dimen.app_icon_size);
                int paddingTop = getPaddingTop();
                int paddingLeft = getPaddingLeft();

                int size = Math.min(iconSize, Math.min(
                        getWidth() - paddingLeft - getPaddingRight(),
                        getHeight() - paddingTop - getPaddingBottom()));
                mRect.set(0, 0, size, size);
                mRect.offsetTo((getWidth() - mRect.width()) / 2, (getHeight() - mRect.height()) / 2);
                mCenterDrawable.setBounds(mRect);

                size = Math.min(size / 2,
                        Math.max(mRect.top - paddingTop, mRect.left - paddingLeft));
                mTopCornerDrawable.setBounds(paddingLeft, paddingTop,
                        paddingLeft + size, paddingTop + size);
                mDrawableSizeChanged = false;
            }

            mCenterDrawable.draw(canvas);
            mTopCornerDrawable.draw(canvas);
        }
    }

}