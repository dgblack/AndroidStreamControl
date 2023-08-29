package com.rcl.androidstreamcontrol.service;

import static com.rcl.androidstreamcontrol.utils.MyConstants.FOL_CLIENT_KEY;
import static com.rcl.androidstreamcontrol.utils.MyConstants.PEER_CONNECTED;
import static com.rcl.androidstreamcontrol.utils.MyConstants.PEER_DISCONNECTED;
import static com.rcl.androidstreamcontrol.utils.MyConstants.PEER_UNAVAILABLE;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.rcl.androidstreamcontrol.utils.Utils;
import com.rcl.androidstreamcontrol.webrtc.RTCClient;
import com.rcl.androidstreamcontrol.webrtc.SocketClient;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.DataChannel;

import java.util.Arrays;

public class ServiceRepository implements SocketClient.SocketListener, RTCClient.RTCListener {
    private static final String TAG = "ServiceRepository";
    private static final String IceSeparatorChar = "|";
    public PeerConnectionListener peerConnectionListener;
    Context context;
    ControlServiceRepository controlServiceRepo;
    protected SocketClient socketClient;
    public RTCClient rtcClient;
    public boolean controlEnabled;

    public ServiceRepository(Context context) {
        this.context = context;
        controlEnabled = false;
        controlServiceRepo = new ControlServiceRepository(context);
        socketClient = new SocketClient(FOL_CLIENT_KEY);
        rtcClient = new RTCClient(context);

        socketClient.listener = this;
        rtcClient.rtcListener = this;
    }

    public void onUnbind() {
        Log.d(TAG, "onUnbind");
        socketClient.handleDispose();
        rtcClient.handleDispose();
    }

    public void setMProjectionIntent(Intent mProjectionIntent) {
        rtcClient.mProjectionIntent = mProjectionIntent;
    }

    public void start() {
        initPeerConnection();
        socketClient.connectToSignallingServer();
    }

    public void initPeerConnection() {
        rtcClient.initializePeerConnectionFactory();
        rtcClient.initializePeerConnections();
        rtcClient.createVideoTrackFromScreenCapture();
        rtcClient.createControlDataChannel();
        rtcClient.createScreenOrientationDataChannel();
    }

    @Override
    public void handleOnNewMessage(String message) throws JSONException {
        if (message.equals(PEER_CONNECTED)) {
            Log.d("peer_status", "connected");
            socketClient.enableDoEncrypt();
            handleStartSignal();
            //peerConnectionListener.postPeerConnected();
            return;
        } else if (message.equals(PEER_DISCONNECTED)) {
            Log.d("peer_status", "disconnected");
            socketClient.disableDoEncrypt();
            peerConnectionListener.postPeerDisconnected();
            return;
        } else if (message.equals(PEER_UNAVAILABLE)) {
            Log.d("peer_status", "unavailable");
            peerConnectionListener.postPeerDisconnected();
        } else {
            JSONObject msgJson = new JSONObject(message);
            int messageType = msgJson.getInt("MessageType");
            String messageData = msgJson.getString("Data");
            switch (messageType) {
                case 1:
                    Log.d(TAG, "handleOfferMessage: received offer, sending answer " + message);
                    handleOfferMessage(messageData);
                    break;
                case 2:
                    Log.d(TAG, "handleAnswerMessage: received answer " + message);
                    handleAnswerMessage(messageData);
                    break;
                case 3:
                    Log.d(TAG, "handleIceCandidateMessage: receiving candidate " + message);
                    String parts[] = messageData.split("\\|");
                    String sdp = parts[0];
                    int sdpMLineIndex = Integer.parseInt(parts[1]);
                    String sdpMid = parts[2];
                    handleIceCandidateMessage(sdp, sdpMLineIndex, sdpMid);
                    break;
                default:
                    handleDefaultMessage(message);
            }
        }
    }

    public void handleStartSignal() {
        Log.d(TAG, "handleStartSignal: follower initiates the WebRTC signaling");
        rtcClient.handleStartSignal();
    }
    public void handleOfferMessage(String sdpContent) {
        rtcClient.handleOfferMessage(sdpContent);
    }
    public void handleAnswerMessage(String sdpContent) {
        rtcClient.handleAnswerMessage(sdpContent);
    }
    public void handleIceCandidateMessage(String sdp, int sdpMLineIndex, String sdpMid) {
        rtcClient.handleIceCandidateMessage(sdp, sdpMLineIndex, sdpMid);
    }
    public void handleDefaultMessage(String msg) {
        Log.d(TAG, msg);
    }

    @Override
    public void onPeerConnected() {
        peerConnectionListener.postPeerConnected();
        if (rtcClient.receiveControlEventsDC == null) {
            rtcClient.createControlDataChannel();
        }
        if (rtcClient.sendScreenOrientationDC == null) {
            rtcClient.createScreenOrientationDataChannel();
        }

        rtcClient.localVideoTrack.setEnabled(true);
        rtcClient.localVideoTrack.setEnabled(false);
    }
    @Override
    public void sendSdpToSocket(String sdp, int type) {
        sendToSocket(type, sdp);
    }

    @Override

    public void sendCandidateToSocket(String sdp, int sdpMLineIndex, String sdpMid) {
        String content = sdp
                + IceSeparatorChar + String.valueOf(sdpMLineIndex)
                + IceSeparatorChar + sdpMid;
        sendToSocket(3, content);
    }

    @Override
    public void renderControlEvent(byte[] eventBytes) {
        Log.d(TAG, "tryRenderControlEvent: " + controlEnabled);
        if (controlEnabled) {
            Log.d(TAG, "enabled");
            controlServiceRepo.renderControlEvent(eventBytes);
        }
    }

    public void sendToSocket(int type, String content) {
        JSONObject message = new JSONObject();

        try {
            message.put("MessageType", type);
            message.put("Data", content);
            message.put("IceDataSeparator", IceSeparatorChar);
            socketClient.sendMessage(String.valueOf(message));
            Log.d(TAG, "onIceCandidate: sending candidate " + message);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public interface PeerConnectionListener {
        public void postPeerConnected();
        public void postPeerDisconnected();
    }

}
