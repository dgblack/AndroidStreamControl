package com.rcl.androidstreamcontrol.service;

import static android.view.View.GONE;
import static com.rcl.androidstreamcontrol.MainActivity.mWindow;
import static com.rcl.androidstreamcontrol.model.ServiceStateHolder.SERVICE_NOT_READY;
import static com.rcl.androidstreamcontrol.model.ServiceStateHolder.SERVICE_READY;
import static com.rcl.androidstreamcontrol.model.ServiceStateHolder.SERVICE_RUNNING;
import static com.rcl.androidstreamcontrol.utils.MyConstants.BUBBLE_ICON_RADIUS;
import static com.rcl.androidstreamcontrol.utils.MyConstants.M_PROJ_INTENT;
import static com.rcl.androidstreamcontrol.utils.MyConstants.NOTIF_CHANNEL_ID;
import static com.rcl.androidstreamcontrol.utils.MyConstants.APP_SCREEN_PIXELS_WIDTH;
import static com.rcl.androidstreamcontrol.utils.MyConstants.TRASH_ICON_SIDE_LEN;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.Icon;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import androidx.core.view.GestureDetectorCompat;
import androidx.core.view.WindowCompat;

import androidx.lifecycle.LifecycleService;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.rcl.androidstreamcontrol.R;
import com.rcl.androidstreamcontrol.databinding.BubbleLayoutBinding;
import com.rcl.androidstreamcontrol.databinding.TrashLayoutBinding;
import com.rcl.androidstreamcontrol.model.ServiceStateHolder;
import com.rcl.androidstreamcontrol.ui.BubbleHandler;
import com.rcl.androidstreamcontrol.ui.WindowLayoutBuilder;


public class FollowerService extends LifecycleService implements ServiceRepository.PeerConnectionListener {

    private static final String TAG = "FollowerService";
    private static WindowManager mWindowManager;
    public BubbleLayoutBinding serviceBubbleBinding;
    public WindowManager.LayoutParams mBubbleLayoutParams; 
    public TrashLayoutBinding trashBarBinding;
    public WindowManager.LayoutParams mTrashLayoutParams;
    private final MutableLiveData<String> notifyEndService = new MutableLiveData<String>();
    private ServiceRepository serviceRepo = new ServiceRepository(this);
    private final IBinder mBinder = new FollowerBinder();
    private static ServiceStateHolder serviceState;

    public class FollowerBinder extends Binder {
        public FollowerService getService() {
            return FollowerService.this;
        }
        public LiveData<String> getNotifyEndService() { return notifyEndService; }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        super.onBind(intent);
        Log.d("BubbleService", "onBind");


        WindowCompat.setDecorFitsSystemWindows(mWindow, false);
        createNotificationChannel();

        createTrashBarView();
        createServiceBubble();

        serviceState = new ServiceStateHolder();
        Observer<Integer> myObserver = new Observer<Integer>() {
            @Override
            public void onChanged(Integer data) {
                Log.d("onServiceStateChanged", String.valueOf(data));
                updateBubbleButtonUI(data);
                switch (data) {
                    case SERVICE_NOT_READY:
                        onServiceNotReady();
                        break;
                    case SERVICE_READY:
                        onServiceReady();
                        break;
                    case SERVICE_RUNNING:
                        onServiceRunning();
                        break;
                }
            }
        };

        serviceState.getServiceState().observe(this, myObserver);

        Intent notificationIntent = getPackageManager()
                .getLaunchIntentForPackage(getPackageName())
                .setPackage(null)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
                .setContentTitle("Foreground Service")
                .setContentText(intent.getStringExtra("inputExtra"))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                //.setBubbleMetadata(NotificationCompat.BubbleMetadata.fromPlatform(bubbleData))
                .setContentIntent(pendingIntent)
                .build();

        startForeground(1, notification);

        Intent mProjectionIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mProjectionIntent = (Intent) intent.getParcelableExtra(M_PROJ_INTENT, Intent.class);
        } else {
            mProjectionIntent = (Intent) intent.getParcelableExtra(M_PROJ_INTENT);
        }
        Log.d("onStartCommand: check mProjectionIntent", String.valueOf(mProjectionIntent));
        serviceRepo.peerConnectionListener = this;
        serviceRepo.setMProjectionIntent(mProjectionIntent);
        serviceRepo.start();


        return mBinder;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d("onNewConfiguration", String.valueOf(newConfig));
        serviceRepo.rtcClient.onScreenOrientationChange(String.valueOf(newConfig.orientation));
    }

    private void updateBubbleButtonUI(@NonNull Integer currentServiceState) {

        serviceBubbleBinding.bubble1.clearColorFilter();
        serviceBubbleBinding.getRoot().setVisibility(View.VISIBLE);
        serviceBubbleBinding.getRoot().setEnabled(true);
        Icon imageIcon = Icon.createWithResource(this, R.drawable.do_not_disturb_24);
        switch (currentServiceState) {
            case SERVICE_NOT_READY:
                break;
            case SERVICE_READY:
                imageIcon = Icon.createWithResource(this, R.drawable.play_arrow_24);
                break;
            case SERVICE_RUNNING:
                imageIcon = Icon.createWithResource(this, R.drawable.pause_24);
        }
        serviceBubbleBinding.bubble1.setImageIcon(imageIcon);
    }

    private GestureDetectorCompat mGestureDetector;
    public void createServiceBubble() {
        serviceBubbleBinding = BubbleLayoutBinding.inflate(LayoutInflater.from(this));
        mBubbleLayoutParams = WindowLayoutBuilder.buildBubbleWindowLayoutParams();
        getWindowManager().addView(serviceBubbleBinding.getRoot(), mBubbleLayoutParams);

        BUBBLE_ICON_RADIUS = (int) (APP_SCREEN_PIXELS_WIDTH / 12.0);
        Log.d("IconRadius", String.valueOf(BUBBLE_ICON_RADIUS));
        serviceBubbleBinding.getRoot().getLayoutParams().width = 2 * BUBBLE_ICON_RADIUS;
        serviceBubbleBinding.getRoot().getLayoutParams().height = 2 * BUBBLE_ICON_RADIUS;
        getWindowManager().updateViewLayout(serviceBubbleBinding.getRoot(), mBubbleLayoutParams);

        serviceBubbleBinding.setHandler(new BubbleHandler(this));
    }

    public void enableBubbleDragAndDrop() {
        trashBarBinding.getRoot().setVisibility(View.VISIBLE);
        trashBarBinding.getRoot().setEnabled(true);
    }

    public void dragTrashEnterDetected() {
        trashBarBinding.trashIcon1.clearColorFilter();
        trashBarBinding.trashIcon1.getBackground().clearColorFilter();
        int tintColor = getResources().getColor(R.color.trash_bar_on_entered, getTheme());
        trashBarBinding.trashIcon1.getBackground().setTint(tintColor);
        trashBarBinding.trashIcon1.setColorFilter(tintColor);
    }

    public void dragTrashExitDetected() {
        trashBarBinding.trashIcon1.clearColorFilter();
        trashBarBinding.trashIcon1.getBackground().clearColorFilter();
        int tintColor = getResources().getColor(R.color.white, getTheme());
        trashBarBinding.trashIcon1.getBackground().setTint(tintColor);
        trashBarBinding.trashIcon1.setColorFilter(tintColor);
    }

    public void disableBubbleDragAndDrop() {
        dragTrashExitDetected();
        trashBarBinding.getRoot().setVisibility(View.GONE);
        trashBarBinding.getRoot().setEnabled(false);
    }

    private void createTrashBarView() {
        trashBarBinding = TrashLayoutBinding.inflate(LayoutInflater.from(this));
        mTrashLayoutParams = WindowLayoutBuilder.buildTrashWindowLayoutParams();
        getWindowManager().addView(trashBarBinding.getRoot(), mTrashLayoutParams);

        TRASH_ICON_SIDE_LEN = 2 * (int) (APP_SCREEN_PIXELS_WIDTH / 12.0); // ensures divisible by 2
        trashBarBinding.trashIcon1.getLayoutParams().width = TRASH_ICON_SIDE_LEN;
        trashBarBinding.trashIcon1.getLayoutParams().height = TRASH_ICON_SIDE_LEN;
        trashBarBinding.trashBar.getLayoutParams().height = 2 * TRASH_ICON_SIDE_LEN;
        getWindowManager().updateViewLayout(trashBarBinding.getRoot(), mTrashLayoutParams);

        // BubbleHandler enables trash bar during bubble onScroll
        /*
        trashBarBinding.trashIcon1.setOnDragListener(new View.OnDragListener() {
            @Override
            public boolean onDrag(View view, DragEvent dragEvent) {
                Log.d("DragEvent", String.valueOf(dragEvent.getAction()));
                int tintColor;
                switch (dragEvent.getAction()) {
                    case ACTION_DRAG_ENTERED:
                        trashBarBinding.trashIcon1.clearColorFilter();
                        tintColor = getResources().getColor(R.color.trash_bar_on_entered, getTheme());
                        trashBarBinding.trashIcon1.getForeground().setTint(tintColor);
                        break;
                    case ACTION_DRAG_EXITED:
                        trashBarBinding.trashIcon1.clearColorFilter();
                        tintColor = getResources().getColor(R.color.white, getTheme());
                        trashBarBinding.trashIcon1.getForeground().setTint(tintColor);
                        break;
                    case ACTION_DROP:

                }
                return true;
            }
        });

         */
        trashBarBinding.getRoot().setVisibility(GONE);
        trashBarBinding.getRoot().setEnabled(false);
    }

    private void destroyServiceBubble() {
        getWindowManager().removeView(serviceBubbleBinding.getRoot());
        serviceBubbleBinding = null;
        mBubbleLayoutParams = null;
    }

    private void destroyTrashBar() {
        getWindowManager().removeView(trashBarBinding.getRoot());
        trashBarBinding = null;
        mTrashLayoutParams = null;
    }


    public WindowManager getWindowManager() {
        if (mWindowManager == null) {
            mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        }
        return mWindowManager;
    }

    public void reopenApp() {
        Intent intent = getPackageManager()
                .getLaunchIntentForPackage(getPackageName())
                .setPackage(null)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
    }

    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(
                NOTIF_CHANNEL_ID,
                "Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
        );

        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(serviceChannel);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        destroyServiceBubble();
        destroyTrashBar();
        serviceRepo.onUnbind();
        return super.onUnbind(intent);
    }

    public void onServiceNotReady() {

    }

    public void onServiceReady() {
        if (serviceRepo.rtcClient.localVideoTrack != null) {
            //serviceRepo.rtcClient.localVideoTrack.setEnabled(true);
            serviceRepo.rtcClient.localVideoTrack.setEnabled(false);
        }

        serviceRepo.controlEnabled = false;
    }

    public void onServiceRunning() {
        if (serviceRepo.rtcClient.localVideoTrack != null) {
            //serviceRepo.rtcClient.localVideoTrack.setEnabled(false);
            serviceRepo.rtcClient.localVideoTrack.setEnabled(true);
        }

        serviceRepo.controlEnabled = true;
        Log.d("DataChannelState", String.valueOf(serviceRepo.rtcClient.receiveControlEventsDC.state()));
    }

    public void onBubbleClick() {
        serviceState.onBubbleButtonClick();
    }

    @Override
    public void postPeerConnected() {
        Log.d("postPeerConnected", "attempt to broadcast");
        serviceState.onPeerConnect();
    }

    @Override
    public void postPeerDisconnected() {
        Log.d("postPeerDisconnected", "attempt to broadcast");
        serviceState.onPeerDisconnect();
    }

    public void notifyEndService() {
        notifyEndService.postValue("end");
        reopenApp();
    }

}