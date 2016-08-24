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

import android.app.Activity;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View.MeasureSpec;
import android.view.animation.Animation;
import android.view.animation.Transformation;

import java.util.ArrayList;

import javax.microedition.khronos.opengles.GL11;

import com.lightbox.android.camera.Util;
import com.lightbox.android.camera.ui.GLRootView;
import com.lightbox.android.camera.ui.GLView;

public class GLView {
    @SuppressWarnings("unused")
    private static final String TAG = "GLView";

    public static final int VISIBLE = 0;
    public static final int INVISIBLE = 1;

    public static final int FLAG_INVISIBLE = 1;
    public static final int FLAG_SET_MEASURED_SIZE = 2;
    public static final int FLAG_LAYOUT_REQUESTED = 4;

    protected final Rect mBounds = new Rect();
    protected final Rect mPaddings = new Rect();

    GLRootView mRootView;
    GLView mParent;
    private ArrayList<GLView> mComponents;
    private GLView mMotionTarget;

    OnTouchListener mOnTouchListener;
    Animation mAnimation;

    protected int mViewFlags = 0;

    protected int mMeasuredWidth = 0;
    protected int mMeasuredHeight = 0;

    private int mLastWidthSpec = -1;
    private int mLastHeightSpec = -1;

    protected int mScrollY = 0;
    protected int mScrollX = 0;
    protected int mScrollHeight = 0;
    protected int mScrollWidth = 0;

    public void setVisibility(int visibility) {
        if (visibility == getVisibility()) return;
        if (visibility == VISIBLE) {
            mViewFlags &= ~FLAG_INVISIBLE;
        } else {
            mViewFlags |= FLAG_INVISIBLE;
        }
        onVisibilityChanged(visibility);
        invalidate();
    }

    public int getVisibility() {
        return (mViewFlags & FLAG_INVISIBLE) == 0 ? VISIBLE : INVISIBLE;
    }

    public static interface OnTouchListener {
        public boolean onTouch(GLView view, MotionEvent event);
    }

    protected void onAddToParent(GLView parent) {
        // TODO: enable the check
        // if (mParent != null) throw new IllegalStateException();
        mParent = parent;
        if (parent != null && parent.mRootView != null) {
            onAttachToRoot(parent.mRootView);
        }
    }

    protected void onRemoveFromParent(GLView parent) {
        if (parent != null && parent.mMotionTarget == this) {
            long now = SystemClock.uptimeMillis();
            dispatchTouchEvent(MotionEvent.obtain(
                    now, now, MotionEvent.ACTION_CANCEL, 0, 0, 0));
            parent.mMotionTarget = null;
        }
        onDetachFromRoot();
        mParent = null;
    }

    public void clearComponents() {
        mComponents = null;
    }

    public int getComponentCount() {
        return mComponents == null ? 0 : mComponents.size();
    }

    public GLView getComponent(int index) {
        if (mComponents == null) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        return mComponents.get(index);
    }

    public void addComponent(GLView component) {
        if (mComponents == null) {
            mComponents = new ArrayList<GLView>();
        }
        mComponents.add(component);
        component.onAddToParent(this);
    }

    public boolean removeComponent(GLView component) {
        if (mComponents == null) return false;
        if (mComponents.remove(component)) {
            component.onRemoveFromParent(this);
            return true;
        }
        return false;
    }

    public int getHeight() {
        return mBounds.bottom - mBounds.top;
    }

    public GLRootView getGLRootView() {
        return mRootView;
    }

    public void setOnTouchListener(OnTouchListener listener) {
        mOnTouchListener = listener;
    }

    public void invalidate() {
        GLRootView root = getGLRootView();
        if (root != null) root.requestRender();
    }

    public void requestLayout() {
        mViewFlags |= FLAG_LAYOUT_REQUESTED;
        if (mParent != null) {
            mParent.requestLayout();
        } else {
            // Is this a content pane ?
            GLRootView root = getGLRootView();
            if (root != null) root.requestLayoutContentPane();
        }
    }

    protected void render(GLRootView view, GL11 gl) {
        renderBackground(view, gl);
        for (int i = 0, n = getComponentCount(); i < n; ++i) {
            GLView component = getComponent(i);
            if (component.getVisibility() != GLView.VISIBLE
                    && component.mAnimation == null) continue;
            view.renderChild(this, gl, component);
        }
    }

    protected void renderBackground(GLRootView view, GL11 gl) {
    }

    /**
	 * @deprecated Use {@link com.lightbox.android.camera.ui.GLRootView#onTouch(com.lightbox.android.camera.ui.GLView,MotionEvent)} instead
	 */
	protected boolean onTouch(MotionEvent event) {
		return mRootView.onTouch(this, event);
	}

    private boolean dispatchTouchEvent(MotionEvent event,
            int x, int y, GLView component, boolean checkBounds) {
        Rect rect = component.mBounds;
        int left = rect.left;
        int top = rect.top;
        if (!checkBounds || rect.contains(x, y)) {
            event.offsetLocation(-left, -top);
            if (component.dispatchTouchEvent(event)) {
                event.offsetLocation(left, top);
                return true;
            }
            event.offsetLocation(left, top);
        }
        return false;
    }

    protected boolean dispatchTouchEvent(MotionEvent event) {
        int x = (int) event.getX();
        int y = (int) event.getY();
        int action = event.getAction();
        if (mMotionTarget != null) {
            if (action == MotionEvent.ACTION_DOWN) {
                MotionEvent cancel = MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                mMotionTarget = null;
            } else {
                dispatchTouchEvent(event, x, y, mMotionTarget, false);
                if (action == MotionEvent.ACTION_CANCEL
                        || action == MotionEvent.ACTION_UP) {
                    mMotionTarget = null;
                }
                return true;
            }
        }
        if (action == MotionEvent.ACTION_DOWN) {
            for (int i = 0, n = getComponentCount(); i < n; ++i) {
                GLView component = getComponent(i);
                if (component.getVisibility() != GLView.VISIBLE) continue;
                if (dispatchTouchEvent(event, x, y, component, true)) {
                    mMotionTarget = component;
                    return true;
                }
            }
        }
        return onTouch(event);
    }

    public void setPaddings(Rect paddings) {
        mPaddings.set(paddings);
    }

    public void setPaddings(int left, int top, int right, int bottom) {
        mPaddings.set(left, top, right, bottom);
    }

    public void layout(int left, int top, int right, int bottom) {
        boolean sizeChanged = mRootView.setBounds(this, left, top, right, bottom);
        if (sizeChanged) {
            mViewFlags &= ~FLAG_LAYOUT_REQUESTED;
            onLayout(true, left, top, right, bottom);
        } else if ((mViewFlags & FLAG_LAYOUT_REQUESTED)!= 0) {
            mViewFlags &= ~FLAG_LAYOUT_REQUESTED;
            onLayout(false, left, top, right, bottom);
        }
    }

    public void measure(int widthSpec, int heightSpec) {
        if (widthSpec == mLastWidthSpec && heightSpec == mLastHeightSpec
                && (mViewFlags & FLAG_LAYOUT_REQUESTED) == 0) {
            return;
        }

        mLastWidthSpec = widthSpec;
        mLastHeightSpec = heightSpec;

        mViewFlags &= ~FLAG_SET_MEASURED_SIZE;
        onMeasure(widthSpec, heightSpec);
        if ((mViewFlags & FLAG_SET_MEASURED_SIZE) == 0) {
            throw new IllegalStateException(getClass().getName()
                    + " should call setMeasuredSize() in onMeasure()");
        }
    }

    protected void onMeasure(int widthSpec, int heightSpec) {
    }

    protected void setMeasuredSize(int width, int height) {
        mViewFlags |= FLAG_SET_MEASURED_SIZE;
        mMeasuredWidth = width;
        mMeasuredHeight = height;
    }

    public int getMeasuredHeight() {
        return mMeasuredHeight;
    }

    protected void onLayout(
            boolean changeSize, int left, int top, int right, int bottom) {
    }

    /**
     * Gets the bounds of the given descendant that relative to this view.
     */
    public boolean getBoundsOf(GLView descendant, Rect out) {
        int xoffset = 0;
        int yoffset = 0;
        GLView view = descendant;
        while (view != this) {
            if (view == null) return false;
            Rect bounds = view.mBounds;
            xoffset += bounds.left;
            yoffset += bounds.top;
            view = view.mParent;
        }
        out.set(xoffset, yoffset, xoffset + descendant.mRootView.getWidth(descendant),
                yoffset + descendant.getHeight());
        return true;
    }

    protected void onVisibilityChanged(int visibility) {
        for (int i = 0, n = getComponentCount(); i < n; ++i) {
            GLView child = getComponent(i);
            if (child.getVisibility() == GLView.VISIBLE) {
                child.onVisibilityChanged(visibility);
            }
        }
    }

    /**
	 * @deprecated Use {@link com.lightbox.android.camera.ui.GLRootView#onAttachToRoot(com.lightbox.android.camera.ui.GLView)} instead
	 */
	protected void onAttachToRoot(GLRootView root) {
		root.onAttachToRoot(this);
	}

    protected void onDetachFromRoot() {
        for (int i = 0, n = getComponentCount(); i < n; ++i) {
            getComponent(i).onDetachFromRoot();
        }
        mRootView = null;
    }

	public void clipRect(GLRootView glRootView, int x, int y, int width, int height) {
	    float point[] = glRootView.mapPoints(
	            glRootView.mTransformation.getMatrix(), x, y + height, x + width, y);
	
	    // mMatrix could be a rotation matrix. In this case, we need to find
	    // the boundaries after rotation. (only handle 90 * n degrees)
	    if (point[0] > point[2]) {
	        x = (int) point[2];
	        width = (int) point[0] - x;
	    } else {
	        x = (int) point[0];
	        width = (int) point[2] - x;
	    }
	    if (point[1] > point[3]) {
	        y = (int) point[3];
	        height = (int) point[1] - y;
	    } else {
	        y = (int) point[1];
	        height = (int) point[3] - y;
	    }
	    glRootView.mGL.glScissor(x, y, width, height);
	}

	void layoutPopupWindow(HeadUpDisplay headUpDisplay) {
	
	    headUpDisplay.mAnchorView = this;
	    Rect rect = new Rect();
	    headUpDisplay.getBoundsOf(this, rect);
	
	    int anchorX = rect.left + HeadUpDisplay.sPopupWindowOverlap;
	    int anchorY = (rect.top + rect.bottom) / 2;
	
	    int width = (int) (headUpDisplay.mRootView.getWidth(headUpDisplay) * HeadUpDisplay.MAX_WIDTH_RATIO + .5);
	    int height = (int) (headUpDisplay.getHeight() * HeadUpDisplay.MAX_HEIGHT_RATIO + .5);
	
	    headUpDisplay.mPopupWindow.measure(
	            MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
	            MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
	
	    width = headUpDisplay.mPopupWindow.mRootView.getMeasuredWidth(headUpDisplay.mPopupWindow);
	    height = headUpDisplay.mPopupWindow.getMeasuredHeight();
	
	    int xoffset = Math.max(anchorX - width, 0);
	    int yoffset = Math.max(0, anchorY - height / 2);
	
	    if (yoffset + height > headUpDisplay.getHeight()) {
	        yoffset = headUpDisplay.getHeight() - height;
	    }
	    headUpDisplay.mPopupWindow.setAnchorPosition(anchorY - yoffset);
	    headUpDisplay.mPopupWindow.layout(
	            xoffset, yoffset, xoffset + width, yoffset + height);
	}

	public Transformation getTransformation(GLRootView glRootView) {
	    return glRootView.mTransformation;
	}

	public CameraEGLConfigChooser getEGLConfigChooser(GLRootView glRootView) {
	    return glRootView.mEglConfigChooser;
	}

	public Transformation obtainTransformation(GLRootView glRootView) {
	    if (!glRootView.mFreeTransform.isEmpty()) {
	        Transformation t = glRootView.mFreeTransform.pop();
	        t.clear();
	        return t;
	    }
	    return new Transformation();
	}

	void showPopupWindow(HeadUpDisplay headUpDisplay) {
	    layoutPopupWindow(headUpDisplay);
	    headUpDisplay.mPopupWindow.popup();
	    headUpDisplay.mSharedPrefs.registerOnSharedPreferenceChangeListener(
	            headUpDisplay.mSharedPreferenceChangeListener);
	    if (headUpDisplay.mListener != null) {
	        headUpDisplay.mListener.onPopupWindowVisibilityChanged(GLView.VISIBLE);
	    }
	}

	public void popTransform(GLRootView glRootView) {
	    Transformation trans = glRootView.mTransformStack.pop();
	    glRootView.mTransformation.set(trans);
	    glRootView.mContentView.freeTransformation(glRootView, trans);
	}

	public void freeTransformation(GLRootView glRootView, Transformation freeTransformation) {
	    glRootView.mFreeTransform.push(freeTransformation);
	}

	public DisplayMetrics getDisplayMetrics(GLRootView glRootView) {
	    if (glRootView.mDisplayMetrics == null) {
	        glRootView.mDisplayMetrics = new DisplayMetrics();
	        ((Activity) glRootView.getContext()).getWindowManager()
	                .getDefaultDisplay().getMetrics(glRootView.mDisplayMetrics);
	    }
	    return glRootView.mDisplayMetrics;
	}

	/**
	 * Stretches the texture according to the nine-patch rules. It will
	 * linearly distribute the strechy parts defined in the nine-patch chunk to
	 * the target area.
	 *
	 * <pre>
	 *                      source
	 *          /--------------^---------------\
	 *         u0    u1       u2  u3     u4   u5
	 * div ---> |fffff|ssssssss|fff|ssssss|ffff| ---> u
	 *          |    div0    div1 div2   div3  |
	 *          |     |       /   /      /    /
	 *          |     |      /   /     /    /
	 *          |     |     /   /    /    /
	 *          |fffff|ssss|fff|sss|ffff| ---> x
	 *         x0    x1   x2  x3  x4   x5
	 *          \----------v------------/
	 *                  target
	 *
	 * f: fixed segment
	 * s: stretchy segment
	 * </pre>
	 *
	 * @param x output, the corresponding position of these dividers on the
	 *        drawing plan
	 * @param u output, the positions of these dividers in the texture
	 *        coordinate
	 * @param div the stretch parts defined in nine-patch chunk
	 * @param source the length of the texture
	 * @param target the length on the drawing plan
	 * @return the number of these dividers.
	 */
	int stretch(
	        int[] x, float[] u, int[] div, int source, int target) {
	    int textureSize = Util.nextPowerOf2(source);
	    float textureBound = (source - 0.5f) / textureSize;
	
	    int stretch = 0;
	    for (int i = 0, n = div.length; i < n; i += 2) {
	        stretch += div[i + 1] - div[i];
	    }
	
	    float remaining = target - source + stretch;
	
	    int lastX = 0;
	    int lastU = 0;
	
	    x[0] = 0;
	    u[0] = 0;
	    for (int i = 0, n = div.length; i < n; i += 2) {
	        // fixed segment
	        x[i + 1] = lastX + (div[i] - lastU);
	        u[i + 1] = Math.min((float) div[i] / textureSize, textureBound);
	
	        // stretchy segment
	        float partU = div[i + 1] - div[i];
	        int partX = (int)(remaining * partU / stretch + 0.5f);
	        remaining -= partX;
	        stretch -= partU;
	
	        lastX = x[i + 1] + partX;
	        lastU = div[i + 1];
	        x[i + 2] = lastX;
	        u[i + 2] = Math.min((float) lastU / textureSize, textureBound);
	    }
	    // the last fixed segment
	    x[div.length + 1] = target;
	    u[div.length + 1] = textureBound;
	
	    // remove segments with length 0.
	    int last = 0;
	    for (int i = 1, n = div.length + 2; i < n; ++i) {
	        if (x[last] == x[i]) continue;
	        x[++last] = x[i];
	        u[last] = u[i];
	    }
	    return last + 1;
	}
}
