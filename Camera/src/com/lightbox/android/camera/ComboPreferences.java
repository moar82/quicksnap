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

package com.lightbox.android.camera;

import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.lightbox.android.camera.activities.Camera;
import com.lightbox.android.camera.ui.GLRootView;
import com.lightbox.android.camera.ui.HeadUpDisplay;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.hardware.Camera.Parameters;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

public class ComboPreferences implements SharedPreferences, OnSharedPreferenceChangeListener {
    private SharedPreferences mPrefGlobal;  // global preferences
    private SharedPreferences mPrefLocal;  // per-camera preferences
    private CopyOnWriteArrayList<OnSharedPreferenceChangeListener> mListeners;
    private static WeakHashMap<Context, ComboPreferences> sMap =
            new WeakHashMap<Context, ComboPreferences>();

    public ComboPreferences(Context context) {
        mPrefGlobal = PreferenceManager.getDefaultSharedPreferences(context);
        mPrefGlobal.registerOnSharedPreferenceChangeListener(this);
        synchronized (sMap) {
            sMap.put(context, this);
        }
        mListeners = new CopyOnWriteArrayList<OnSharedPreferenceChangeListener>();
    }

    public static ComboPreferences get(Context context) {
        synchronized (sMap) {
            return sMap.get(context);
        }
    }

    public void setLocalId(Context context, int cameraId) {
        String prefName = context.getPackageName() + "_preferences_" + cameraId;
        if (mPrefLocal != null) {
            mPrefLocal.unregisterOnSharedPreferenceChangeListener(this);
        }
        mPrefLocal = context.getSharedPreferences(
                prefName, Context.MODE_PRIVATE);
        mPrefLocal.registerOnSharedPreferenceChangeListener(this);
    }

    public SharedPreferences getGlobal() {
        return mPrefGlobal;
    }

    public SharedPreferences getLocal() {
        return mPrefLocal;
    }

    public Map<String, ?> getAll() {
        throw new UnsupportedOperationException(); // Can be implemented if needed.
    }

    public String getString(String key, String defValue) {
        if (key.equals(CameraSettings.KEY_CAMERA_ID) || !mPrefLocal.contains(key)) {
            return mPrefGlobal.getString(key, defValue);
        } else {
            return mPrefLocal.getString(key, defValue);
        }
    }

    public int getInt(String key, int defValue) {
        if (key.equals(CameraSettings.KEY_CAMERA_ID) || !mPrefLocal.contains(key)) {
            return mPrefGlobal.getInt(key, defValue);
        } else {
            return mPrefLocal.getInt(key, defValue);
        }
    }

    public long getLong(String key, long defValue) {
        if (key.equals(CameraSettings.KEY_CAMERA_ID) || !mPrefLocal.contains(key)) {
            return mPrefGlobal.getLong(key, defValue);
        } else {
            return mPrefLocal.getLong(key, defValue);
        }
    }

    public float getFloat(String key, float defValue) {
        if (key.equals(CameraSettings.KEY_CAMERA_ID) || !mPrefLocal.contains(key)) {
            return mPrefGlobal.getFloat(key, defValue);
        } else {
            return mPrefLocal.getFloat(key, defValue);
        }
    }

    public boolean getBoolean(String key, boolean defValue) {
        if (key.equals(CameraSettings.KEY_CAMERA_ID) || !mPrefLocal.contains(key)) {
            return mPrefGlobal.getBoolean(key, defValue);
        } else {
            return mPrefLocal.getBoolean(key, defValue);
        }
    }

    // This method is not used.
    public Set<String> getStringSet(String key, Set<String> defValues) {
        throw new UnsupportedOperationException();
    }

    public boolean contains(String key) {
        if (mPrefLocal.contains(key)) return true;
        if (mPrefGlobal.contains(key)) return true;
        return false;
    }

    private class MyEditor implements Editor {
        private Editor mEditorGlobal;
        private Editor mEditorLocal;

        MyEditor() {
            mEditorGlobal = mPrefGlobal.edit();
            mEditorLocal = mPrefLocal.edit();
        }

        public boolean commit() {
            boolean result1 = mEditorGlobal.commit();
            boolean result2 = mEditorLocal.commit();
            return result1 && result2;
        }

        public void apply() {
        	if (Build.VERSION.SDK_INT >= 0x00000009 /*Build.VERSION_CODES.GINGERBREAD*/) {
        		mEditorGlobal.apply();
            	mEditorLocal.apply();
        	} else {
        		commit();
        	}
        }

        // Note: clear() and remove() affects both local and global preferences.
        public Editor clear() {
            mEditorGlobal.clear();
            mEditorLocal.clear();
            return this;
        }

        public Editor remove(String key) {
            mEditorGlobal.remove(key);
            mEditorLocal.remove(key);
            return this;
        }

        public Editor putString(String key, String value) {
            if (key.equals(CameraSettings.KEY_CAMERA_ID)) {
                mEditorGlobal.putString(key, value);
            } else {
                mEditorLocal.putString(key, value);
            }
            return this;
        }

        public Editor putInt(String key, int value) {
            if (key.equals(CameraSettings.KEY_CAMERA_ID)) {
                mEditorGlobal.putInt(key, value);
            } else {
                mEditorLocal.putInt(key, value);
            }
            return this;
        }

        public Editor putLong(String key, long value) {
            if (key.equals(CameraSettings.KEY_CAMERA_ID)) {
                mEditorGlobal.putLong(key, value);
            } else {
                mEditorLocal.putLong(key, value);
            }
            return this;
        }

        public Editor putFloat(String key, float value) {
            if (key.equals(CameraSettings.KEY_CAMERA_ID)) {
                mEditorGlobal.putFloat(key, value);
            } else {
                mEditorLocal.putFloat(key, value);
            }
            return this;
        }

        public Editor putBoolean(String key, boolean value) {
            if (key.equals(CameraSettings.KEY_CAMERA_ID)) {
                mEditorGlobal.putBoolean(key, value);
            } else {
                mEditorLocal.putBoolean(key, value);
            }
            return this;
        }

        // This method is not used.
        public Editor putStringSet(String key, Set<String> values) {
            throw new UnsupportedOperationException();
        }
    }

    // Note the remove() and clear() of the returned Editor may not work as
    // expected because it doesn't touch the global preferences at all.
    public Editor edit() {
        return new MyEditor();
    }

    public void registerOnSharedPreferenceChangeListener(
            OnSharedPreferenceChangeListener listener) {
        mListeners.add(listener);
    }

    public void unregisterOnSharedPreferenceChangeListener(
            OnSharedPreferenceChangeListener listener) {
        mListeners.remove(listener);
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {
        for (OnSharedPreferenceChangeListener listener : mListeners) {
            listener.onSharedPreferenceChanged(this, key);
        }
    }

	public void startGallery(Camera camera) {
		if (camera.mIsLightboxPhotosIntent) {
			camera.setResult(Activity.RESULT_CANCELED);
			camera.finish();
		} else 	if (camera.mThumbController.isUriValid()) { 
	    	// Open in the gallery
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

	public void updateSceneModeInHud(Camera camera) {
	    // If scene mode is set, we cannot set flash mode, white balance, and
	    // focus mode, instead, we read it from driver
	    if (!Parameters.SCENE_MODE_AUTO.equals(camera.mSceneMode)) {
	        camera.mHeadUpDisplay.overrideHudSettings(camera.mParameters.getFlashMode(),
	                camera.mParameters.getWhiteBalance(), camera.mParameters.getFocusMode());
	    } else {
	        camera.mHeadUpDisplay.overrideHudSettings(null, null, null);
	    }
	}

	public void restorePreferences(HeadUpDisplay headUpDisplay, final Parameters param) {
	    // Do synchronization in "reloadPreferences()"
	
	    OnSharedPreferenceChangeListener l =
	            headUpDisplay.mSharedPreferenceChangeListener;
	    // Unregister the listener since "upgrade preference" will
	    // change bunch of preferences. We can handle them with one
	    // onSharedPreferencesChanged();
	    unregisterOnSharedPreferenceChangeListener(l);
	    Context context = headUpDisplay.getGLRootView().getContext();
	    Editor editor = edit();
	    editor.clear();
	    editor.commit();
	    CameraSettings.upgradeAllPreferences(this);
	    CameraSettings.initialCameraPictureSize(context, param);
	    headUpDisplay.reloadPreferences();
	    if (headUpDisplay.mListener != null) {
	        headUpDisplay.mListener.onSharedPreferencesChanged();
	    }
	    registerOnSharedPreferenceChangeListener(l);
	}

	public void initializeSecondTime(Camera camera) {
		// Create orientation listenter. This should be done first because it
	    // takes some time to get first orientation.
	    ((CameraApplication)camera.getApplication()).registerOrientationChangeListener(camera.mOrientationChangeListener);
	    
	    camera.mFocusRectangle.installIntentFilter(camera);
	    camera.initializeFocusTone();
	    camera.initializeZoom();
	    camera.changeHeadUpDisplayState();
	
	    camera.keepMediaProviderInstance();
	    camera.checkStorage();
	
	    if (!camera.mIsImageCaptureIntent) {
	        camera.updateThumbnailButton();
	    }
	}

	public void cancelAutoFocus(Camera camera) {
	    // User releases half-pressed focus key.
	    if (camera.mStatus != Camera.SNAPSHOT_IN_PROGRESS && (camera.mFocusState == Camera.FOCUSING
	            || camera.mFocusState == Camera.FOCUS_SUCCESS || camera.mFocusState == Camera.FOCUS_FAIL)) {
	        Log.v(Camera.TAG, "Cancel autofocus.");
	        camera.mHeadUpDisplay.mSharedPrefs.setEnabled(camera.mHeadUpDisplay, true);
	        camera.mCameraDevice.cancelAutoFocus();
	    }
	    if (camera.mFocusState != Camera.FOCUSING_SNAP_ON_FINISH) {
	        camera.clearFocusState();
	    }
	}

	public void setEnabled(HeadUpDisplay headUpDisplay, boolean enabled) {
	    // The mEnabled variable is not related to the rendering thread, so we
	    // don't need to synchronize on the GLRootView.
	    if (headUpDisplay.mEnabled == enabled) return;
	    headUpDisplay.mEnabled = enabled;
	}

	public void setOrientation(HeadUpDisplay headUpDisplay, int orientation) {
	    GLRootView root = headUpDisplay.getGLRootView();
	    if (root != null) {
	        synchronized (root) {
	            headUpDisplay.setOrientationLocked(orientation);
	        }
	    } else {
	        headUpDisplay.setOrientationLocked(orientation);
	    }
	}
}
