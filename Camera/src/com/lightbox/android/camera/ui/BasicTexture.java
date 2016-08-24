package com.lightbox.android.camera.ui;

import javax.microedition.khronos.opengles.GL11;
import javax.microedition.khronos.opengles.GL11Ext;

import android.graphics.Matrix;

import com.lightbox.android.camera.ui.GLRootView;
import com.lightbox.android.camera.ui.Texture;

abstract class BasicTexture implements Texture {

    protected static final int UNSPECIFIED = -1;

    public static final int STATE_UNLOADED = 0;
    public static final int STATE_LOADED = 1;
    public static final int STATE_ERROR = -1;

    protected GL11 mGL;

    protected int mId;
    protected int mState;

    protected int mWidth = UNSPECIFIED;
    protected int mHeight = UNSPECIFIED;

    protected int mTextureWidth;
    protected int mTextureHeight;

    protected BasicTexture(GL11 gl, int id, int state) {
        mGL = gl;
        mId = id;
        mState = state;
    }

    protected BasicTexture() {
        this(null, 0, STATE_UNLOADED);
    }

    protected void setSize(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    /**
     * Sets the size of the texture. Due to the limit of OpenGL, the texture
     * size must be of power of 2, the size of the content may not be the size
     * of the texture.
     */
    protected void setTextureSize(int width, int height) {
        mTextureWidth = width;
        mTextureHeight = height;
    }

    public int getId() {
        return mId;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public void deleteFromGL() {
        if (mState == STATE_LOADED) {
            mGL.glDeleteTextures(1, new int[]{mId}, 0);
        }
        mState = STATE_UNLOADED;
    }

    public void draw(GLRootView root, int x, int y) {
        drawTexture(root, x, y, mWidth, mHeight);
    }

    public void draw(GLRootView root, int x, int y, int w, int h) {
        drawTexture(root, x, y, w, h);
    }

    abstract protected boolean bind(GLRootView root, GL11 gl);

	public void drawTexture(
	        GLRootView glRootView, int x, int y, int width, int height) {
	    drawTexture(glRootView, x, y, width, height, glRootView.mTransformation.getAlpha());
	}

	public void drawTexture(GLRootView glRootView, int x, int y, int width, int height, float alpha) {
	
	    if (!GLRootView.mTexture2DEnabled) {
	        glRootView.mGL.glEnable(GL11.GL_TEXTURE_2D);
	        GLRootView.mTexture2DEnabled = true;
	    }
	
	    if (!bind(glRootView, glRootView.mGL)) {
	        throw new RuntimeException("cannot bind" + toString());
	    }
	    if (width <= 0 || height <= 0) return ;
	
	    Matrix matrix = glRootView.mTransformation.getMatrix();
	    matrix.getValues(glRootView.mMatrixValues);
		float[] matrix1 = glRootView.mMatrixValues;
	
	    // Test whether it has been rotated or flipped, if so, glDrawTexiOES
	    // won't work
	    if (matrix1[Matrix.MSKEW_X] != 0 || matrix1[Matrix.MSKEW_Y] != 0
		|| matrix1[Matrix.MSCALE_X] < 0 || matrix1[Matrix.MSCALE_Y] > 0) {
	        GLRootView.putRectangle(0, 0,
	                (mWidth - 0.5f) / mTextureWidth,
	                (mHeight - 0.5f) / mTextureHeight,
	                glRootView.mUvBuffer, glRootView.mUvPointer);
	        glRootView.setAlphaValue(alpha);
	        glRootView.drawRect(x, y, width, height, glRootView.mMatrixValues);
	    } else {
	        // draw the rect from bottom-left to top-right
	        float points[] = glRootView.mapPoints(matrix, x, y + height, x + width, y);
	        x = (int) points[0];
	        y = (int) points[1];
	        width = (int) points[2] - x;
	        height = (int) points[3] - y;
	        if (width > 0 && height > 0) {
	            glRootView.setAlphaValue(alpha);
	            ((GL11Ext) glRootView.mGL).glDrawTexiOES(x, y, 0, width, height);
	        }
	    }
	}
}
