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
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.view.MotionEvent;

import com.lightbox.android.camera.ui.BitmapTexture;
import com.lightbox.android.camera.ui.GLRootView;
import com.lightbox.android.camera.ui.GLView;
import com.lightbox.android.camera.ui.MeasureHelper;
import com.lightbox.android.camera.ui.NinePatchTexture;
import com.lightbox.android.camera.ui.ResourceTexture;
import com.lightbox.android.camera.ui.CanvasTexture;
import com.lightbox.android.camera.ui.ZoomControllerListener;
import com.lightbox.android.camera.R;

import java.text.DecimalFormat;

import javax.microedition.khronos.opengles.GL11;

class ZoomController extends GLView {
    static final int LABEL_COLOR = Color.WHITE;

    static final DecimalFormat sZoomFormat = new DecimalFormat("#.#x");
    static final int INVALID_POSITION = Integer.MAX_VALUE;

    private static final float LABEL_FONT_SIZE = 18;
    private static final int HORIZONTAL_PADDING = 3;
    private static final int VERTICAL_PADDING = 3;
    private static final int MINIMAL_HEIGHT = 150;
    private static final float TOLERANCE_RADIUS = 30;

    static float sLabelSize;
    private static int sHorizontalPadding;
    private static int sVerticalPadding;
    private static int sMinimalHeight;
    static float sToleranceRadius;

    static NinePatchTexture sBackground;
    static BitmapTexture sSlider;
    private static BitmapTexture sTickMark;
    static BitmapTexture sFineTickMark;

    CanvasTexture mTickLabels[];
    float mRatios[];
    int mIndex;

    int mFineTickStep;
    int mLabelStep;

    int mMaxLabelWidth;
    int mMaxLabelHeight;

    int mSliderTop;
    int mSliderBottom;
    int mSliderLeft;
    int mSliderPosition = INVALID_POSITION;
    float mValueGap;
    ZoomControllerListener mZoomListener;

    public ZoomController(Context context) {
        initializeStaticVariable(context);
    }

    private static void initializeStaticVariable(Context context) {
        if (sBackground != null) return;

        sLabelSize = dpToPixel(context, LABEL_FONT_SIZE);
        sHorizontalPadding = dpToPixel(context, HORIZONTAL_PADDING);
        sVerticalPadding = dpToPixel(context, VERTICAL_PADDING);
        sMinimalHeight = dpToPixel(context, MINIMAL_HEIGHT);
        sToleranceRadius = dpToPixel(context, TOLERANCE_RADIUS);

        sBackground = new NinePatchTexture(context, R.drawable.zoom_background);
        sSlider = new ResourceTexture(context, R.drawable.zoom_slider);
        sTickMark = new ResourceTexture(context, R.drawable.zoom_tickmark);
        sFineTickMark = new ResourceTexture(
                context, R.drawable.zoom_finetickmark);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (!changed) return;
        Rect p = mPaddings;
        int height = b - t - p.top - p.bottom;
        int margin = Math.max(sSlider.getHeight(), mMaxLabelHeight);
        mValueGap = (float) (height - margin) / (mRatios.length - 1);

        mSliderLeft = p.left + mMaxLabelWidth + sHorizontalPadding
                + sTickMark.getWidth() + sHorizontalPadding;

        mSliderTop = p.top + margin / 2 - sSlider.getHeight() / 2;
        mSliderBottom = mSliderTop + height - margin + sSlider.getHeight();
    }

    @Override
    protected boolean onTouch(MotionEvent e) {
        float x = e.getX();
        float y = e.getY();
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (sBackground.withInToleranceRange(this, x, y)) {
                    sFineTickMark.onSliderMoved(this, (int) (y - sSlider.getHeight()), true);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (mSliderPosition != INVALID_POSITION) {
                    sFineTickMark.onSliderMoved(this, (int) (y - sSlider.getHeight()), true);
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (mSliderPosition != INVALID_POSITION) {
                    sFineTickMark.onSliderMoved(this, (int) (y - sSlider.getHeight()), false);
                    mSliderPosition = INVALID_POSITION;
                }
                return true;
        }
        return true;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int labelCount = mTickLabels.length;
        int ratioCount = mRatios.length;

        int height = (mMaxLabelHeight + sVerticalPadding)
                * (labelCount - 1) * ratioCount / (mLabelStep * labelCount)
                + Math.max(sSlider.getHeight(), mMaxLabelHeight);

        int width = mMaxLabelWidth + sHorizontalPadding + sTickMark.getWidth()
                + sHorizontalPadding + sBackground.getWidth();
        height = Math.max(sMinimalHeight, height);

        new MeasureHelper(this)
                .setPreferredContentSize(width, height)
                .measure(widthSpec, heightSpec);
    }

    @Override
    protected void render(GLRootView root, GL11 gl) {
        renderTicks(root, gl);
        root.renderSlider(this, gl);
    }

    private void renderTicks(GLRootView root, GL11 gl) {
        float gap = mValueGap;
        int labelStep = mLabelStep;

        // render the tick labels
        int xoffset = mPaddings.left + mMaxLabelWidth;
        float yoffset = mSliderBottom - sSlider.getHeight() / 2;
        for (int i = 0, n = mTickLabels.length; i < n; ++i) {
            BitmapTexture t = mTickLabels[i];
            t.draw(root, xoffset - t.getWidth(),
                    (int) (yoffset - t.getHeight() / 2));
            yoffset -= labelStep * gap;
        }

        // render the main tick marks
        BitmapTexture tickMark = sTickMark;
        xoffset += sHorizontalPadding;
        yoffset = mSliderBottom - sSlider.getHeight() / 2;
        int halfHeight = tickMark.getHeight() / 2;
        for (int i = 0, n = mTickLabels.length; i < n; ++i) {
            tickMark.draw(root, xoffset, (int) (yoffset - halfHeight));
            yoffset -= labelStep * gap;
        }

        if (mFineTickStep > 0) {
            // render the fine tick marks
            tickMark = sFineTickMark;
            xoffset += sTickMark.getWidth() - tickMark.getWidth();
            yoffset = mSliderBottom - sSlider.getHeight() / 2;
            halfHeight = tickMark.getHeight() / 2;
            for (int i = 0, n = mRatios.length; i < n; ++i) {
                if (i % mLabelStep != 0) {
                    tickMark.draw(root, xoffset, (int) (yoffset - halfHeight));
                }
                yoffset -= gap;
            }
        }
    }
}
