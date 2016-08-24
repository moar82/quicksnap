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

import javax.microedition.khronos.opengles.GL11;

import com.lightbox.android.camera.Util;
import com.lightbox.android.camera.ui.GLListView.OnItemSelectedListener;
import com.lightbox.android.camera.ui.GLRootView;
import com.lightbox.android.camera.ui.NinePatchChunk;
import com.lightbox.android.camera.ui.ResourceTexture;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.view.animation.Animation;
import android.view.animation.Transformation;

class NinePatchTexture extends ResourceTexture {
    private NinePatchChunk mChunk;

    public NinePatchTexture(Context context, int resId) {
        super(context, resId);
    }

    @Override
    protected Bitmap getBitmap() {
        if (mBitmap != null) return mBitmap;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeResource(
                mContext.getResources(), mResId, options);
        mBitmap = bitmap;
        setSize(bitmap.getWidth(), bitmap.getHeight());
        mChunk = NinePatchChunk.deserialize(bitmap.getNinePatchChunk());
        if (mChunk == null) {
            throw new RuntimeException("invalid nine-patch image: " + mResId);
        }
        return bitmap;
    }

    public Rect getPaddings() {
        // get the paddings from nine patch
        if (mChunk == null) getBitmap();
        return mChunk.mPaddings;
    }

    public NinePatchChunk getNinePatchChunk() {
        if (mChunk == null) getBitmap();
        return mChunk;
    }

    @Override
    public void draw(GLRootView root, int x, int y, int w, int h) {
        drawNinePatch(root, x, y, w, h);
    }

	public void drawNinePatch(
	        GLRootView glRootView, int x, int y, int width, int height) {
	
	    NinePatchChunk chunk = getNinePatchChunk();
	
	    // The code should be easily extended to handle the general cases by
	    // allocating more space for buffers. But let's just handle the only
	    // use case.
	    if (chunk.mDivX.length != 2 || chunk.mDivY.length != 2) {
	        throw new RuntimeException("unsupported nine patch");
	    }
	    if (!bind(glRootView, glRootView.mGL)) {
	        throw new RuntimeException("cannot bind" + toString());
	    }
	    if (width <= 0 || height <= 0) return ;
	
	    int divX[] = glRootView.mNinePatchX;
	    int divY[] = glRootView.mNinePatchY;
	    float divU[] = glRootView.mNinePatchU;
	    float divV[] = glRootView.mNinePatchV;
	
	    int nx = glRootView.mContentView.stretch(divX, divU, chunk.mDivX, getWidth(), width);
	    int ny = glRootView.mContentView.stretch(divY, divV, chunk.mDivY, getHeight(), height);
	
	    glRootView.setAlphaValue(glRootView.mTransformation.getAlpha());
	    Matrix matrix = glRootView.mTransformation.getMatrix();
	    matrix.getValues(glRootView.mMatrixValues);
	    GL11 gl = glRootView.mGL;
	    gl.glPushMatrix();
	    gl.glMultMatrixf(GLRootView.toGLMatrix(glRootView.mMatrixValues), 0);
	    gl.glTranslatef(x, y, 0);
	    glRootView.mEglConfigChooser.drawMesh(glRootView, divX, divY, divU, divV, nx, ny);
	    gl.glPopMatrix();
	}

	public void setZoomIndex(ZoomController zoomController, int index) {
	    index = Util.clamp(index, 0, zoomController.mRatios.length - 1);
	    if (zoomController.mIndex == index) return;
	    zoomController.mIndex = index;
	    if (zoomController.mZoomListener != null) {
	        zoomController.mZoomListener.onZoomChanged(zoomController.mIndex, zoomController.mRatios[zoomController.mIndex], false);
	    }
	}

	public void setOnItemSelectedListener(GLListView glListView, OnItemSelectedListener l) {
	    glListView.mOnItemSelectedListener = l;
	}

	void setVisibleRange(GLListView glListView, int start, int end) {
	    if (start == glListView.mVisibleStart && end == glListView.mVisibleEnd) return;
	    glListView.mVisibleStart = start;
	    glListView.mVisibleEnd = end;
	}

	public void setScroller(GLListView glListView) {
	    glListView.mScrollbar = this;
	    glListView.requestLayout();
	}

	boolean withInToleranceRange(ZoomController zoomController, float x, float y) {
	    float sx = zoomController.mSliderLeft + ZoomController.sSlider.getWidth() / 2;
	    float sy = zoomController.mSliderTop + (zoomController.mRatios.length - 1 - zoomController.mIndex) * zoomController.mValueGap
	            + ZoomController.sSlider.getHeight() / 2;
	    float dist = Util.distance(x, y, sx, sy);
	    return dist <= ZoomController.sToleranceRadius;
	}

	boolean drawWithAnimation(GLListView glListView, GLRootView root, Texture texture, int x, int y, int w, int h, Animation anim) {
	    long now = root.mEglConfigChooser.currentAnimationTimeMillis(root);
	    Transformation temp = root.mContentView.obtainTransformation(root);
	    boolean more = anim.getTransformation(now, temp);
	    Transformation transformation = root.mEglConfigChooser.pushTransform(root);
	    transformation.compose(temp);
	    texture.draw(root, x, y, w, h);
	    glListView.invalidate();
	    root.mContentView.popTransform(root);
	    return more;
	}

	public void setZoomListener(ZoomController zoomController, ZoomControllerListener listener) {
	    zoomController.mZoomListener = listener;
	}
}
