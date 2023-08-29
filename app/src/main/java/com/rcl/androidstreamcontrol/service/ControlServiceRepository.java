package com.rcl.androidstreamcontrol.service;

import static com.rcl.androidstreamcontrol.service.ControlService.numBytesPerVal;
//import static com.rcl.androidstreamcontrol.service.twoBytesToInt;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import com.rcl.androidstreamcontrol.utils.Utils;

import java.util.Arrays;

public class ControlServiceRepository {
    private static final String TAG = "ControlServiceRepository";
    public static final int GESTURE_WILL_CONTINUE = 0xFF;
    public static final int GESTURE_COMPLETE = 0x00;
    public static final String IS_CONTINUED_TAG = "isContinuedFromPrev";
    public static final String WILL_CONTINUE_TAG = "willContinue";
    public static final String EVENT_BYTES_TAG = "event";
    Context context;
    boolean willContinue;
    boolean isContinuedFromPrev;

    public ControlServiceRepository(Context context) {
        this.context = context;
        willContinue = false;
        isContinuedFromPrev = false;
    }

    public void renderControlEvent (byte[] bytes) {
        int intWillContinue = bytes[bytes.length - 1];
        willContinue = intWillContinue != GESTURE_COMPLETE ? true : false;

        Intent intent = new Intent(context, ControlService.class);
        intent.putExtra(EVENT_BYTES_TAG, bytes);
        intent.putExtra(IS_CONTINUED_TAG, isContinuedFromPrev);
        intent.putExtra(WILL_CONTINUE_TAG, willContinue);
        context.startService(intent);

        isContinuedFromPrev = willContinue;
    }
}
