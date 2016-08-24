package com.lightbox.android.camera.ui;

import java.util.Arrays;

import com.lightbox.android.camera.Util;
import com.lightbox.android.camera.ui.BasicTexture;
import com.lightbox.android.camera.ui.BitmapTexture;
import com.lightbox.android.camera.ui.GLOutOfMemoryException;
import com.lightbox.android.camera.ui.GLRootView;

import android.graphics.Bitmap;
import android.opengl.GLUtils;

import javax.microedition.khronos.opengles.GL11;
import javax.microedition.khronos.opengles.GL11Ext;

abstract class BitmapTexture extends BasicTexture {

    @SuppressWarnings("unused")
    private static final String TAG = "Texture";

    protected BitmapTexture() {
        super(null, 0, STATE_UNLOADED);
    }

    @Override
    public int getWidth() {
        if (mWidth == UNSPECIFIED) getBitmap();
        return mWidth;
    }

    @Override
    public int getHeight() {
        if (mWidth == UNSPECIFIED) getBitmap();
        return mHeight;
    }

    protected abstract Bitmap getBitmap();

    protected abstract void freeBitmap(Bitmap bitmap);

    private void uploadToGL(GL11 gl) throws GLOutOfMemoryException {
        Bitmap bitmap = getBitmap();
        int glError = GL11.GL_NO_ERROR;
        if (bitmap != null) {
            int[] textureId = new int[1];
            try {
                // Define a vertically flipped crop rectangle for
                // OES_draw_texture.
                int width = bitmap.getWidth();
                int height = bitmap.getHeight();
                int[] cropRect = {0,  height, width, -height};

                // Upload the bitmap to a new texture.
                gl.glGenTextures(1, textureId, 0);
                gl.glBindTexture(GL11.GL_TEXTURE_2D, textureId[0]);
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

                int widthExt = Util.nextPowerOf2(width);
                int heightExt = Util.nextPowerOf2(height);
                int format = GLUtils.getInternalFormat(bitmap);
                int type = GLUtils.getType(bitmap);

                mTextureWidth = widthExt;
                mTextureHeight = heightExt;
                gl.glTexImage2D(GL11.GL_TEXTURE_2D, 0, format,
                        widthExt, heightExt, 0, format, type, null);
                GLUtils.texSubImage2D(
                        GL11.GL_TEXTURE_2D, 0, 0, 0, bitmap, format, type);
            } finally {
                freeBitmap(bitmap);
            }
            if (glError == GL11.GL_OUT_OF_MEMORY) {
                throw new GLOutOfMemoryException();
            }
            if (glError != GL11.GL_NO_ERROR) {
                mId = 0;
                mState = STATE_UNLOADED;
                throw new RuntimeException(
                        "Texture upload fail, glError " + glError);
            } else {
                // Update texture state.
                mGL = gl;
                mId = textureId[0];
                mState = BitmapTexture.STATE_LOADED;
            }
        } else {
            mState = STATE_ERROR;
            throw new RuntimeException("Texture load fail, no bitmap");
        }
    }

    @Override
    protected boolean bind(GLRootView root, GL11 gl) {
        if (mState == BitmapTexture.STATE_UNLOADED || mGL != gl) {
            mState = BitmapTexture.STATE_UNLOADED;
            try {
                uploadToGL(gl);
            } catch (GLOutOfMemoryException e) {
                root.mEglConfigChooser.handleLowMemory();
                return false;
            }
        } else {
            gl.glBindTexture(GL11.GL_TEXTURE_2D, getId());
        }
        return true;
    }

	public void setAvailableZoomRatios(ZoomController zoomController, float[] ratios) {
	    if (Arrays.equals(ratios, zoomController.mRatios)) return;
	    zoomController.mRatios = ratios;
	    zoomController.mLabelStep = zoomController.sFineTickMark.getLabelStep(ratios.length);
	    zoomController.mTickLabels = new CanvasTexture[
	            (ratios.length + zoomController.mLabelStep - 1) / zoomController.mLabelStep];
	    for (int i = 0, n = zoomController.mTickLabels.length; i < n; ++i) {
	        zoomController.mTickLabels[i] = CanvasTexture.newInstance(
	                ZoomController.sZoomFormat.format(ratios[i * zoomController.mLabelStep]),
	                ZoomController.sLabelSize, ZoomController.LABEL_COLOR);
	    }
	    zoomController.mFineTickStep = zoomController.mLabelStep % 3 == 0
	            ? zoomController.mLabelStep / 3
	            : zoomController.mLabelStep %2 == 0 ? zoomController.mLabelStep / 2 : 0;
	
	    int maxHeight = 0;
	    int maxWidth = 0;
	    int labelCount = zoomController.mTickLabels.length;
	    for (int i = 0; i < labelCount; ++i) {
	        maxWidth = Math.max(maxWidth, zoomController.mTickLabels[i].getWidth());
	        maxHeight = Math.max(maxHeight, zoomController.mTickLabels[i].getHeight());
	    }
	
	    zoomController.mMaxLabelHeight = maxHeight;
	    zoomController.mMaxLabelWidth = maxWidth;
	    zoomController.invalidate();
	}

	void onSliderMoved(ZoomController zoomController, int position, boolean isMoving) {
	    position = Util.clamp(position,
	            zoomController.mSliderTop, zoomController.mSliderBottom - ZoomController.sSlider.getHeight());
	    zoomController.mSliderPosition = position;
	    zoomController.invalidate();
	
	    int index = zoomController.mRatios.length - 1 - (int)
	            ((position - zoomController.mSliderTop) /  zoomController.mValueGap + .5f);
	    if (index != zoomController.mIndex || !isMoving) {
	        zoomController.mIndex = index;
	        if (zoomController.mZoomListener != null) {
	            zoomController.mZoomListener.onZoomChanged(zoomController.mIndex, zoomController.mRatios[zoomController.mIndex], isMoving);
	        }
	    }
	}

	int getLabelStep(final int valueCount) {
	    if (valueCount < 5) return 1;
	    for (int step = valueCount / 5;; ++step) {
	        if (valueCount / step <= 5) return step;
	    }
	}
}
