package com.rcl.androidstreamcontrol.service;

import static com.rcl.androidstreamcontrol.utils.MyConstants.APP_SCREEN_PIXELS_WIDTH;
import static com.rcl.androidstreamcontrol.utils.MyConstants.FULL_SCREEN_PIXELS_HEIGHT;
import static com.rcl.androidstreamcontrol.utils.Utils.twoBytesToInt;

import android.content.Context;
import android.content.Intent;
import timber.log.Timber;

public class ControlServiceRepository {
    private static final String TAG = "ControlServiceRepository";
    public static final int GESTURE_WILL_CONTINUE = 1;
    public static final int IS_PINCH = 2;
    public static final String IS_CONTINUED_TAG = "isContinuedFromPrev";
    public static final String WILL_CONTINUE_TAG = "willContinue";
    public static final String EVENT_BYTES_TAG = "event";
    public static final String IS_PINCH_TAG = "isPinch";
    Context context;
    boolean willContinue;
    boolean isContinuedFromPrev;
    float lastEndX = 0;
    float lastEndY = 0;
    boolean serviceStarted = false;
    ControlService gestureService;
    public static final float rng = 65535.f;

    public ControlServiceRepository(Context context) {
        this.context = context;
        willContinue = false;
        isContinuedFromPrev = false;
    }

    public void renderControlEvent (byte[] bytes) {
        // Check if we will continue
        willContinue = (bytes[bytes.length - 1] & GESTURE_WILL_CONTINUE) > 0;

        // Check for special gesture types
        boolean isPinch = (bytes[bytes.length - 1] & IS_PINCH) > 0;

        // Get screen coordinates
        float[] eventCoords = bytesToScreenCoords(bytes);

        // Make sure continued gestures start where the last one ended
        if (isContinuedFromPrev) {
            eventCoords[0] = lastEndX;
            eventCoords[1] = lastEndY;
        }

        if (!serviceStarted) {
            Intent intt = new Intent(context, ControlService.class);
            context.startService(intt);
            gestureService = ControlService.getSharedInstance();
            serviceStarted = true;
        }

        Timber.d("Triggering service");
        // Send intent to dispatch gesture in ControlService.java
        Intent intent = new Intent();
        intent.putExtra(EVENT_BYTES_TAG, eventCoords);
        intent.putExtra(IS_PINCH_TAG, isPinch);
        intent.putExtra(IS_CONTINUED_TAG, isContinuedFromPrev);
        intent.putExtra(WILL_CONTINUE_TAG, willContinue);
        //intent.setAction("com.rcl.mirthusandroid.GESTURE_EVENT");
        //context.sendBroadcast(intent);
        gestureService.triggerGesture(intent);

        // Record where this gesture ended
        if (willContinue) {
            lastEndX = eventCoords[2];
            lastEndY = eventCoords[3];
        }

        isContinuedFromPrev = willContinue;
    }

    public static float[] bytesToScreenCoords(byte[] bytes) {
        float[] mappedCoords = new float[5];
        float normVal;
        for(int i = 0; i < 5; i++) {
            normVal = twoBytesToInt(bytes[2 * i], bytes[2 * i + 1]) / rng;

            // x-coords are at even indices (0 and 2), y-coords are at odd indices (1 and 3)
            if (i == 4) mappedCoords[i] = normVal;
            else if(i % 2 == 0)  mappedCoords[i] = APP_SCREEN_PIXELS_WIDTH * normVal;
            else mappedCoords[i] = FULL_SCREEN_PIXELS_HEIGHT * (1 - normVal);
        }
        return mappedCoords;
    }
}
