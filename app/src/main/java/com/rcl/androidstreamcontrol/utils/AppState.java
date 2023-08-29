package com.rcl.androidstreamcontrol.utils;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class AppState extends ViewModel {
    public static final int AWAIT_LAUNCH_PERMISSIONS = 0;
    public static final int LAUNCH_PERMISSIONS = 1;
    public static final int AWAIT_SERVICE_START = 2;
    public static final int SERVICE_BOUND_AWAIT_PEER = 3;
    public static final int SERVICE_READY = 4;
    public static final int SERVICE_RUNNING = 5;

    private final MutableLiveData<Integer> appState = new MutableLiveData<Integer>();
    private static Integer currentAppState;
    public AppState() {}

    public LiveData<Integer> getAppState() { return appState; }
    public Integer getCurrentAppState() { return currentAppState; }
    private void setAppState(Integer newState) {
        currentAppState = newState;
        appState.setValue(newState);
    }

    public void onPermissionsGranted() {
        setAppState(AWAIT_SERVICE_START);
    }

    public void onPermissionsNotGranted() {
        setAppState(AWAIT_LAUNCH_PERMISSIONS);
    }

    public void onMainButtonClick() {
        switch (currentAppState) {
            case AWAIT_LAUNCH_PERMISSIONS:
                setAppState(LAUNCH_PERMISSIONS);
                break;
            case LAUNCH_PERMISSIONS:
                break;
            case AWAIT_SERVICE_START:
                setAppState(SERVICE_BOUND_AWAIT_PEER);
                break;
            case SERVICE_BOUND_AWAIT_PEER:
            case SERVICE_READY:
            case SERVICE_RUNNING:
                setAppState(LAUNCH_PERMISSIONS);
                break;
        }
    }

    public void onBubbleButtonClick() {
        switch (currentAppState) {
            case AWAIT_LAUNCH_PERMISSIONS:
            case LAUNCH_PERMISSIONS:
            case AWAIT_SERVICE_START:
            case SERVICE_BOUND_AWAIT_PEER:
                break;
            case SERVICE_READY:
                setAppState(SERVICE_RUNNING);
                break;
            case SERVICE_RUNNING:
                setAppState(SERVICE_READY);
                break;
        }
    }

    public void onPeerDisconnect() {
        switch (currentAppState) {
            case AWAIT_LAUNCH_PERMISSIONS:
            case LAUNCH_PERMISSIONS:
            case AWAIT_SERVICE_START:
            case SERVICE_BOUND_AWAIT_PEER:
                break;
            case SERVICE_READY:
            case SERVICE_RUNNING:
                setAppState(SERVICE_BOUND_AWAIT_PEER);
                break;
        }
    }

    public void onPeerConnect() {
        switch (currentAppState) {
            case AWAIT_LAUNCH_PERMISSIONS:
            case LAUNCH_PERMISSIONS:
            case AWAIT_SERVICE_START:
                break;
            case SERVICE_BOUND_AWAIT_PEER:
                setAppState(SERVICE_READY);
                break;
            case SERVICE_READY:
            case SERVICE_RUNNING:
        }
    }

}


