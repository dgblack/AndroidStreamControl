package com.rcl.androidstreamcontrol.webrtc;

import static com.rcl.androidstreamcontrol.utils.MyConstants.*;
import static org.webrtc.SessionDescription.Type.ANSWER;
import static org.webrtc.SessionDescription.Type.OFFER;

import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.util.Log;

import com.rcl.androidstreamcontrol.utils.Type;
import com.rcl.androidstreamcontrol.utils.Utils;

import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.YuvConverter;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class RTCClient {
    private static final String TAG = "RTCClient";

    private final Context context;

    public RTCClient.RTCListener rtcListener;

    PeerConnection peerConnection;
    public EglBase rootEglBase;
    PeerConnectionFactory factory;
    private ScreenCapturerAndroid screenCapturer;
    public VideoTrack localVideoTrack;
    public DataChannel receiveControlEventsDC;
    public DataChannel sendScreenOrientationDC;
    public static Intent mProjectionIntent;
    SurfaceTextureHelper mSurfaceTextureHelper;

    public RTCClient(Context context) {
        this.context = context;
    }

    public void handleDispose() {
        if (peerConnection != null) {
            if (localVideoTrack != null) {
                peerConnection.removeTrack(peerConnection.getSenders().get(0));
                localVideoTrack.dispose();
                mSurfaceTextureHelper.stopListening();
                mSurfaceTextureHelper.dispose();
            }
            peerConnection.close();
            peerConnection.dispose();
            factory.stopAecDump();
        }
    }

    public void handleStartSignal() {
        MediaConstraints sdpMediaConstraints = new MediaConstraints();

        sdpMediaConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        peerConnection.createOffer(new SimpleSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.d(TAG, "onCreateSuccess: ");
                peerConnection.setLocalDescription(new SimpleSdpObserver(), sessionDescription);
                rtcListener.sendSdpToSocket(sessionDescription.description, Type.Offer.getVal());
            }
        }, sdpMediaConstraints);
    }

    public void handleOfferMessage(String sdpContent) {
        peerConnection.setRemoteDescription(new SimpleSdpObserver(), new SessionDescription(
                OFFER, sdpContent));
        peerConnection.createAnswer(new SimpleSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                peerConnection.setLocalDescription(new SimpleSdpObserver(), sessionDescription);
                rtcListener.sendSdpToSocket(sessionDescription.description, Type.Answer.getVal());
            }
        }, new MediaConstraints());
    }

    public void handleAnswerMessage(String sdpContent) {
        peerConnection.setRemoteDescription(new SimpleSdpObserver(), new SessionDescription(ANSWER, sdpContent));
    }

    public void handleIceCandidateMessage(String sdp, int sdpMLineIndex, String sdpMid) {
        IceCandidate candidate = new IceCandidate(sdpMid, sdpMLineIndex, sdp);
        peerConnection.addIceCandidate(candidate);
    }


    public void initializePeerConnectionFactory() {

        rootEglBase = EglBase.create(null, new EglBase.ConfigBuilder().setHasAlphaChannel(false).createConfigAttributes());
        //rootEglBase = EglBase.create();
        PeerConnectionFactory.InitializationOptions initOptions = PeerConnectionFactory
                .InitializationOptions.builder(context)
                .createInitializationOptions();

        PeerConnectionFactory.initialize(initOptions);
        //Create a new PeerConnectionFactory instance - using Hardware encoder and decoder.
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        DefaultVideoEncoderFactory defaultVideoEncoderFactory = new DefaultVideoEncoderFactory(
                rootEglBase.getEglBaseContext(), true,true);


        DefaultVideoDecoderFactory defaultVideoDecoderFactory = new DefaultVideoDecoderFactory(
                rootEglBase.getEglBaseContext());

        factory = PeerConnectionFactory.builder().setOptions(options)
                .setVideoEncoderFactory(defaultVideoEncoderFactory)
                .setVideoDecoderFactory(defaultVideoDecoderFactory)
                .createPeerConnectionFactory();
    }

    public void initializePeerConnections() {
        peerConnection = createPeerConnection(factory);
    }

    public void createControlDataChannel() {
        Log.d(TAG, "createControlDataChannel: ");
        receiveControlEventsDC = peerConnection.createDataChannel(DC_CONTROL_LABEL, new DataChannel.Init());

        Log.d("DataChannelState", String.valueOf(receiveControlEventsDC.state()));

        receiveControlEventsDC.registerObserver(new DataChannel.Observer() {
            @Override
            public void onBufferedAmountChange(long l) { }
            @Override
            public void onStateChange() { Log.d("DataChannelState", String.valueOf(receiveControlEventsDC.state()));  }
            @Override
            public void onMessage(DataChannel.Buffer buffer) { receiveMessageFromChannel(buffer.data); }
        });
    }

    public void createScreenOrientationDataChannel() {
        Log.d(TAG, "createScreenOrientationDataChannel: ");
        sendScreenOrientationDC = peerConnection.createDataChannel(DC_ORIENTATION_LABEL, new DataChannel.Init());
    }

    public void onScreenOrientationChange(String message) {
        ByteBuffer data = Utils.stringToByteBuffer(message);

        if (sendScreenOrientationDC != null) {
            sendScreenOrientationDC.send(new DataChannel.Buffer(data, false));
        }
    }

    public void receiveMessageFromChannel(ByteBuffer msgByteBuffer) {
        Log.d(TAG, "receiveMessageFromChannel");
        byte[] message = Utils.byteBufferToBytes(msgByteBuffer);
        rtcListener.renderControlEvent(message);
    }

    public void createVideoTrackFromScreenCapture() {
        VideoCapturer videoCapturer = createScreenCapturer();
        VideoSource videoSource = factory.createVideoSource(videoCapturer.isScreencast(), true);
        SurfaceTextureHelper.FrameRefMonitor frameRefMonitor = new SurfaceTextureHelper.FrameRefMonitor() {
            int countRepeat = 0;
            long prevNumCapturedFrames = 0;
            @Override
            public void onNewBuffer(VideoFrame.TextureBuffer textureBuffer) {

            }

            @Override
            public void onRetainBuffer(VideoFrame.TextureBuffer textureBuffer) {
                long currentNumCapturedFrames = screenCapturer.getNumCapturedFrames();
                if (prevNumCapturedFrames == currentNumCapturedFrames) {
                    countRepeat ++;
                } else {
                    //Log.d("onRetainBuffer", "getNumRepeatFrames: " + String.valueOf(countRepeat));
                    prevNumCapturedFrames = currentNumCapturedFrames;
                    countRepeat = 0;

                }
            }

            @Override
            public void onReleaseBuffer(VideoFrame.TextureBuffer textureBuffer) {
                long currentNumCapturedFrames = screenCapturer.getNumCapturedFrames();
                if (prevNumCapturedFrames == currentNumCapturedFrames) {
                    countRepeat ++;
                } else {
                    //Log.d("onReleaseBuffer", "getNumRepeatFrames: " + String.valueOf(countRepeat));
                    prevNumCapturedFrames = currentNumCapturedFrames;
                    countRepeat = 0;

                }
            }

            @Override
            public void onDestroyBuffer(VideoFrame.TextureBuffer textureBuffer) {

            }
        };
        mSurfaceTextureHelper = SurfaceTextureHelper.create(Thread.currentThread().getName(), rootEglBase.getEglBaseContext(), true, new YuvConverter(), frameRefMonitor);
        videoCapturer.initialize(mSurfaceTextureHelper, context, videoSource.getCapturerObserver());
        /*
        videoSource.adaptOutputFormat(
                new VideoSource.AspectRatio(PROJECTED_PIXELS_WIDTH, PROJECTED_PIXELS_HEIGHT),
                null,
                new VideoSource.AspectRatio(PROJECTED_PIXELS_WIDTH, PROJECTED_PIXELS_HEIGHT),
                null,
                null);

         */
        videoCapturer.startCapture(PROJECTED_PIXELS_WIDTH, PROJECTED_PIXELS_HEIGHT, FPS);
        localVideoTrack = factory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
        peerConnection.addTrack(localVideoTrack);
    }

    private PeerConnection createPeerConnection(PeerConnectionFactory factory) {
        ArrayList<PeerConnection.IceServer> iceServers = new ArrayList<PeerConnection.IceServer>();
        String URL = "stun:stun.l.google.com:19302";
        iceServers.add(PeerConnection.IceServer.builder(URL).createIceServer());


        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

        MediaConstraints pcConstraints = new MediaConstraints();

        PeerConnection.Observer pcObserver = new PeerConnection.Observer() {
            @Override
            public void onConnectionChange(PeerConnection.PeerConnectionState newState) {
                Log.d(TAG, "onConnectionChange: " + newState);
                if (newState.equals(PeerConnection.PeerConnectionState.CONNECTED)) {
                    rtcListener.onPeerConnected();
                }
            }
            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {
                Log.d(TAG, "onSignalingChange: ");
            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                Log.d(TAG, "onIceConnectionChange: ");
            }

            @Override
            public void onIceConnectionReceivingChange(boolean b) {
                Log.d(TAG, "onIceConnectionReceivingChange: ");
            }

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
                Log.d(TAG, "onIceGatheringChange: ");
            }

            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                rtcListener.sendCandidateToSocket(
                        iceCandidate.sdp,
                        iceCandidate.sdpMLineIndex,
                        iceCandidate.sdpMid
                        );
            }

            @Override
            public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
                Log.d(TAG, "onIceCandidatesRemoved: ");
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                Log.d(TAG, "onAddStream: " + "FOL does not receive media streams from expert");
            }

            @Override
            public void onRemoveStream(MediaStream mediaStream) {
                Log.d(TAG, "onRemoveStream: ");
            }

            @Override
            public void onDataChannel(DataChannel dataChannel) {
                Log.d(TAG, "onDataChannel: ");
            }

            @Override
            public void onRenegotiationNeeded() {
                Log.d(TAG, "onRenegotiationNeeded: ");
            }

            @Override
            public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
                Log.d(TAG, "onRenegotiationNeeded: ");
            }
        };

        return factory.createPeerConnection(rtcConfig, pcObserver);
    }



    private VideoCapturer createScreenCapturer() {
        MediaProjection.Callback mediaProjectionCallback = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                Log.d(TAG, "mediaProjectionCallback: capture stopped");
            }
        };

        screenCapturer = new ScreenCapturerAndroid(
                mProjectionIntent, mediaProjectionCallback);

        return (VideoCapturer) screenCapturer;
    }


    public interface RTCListener {
        void sendSdpToSocket(String sdp, int type);
        void sendCandidateToSocket(String sdp, int sdpMLineIndex, String sdpMid);
        void renderControlEvent(byte[] eventBytes);
        void onPeerConnected();
    }
}