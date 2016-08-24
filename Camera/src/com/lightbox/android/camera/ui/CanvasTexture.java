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

import com.lightbox.android.camera.ui.BitmapTexture;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Bitmap.Config;
import android.graphics.Paint.FontMetricsInt;

/** Using a canvas to draw the texture */
class CanvasTexture extends BitmapTexture {
    protected Canvas mCanvas;
    
    private static int DEFAULT_PADDING = 2;

    private final String mText;
    private final Paint mPaint;
    private final FontMetricsInt mMetrics;

    public CanvasTexture(String text, Paint paint,
            FontMetricsInt metrics, int width, int height) {
        setSize(width, height);
        mText = text;
        mPaint = paint;
        mMetrics = metrics;
    }


    public static CanvasTexture newInstance(String text, Paint paint) {
        FontMetricsInt metrics = paint.getFontMetricsInt();
        int width = (int) (.5f + paint.measureText(text)) + DEFAULT_PADDING * 2;
        int height = metrics.bottom - metrics.top + DEFAULT_PADDING * 2;
        return new CanvasTexture(text, paint, metrics, width, height);
    }

    public static CanvasTexture newInstance(
            String text, float textSize, int color) {
        Paint paint = new Paint();
        paint.setTextSize(textSize);
        paint.setAntiAlias(true);
        paint.setColor(color);
        paint.setShadowLayer(1.5f, 0, 0, Color.BLACK);

        return newInstance(text, paint);
    }

    protected void onDraw(Canvas canvas, Bitmap backing) {
        canvas.translate(DEFAULT_PADDING, DEFAULT_PADDING - mMetrics.ascent);
        canvas.drawText(mText, 0, 0, mPaint);
    }

    @Override
    protected Bitmap getBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(mWidth, mHeight, Config.ARGB_8888);
        mCanvas = new Canvas(bitmap);
        onDraw(mCanvas, bitmap);
        return bitmap;
    }

    @Override
    protected void freeBitmap(Bitmap bitmap) {
        bitmap.recycle();
    }

}
