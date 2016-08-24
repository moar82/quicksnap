/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.graphics.Matrix;
import android.opengl.GLSurfaceView.EGLConfigChooser;
import android.opengl.GLU;
import android.util.Log;
import android.view.animation.Transformation;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.opengles.GL11;

/*
 * The code is copied/adapted from
 * <code>android.opengl.GLSurfaceView.BaseConfigChooser</code>. Here we try to
 * choose a configuration that support RGBA_8888 format and if possible,
 * with stencil buffer, but is not required.
 */
class CameraEGLConfigChooser implements EGLConfigChooser {

    private static final int COLOR_BITS = 8;

    private int mStencilBits;

    private final int mConfigSpec[] = new int[] {
            EGL10.EGL_RED_SIZE, COLOR_BITS,
            EGL10.EGL_GREEN_SIZE, COLOR_BITS,
            EGL10.EGL_BLUE_SIZE, COLOR_BITS,
            EGL10.EGL_ALPHA_SIZE, COLOR_BITS,
            EGL10.EGL_NONE
    };

    public int getStencilBits() {
        return mStencilBits;
    }

    public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
        int[] numConfig = new int[1];
        if (!egl.eglChooseConfig(display, mConfigSpec, null, 0, numConfig)) {
            throw new RuntimeException("eglChooseConfig failed");
        }

        if (numConfig[0] <= 0) {
            throw new RuntimeException("No configs match configSpec");
        }

        EGLConfig[] configs = new EGLConfig[numConfig[0]];
        if (!egl.eglChooseConfig(display,
                mConfigSpec, configs, configs.length, numConfig)) {
            throw new RuntimeException();
        }

        return chooseConfig(egl, display, configs);
    }

    private EGLConfig chooseConfig(
            EGL10 egl, EGLDisplay display, EGLConfig configs[]) {

        EGLConfig result = null;
        int minStencil = Integer.MAX_VALUE;
        int value[] = new int[1];

        // Because we need only one bit of stencil, try to choose a config that
        // has stencil support but with smallest number of stencil bits. If
        // none is found, choose any one.
        for (int i = 0, n = configs.length; i < n; ++i) {
            if (egl.eglGetConfigAttrib(
                    display, configs[i], EGL10.EGL_STENCIL_SIZE, value)) {
                if (value[0] == 0) continue;
                if (value[0] < minStencil) {
                    minStencil = value[0];
                    result = configs[i];
                }
            } else {
                throw new RuntimeException(
                        "eglGetConfigAttrib error: " + egl.eglGetError());
            }
        }
        if (result == null) result = configs[0];
        egl.eglGetConfigAttrib(
                display, result, EGL10.EGL_STENCIL_SIZE, value);
        mStencilBits = value[0];
        return result;
    }

	public void clearClip(GLRootView glRootView) {
	    glRootView.mGL.glScissor(0, 0, glRootView.getWidth(), glRootView.getHeight());
	}

	public Transformation pushTransform(GLRootView glRootView) {
	    Transformation trans = glRootView.mContentView.obtainTransformation(glRootView);
	    trans.set(glRootView.mTransformation);
	    glRootView.mTransformStack.push(trans);
	    return glRootView.mTransformation;
	}

	public long currentAnimationTimeMillis(GLRootView glRootView) {
	    return glRootView.mAnimationTime;
	}

	public void drawRect(GLRootView glRootView, int x, int y, int width, int height) {
	    float matrix[] = glRootView.mMatrixValues;
	    glRootView.mTransformation.getMatrix().getValues(matrix);
	    glRootView.drawRect(x, y, width, height, matrix);
	}

	void handleLowMemory() {
	    //TODO: delete texture from GL
	}

	public GLView getContentPane(GLRootView glRootView) {
	    return glRootView.mContentView;
	}

	void drawMesh(
	        GLRootView glRootView, int[] x, int[] y, float[] u, float[] v, int nx, int ny) {
	    /*
	     * Given a 3x3 nine-patch image, the vertex order is defined as the
	     * following graph:
	     *
	     * (0) (1) (2) (3)
	     *  |  /|  /|  /|
	     *  | / | / | / |
	     * (4) (5) (6) (7)
	     *  | \ | \ | \ |
	     *  |  \|  \|  \|
	     * (8) (9) (A) (B)
	     *  |  /|  /|  /|
	     *  | / | / | / |
	     * (C) (D) (E) (F)
	     *
	     * And we draw the triangle strip in the following index order:
	     *
	     * index: 04152637B6A5948C9DAEBF
	     */
	    int pntCount = 0;
	    float xy[] = glRootView.mXyBuffer;
	    float uv[] = glRootView.mUvBuffer;
	    for (int j = 0; j < ny; ++j) {
	        for (int i = 0; i < nx; ++i) {
	            int xIndex = (pntCount++) << 1;
	            int yIndex = xIndex + 1;
	            xy[xIndex] = x[i];
	            xy[yIndex] = y[j];
	            uv[xIndex] = u[i];
	            uv[yIndex] = v[j];
	        }
	    }
	    glRootView.mUvPointer.asFloatBuffer().put(uv, 0, pntCount << 1).position(0);
	    glRootView.mXyPointer.asFloatBuffer().put(xy, 0, pntCount << 1).position(0);
	
	    int idxCount = 1;
	    byte index[] = glRootView.mIndexBuffer;
	    for (int i = 0, bound = nx * (ny - 1); true;) {
	        // normal direction
	        --idxCount;
	        for (int j = 0; j < nx; ++j, ++i) {
	            index[idxCount++] = (byte) i;
	            index[idxCount++] = (byte) (i + nx);
	        }
	        if (i >= bound) break;
	
	        // reverse direction
	        int sum = i + i + nx - 1;
	        --idxCount;
	        for (int j = 0; j < nx; ++j, ++i) {
	            index[idxCount++] = (byte) (sum - i);
	            index[idxCount++] = (byte) (sum - i + nx);
	        }
	        if (i >= bound) break;
	    }
	    glRootView.mIndexPointer.put(index, 0, idxCount).position(0);
	
	    glRootView.mGL.glDrawElements(GL11.GL_TRIANGLE_STRIP,
	            idxCount, GL11.GL_UNSIGNED_BYTE, glRootView.mIndexPointer);
	}

	/**
	 * Called when the OpenGL surface is recreated without destroying the
	 * context.
	 * @param glRootView TODO
	 * @param gl1 TODO
	 * @param width TODO
	 * @param height TODO
	 */
	// This is a GLSurfaceView.Renderer callback
	public void onSurfaceChanged(GLRootView glRootView, GL10 gl1, int width, int height) {
	    Log.v(GLRootView.TAG, "onSurfaceChanged: " + width + "x" + height
	            + ", gl10: " + gl1.toString());
	    GL11 gl = (GL11) gl1;
	    glRootView.mGL = gl;
	    gl.glViewport(0, 0, width, height);
	
	    gl.glMatrixMode(GL11.GL_PROJECTION);
	    gl.glLoadIdentity();
	
	    GLU.gluOrtho2D(gl, 0, width, 0, height);
	    Matrix matrix = glRootView.mTransformation.getMatrix();
	    matrix.reset();
	    matrix.preTranslate(0, glRootView.getHeight());
	    matrix.preScale(1, -1);
	}
}
