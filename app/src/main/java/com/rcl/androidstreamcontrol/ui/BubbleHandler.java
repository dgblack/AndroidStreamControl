package com.rcl.androidstreamcontrol.ui;

import static com.rcl.androidstreamcontrol.utils.MyConstants.APP_SCREEN_PIXELS_HEIGHT;
import static com.rcl.androidstreamcontrol.utils.MyConstants.BUBBLE_ICON_RADIUS;
import static com.rcl.androidstreamcontrol.utils.MyConstants.APP_SCREEN_PIXELS_WIDTH;
import static com.rcl.androidstreamcontrol.utils.MyConstants.TRASH_ICON_SIDE_LEN;

import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import androidx.core.view.GestureDetectorCompat;

import com.rcl.androidstreamcontrol.service.FollowerService;

public class BubbleHandler {

    private GestureDetectorCompat mGestureDetector;
    private FollowerService service;
    private boolean isDragAndDropEnabled;
    private boolean isDragEntered;

    public BubbleHandler(FollowerService followerService) {
        this.service = followerService;
        mGestureDetector = new GestureDetectorCompat(service, new GestureHandler());

        service.serviceBubbleBinding.bubble1.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_UP:
                        if (mGestureDetector.onTouchEvent(motionEvent)) {
                            return true;
                        }

                        if (isDragEntered) {
                            service.notifyEndService();
                        }
                        service.disableBubbleDragAndDrop();
                        isDragAndDropEnabled = false;
                        isDragEntered = false;
                        return true;

                    default:
                        if (mGestureDetector.onTouchEvent(motionEvent)) {
                            return true;
                        }
                        return false;
                }
            }
        });
    }

    class GestureHandler extends GestureDetector.SimpleOnGestureListener {
        private static final String TAG = "BubbleHandler";
        private int trashPosX;
        private int trashPosY;
        private float offsetX;
        private float offsetY;

        WindowManager.LayoutParams bubbleParams;

        public GestureHandler() {
            WindowManager.LayoutParams trashParams = (WindowManager.LayoutParams)
                    service.trashBarBinding.getRoot().getLayoutParams();
            trashPosX = (APP_SCREEN_PIXELS_WIDTH / 2) - TRASH_ICON_SIDE_LEN / 2;
            trashPosY = APP_SCREEN_PIXELS_HEIGHT - 3 * TRASH_ICON_SIDE_LEN / 2;

            Log.d("TrashPos", trashPosX + " " + trashPosY);
        }

        public void resetBubbleOffset(MotionEvent event) {
            offsetX = bubbleParams.x - event.getRawX();
            offsetY = bubbleParams.y - event.getRawY();
        }

        @Override
        public boolean onDown(MotionEvent event) {
            Log.d(TAG,"onDown: " + event.toString());
            bubbleParams = (WindowManager.LayoutParams) service.serviceBubbleBinding.getRoot().getLayoutParams();
            resetBubbleOffset(event);

            isDragAndDropEnabled = false;
            isDragEntered = false;

            return true;
        }
        @Override
        public boolean onSingleTapConfirmed(MotionEvent event) {
            Log.d(TAG, "onSingleTapConfirmed: " + event.toString());
            service.onBubbleClick();
            return true;
        }
        @Override
        public boolean onDoubleTap(MotionEvent event) {
            Log.d(TAG, "onDoubleTap: " + event.toString());
            service.reopenApp();
            return true;
        }
        @Override
        public boolean onScroll(MotionEvent event1, MotionEvent event2, float distanceX,
                                float distanceY) {
            Log.d(TAG, "onScroll: " + event2.getAction());
            if (!isDragAndDropEnabled) {
                service.enableBubbleDragAndDrop();
                isDragAndDropEnabled = true;
            }

            bubbleParams.x = (int) (offsetX + event2.getRawX());
            bubbleParams.y = (int) (offsetY + event2.getRawY());
            service.getWindowManager().updateViewLayout(service.serviceBubbleBinding.getRoot(), bubbleParams);

            if (bubbleParams.y >= APP_SCREEN_PIXELS_HEIGHT) {
                resetBubbleOffset(event2);
            }

            boolean isOverlapDetected = checkAABBOverlap(bubbleParams.x, bubbleParams.y);
            if (isOverlapDetected && isDragEntered) {
                // overlap and we are already in entered state: DO NOTHING
            } else if (isOverlapDetected && !isDragEntered) {
                // new overlap detected: from !ENTERED STATE to ENTERED STATE
                isDragEntered = true;
                service.dragTrashEnterDetected();
            } else if (!isOverlapDetected && isDragEntered) {
                // no overlap detected: from ENTERED STATE to !ENTERED STATE
                isDragEntered = false;
                service.dragTrashExitDetected();
            } else {
                // no overlap and we are already in !entered state: DO NOTHING
            }

            return super.onScroll(event1, event2, distanceX, distanceY);
        }
        private boolean checkAABBOverlap(int x, int y) {

            int centeredX = x + BUBBLE_ICON_RADIUS;
            int centeredY = y + BUBBLE_ICON_RADIUS;

            if (centeredX + BUBBLE_ICON_RADIUS > trashPosX
                    && centeredX - BUBBLE_ICON_RADIUS < trashPosX + TRASH_ICON_SIDE_LEN
                    && centeredY + BUBBLE_ICON_RADIUS > trashPosY
                    && centeredY - BUBBLE_ICON_RADIUS < trashPosY + TRASH_ICON_SIDE_LEN) {
                return true;
            } else if (y < APP_SCREEN_PIXELS_HEIGHT
                    && centeredX + BUBBLE_ICON_RADIUS > trashPosX
                    && centeredX - BUBBLE_ICON_RADIUS < trashPosX + TRASH_ICON_SIDE_LEN
                    && centeredY + BUBBLE_ICON_RADIUS > trashPosY) {
                return true;
            } else {
                return false;
            }
        }
        @Override
        public boolean onFling(MotionEvent event1, MotionEvent event2,
                               float velocityX, float velocityY) {
            Log.d(TAG, "onFling: " + velocityX + " " + velocityY);
            //mScroller.fling((int) event1.getRawX(),(int) event1.getRawY(), (int) velocityX,(int) velocityY, 0, 0, SCREEN_PIXELS_WIDTH, SCREEN_PIXELS_HEIGHT);
            return false;
        }
    }
}

