package com.rcl.androidstreamcontrol.service;

import static com.rcl.androidstreamcontrol.service.ControlServiceRepository.EVENT_BYTES_TAG;
import static com.rcl.androidstreamcontrol.service.ControlServiceRepository.IS_CONTINUED_TAG;
import static com.rcl.androidstreamcontrol.service.ControlServiceRepository.WILL_CONTINUE_TAG;
import static com.rcl.androidstreamcontrol.utils.MyConstants.APP_SCREEN_PIXELS_WIDTH;
import static com.rcl.androidstreamcontrol.utils.MyConstants.FULL_SCREEN_PIXELS_HEIGHT;
import static com.rcl.androidstreamcontrol.utils.Utils.twoBytesToInt;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.Service;
import android.content.Intent;
import android.graphics.Path;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.util.Arrays;

public class ControlService extends AccessibilityService {

    private static final String TAG = "ControlService";
    public static final float setSigFigs = 1000.f;
    public static final int numBytesPerVal = 2;

    @Override
    public void onCreate() {
        Log.d("onCreate",  "service created");

    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
        //Log.d(TAG, "onAccessibilityEvent");
    }

    @Override
    public void onInterrupt() {

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "control service started");

        byte[] eventBytes = intent.getByteArrayExtra(EVENT_BYTES_TAG);

        float[] eventCoords = bytesToScreenCoords(eventBytes);
        float x1 = eventCoords[0];
        float y1 = eventCoords[1];
        float x2 = eventCoords[2];
        float y2 = eventCoords[3];

        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        GestureDescription.StrokeDescription gesture = new GestureDescription.StrokeDescription(
                path, 0, 1);
        gestureBuilder.addStroke(gesture);
        dispatchGesture(gestureBuilder);

        return Service.START_NOT_STICKY;
    }

    private void dispatchGesture(GestureDescription.Builder builder) {
        boolean result = dispatchGesture(builder.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
                Log.d("dispatchGesture onCompleted", String.valueOf(gestureDescription));
            }
            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                super.onCancelled(gestureDescription);
                Log.d("dispatchGesture onCancelled", String.valueOf(gestureDescription));
            }
        }, null);
        Log.d(TAG, "Gesture dispatched, result=" + result);
    }

    public static float[] bytesToScreenCoords(byte[] bytes) {

        float[] mappedCoords = new float[4];
        float normVal;
        for(int i = 0; i < 4; i++) {
            normVal = twoBytesToInt(Arrays.copyOfRange(bytes, numBytesPerVal * i, numBytesPerVal * (i+1))) / setSigFigs;

            // x-coords are at even indices (0 and 2), y-coords are at odd indices (1 and 3)
            if(i % 2 == 0)  { mappedCoords[i] = APP_SCREEN_PIXELS_WIDTH * normVal; }
            else            { mappedCoords[i] = FULL_SCREEN_PIXELS_HEIGHT * (1 - normVal); }
        }
        return mappedCoords;
    }
}

