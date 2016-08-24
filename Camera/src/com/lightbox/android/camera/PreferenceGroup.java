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

import com.lightbox.android.camera.CameraPreference;
import com.lightbox.android.camera.ListPreference;
import com.lightbox.android.camera.PreferenceGroup;
import com.lightbox.android.camera.R.drawable;
import com.lightbox.android.camera.R.string;
import com.lightbox.android.camera.ui.HeadUpDisplay;

import android.content.Context;
import android.util.AttributeSet;
import android.util.FloatMath;

/**
 * A collection of <code>CameraPreference</code>s. It may contain other
 * <code>PreferenceGroup</code> and form a tree structure.
 */
public class PreferenceGroup extends CameraPreference {
    private ArrayList<CameraPreference> list =
            new ArrayList<CameraPreference>();

    public PreferenceGroup(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void addChild(CameraPreference child) {
        list.add(child);
    }

    public void removePreference(int index) {
        list.remove(index);
    }

    public CameraPreference get(int index) {
        return list.get(index);
    }

    public int size() {
        return list.size();
    }

    @Override
    public void reloadValue() {
        for (CameraPreference pref : list) {
            pref.reloadValue();
        }
    }

    /**
     * Finds the preference with the given key recursively. Returns
     * <code>null</code> if cannot find.
     */
    public ListPreference findPreference(String key) {
        // Find a leaf preference with the given key. Currently, the base
        // type of all "leaf" preference is "ListPreference". If we add some
        // other types later, we need to change the code.
        for (CameraPreference pref : list) {
            if (pref instanceof ListPreference) {
                ListPreference listPref = (ListPreference) pref;
                if(listPref.getKey().equals(key)) return listPref;
            } else if(pref instanceof PreferenceGroup) {
                ListPreference listPref =
                        ((PreferenceGroup) pref).findPreference(key);
                if (listPref != null) return listPref;
            }
        }
        return null;
    }

	void buildExposureCompensation(
	        CameraSettings cameraSettings, ListPreference exposure) {
	    int max = ParameterUtils.getMaxExposureCompensation(cameraSettings.mParameters);
	    int min = ParameterUtils.getMinExposureCompensation(cameraSettings.mParameters);
	    if (max == 0 && min == 0) {
	        CameraSettings.removePreference(this, exposure.getKey());
	        return;
	    }
	    float step = ParameterUtils.getExposureCompensationStep(cameraSettings.mParameters);
	
	    // show only integer values for exposure compensation
	    int maxValue = (int) FloatMath.floor(max * step);
	    int minValue = (int) FloatMath.ceil(min * step);
	    CharSequence entries[] = new CharSequence[maxValue - minValue + 1];
	    CharSequence entryValues[] = new CharSequence[maxValue - minValue + 1];
	    for (int i = minValue; i <= maxValue; ++i) {
	        entryValues[maxValue - i] = Integer.toString(Math.round(i / step));
	        StringBuilder builder = new StringBuilder();
	        if (i > 0) builder.append('+');
	        entries[maxValue - i] = builder.append(i).toString();
	    }
	    exposure.setEntries(entries);
	    exposure.setEntryValues(entryValues);
	}

	void buildCameraId(
	        CameraSettings cameraSettings, IconListPreference cameraId) {
	    int numOfCameras = cameraSettings.mCameraHolder.getNumberOfCameras();
	    if (numOfCameras < 2) {
	        CameraSettings.removePreference(this, cameraId.getKey());
	        return;
	    }
	
	    CharSequence entries[] = new CharSequence[numOfCameras];
	    CharSequence entryValues[] = new CharSequence[numOfCameras];
	    int[] iconIds = new int[numOfCameras];
	    int[] largeIconIds = new int[numOfCameras];
	    for (int i = 0; i < numOfCameras; i++) {
	        entryValues[i] = Integer.toString(i);
	        if (cameraSettings.mCameraHolder.isFrontFacing(i)) {
	            entries[i] = cameraSettings.mContext.getString(
	                    string.pref_camera_id_entry_front);
	            iconIds[i] = drawable.ic_menuselect_camera_facing_front;
	            largeIconIds[i] = drawable.ic_viewfinder_camera_facing_front;
	        } else {
	            entries[i] = cameraSettings.mContext.getString(
	                    string.pref_camera_id_entry_back);
	            iconIds[i] = drawable.ic_menuselect_camera_facing_back;
	            largeIconIds[i] = drawable.ic_viewfinder_camera_facing_back;
	        }
	    }
	    cameraId.setEntries(entries);
	    cameraId.setEntryValues(entryValues);
	    cameraId.setIconIds(iconIds);
	    cameraId.setLargeIconIds(largeIconIds);
	}

	void filterUnsupportedOptions(CameraSettings cameraSettings, ListPreference pref, List<String> supported) {
	
	    // Remove the preference if the parameter is not supported or there is
	    // only one options for the settings.
	    if (supported == null || supported.size() <= 1) {
	        CameraSettings.removePreference(this, pref.getKey());
	        return;
	    }
	
	    pref.filterUnsupported(supported);
	    if (pref.getEntries().length <= 1) {
	        CameraSettings.removePreference(this, pref.getKey());
	        return;
	    }
	
	    // Set the value to the first entry if it is invalid.
	    String value = pref.getValue();
	    if (pref.findIndexOfValue(value) == CameraSettings.NOT_FOUND) {
	        pref.setValueIndex(0);
	    }
	}

	public void scheduleDeactiviateIndicatorBar(HeadUpDisplay headUpDisplay) {
	    headUpDisplay.mHandler.removeMessages(HeadUpDisplay.DESELECT_INDICATOR);
	    headUpDisplay.mHandler.sendEmptyMessageDelayed(
	            HeadUpDisplay.DESELECT_INDICATOR, HeadUpDisplay.POPUP_WINDOW_TIMEOUT);
	    headUpDisplay.mHandler.removeMessages(HeadUpDisplay.DEACTIVATE_INDICATOR_BAR);
	    headUpDisplay.mHandler.sendEmptyMessageDelayed(
	            HeadUpDisplay.DEACTIVATE_INDICATOR_BAR, HeadUpDisplay.INDICATOR_BAR_TIMEOUT);
	}
}
