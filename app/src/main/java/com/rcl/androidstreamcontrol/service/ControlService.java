package com.rcl.androidstreamcontrol.service;

import static com.rcl.androidstreamcontrol.service.ControlServiceRepository.EVENT_BYTES_TAG;
import static com.rcl.androidstreamcontrol.service.ControlServiceRepository.IS_CONTINUED_TAG;
import static com.rcl.androidstreamcontrol.service.ControlServiceRepository.IS_PINCH_TAG;
import static com.rcl.androidstreamcontrol.service.ControlServiceRepository.WILL_CONTINUE_TAG;
import static com.rcl.androidstreamcontrol.utils.MyConstants.APP_SCREEN_PIXELS_WIDTH;
import static com.rcl.androidstreamcontrol.utils.MyConstants.FULL_SCREEN_PIXELS_HEIGHT;
import static com.rcl.androidstreamcontrol.utils.Utils.twoBytesToInt;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Path;
import timber.log.Timber;
import android.view.accessibility.AccessibilityEvent;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;

import java.util.ArrayDeque;

public class ControlService extends AccessibilityService {

    private static final String TAG = "ControlService";
    private GestureDescription.StrokeDescription gesture;
    private float zoomScale = 3;
    private BroadcastReceiver bro;
    private static ControlService sharedInstance;
    private ArrayDeque<GestureDescription.Builder> gestureQ;
    private long startTime = 0;

    public static ControlService getSharedInstance() {
        return sharedInstance;
    }

    public void triggerGesture(Intent intent) {
        handleGestureRequest(intent);
    }

    @Override
    public void onCreate() {
        Timber.tag("onCreate").d("Accessibility service created");
        gestureQ = new ArrayDeque<>();
        bro = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals("com.rcl.androidstreamcontrol.GESTURE_EVENT")) {
                    Timber.d("Received broadcast");
                    handleGestureRequest(intent);
                }
            }
        };

        sharedInstance = this;

        IntentFilter filter = new IntentFilter();
        filter.addAction("com.rcl.androidstreamcontrol.GESTURE_EVENT");
        registerReceiver(bro, filter);
    }

    private void handleGestureRequest(Intent intent) {
        boolean willContinue = intent.getBooleanExtra(WILL_CONTINUE_TAG,false);
        GestureDescription.Builder b;
        if (intent.getBooleanExtra(IS_PINCH_TAG, false))
            tryDispatchGesture(createPinchGesture(intent));
        else if (willContinue)
            createGeneralGesture(intent); // Create the gesture but don't dispatch it until it's done
        else
            tryDispatchGesture(createGeneralGesture(intent));
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
        //Timber.tag(TAG).d( "onAccessibilityEvent");
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Timber.tag(TAG).d( "control service started");

        return Service.START_NOT_STICKY;
    }

    private GestureDescription.Builder createPinchGesture(Intent intent) {
        float[] eventCoords = intent.getFloatArrayExtra(EVENT_BYTES_TAG);

        float x = eventCoords[0];
        float y = eventCoords[1];
        float sgn = eventCoords[2];
        float len = (sgn > 0) ? eventCoords[4] * zoomScale : 30; // len=80 works well but not for lumify
        long d1 = (sgn > 0) ? 400 : 600;
        long d2 = 200;

        float[] ep = findEndpts(x,y,len);
        float xp = ep[0], yp = ep[1], xq = ep[2], yq = ep[3];
        Timber.d("From (%f,%f) to (%f,%f) and (%f,%f)",x,y,xp,yp,xq,yq);

        // Create two paths, each from the centre to one of the outer positions
        Path p = new Path();
        Path p2 = new Path();
        Path q = new Path();
        Path q2 = new Path();
        if (sgn > 0) {// Zoom in
            Timber.d("Zoom in");
            p.moveTo(x, y);
            p.lineTo(xp, yp);
            q.moveTo(x, y);
            q.lineTo(xq, yq);

            // Extra small motion at the end to avoid flinging the screen
            float mag = (float)Math.sqrt(Math.pow(xp-x,2) + Math.pow(yp-y,2));
            p2.moveTo(xp,yp);
            p2.lineTo((xp-x)/mag + xp,(yp-y)/mag + yp);
            mag = (float)Math.sqrt(Math.pow(xq-x,2) + Math.pow(yq-y,2));
            q2.moveTo(xq,yq);
            q2.lineTo((xq-x)/mag + xq,(yq-y)/mag + yq);

        } else { // Zoom out
            Timber.d("Zoom out");
            p.moveTo(xp,yp);
            p.lineTo(x+1,y+1);
            q.moveTo(xq,yq);
            q.lineTo(x,y);

            // Extra small motion at the end to avoid flinging the screen
            float mag = (float)Math.sqrt(Math.pow(xp-x,2) + Math.pow(yp-y,2));
            p2.moveTo(x,y);
            p2.lineTo((x-xp)/mag + x + 1,(y-yp)/mag + y + 1);
            mag = (float)Math.sqrt(Math.pow(xq-x,2) + Math.pow(yq-y,2));
            q2.moveTo(x,y);
            q2.lineTo((x-xq)/mag + x,(y-yq)/mag + y);
        }

        // Add a small pause at the end of the gesture so it isn't too violent
        GestureDescription.Builder gb = new GestureDescription.Builder();
        GestureDescription.StrokeDescription stroke1 = new GestureDescription.StrokeDescription(p,0,400,true);
        stroke1.continueStroke(p2,400, 200, false);
        GestureDescription.StrokeDescription stroke2 = new GestureDescription.StrokeDescription(q,0,400,true);
        stroke2.continueStroke(q2,400, 200, false);

        // Add both strokes to a single gesture
        gb.addStroke(stroke1);
        gb.addStroke(stroke2);

        return gb;
    }

    private float[] findEndpts(float x, float y, float len) {
        // Max values
        float xm = APP_SCREEN_PIXELS_WIDTH;
        float ym = FULL_SCREEN_PIXELS_HEIGHT;

        // Normalized coords
        float xr = x / xm, yr = y / ym;

        // Save values
        float bestDist = 0;
        float[] ep = new float[4];

        // Top left:
        float x1 = 0, y1 = 0;
        float x2, y2;
        if (xr > yr) {
            y2 = yRightSide(x1, y1, x, y, xm);
            x2 = xm;
        } else {
            x2 = xBottomSide(x1, y1, x, y, ym);
            y2 = ym;
        }
        float minDist = minLength(x, y, x1, y1, x2, y2);
        if (minDist > bestDist) {
            bestDist = minDist;
            ep = new float[]{x1, y1, x2, y2};
        }

        // Top right:
        x1 = xm; y1 = 0;
        if (yr > 1 - xr) {
            x2 = xBottomSide(x1, y1, x, y, ym);
            y2 = ym;
        } else {
            x2 = 0;
            y2 = yLeftSide(x1, y1, x, y);
        }
        minDist = minLength(x,y,x1,y1,x2,y2);
        if (minDist > bestDist) {
            bestDist = minDist;
            ep = new float[]{x1, y1, x2, y2};
        }

        // Bottom left:
        x1 = 0; y1 = ym;
        if (yr > 1 - xr) {
            x2 = xm;
            y2 = yRightSide(x1, y1, x, y, xm);
        } else {
            x2 = xTopSide(x1, y1, x, y);
            y2 = 0;
        }
        minDist = minLength(x,y,x1,y1,x2,y2);
        if (minDist > bestDist) {
            bestDist = minDist;
            ep = new float[]{x1, y1, x2, y2};
        }

        // Bottom right:
        x1 = xm; y1 = ym;
        if (xr > yr) {
            x2 = xTopSide(x1, y1, x, y);
            y2 = 0;
        } else {
            x2 = 0;
            y2 = yLeftSide(x1, y1, x, y);
        }
        minDist = minLength(x,y,x1,y1,x2,y2);
        if (minDist > bestDist) {
            bestDist = minDist;
            ep = new float[]{x1, y1, x2, y2};
        }

        // Scale the result if needed
        if (len < bestDist) {
            for (int i = 0; i < 2; i++) {
                ep[i*2] = x + (ep[i*2] - x) * len / bestDist;
                ep[i*2+1] = y + (ep[i*2+1] - y) * len / bestDist;
            }
        }

        return ep;
    }

    private float yRightSide(float x0, float y0, float x, float y, float xm) {
        float l = (xm - x) / (x - x0);
        return y + l * (y - y0);
    }

    private float yLeftSide(float x0, float y0, float x, float y) {
        float l = -x / (x - x0);
        return y + l * (y - y0);
    }
    private float xTopSide(float x0, float y0, float x, float y) {
        float l = -y / (y - y0);
        return x + l * (x - x0);
    }

    private float xBottomSide(float x0, float y0, float x, float y, float ym) {
        float l = (ym - y) / (y - y0);
        return x + l * (x - x0);
    }

    private float minLength(float x, float y, float x1, float y1, float x2, float y2) {
        float dist1 = (float)Math.sqrt((x-x1)*(x-x1) + (y-y1)*(y-y1));
        float dist2 = (float)Math.sqrt((x-x2)*(x-x2) + (y-y2)*(y-y2));
        return Math.min(dist1, dist2);
    }

    private GestureDescription.Builder createGeneralGesture(Intent intent) {
        float[] eventCoords = intent.getFloatArrayExtra(EVENT_BYTES_TAG);
        boolean willContinue = intent.getBooleanExtra(WILL_CONTINUE_TAG,false);
        boolean isContinued = intent.getBooleanExtra(IS_CONTINUED_TAG, false);

        float x1 = eventCoords[0];
        float y1 = eventCoords[1];
        float x2 = eventCoords[2];
        float y2 = eventCoords[3];
        long dt = (long)eventCoords[4];
        dt = (dt > 0) ? dt : 1;

        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);

        if (isContinued) {
            gesture.continueStroke(path, startTime, dt, true);
            startTime += dt;
            // If the gesture is ending, add a little pause
            if (!willContinue) {
                Path ep = new Path();
                ep.moveTo(x2,y2);
                ep.lineTo(x2+1,y2+1);
                gesture.continueStroke(ep,startTime,200,false);
                startTime = 0;
            }

        } else {
            gesture = new GestureDescription.StrokeDescription(path, 0, dt, willContinue);
            startTime = 0;
        }

        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.addStroke(gesture);

        return gestureBuilder;
    }

    private void tryDispatchGesture(GestureDescription.Builder builder) {
        if (gestureQ.isEmpty())
            dispatchGesture(builder);
        else
            gestureQ.addFirst(builder);
    }

    private void dispatchGesture(GestureDescription.Builder builder) {
        boolean result = dispatchGesture(builder.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
                Timber.tag("dispatchGesture onCompleted").d(String.valueOf(gestureDescription));
                // When completed, start dispatching the next gesture. Otherwise it'll cancel the current one
                if (!gestureQ.isEmpty()) {
                    GestureDescription.Builder b = gestureQ.pollLast();
                    if (b != null)
                        dispatchGesture(b);
                }
            }
            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                super.onCancelled(gestureDescription);
                Timber.tag("dispatchGesture onCancelled").d(String.valueOf(gestureDescription));
            }
        }, null);
        Timber.tag(TAG).d( "Gesture dispatched, result=%s", result);
    }
}

