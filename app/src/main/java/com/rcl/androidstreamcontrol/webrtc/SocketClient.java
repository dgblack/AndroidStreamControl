package com.rcl.androidstreamcontrol.webrtc;

import static com.rcl.androidstreamcontrol.utils.MyConstants.AUTH_TOKEN;
import static com.rcl.androidstreamcontrol.utils.MyConstants.SERVER_URL;

import android.util.Log;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;

import java.net.URI;
import java.net.URISyntaxException;

public class SocketClient {

    private static final String TAG = "SocketClient";

    public WebSocketClient websocket;
    public SocketClient.SocketListener listener;
    private boolean doEncrypt;
    private String clientKey;

    public SocketClient(String clientKey) {
        this.clientKey = clientKey;
        doEncrypt = false;
    }

    public void handleDispose() {
        if (websocket != null) {
            websocket.close();
        }
    }

    public void enableDoEncrypt() {
        doEncrypt = true;
    }

    public void disableDoEncrypt() {
        doEncrypt = false;
    }

    public void connectToSignallingServer() {
        URI uri;
        try {
            uri = new URI(SERVER_URL);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }


        websocket = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                Log.d(TAG, "connectToSignallingServer: on open");
                checkIsPeerConnected();
            }

            @Override
            public void onMessage(String message) {
                Log.d(TAG, "connectToSignallingServer: on message: " + message);
                receiveMessage(message);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                Log.d(TAG, "connectToSignallingServer: on close");
            }

            @Override
            public void onError(Exception ex) {
                Log.d(TAG, "connectToSignallingServer: on error " + ex.getMessage());

            }
        };
        websocket.connect();
        Log.d(TAG, "connectToSignallingServer: try connect to server");
        Log.d(TAG, AUTH_TOKEN + clientKey);
    }

    public void checkIsPeerConnected() {
        websocket.send(AUTH_TOKEN + clientKey);
    }

    public static String dumbEncrypt(String msg) {
        int msgLen = msg.length();
        char msgChar[] = msg.toCharArray();

        for (int i = 0; i < msgLen; i++) {
            if (i % 2 == 0) {
                if (msgChar[i] > 16) msgChar[i] -= 16;
            } else {
                if (msgChar[i] < 128) msgChar[i] += 128;
            }
        }

        return String.valueOf(msgChar);
    }

    public static String dumbDecrypt(String msg) {
        int msgLen = msg.length();
        char msgChar[] = msg.toCharArray();

        for (int i = 0; i < msgLen; i++) {
            if (i % 2 == 0) {
                msgChar[i] += 16;
            } else {
                msgChar[i] -= 128;
            }
        }

        return String.valueOf(msgChar);
    }


    public void sendMessage(String message) {
        String messageToSend;
        if (false) {
            messageToSend = dumbEncrypt(message);
        } else {
            messageToSend = message;
        }

        try {
            websocket.send(messageToSend);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void receiveMessage(String messageReceived) {
        String message;
        if (false) {
            message = dumbDecrypt(messageReceived);
        } else {
            message = messageReceived;
        }

        try {
            listener.handleOnNewMessage(message);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }



    public interface SocketListener {
        void handleOnNewMessage(String message) throws JSONException;
    }

}
