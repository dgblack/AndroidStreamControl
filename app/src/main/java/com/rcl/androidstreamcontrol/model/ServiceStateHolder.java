package com.rcl.androidstreamcontrol.model;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class ServiceStateHolder extends ViewModel {
    public static final int SERVICE_NOT_READY = 10;
    public static final int SERVICE_READY = 11;
    public static final int SERVICE_RUNNING = 12;

    private final MutableLiveData<Integer> serviceState = new MutableLiveData<Integer>();
    private static Integer currentServiceState;
    public ServiceStateHolder() { setServiceState(SERVICE_NOT_READY); }

    public LiveData<Integer> getServiceState() { return serviceState; }
    public Integer getCurrentServiceState() { return currentServiceState; }

    private void setServiceState(Integer newState) {
        currentServiceState = newState;
        serviceState.postValue(newState);

        Log.d("newServiceState", String.valueOf(newState));
        Log.d("hasObservers", String.valueOf(serviceState.hasObservers()));
        Log.d("isInitialized", String.valueOf(serviceState.isInitialized()));
    }

    public void onBubbleButtonClick() {
        switch (currentServiceState) {
            case SERVICE_NOT_READY:
                break;
            case SERVICE_READY:
                setServiceState(SERVICE_RUNNING);
                break;
            case SERVICE_RUNNING:
                setServiceState(SERVICE_READY);
                break;
        }
    }

    public void onPeerDisconnect() {
        switch (currentServiceState) {
            case SERVICE_NOT_READY:
                break;
            case SERVICE_READY:
            case SERVICE_RUNNING:
                setServiceState(SERVICE_NOT_READY);
                break;
        }
    }

    public void onPeerConnect() {
        switch (currentServiceState) {
            case SERVICE_NOT_READY:
                setServiceState(SERVICE_READY);
                break;
            case SERVICE_READY:
            case SERVICE_RUNNING:
                break;
        }
    }

}
