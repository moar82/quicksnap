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

import com.lightbox.android.camera.CameraSettings;
import com.lightbox.android.camera.MenuHelper;
import com.lightbox.android.camera.R;
import com.lightbox.android.camera.R.array;
import com.lightbox.android.camera.R.id;
import com.lightbox.android.camera.R.string;
import com.lightbox.android.camera.Util;
import com.lightbox.android.camera.activities.Camera;
import com.lightbox.android.camera.device.CameraHolder;
import com.lightbox.android.camera.ui.CameraEGLConfigChooser;
import com.lightbox.android.camera.ui.GLRootView;
import com.lightbox.android.camera.ui.GLView;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.opengl.GLSurfaceView;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.Transformation;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.opengles.GL11;

// The root component of all <code>GLView</code>s. The rendering is done in GL
// thread while the event handling is done in the main thread.  To synchronize
// the two threads, the entry points of this package need to synchronize on the
// <code>GLRootView</code> instance unless it can be proved that the rendering
// thread won't access the same thing as the method. The entry points include:
// (1) The public methods of HeadUpDisplay
// (2) The public methods of CameraHeadUpDisplay
// (3) The overridden methods in GLRootView.
public class GLRootView extends GLSurfaceView
        implements GLSurfaceView.Renderer {
    static final String TAG = "GLRootView";

    private final boolean ENABLE_FPS_TEST = false;
    private int mFrameCount = 0;
    private long mFrameCountingStart = 0;

    // We need 16 vertices for a normal nine-patch image (the 4x4 vertices)
    private static final int VERTEX_BUFFER_SIZE = 16 * 2;

    // We need 22 indices for a normal nine-patch image
    private static final int INDEX_BUFFER_SIZE = 22;

    private static final int FLAG_INITIALIZED = 1;
    private static final int FLAG_NEED_LAYOUT = 2;

    static boolean mTexture2DEnabled;

    private static float sPixelDensity = -1f;

    GL11 mGL;
    GLView mContentView;
    DisplayMetrics mDisplayMetrics;

    private final List<Animation> mAnimations =  Collections.synchronizedList(new ArrayList<Animation>());

    final Stack<Transformation> mFreeTransform =
            new Stack<Transformation>();

    final Transformation mTransformation = new Transformation();
    final Stack<Transformation> mTransformStack =
            new Stack<Transformation>();

    private float mLastAlpha = mTransformation.getAlpha();

    final float mMatrixValues[] = new float[16];

    final float mUvBuffer[] = new float[VERTEX_BUFFER_SIZE];
    final float mXyBuffer[] = new float[VERTEX_BUFFER_SIZE];
    final byte mIndexBuffer[] = new byte[INDEX_BUFFER_SIZE];

    int mNinePatchX[] = new int[4];
    int mNinePatchY[] = new int[4];
    float mNinePatchU[] = new float[4];
    float mNinePatchV[] = new float[4];

    ByteBuffer mXyPointer;
    ByteBuffer mUvPointer;
    ByteBuffer mIndexPointer;

    private int mFlags = FLAG_NEED_LAYOUT;
    long mAnimationTime;

    CameraEGLConfigChooser mEglConfigChooser = new CameraEGLConfigChooser();

    public GLRootView(Context context) {
        this(context, null);
    }

    public GLRootView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mFlags |= FLAG_INITIALIZED;
		setEGLConfigChooser(mEglConfigChooser);
		getHolder().setFormat(PixelFormat.TRANSLUCENT);
		setZOrderOnTop(true);
		
		setRenderer(this);
		
		int size = VERTEX_BUFFER_SIZE * Float.SIZE / Byte.SIZE;
		mXyPointer = allocateDirectNativeOrderBuffer(size);
		mUvPointer = allocateDirectNativeOrderBuffer(size);
		mIndexPointer = allocateDirectNativeOrderBuffer(INDEX_BUFFER_SIZE);
    }

    void registerLaunchedAnimation(Animation animation) {
        // Register the newly launched animation so that we can set the start
        // time more precisely. (Usually, it takes much longer for the first
        // rendering, so we set the animation start time as the time we
        // complete rendering)
        mAnimations.add(animation);
    }

    public synchronized static float dpToPixel(Context context, float dp) {
        if (sPixelDensity < 0) {
            DisplayMetrics metrics = new DisplayMetrics();
            ((Activity) context).getWindowManager()
                    .getDefaultDisplay().getMetrics(metrics);
            sPixelDensity =  metrics.density;
        }
        return sPixelDensity * dp;
    }

    public static int dpToPixel(Context context, int dp) {
        return (int)(dpToPixel(context, (float) dp) + .5f);
    }

    private static ByteBuffer allocateDirectNativeOrderBuffer(int size) {
        return ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
    }

    public void setContentPane(GLView content) {
        mContentView = content;
        content.onAttachToRoot(this);

        // no parent for the content pane
        content.onAddToParent(null);
        requestLayoutContentPane();
    }

    public synchronized void requestLayoutContentPane() {
        if (mContentView == null || (mFlags & FLAG_NEED_LAYOUT) != 0) return;

        // "View" system will invoke onLayout() for initialization(bug ?), we
        // have to ignore it since the GLThread is not ready yet.
        if ((mFlags & FLAG_INITIALIZED) == 0) return;

        mFlags |= FLAG_NEED_LAYOUT;
        requestRender();
    }

    private synchronized void layoutContentPane() {
        mFlags &= ~FLAG_NEED_LAYOUT;
        int width = getWidth();
        int height = getHeight();
        Log.v(TAG, "layout content pane " + width + "x" + height);
        if (mContentView != null && width != 0 && height != 0) {
            mContentView.layout(0, 0, width, height);
        }
    }

    @Override
    protected void onLayout(
            boolean changed, int left, int top, int right, int bottom) {
        if (changed) requestLayoutContentPane();
    }

    /**
     * Called when the context is created, possibly after automatic destruction.
     */
    // This is a GLSurfaceView.Renderer callback
    public void onSurfaceCreated(GL10 gl1, EGLConfig config) {
        GL11 gl = (GL11) gl1;
        if (mGL != null) {
            // The GL Object has changed
            Log.i(TAG, "GLObject has changed from " + mGL + " to " + gl);
        }
        mGL = gl;

        if (!ENABLE_FPS_TEST) {
            setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        } else {
            setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        }

        // Disable unused state
        gl.glDisable(GL11.GL_LIGHTING);

        // Enable used features
        gl.glEnable(GL11.GL_BLEND);
        gl.glEnable(GL11.GL_SCISSOR_TEST);
        gl.glEnable(GL11.GL_STENCIL_TEST);
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
        gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
        gl.glEnable(GL11.GL_TEXTURE_2D);
        mTexture2DEnabled = true;

        gl.glTexEnvf(GL11.GL_TEXTURE_ENV,
                GL11.GL_TEXTURE_ENV_MODE, GL11.GL_REPLACE);

        // Set the background color
        gl.glClearColor(0f, 0f, 0f, 0f);
        gl.glClearStencil(0);

        gl.glVertexPointer(2, GL11.GL_FLOAT, 0, mXyPointer);
        gl.glTexCoordPointer(2, GL11.GL_FLOAT, 0, mUvPointer);

    }

    /**
	 * Called when the OpenGL surface is recreated without destroying the
	 * context.
	 * @deprecated Use {@link com.lightbox.android.camera.ui.CameraEGLConfigChooser#onSurfaceChanged(com.lightbox.android.camera.ui.GLRootView,GL10,int,int)} instead
	 */
	// This is a GLSurfaceView.Renderer callback
	public void onSurfaceChanged(GL10 gl1, int width, int height) {
		mEglConfigChooser.onSurfaceChanged(this, gl1, width, height);
	}

    void setAlphaValue(float alpha) {
        if (mLastAlpha == alpha) return;

        GL11 gl = mGL;
        mLastAlpha = alpha;
        if (alpha >= 0.95f) {
            gl.glTexEnvf(GL11.GL_TEXTURE_ENV,
                    GL11.GL_TEXTURE_ENV_MODE, GL11.GL_REPLACE);
        } else {
            gl.glTexEnvf(GL11.GL_TEXTURE_ENV,
                    GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
            gl.glColor4f(alpha, alpha, alpha, alpha);
        }
    }

    static void putRectangle(float x, float y,
            float width, float height, float[] buffer, ByteBuffer pointer) {
        buffer[0] = x;
        buffer[1] = y;
        buffer[2] = x + width;
        buffer[3] = y;
        buffer[4] = x;
        buffer[5] = y + height;
        buffer[6] = x + width;
        buffer[7] = y + height;
        pointer.asFloatBuffer().put(buffer, 0, 8).position(0);
    }

    void drawRect(
            int x, int y, int width, int height, float matrix[]) {
        GL11 gl = mGL;
        gl.glPushMatrix();
        gl.glMultMatrixf(toGLMatrix(matrix), 0);
        putRectangle(x, y, width, height, mXyBuffer, mXyPointer);
        gl.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);
        gl.glPopMatrix();
    }

    float[] mapPoints(Matrix matrix, int x1, int y1, int x2, int y2) {
        float[] point = mXyBuffer;
        point[0] = x1; point[1] = y1; point[2] = x2; point[3] = y2;
        matrix.mapPoints(point, 0, point, 0, 4);
        return point;
    }

    static float[] toGLMatrix(float v[]) {
        v[15] = v[8]; v[13] = v[5]; v[5] = v[4]; v[4] = v[1];
        v[12] = v[2]; v[1] = v[3]; v[3] = v[6];
        v[2] = v[6] = v[8] = v[9] = 0;
        v[10] = 1;
        return v;
    }

    public void drawColor(int x, int y, int width, int height, int color) {
        float alpha = mTransformation.getAlpha();
        GL11 gl = mGL;
        if (mTexture2DEnabled) {
            // Set mLastAlpha to an invalid value, so that it will reset again
            // in setAlphaValue(float) later.
            mLastAlpha = -1.0f;
            gl.glDisable(GL11.GL_TEXTURE_2D);
            mTexture2DEnabled = false;
        }
        alpha /= 256.0f;
        gl.glColor4f(Color.red(color) * alpha, Color.green(color) * alpha,
                Color.blue(color) * alpha, Color.alpha(color) * alpha);
        mEglConfigChooser.drawRect(this, x, y, width, height);
    }

    public synchronized void onDrawFrame(GL10 gl) {
        if (ENABLE_FPS_TEST) {
            long now = System.nanoTime();
            if (mFrameCountingStart == 0) {
                mFrameCountingStart = now;
            } else if ((now - mFrameCountingStart) > 1000000000) {
                Log.v(TAG, "fps: " + (double) mFrameCount
                        * 1000000000 / (now - mFrameCountingStart));
                mFrameCountingStart = now;
                mFrameCount = 0;
            }
            ++mFrameCount;
        }

        if ((mFlags & FLAG_NEED_LAYOUT) != 0) layoutContentPane();
        mEglConfigChooser.clearClip(this);
        gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_STENCIL_BUFFER_BIT);
        gl.glEnable(GL11.GL_BLEND);
        gl.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

        mAnimationTime = SystemClock.uptimeMillis();
        if (mContentView != null) {
            mContentView.render(GLRootView.this, (GL11) gl);
        }
        long now = SystemClock.uptimeMillis();
        for (Animation animation : mAnimations) {
            animation.setStartTime(now);
        }
        mAnimations.clear();
    }

    @Override
    public synchronized boolean dispatchTouchEvent(MotionEvent event) {
        // If this has been detached from root, we don't need to handle event
        return mContentView != null
                ? mContentView.dispatchTouchEvent(event)
                : false;
    }

    boolean setBounds(GLView glView, int left, int top, int right, int bottom) {
	    boolean sizeChanged = (right - left) != (glView.mBounds.right - glView.mBounds.left)
	            || (bottom - top) != (glView.mBounds.bottom - glView.mBounds.top);
	    glView.mBounds.set(left, top, right, bottom);
	    return sizeChanged;
	}

	public Rect bounds(GLView glView) {
	    return glView.mBounds;
	}

	public Rect getPaddings(GLView glView) {
	    return glView.mPaddings;
	}

	protected void onAttachToRoot(GLView glView) {
	    glView.mRootView = this;
	    for (int i = 0, n = glView.getComponentCount(); i < n; ++i) {
	        glView.getComponent(i).onAttachToRoot(this);
	    }
	}

	protected void renderChild(GLView glView, GL11 gl, GLView component) {
	    int xoffset = component.mBounds.left - glView.mScrollX;
	    int yoffset = component.mBounds.top - glView.mScrollY;
	
	    Transformation transform = mContentView.getTransformation(this);
	    Matrix matrix = transform.getMatrix();
	    matrix.preTranslate(xoffset, yoffset);
	
	    Animation anim = component.mAnimation;
	    if (anim != null) {
	        long now = mEglConfigChooser.currentAnimationTimeMillis(this);
	        Transformation temp = mContentView.obtainTransformation(this);
	        if (!anim.getTransformation(now, temp)) {
	            component.mAnimation = null;
	        }
	        glView.invalidate();
	        mEglConfigChooser.pushTransform(this);
	        transform.compose(temp);
	        mContentView.freeTransformation(this, temp);
	    }
	    component.render(this, gl);
	    if (anim != null) mContentView.popTransform(this);
	    matrix.preTranslate(-xoffset, -yoffset);
	}

	public void doCancel(Camera camera) {
	    camera.setResult(Activity.RESULT_CANCELED, new Intent());
	    camera.finish();
	}

	public void viewLastImage(Camera camera) {
	    if (camera.mThumbController.isUriValid()) {
	        Intent intent = new Intent(Util.REVIEW_ACTION, camera.mThumbController.getUri());
	        try {
	            camera.startActivity(intent);
	        } catch (ActivityNotFoundException ex) {
	            try {
	                intent = new Intent(Intent.ACTION_VIEW, camera.mThumbController.getUri());
	                camera.startActivity(intent);
	            } catch (ActivityNotFoundException e) {
	                Log.e(Camera.TAG, "review image fail", e);
	            }
	        }
	    } else {
	        Log.e(Camera.TAG, "Can't view last image.");
	    }
	}

	public void onRestorePreferencesClicked(final Camera camera) {
	    if (camera.mPausing) return;
	    Runnable runnable = new Runnable() {
	        public void run() {
	            camera.mHeadUpDisplay.mSharedPrefs.restorePreferences(camera.mHeadUpDisplay, camera.mParameters);
	        }
	    };
	    MenuHelper.confirmAction(camera,
	            camera.getString(string.confirm_restore_title),
	            camera.getString(string.confirm_restore_message),
	            runnable);
	}

	public int getMeasuredWidth(GLView glView) {
	    return glView.mMeasuredWidth;
	}

	public void startAnimation(GLView glView, Animation animation) {
	    GLRootView root = glView.getGLRootView();
	    if (root == null) throw new IllegalStateException();
	
	    glView.mAnimation = animation;
	    animation.initialize(glView.mRootView.getWidth(glView),
	            glView.getHeight(), glView.mParent.mRootView.getWidth(glView.mParent), glView.mParent.getHeight());
	    glView.mAnimation.start();
	    root.registerLaunchedAnimation(animation);
	    glView.invalidate();
	}

	public void resetScreenOn(Camera camera) {
	    camera.mHandler.removeMessages(Camera.CLEAR_SCREEN_DELAY);
	    camera.getWindow().clearFlags(LayoutParams.FLAG_KEEP_SCREEN_ON);
	}

	public void onClick(Camera camera, View v) {
	    switch (v.getId()) {
	        /*case R.id.btn_retake:
	            hidePostCaptureAlert();
	            restartPreview();
	            break;*/
	        case id.review_thumbnail:
	            if (camera.isCameraIdle()) {
	                //viewLastImage();
	            	camera.mPreferences.startGallery(camera);
	            }
	            break;
	        /*case R.id.btn_done:
	            doAttach();
	            break;
	        case R.id.btn_cancel:
	            doCancel();
	            break;*/
	        case id.btn_flash:
	        	String flashMode = camera.mParameters.getFlashMode();
	        	String[] flashModes = camera.getResources().getStringArray(array.pref_camera_flashmode_entryvalues);
	        	Editor editor = camera.mPreferences.edit();
	        	if (flashMode.equals(flashModes[0])) {
	            	editor.putString("pref_camera_flashmode_key", flashModes[1]);
	        	} else if (flashMode.equals(flashModes[1])) {
	            	editor.putString("pref_camera_flashmode_key", flashModes[2]);   		
	        	} else if (flashMode.equals(flashModes[2])) {
	            	editor.putString("pref_camera_flashmode_key", flashModes[0]); 		
	        	};
	        	editor.commit();
	        	camera.mGLRootView.onSharedPreferenceChanged(camera);
	        	break;
	        case id.btn_camera_type:
	        	if (CameraHolder.instance().isFrontFacing(camera.mCameraId)) {
	        		int rearCamId = CameraHolder.instance().getRearFacingCameraId();
	        		camera.mThumbController.switchCameraId(camera, rearCamId);
	        	} else {
	        		int frontCamId = CameraHolder.instance().getFrontFacingCameraId();
	        		camera.mThumbController.switchCameraId(camera, frontCamId);
	        	}
	        	break;
	    }
	}

	protected boolean onTouch(GLView glView, MotionEvent event) {
	    if (glView.mOnTouchListener != null) {
	        return glView.mOnTouchListener.onTouch(glView, event);
	    }
	    return false;
	}

	public void onSharedPreferenceChanged(Camera camera) {
	    // ignore the events after "onPause()"
	    if (camera.mPausing) return;
	    
	    int cameraId = CameraSettings.readPreferredCameraId(camera.mPreferences);
	    if (camera.mCameraId != cameraId) {
	        camera.mThumbController.switchCameraId(camera, cameraId);
	    } else {
	        camera.setCameraParametersWhenIdle(Camera.UPDATE_PARAM_PREFERENCE);
	    }
	}

	public int getWidth(GLView glView) {
	    return glView.mBounds.right - glView.mBounds.left;
	}

	void renderSlider(ZoomController zoomController, GL11 gl) {
	    int left = zoomController.mSliderLeft;
	    int bottom = zoomController.mSliderBottom;
	    int top = zoomController.mSliderTop;
	    ZoomController.sBackground.draw(this, left, top, ZoomController.sBackground.getWidth(), bottom - top);
	
	    if (zoomController.mSliderPosition == ZoomController.INVALID_POSITION) {
	        ZoomController.sSlider.draw(this, left, (int)
	                (top + zoomController.mValueGap * (zoomController.mRatios.length - 1 - zoomController.mIndex)));
	    } else {
	        ZoomController.sSlider.draw(this, left, zoomController.mSliderPosition);
	    }
	}

}
