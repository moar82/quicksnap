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
import javax.microedition.khronos.opengles.GL11Ext;

import android.graphics.Matrix;

import com.lightbox.android.camera.Util;
import com.lightbox.android.camera.ui.BasicTexture;
import com.lightbox.android.camera.ui.GLRootView;
import com.lightbox.android.camera.ui.RawTexture;

class RawTexture extends BasicTexture {

    private RawTexture(GL11 gl, int id) {
        super(gl, id, STATE_LOADED);
    }

    public GL11 getBoundGL() {
        return mGL;
    }

    public static RawTexture newInstance(GL11 gl) {
        int[] textureId = new int[1];
        gl.glGenTextures(1, textureId, 0);
        int glError = gl.glGetError();
        if (glError != GL11.GL_NO_ERROR) {
            throw new RuntimeException("GL_ERROR: " + glError);
        }
        return new RawTexture(gl, textureId[0]);
    }

    @Override
    protected boolean bind(GLRootView glRootView, GL11 gl) {
        if (mGL == gl) {
            gl.glBindTexture(GL11.GL_TEXTURE_2D, getId());
            return true;
        }
        return false;
    }

    public void drawBack(GLRootView root, int x, int y, int w, int h) {
        drawTexture(root, x, y, w, h, 1f);
    }

	public void copyTexture2D(
	        GLRootView glRootView, int x, int y, int width, int height)
	        throws GLOutOfMemoryException {
	    Matrix matrix = glRootView.mTransformation.getMatrix();
	    matrix.getValues(glRootView.mMatrixValues);
		float[] matrix1 = glRootView.mMatrixValues;
	
	    if (matrix1[Matrix.MSKEW_X] != 0 || matrix1[Matrix.MSKEW_Y] != 0
		|| matrix1[Matrix.MSCALE_X] < 0 || matrix1[Matrix.MSCALE_Y] > 0) {
	        throw new IllegalArgumentException("cannot support rotated matrix");
	    }
	    float points[] = glRootView.mapPoints(matrix, x, y + height, x + width, y);
	    x = (int) points[0];
	    y = (int) points[1];
	    width = (int) points[2] - x;
	    height = (int) points[3] - y;
	
	    GL11 gl = glRootView.mGL;
	    int newWidth = Util.nextPowerOf2(width);
	    int newHeight = Util.nextPowerOf2(height);
	    int glError = GL11.GL_NO_ERROR;
	
	    gl.glBindTexture(GL11.GL_TEXTURE_2D, getId());
	
	    int[] cropRect = {0,  0, width, height};
	    gl.glTexParameteriv(GL11.GL_TEXTURE_2D,
	            GL11Ext.GL_TEXTURE_CROP_RECT_OES, cropRect, 0);
	    gl.glTexParameteri(GL11.GL_TEXTURE_2D,
	            GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP_TO_EDGE);
	    gl.glTexParameteri(GL11.GL_TEXTURE_2D,
	            GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP_TO_EDGE);
	    gl.glTexParameterf(GL11.GL_TEXTURE_2D,
	            GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
	    gl.glTexParameterf(GL11.GL_TEXTURE_2D,
	            GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
	    gl.glCopyTexImage2D(GL11.GL_TEXTURE_2D, 0,
	            GL11.GL_RGBA, x, y, newWidth, newHeight, 0);
	    glError = gl.glGetError();
	
	    if (glError == GL11.GL_OUT_OF_MEMORY) {
	        throw new GLOutOfMemoryException();
	    }
	
	    if (glError != GL11.GL_NO_ERROR) {
	        throw new RuntimeException(
	                "Texture copy fail, glError " + glError);
	    }
	
	    setSize(width, height);
	    setTextureSize(newWidth, newHeight);
	}
}
