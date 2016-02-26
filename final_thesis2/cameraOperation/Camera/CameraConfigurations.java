package com.emmanbraulio.final_thesis2.cameraOperation.Camera;

import android.content.Context;
import android.graphics.Point;
import android.hardware.Camera;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by Emman Braulio on 2/16/2016.
 */
public class CameraConfigurations {

    private static final String TAG = "CameraConfiguration";
    // This is bigger than the size of a small screen, which is still supported. The routine
    // below will still select the default (presumably 320x240) size for these. This prevents
    // accidental selection of very low resolution on some devices.
    private static final int MIN_PREVIEW_PIXELS = 470 * 320; // normal screen
    private static final int MAX_PREVIEW_PIXELS = 800 * 600; // more than large/HD screen

    private final Context context;
    private Point cameraResolution;
    private Point screenResolution;
    private Point pictureSize;

    public CameraConfigurations(Context context) {

        this.context = context;
    }

    public void initFromCamera(Camera camera) {
        Camera.Parameters parameters = camera.getParameters();
        WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = manager.getDefaultDisplay();
        int width = display.getWidth();
        int height = display.getHeight();
        // We're landscape-only, and have apparently seen issues with display thinking it's portrait
        // when waking from sleep. If it's not landscape, assume it's mistaken and reverse them:
        if (width < height) {
            Log.i(TAG, "Display reports portrait orientation; assuming this is incorrect");
            int temp = width;
            width = height;
            height = temp;
        }
        screenResolution = new Point(width, height);
        Log.i(TAG, "Screen resolution: " + screenResolution);
        cameraResolution = findBestPreviewSizeValue(parameters, screenResolution);
        Log.i(TAG, "Camera resolution: " + cameraResolution);
    }

    private Point findBestPreviewSizeValue(Camera.Parameters parameters, Point screenResolution) {
        // Sort by size, descending
        List<Camera.Size> supportedPreviewSizes = new ArrayList<Camera.Size>(parameters.getSupportedPreviewSizes());
        Collections.sort(supportedPreviewSizes, new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size a, Camera.Size b) {
                int aPixels = a.height * a.width;
                int bPixels = b.height * b.width;
                if (bPixels < aPixels) {
                    return -1;
                }
                if (bPixels > aPixels) {
                    return 1;
                }
                return 0;
            }
        });

        if (Log.isLoggable(TAG, Log.INFO)) {
            StringBuilder previewSizesString = new StringBuilder();
            for (Camera.Size supportedPreviewSize : supportedPreviewSizes) {
                previewSizesString.append(supportedPreviewSize.width).append('x')
                        .append(supportedPreviewSize.height).append(' ');
            }
            Log.i(TAG, "Supported preview sizes: " + previewSizesString);
        }

        Point bestSize = null;
        float screenAspectRatio = (float) screenResolution.x / (float) screenResolution.y;

        float diff = Float.POSITIVE_INFINITY;
        for (Camera.Size supportedPreviewSize : supportedPreviewSizes) {
            int realWidth = supportedPreviewSize.width;
            int realHeight = supportedPreviewSize.height;
            int pixels = realWidth * realHeight;
            if (pixels < MIN_PREVIEW_PIXELS || pixels > MAX_PREVIEW_PIXELS) {
                continue;
            }
            boolean isCandidatePortrait = realWidth < realHeight;
            int maybeFlippedWidth = isCandidatePortrait ? realHeight : realWidth;
            int maybeFlippedHeight = isCandidatePortrait ? realWidth : realHeight;
            if (maybeFlippedWidth == screenResolution.x && maybeFlippedHeight == screenResolution.y) {
                Point exactPoint = new Point(realWidth, realHeight);
                Log.i(TAG, "Found preview size exactly matching screen size: " + exactPoint);
                return exactPoint;
            }
            float aspectRatio = (float) maybeFlippedWidth / (float) maybeFlippedHeight;
            float newDiff = Math.abs(aspectRatio - screenAspectRatio);
            if (newDiff < diff) {
                bestSize = new Point(realWidth, realHeight);
                diff = newDiff;
            }
        }

        if (bestSize == null) {
            Camera.Size defaultSize = parameters.getPreviewSize();
            bestSize = new Point(defaultSize.width, defaultSize.height);
            Log.i(TAG, "No suitable preview sizes, using default: " + bestSize);
        }

        Log.i(TAG, "Found best approximate preview size: " + bestSize);
        return bestSize;
    }

    public void setDesiredCameraParameters(Camera camera) {
        Camera.Parameters parameters = camera.getParameters();

        if (parameters == null) {
            Log.w(TAG, "Device error: no camera parameters are available. Proceeding without configuration.");
            return;
        }


        String focusMode = null;

        focusMode = findSettableValue(parameters.getSupportedFocusModes(),
                        Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);

        /*focusMode = findSettableValue(parameters.getSupportedFocusModes(),
                        "continuous-video", // Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO in 4.0+
                        "continuous-picture", // Camera.Paramters.FOCUS_MODE_CONTINUOUS_PICTURE in 4.0+
                        Camera.Parameters.FOCUS_MODE_AUTO);*/

        // Maybe selected auto-focus but not available, so fall through here:
        if (focusMode == null) {
            focusMode = findSettableValue(parameters.getSupportedFocusModes(),
                    Camera.Parameters.FOCUS_MODE_MACRO,
                    "edof"); // Camera.Parameters.FOCUS_MODE_EDOF in 2.2+
        }
        if (focusMode != null) {
            parameters.setFocusMode(focusMode);
        }

        parameters.setPreviewSize(cameraResolution.x, cameraResolution.y);
        pictureSize = findPictureSize(parameters);
        parameters.setPictureSize(pictureSize.x, pictureSize.y);
        camera.setParameters(parameters);
    }

    private static String findSettableValue(Collection<String> supportedValues,
                                            String... desiredValues) {
        Log.i(TAG, "Supported values: " + supportedValues);
        String result = null;
        if (supportedValues != null) {
            for (String desiredValue : desiredValues) {
                if (supportedValues.contains(desiredValue)) {
                    result = desiredValue;
                    break;
                }
            }
        }
        Log.i(TAG, "Settable value: " + result);
        return result;
    }

    private Point findPictureSize(Camera.Parameters parameters) {
        List<Camera.Size> supportedPictureSizes = new ArrayList<Camera.Size>(parameters.getSupportedPictureSizes());
        Point bestPictureSize = null;
        Camera.Size previewSize = parameters.getPreviewSize();
        Camera.Size retSize = null;

        Camera.Size size = supportedPictureSizes.get(0);
        if (Log.isLoggable(TAG, Log.INFO)) {
            StringBuilder pictureSizesString = new StringBuilder();

            for (int i=0; i < supportedPictureSizes.size(); i++) {
                if(supportedPictureSizes.get(i).width > size.width)
                    size = supportedPictureSizes.get(i);

                pictureSizesString.append(size.width).append('x')
                        .append(size.height).append(' ');
            }
            Log.i(TAG, "Supported picture sizes: " + pictureSizesString);
        }

        size = supportedPictureSizes.get(0);
        for (int i=0; i < supportedPictureSizes.size(); i++) {
            if(supportedPictureSizes.get(i).width > size.width)
                size = supportedPictureSizes.get(i);
        }

        bestPictureSize = new Point(size.width, size.height);
        Log.i(TAG, "Found picture size exactly with preview size: " + bestPictureSize);
        return bestPictureSize;

        /*for (Camera.Size size : supportedPictureSizes) {
           if (size.equals(previewSize)) {
               bestPictureSize = new Point(previewSize.width, previewSize.height);
               Log.i(TAG, "Found picture size exactly with preview size: " + bestPictureSize);
               return bestPictureSize;
           }
        }

       // if the preview size is not supported as a picture size
       float reqRatio = ((float) previewSize.width) / previewSize.height;
       float curRatio, deltaRatio;
       float deltaRatioMin = Float.MAX_VALUE;
       for (Camera.Size size : supportedPictureSizes) {
           curRatio = ((float) size.width) / size.height;
           deltaRatio = Math.abs(reqRatio - curRatio);
           if (deltaRatio < deltaRatioMin) {
               deltaRatioMin = deltaRatio;
               retSize = size;
               bestPictureSize = new Point(retSize.width, retSize.height);
           }
       }

       Log.i(TAG, "Found approximate picture size: " + bestPictureSize);
       return bestPictureSize;*/
    }
}