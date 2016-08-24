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

import java.io.File;

import android.content.Context;
import android.graphics.Bitmap;
import android.hardware.Camera.Parameters;
import android.os.Build;
import android.os.Build.VERSION;
import android.util.Log;

import com.lightbox.android.camera.CameraSettings;
import com.lightbox.android.camera.ImageManager;
import com.lightbox.android.camera.ListPreference;
import com.lightbox.android.camera.ParameterUtils;
import com.lightbox.android.camera.PreferenceGroup;
import com.lightbox.android.camera.Util;
import com.lightbox.android.camera.activities.Camera;
import com.lightbox.android.camera.device.CameraHolder;

public class CameraHeadUpDisplay extends HeadUpDisplay {

    @SuppressWarnings("unused")
	private static final String TAG = "CamcoderHeadUpDisplay";

    private OtherSettingsIndicator mOtherSettings;
    private ZoomIndicator mZoomIndicator;
    private Context mContext;
    private float[] mInitialZoomRatios;
    private int mInitialOrientation;

    public CameraHeadUpDisplay(Context context) {
        super(context);
        mContext = context;
    }

    public void initialize(Context context, PreferenceGroup group,
            float[] initialZoomRatios, int initialOrientation) {
        mInitialZoomRatios = initialZoomRatios;
        mInitialOrientation = initialOrientation;
        super.initialize(context, group);
    }

    @Override
    protected void initializeIndicatorBar(
            Context context, PreferenceGroup group) {
        super.initializeIndicatorBar(context, group);

        ListPreference prefs[] = getListPreferences(group,
                CameraSettings.KEY_FOCUS_MODE,
                CameraSettings.KEY_WHITE_BALANCE,
                CameraSettings.KEY_EXPOSURE,
                CameraSettings.KEY_SCENE_MODE);

        mOtherSettings = new OtherSettingsIndicator(context, prefs);
        mOtherSettings.setOnRestorePreferencesClickedRunner(new Runnable() {
            public void run() {
                if (mListener != null) {
                    mListener.onRestorePreferencesClicked();
                }
            }
        });
        mIndicatorBar.addComponent(mOtherSettings);

        if (mInitialZoomRatios != null) {
            mZoomIndicator = new ZoomIndicator(mContext);
            mZoomIndicator.setZoomRatios(mInitialZoomRatios);
            mIndicatorBar.addComponent(mZoomIndicator);
        } else {
            mZoomIndicator = null;
        }

        mIndicatorBar.setOrientation(mInitialOrientation);
    }

    public void setZoomListener(ZoomControllerListener listener) {
        // The rendering thread won't access listener variable, so we don't
        // need to do concurrency protection here
        mZoomIndicator.setZoomListener(listener);
    }

    public void setZoomIndex(int index) {
    	if (mZoomIndicator != null) {
	        GLRootView root = getGLRootView();
	        if (root != null) {
	            synchronized (root) {
	                mZoomIndicator.setZoomIndex(index);
	            }
	        } else {
	            mZoomIndicator.setZoomIndex(index);
	        }
    	}
    }

    private void setZoomRatiosLocked(float[] zoomRatios) {
        mZoomIndicator.setZoomRatios(zoomRatios);
    }

	public Bitmap createCaptureBitmap(Camera camera, byte[] data) {
	    // This is really stupid...we just want to read the orientation in
	    // the jpeg header.
	    String filepath = ImageManager.getTempJpegPath();
	    int degree = 0;
	    if (camera.mThumbController.saveDataToFile(filepath, data)) {
	        degree = ImageManager.getExifOrientation(filepath);
	        new File(filepath).delete();
	    }
	
	    // Limit to 50k pixels so we can return it in the intent.
	    Bitmap bitmap = Util.makeBitmap(data, 50 * 1024);
	    bitmap = Util.rotate(bitmap, degree);
	    return bitmap;
	}

	public void closeCamera(Camera camera) {
	    if (camera.mCameraDevice != null) {
	        CameraHolder.instance().release();
	        if (VERSION.SDK_INT >= 0x00000008) {
	        	camera.mCameraDevice.setZoomChangeListener(null);
	        }
	        camera.mCameraDevice = null;
	        camera.mPreviewing = false;
	    }
	}

	public boolean canTakePicture(Camera camera) {
	    return camera.isCameraIdle() && camera.mPreviewing && (camera.mPicturesRemaining > 0);
	}

	public void overrideHudSettings(final String flashMode, final String whiteBalance, final String focusMode) {
	    overrideSettings(
	            CameraSettings.KEY_FLASH_MODE, flashMode,
	            CameraSettings.KEY_WHITE_BALANCE, whiteBalance,
	            CameraSettings.KEY_FOCUS_MODE, focusMode);
	}

	public void doSnap(Camera camera) {
	    if (collapse()) return;
	
	    Log.v(Camera.TAG, "doSnap: mFocusState=" + camera.mFocusState);
	    // If the user has half-pressed the shutter and focus is completed, we
	    // can take the photo right away. If the focus mode is infinity, we can
	    // also take the photo.
	    if (camera.mFocusMode.equals(Parameters.FOCUS_MODE_INFINITY)
	            || camera.mFocusMode.equals(Parameters.FOCUS_MODE_FIXED)
	            || camera.mFocusMode.equals(ParameterUtils.FOCUS_MODE_EDOF)
	            || (camera.mFocusState == Camera.FOCUS_SUCCESS
	            || camera.mFocusState == Camera.FOCUS_FAIL)) {
	        camera.mImageCapture.onSnap();
	    } else if (camera.mFocusState == Camera.FOCUSING) {
	        // Half pressing the shutter (i.e. the focus button event) will
	        // already have requested AF for us, so just request capture on
	        // focus here.
	        camera.mFocusState = Camera.FOCUSING_SNAP_ON_FINISH;
	    } else if (camera.mFocusState == Camera.FOCUS_NOT_STARTED) {
	        // Focus key down event is dropped for some reasons. Just ignore.
	    }
	}
}
