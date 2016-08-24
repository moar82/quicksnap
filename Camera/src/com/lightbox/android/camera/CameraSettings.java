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

package com.lightbox.android.camera;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.media.CamcorderProfile;
import android.os.Build;
import android.util.Log;

import com.lightbox.android.camera.device.CameraHolder;

/**
 *  Provides utilities and keys for Camera settings.
 */
public class CameraSettings {
    static final int NOT_FOUND = -1;

    public static final String KEY_VERSION = "pref_version_key";
    public static final String KEY_LOCAL_VERSION = "pref_local_version_key";
    public static final String KEY_VIDEO_QUALITY = "pref_video_quality_key";
    public static final String KEY_PICTURE_SIZE = "pref_camera_picturesize_key";
    public static final String KEY_JPEG_QUALITY = "pref_camera_jpegquality_key";
    public static final String KEY_FOCUS_MODE = "pref_camera_focusmode_key";
    public static final String KEY_FLASH_MODE = "pref_camera_flashmode_key";
    public static final String KEY_VIDEOCAMERA_FLASH_MODE = "pref_camera_video_flashmode_key";
    public static final String KEY_COLOR_EFFECT = "pref_camera_coloreffect_key";
    public static final String KEY_WHITE_BALANCE = "pref_camera_whitebalance_key";
    public static final String KEY_SCENE_MODE = "pref_camera_scenemode_key";
    public static final String KEY_EXPOSURE = "pref_camera_exposure_key";
    public static final String KEY_CAMERA_ID = "pref_camera_id_key";

    private static final String VIDEO_QUALITY_HIGH = "high";
    private static final String VIDEO_QUALITY_MMS = "mms";
    private static final String VIDEO_QUALITY_YOUTUBE = "youtube";

    public static final String EXPOSURE_DEFAULT_VALUE = "0";

    public static final int CURRENT_VERSION = 4;
    public static final int CURRENT_LOCAL_VERSION = 1;

    // max video duration in seconds for mms and youtube.
    private static final int MMS_VIDEO_DURATION = (Build.VERSION.SDK_INT >= 0x00000008) ? CamcorderProfile.get(CamcorderProfile.QUALITY_LOW).duration : 60;
    private static final int YOUTUBE_VIDEO_DURATION = 10 * 60; // 10 mins
    private static final int DEFAULT_VIDEO_DURATION = 30 * 60; // 10 mins

    public static final String DEFAULT_VIDEO_QUALITY_VALUE = "high";

    // MMS video length
    public static final int DEFAULT_VIDEO_DURATION_VALUE = -1;

    private static final String TAG = "CameraSettings";

    public final Context mContext;
    final Parameters mParameters;
    final CameraHolder mCameraHolder;

    public CameraSettings(Activity activity, Parameters parameters,
    		CameraHolder cameraHolder) {
        mContext = activity;
        mParameters = parameters;
        mCameraHolder = cameraHolder;
    }

    public static void initialCameraPictureSize(Context context, Parameters parameters) {
		// When launching the camera app first time, we will set the picture
		// size to the first one in the list defined in "arrays.xml" and is also
		// supported by the driver.
		List<Size> supported = parameters.getSupportedPictureSizes();
		if (supported == null)
			return;

		Size candidate = null;
		for (Size size : supported) {
			// check for 4:3 ratio
			if (Math.abs((((float) size.width / 4) * 3) - size.height) < (0.10f * size.width)) { // 10% tolerence to 4:3 ratio
				if (candidate == null || size.height > candidate.height) {
					candidate = size;
				}
			}
		}

		if (candidate != null) {
			parameters.setPictureSize(candidate.width, candidate.height);
		} else {
			Log.e(TAG, "No supported picture size found");
		}
	}

    public static void removePreferenceFromScreen(
            PreferenceGroup group, String key) {
        removePreference(group, key);
    }

    public static boolean setCameraPictureSize(
            String candidate, List<Size> supported, Parameters parameters) {
        int index = candidate.indexOf('x');
        if (index == NOT_FOUND) return false;
        int width = Integer.parseInt(candidate.substring(0, index));
        int height = Integer.parseInt(candidate.substring(index + 1));
        for (Size size: supported) {
            if (size.width == width && size.height == height) {
                parameters.setPictureSize(width, height);
                return true;
            }
        }
        return false;
    }

    public void initPreference(PreferenceGroup group) {
        ListPreference videoQuality = group.findPreference(KEY_VIDEO_QUALITY);
        ListPreference pictureSize = group.findPreference(KEY_PICTURE_SIZE);
        ListPreference whiteBalance =  group.findPreference(KEY_WHITE_BALANCE);
        ListPreference colorEffect = group.findPreference(KEY_COLOR_EFFECT);
        ListPreference sceneMode = group.findPreference(KEY_SCENE_MODE);
        ListPreference flashMode = group.findPreference(KEY_FLASH_MODE);
        ListPreference focusMode = group.findPreference(KEY_FOCUS_MODE);
        ListPreference exposure = group.findPreference(KEY_EXPOSURE);
        IconListPreference cameraId =
                (IconListPreference)group.findPreference(KEY_CAMERA_ID);
        ListPreference videoFlashMode =
                group.findPreference(KEY_VIDEOCAMERA_FLASH_MODE);

        // Since the screen could be loaded from different resources, we need
        // to check if the preference is available here
        if (videoQuality != null) {
            // Modify video duration settings.
            // The first entry is for MMS video duration, and we need to fill
            // in the device-dependent value (in seconds).
            CharSequence[] entries = videoQuality.getEntries();
            CharSequence[] values = videoQuality.getEntryValues();
            for (int i = 0; i < entries.length; ++i) {
                if (VIDEO_QUALITY_MMS.equals(values[i])) {
                    entries[i] = entries[i].toString().replace(
                            "30", Integer.toString(MMS_VIDEO_DURATION));
                    break;
                }
            }
        }

        // Filter out unsupported settings / options
        if (pictureSize != null) {
            group.filterUnsupportedOptions(this, pictureSize, sizeListToStringList(
                    mParameters.getSupportedPictureSizes()));
        }
        if (whiteBalance != null) {
            group.filterUnsupportedOptions(this,
                    whiteBalance, mParameters.getSupportedWhiteBalance());
        }
        if (colorEffect != null) {
            group.filterUnsupportedOptions(this,
                    colorEffect, mParameters.getSupportedColorEffects());
        }
        if (sceneMode != null) {
            group.filterUnsupportedOptions(this,
                    sceneMode, mParameters.getSupportedSceneModes());
        }
        if (flashMode != null) {
            group.filterUnsupportedOptions(this,
                    flashMode, mParameters.getSupportedFlashModes());
        }
        if (focusMode != null) {
            group.filterUnsupportedOptions(this,
                    focusMode, mParameters.getSupportedFocusModes());
        }
        if (videoFlashMode != null) {
            group.filterUnsupportedOptions(this,
                    videoFlashMode, mParameters.getSupportedFlashModes());
        }
        if (exposure != null) group.buildExposureCompensation(this, exposure);
        if (cameraId != null) group.buildCameraId(this, cameraId);
    }

    static boolean removePreference(PreferenceGroup group, String key) {
        for (int i = 0, n = group.size(); i < n; i++) {
            CameraPreference child = group.get(i);
            if (child instanceof PreferenceGroup) {
                if (removePreference((PreferenceGroup) child, key)) {
                    return true;
                }
            }
            if (child instanceof ListPreference &&
                    ((ListPreference) child).getKey().equals(key)) {
                group.removePreference(i);
                return true;
            }
        }
        return false;
    }

    private static List<String> sizeListToStringList(List<Size> sizes) {
        ArrayList<String> list = new ArrayList<String>();
        for (Size size : sizes) {
            list.add(String.format("%dx%d", size.width, size.height));
        }
        return list;
    }

    public static void upgradeLocalPreferences(SharedPreferences pref) {
        int version;
        try {
            version = pref.getInt(KEY_LOCAL_VERSION, 0);
        } catch (Exception ex) {
            version = 0;
        }
        if (version == CURRENT_LOCAL_VERSION) return;
        SharedPreferences.Editor editor = pref.edit();
        editor.putInt(KEY_LOCAL_VERSION, CURRENT_LOCAL_VERSION);
        editor.commit();
    }

    public static void upgradeGlobalPreferences(SharedPreferences pref) {
        int version;
        try {
            version = pref.getInt(KEY_VERSION, 0);
        } catch (Exception ex) {
            version = 0;
        }
        if (version == CURRENT_VERSION) return;

        SharedPreferences.Editor editor = pref.edit();
        if (version == 0) {
            // We won't use the preference which change in version 1.
            // So, just upgrade to version 1 directly
            version = 1;
        }
        if (version == 1) {
            // Change jpeg quality {65,75,85} to {normal,fine,superfine}
            String quality = pref.getString(KEY_JPEG_QUALITY, "85");
            if (quality.equals("65")) {
                quality = "normal";
            } else if (quality.equals("75")) {
                quality = "fine";
            } else {
                quality = "superfine";
            }
            editor.putString(KEY_JPEG_QUALITY, quality);
            version = 2;
        }
        if (version == 2) {
            version = 3;
        }
        if (version == 3) {
            // Just use video quality to replace it and
            // ignore the current settings.
            editor.remove("pref_camera_videoquality_key");
            editor.remove("pref_camera_video_duration_key");
        }
        editor.putInt(KEY_VERSION, CURRENT_VERSION);
        editor.commit();
    }

    public static void upgradeAllPreferences(ComboPreferences pref) {
        upgradeGlobalPreferences(pref.getGlobal());
        upgradeLocalPreferences(pref.getLocal());
    }

    public static boolean getVideoQuality(String quality) {
        return VIDEO_QUALITY_YOUTUBE.equals(
                quality) || VIDEO_QUALITY_HIGH.equals(quality);
    }

    public static int getVidoeDurationInMillis(String quality) {
        if (VIDEO_QUALITY_MMS.equals(quality)) {
            return MMS_VIDEO_DURATION * 1000;
        } else if (VIDEO_QUALITY_YOUTUBE.equals(quality)) {
            return YOUTUBE_VIDEO_DURATION * 1000;
        }
        return DEFAULT_VIDEO_DURATION * 1000;
    }

    public static int readPreferredCameraId(SharedPreferences pref) {
        return Integer.parseInt(pref.getString(KEY_CAMERA_ID, "0"));
    }

    public static void writePreferredCameraId(SharedPreferences pref,
            int cameraId) {
        Editor editor = pref.edit();
        editor.putString(KEY_CAMERA_ID, Integer.toString(cameraId));
        editor.commit();
    }
}