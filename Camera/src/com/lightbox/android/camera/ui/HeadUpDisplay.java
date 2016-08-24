/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.lightbox.android.camera.ui;

import static com.lightbox.android.camera.ui.GLRootView.dpToPixel;

import java.util.ArrayList;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View.MeasureSpec;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;

import com.lightbox.android.camera.ComboPreferences;
import com.lightbox.android.camera.IconListPreference;
import com.lightbox.android.camera.ListPreference;
import com.lightbox.android.camera.PreferenceGroup;
import com.lightbox.android.camera.ui.AbstractIndicator;
import com.lightbox.android.camera.ui.BasicIndicator;
import com.lightbox.android.camera.ui.ColorTexture;
import com.lightbox.android.camera.ui.GLRootView;
import com.lightbox.android.camera.ui.GLView;
import com.lightbox.android.camera.ui.IndicatorBar;
import com.lightbox.android.camera.ui.NinePatchTexture;
import com.lightbox.android.camera.ui.PopupWindow;
import com.lightbox.android.camera.ui.ResourceTexture;
import com.lightbox.android.camera.R;

// This is the UI for the on-screen settings. Since the rendering is run in the
// GL thread. If any values will be changed in the main thread, it needs to
// synchronize on the <code>GLRootView</code> instance.
public class HeadUpDisplay extends GLView {
    @SuppressWarnings("unused")
	private static final String TAG = "HeadUpDisplay";
    
    public static final int INDICATOR_BAR_TIMEOUT = 5500;
    public static final int POPUP_WINDOW_TIMEOUT = 5000;
    private static final int INDICATOR_BAR_RIGHT_MARGIN = 10;
    private static final int POPUP_WINDOW_OVERLAP = 20;
    private static final int POPUP_TRIANGLE_OFFSET = 16;

    private static final int COLOR_ICONBAR_HIGHLIGHT = 0x9A2B2B2B;

    static final float MAX_HEIGHT_RATIO = 0.85f;
    static final float MAX_WIDTH_RATIO = 0.8f;

    public static final int DESELECT_INDICATOR = 0;
    public static final int DEACTIVATE_INDICATOR_BAR = 1;

    private static int sIndicatorBarRightMargin = -1;
    static int sPopupWindowOverlap;
    private static int sPopupTriangleOffset;


    protected IndicatorBar mIndicatorBar;

    public ComboPreferences mSharedPrefs;
    private PreferenceGroup mPreferenceGroup;

    PopupWindow mPopupWindow;

    GLView mAnchorView;
    private int mOrientation = 0;
    public boolean mEnabled = true;

    public Listener mListener;

    public Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            GLRootView root = getGLRootView();
            if (root != null) {
                synchronized (root) {
                    handleMessageLocked(msg);
                }
            } else {
                handleMessageLocked(msg);
            }
        }

        private void handleMessageLocked(Message msg) {
            switch(msg.what) {
                case DESELECT_INDICATOR:
                    mIndicatorBar.setSelectedIndex(IndicatorBar.INDEX_NONE);
                    break;
                case DEACTIVATE_INDICATOR_BAR:
                    if (mIndicatorBar != null) {
                        mIndicatorBar.setActivated(false);
                    }
                    break;
            }
        }
    };

    public final OnSharedPreferenceChangeListener mSharedPreferenceChangeListener =
            new OnSharedPreferenceChangeListener() {
        public void onSharedPreferenceChanged(
                SharedPreferences sharedPreferences, String key) {
            if (mListener != null) {
                mListener.onSharedPreferencesChanged();
            }
        }
    };

    public HeadUpDisplay(Context context) {
        initializeStaticVariables(context);
    }

    private static void initializeStaticVariables(Context context) {
        if (sIndicatorBarRightMargin >= 0) return;

        sIndicatorBarRightMargin = dpToPixel(context, INDICATOR_BAR_RIGHT_MARGIN);
        sPopupWindowOverlap = dpToPixel(context, POPUP_WINDOW_OVERLAP);
        sPopupTriangleOffset = dpToPixel(context, POPUP_TRIANGLE_OFFSET);
    }

    /**
     * The callback interface. All the callbacks will be called from the
     * GLThread.
     */
    static public interface Listener {
        public void onPopupWindowVisibilityChanged(int visibility);
        public void onRestorePreferencesClicked();
        public void onSharedPreferencesChanged();
    }

    public void overrideSettings(final String ... keyvalues) {
        GLRootView root = getGLRootView();
        if (root != null) {
            synchronized (root) {
                overrideSettingsLocked(keyvalues);
            }
        } else {
            overrideSettingsLocked(keyvalues);
        }
    }

    public void overrideSettingsLocked(final String ... keyvalues) {
        if (keyvalues.length % 2 != 0) {
            throw new IllegalArgumentException();
        }
        for (int i = 0, n = keyvalues.length; i < n; i += 2) {
            mIndicatorBar.overrideSettings(keyvalues[i], keyvalues[i + 1]);
        }
    }

    @Override
    protected void onLayout(
            boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;
        mIndicatorBar.measure(
                MeasureSpec.makeMeasureSpec(width / 3, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        DisplayMetrics metrics = getGLRootView().mContentView.getDisplayMetrics(getGLRootView());
        int rightMargin = (int) (metrics.density * INDICATOR_BAR_RIGHT_MARGIN);

        mIndicatorBar.layout(
                width - mIndicatorBar.mRootView.getMeasuredWidth(mIndicatorBar) - rightMargin, 0,
                width - rightMargin, height);

        if(mPopupWindow != null
                && mPopupWindow.getVisibility() == GLView.VISIBLE) {
            mAnchorView.layoutPopupWindow(this);
        }
    }

    public void initialize(Context context, PreferenceGroup preferenceGroup) {
        mPreferenceGroup = preferenceGroup;
        mSharedPrefs = ComboPreferences.get(context);
        mPopupWindow = null;
        clearComponents();
        initializeIndicatorBar(context, preferenceGroup);
        requestLayout();
    }

    private void hidePopupWindow() {
        mPopupWindow.popoff();
        // Unregister is important to avoid leaking activities.
        // ComboPreference.sMap->ComboPreference->HeadUpDisplay->Activity
        mSharedPrefs.unregisterOnSharedPreferenceChangeListener(
                mSharedPreferenceChangeListener);
        if (mListener != null) {
            mListener.onPopupWindowVisibilityChanged(GLView.INVISIBLE);
        }
    }

    public void setOrientationLocked(int orientation) {
        mOrientation = orientation;
        mIndicatorBar.setOrientation(orientation);
        if (mPopupWindow == null) return;
        if (mPopupWindow.getVisibility() == GLView.VISIBLE) {
            Animation alpha = new AlphaAnimation(0.2f, 1);
            alpha.setDuration(250);
            mPopupWindow.mRootView.startAnimation(mPopupWindow, alpha);
            mPreferenceGroup.scheduleDeactiviateIndicatorBar(this);
        }
        mPopupWindow.setOrientation(orientation);
    }

    private void initializePopupWindow(Context context) {
        mPopupWindow = new PopupWindow();
        mPopupWindow.setBackground(
                new NinePatchTexture(context, R.drawable.menu_popup));
        mPopupWindow.setAnchor(new ResourceTexture(
                context, R.drawable.menu_popup_triangle), sPopupTriangleOffset);
        mPopupWindow.setVisibility(GLView.INVISIBLE);
        mPopupWindow.setOrientation(mOrientation);
        addComponent(mPopupWindow);
    }

    @Override
    protected boolean dispatchTouchEvent(MotionEvent event) {
        if (mEnabled && super.dispatchTouchEvent(event)) {
            mPreferenceGroup.scheduleDeactiviateIndicatorBar(this);
            return true;
        }
        return false;
    }

    @Override
    protected boolean onTouch(MotionEvent event) {
        if (mPopupWindow == null
                || mPopupWindow.getVisibility() == GLView.INVISIBLE) {
            return false;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_UP:
                hidePopupWindow();
                mIndicatorBar.setSelectedIndex(IndicatorBar.INDEX_NONE);
                mIndicatorBar.setActivated(false);
                break;
        }
        return true;
    }

    protected static ListPreference[] getListPreferences(
            PreferenceGroup group, String ... prefKeys) {
        ArrayList<ListPreference> list = new ArrayList<ListPreference>();
        for (String key : prefKeys) {
            ListPreference pref = group.findPreference(key);
            if (pref != null && pref.getEntries().length > 0) {
                list.add(pref);
            }
        }
        return list.toArray(new ListPreference[list.size()]);
    }

    protected BasicIndicator addIndicator(
            Context context, PreferenceGroup group, String key) {
        IconListPreference iconPref =
                (IconListPreference) group.findPreference(key);
        if (iconPref == null) return null;
        BasicIndicator indicator = new BasicIndicator(context, iconPref);
        mIndicatorBar.addComponent(indicator);
        return indicator;
    }

    protected void initializeIndicatorBar(
            Context context, PreferenceGroup group) {
        mIndicatorBar = new IndicatorBar();

        mIndicatorBar.setBackground(new NinePatchTexture(
                context, R.drawable.ic_viewfinder_iconbar));
        mIndicatorBar.setHighlight(new ColorTexture(COLOR_ICONBAR_HIGHLIGHT));
        addComponent(mIndicatorBar);
        mIndicatorBar.setOnItemSelectedListener(new IndicatorBarListener());
    }

    private class IndicatorBarListener
            implements IndicatorBar.OnItemSelectedListener {

        public void onItemSelected(GLView view, int position) {

            AbstractIndicator indicator = (AbstractIndicator) view;
            if (mPopupWindow == null) {
                initializePopupWindow(getGLRootView().getContext());
            }
            mPopupWindow.setContent(indicator.getPopupContent());

            //if (mPopupWindow.getVisibility() == GLView.VISIBLE) {
             //   layoutPopupWindow(indicator);
            //} else {
                indicator.showPopupWindow(HeadUpDisplay.this);
            //}
        }

        public void onNothingSelected() {
            hidePopupWindow();
        }
    }

    public boolean collapse() {
        // We don't need to synchronize on GLRootView, since both the
        // <code>isActivated()</code> and rendering thread are read-only to
        // the variables inside.
        if (!mIndicatorBar.isActivated()) return false;
        mHandler.removeMessages(DESELECT_INDICATOR);
        mHandler.removeMessages(DEACTIVATE_INDICATOR_BAR);
        GLRootView root = getGLRootView();
        if (root != null) {
            synchronized (root) {
                mIndicatorBar.setSelectedIndex(IndicatorBar.INDEX_NONE);
                mIndicatorBar.setActivated(false);
            }
        } else {
            mIndicatorBar.setSelectedIndex(IndicatorBar.INDEX_NONE);
            mIndicatorBar.setActivated(false);
        }
        return true;
    }

    public void setListener(Listener listener) {
        // No synchronization: mListener won't be accessed in rendering thread
        mListener = listener;
    }

    public void reloadPreferences() {
        GLRootView root = getGLRootView();
        if (root != null) {
            synchronized (root) {
                mPreferenceGroup.reloadValue();
                mIndicatorBar.reloadPreferences();
            }
        } else {
            mPreferenceGroup.reloadValue();
            mIndicatorBar.reloadPreferences();
        }
    }
}
