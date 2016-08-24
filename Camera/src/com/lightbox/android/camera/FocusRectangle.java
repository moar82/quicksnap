package com.lightbox.android.camera;

import com.lightbox.android.camera.R;
import com.lightbox.android.camera.activities.Camera;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

public class FocusRectangle extends View {

    @SuppressWarnings("unused")
    private static final String TAG = "FocusRectangle";

    public FocusRectangle(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void showStart() {
        setBackgroundDrawable(getResources().getDrawable(R.drawable.focus_focusing));
    }

    public void showSuccess() {
        setBackgroundDrawable(getResources().getDrawable(R.drawable.focus_focused));
    }

    public void showFail() {
        setBackgroundDrawable(getResources().getDrawable(R.drawable.focus_focus_failed));
    }

    public void clear() {
        setBackgroundDrawable(null);
    }

	public void installIntentFilter(Camera camera) {
	    // install an intent filter to receive SD card related events.
	    IntentFilter intentFilter =
	            new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);
	    intentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
	    intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
	    intentFilter.addAction(Intent.ACTION_MEDIA_CHECKING);
	    intentFilter.addDataScheme("file");
	    camera.registerReceiver(camera.mReceiver, intentFilter);
	    camera.mDidRegister = true;
	}

	public boolean isSmoothZoomSupported(Camera camera) {
		if (VERSION.SDK_INT <= 0x00000007) {
			return false;
		}
		
	    String str = camera.mParameters.get(Camera.KEY_SMOOTH_ZOOM_SUPPORTED);
	    return (str != null && Camera.TRUE.equals(str));
	}

	public void setupCaptureParams(Camera camera) {
	    Bundle myExtras = camera.getIntent().getExtras();
	    if (myExtras != null) {
	        camera.mSaveUri = (Uri) myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
	        camera.mCropValue = myExtras.getString("crop");
	    }
	}

	public Bitmap loadPreviewBitmap(Camera camera, byte[] jpegData, int degree) {
		//TODO use preview, so rotation is always correct?
		Options opts = new Options();
		opts.inJustDecodeBounds = true;
		int sampleSize = 1;
		opts.inSampleSize = sampleSize;
		BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length, opts);
		int width = opts.outWidth;
		int height = opts.outHeight;
		
		int surfaceWidth = camera.mSurfaceView.getWidth();
		int surfaceHeight = camera.mSurfaceView.getHeight();
		
		while (width/sampleSize > surfaceWidth && height/sampleSize > surfaceHeight) {
			sampleSize++;
		}
		
		opts.inSampleSize = sampleSize;
		opts.inJustDecodeBounds = false;
		
		return Util.rotate(BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length, opts), degree);
	}

	public void detachHeadUpDisplay(Camera camera) {
	    camera.mHeadUpDisplay.collapse();
	    ((ViewGroup) camera.mGLRootView.getParent()).removeView(camera.mGLRootView);
	    camera.mGLRootView = null;
	}
}
