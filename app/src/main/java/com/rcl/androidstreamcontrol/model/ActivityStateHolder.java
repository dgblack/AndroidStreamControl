package com.rcl.androidstreamcontrol.model;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class ActivityStateHolder extends ViewModel {
    public static final int AWAIT_LAUNCH_PERMISSIONS = 0;
    public static final int LAUNCH_PERMISSIONS = 1;
    public static final int AWAIT_SERVICE_START = 2;
    public static final int SERVICE_BOUND = 3;

    private final MutableLiveData<Integer> appState = new MutableLiveData<>();
    private static Integer currentAppState;
    public ActivityStateHolder() { setAppState(AWAIT_LAUNCH_PERMISSIONS); }

    public LiveData<Integer> getAppState() { return appState; }
    public Integer getCurrentAppState() { return currentAppState; }
    private void setAppState(Integer newState) {
        Log.d("ActivityStateHolder", "newState: " + newState);
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
                setAppState(SERVICE_BOUND);
                break;
            case SERVICE_BOUND:
                setAppState(AWAIT_LAUNCH_PERMISSIONS);
                break;
        }
    }
}