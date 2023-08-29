# AndroidControlProject

### Main App Layout
![Main App Layout](/imgs/1_app_buttons.png) <br>
The Main Button colour signifies the state the app is currently in.
* Grey: permissions have not been granted, streaming service has not been started
* White: permissions have been granted, streaming service has not been started
* Green: permissions have been granted, streaming service started

The Bubble Button symbol signifies the state that the service is in and the state of the peer. The bubble button does not appear until the streaming service has been started by the user. 
The Bubble Button is a window overlay and will be visible as long as the service is running.
* Circle-Backslash: peer is not connected to Websocket
* Play: peer is connected to socket, and RTC communication has been established, video is not currently streaming
* Pause: peer is connected to socket, and RTC communication has been established, video is currently streaming to peer

![Enable Permissions](/imgs/2_accessibility_permissions.png) <br>
Image descriptions from left to right:
* Grey Main Button signifies that not all permissions have been granted
* Pressing the Main Button launches a dialogue describing the permission required (in this case the Accessibility Service permission for our app)
* Pressing "Continue" brings you to settings. Navigate to Accessibility > Installed Apps > Control Service
* Toggle ControlService ON and allow service to have full control of device
You should only need to set permissions in device settings when the app is first installed.

On other Android versions it may look slightly different. For example:
![Enable Permissions Older Android](/imgs/3_screen_share_permissions2.png) <br>

![Enable Screen Sharing](/imgs/3_screen_share_permissions.png) <br>
Image descriptions from left to right:
* White Main Button signifies that all required permissions from the settings have been granted
* Pressing the Main Button to begin screen capture launches a dialogue to obtain user permissions to access screen information
* Service is ready, awaiting peer
* Peer is connected, start streaming screen by pressing bubble button. 

![End Service](/imgs/4_end_service.png) <br>
Image descriptions from left to right:
* You are able to start and stop screen streaming using the bubble button either inside or outside the AndroidControl app
* You can end the service by pressing the Main Button in the app or dragging the bubble button to the bottom of the screen onto the trash icon
* You will receive a dialogue ensuring that you want to end the service. This will destroy the peer connection and disconnect from websocket

### Main Components Used in App
* WebRTC: com.dafruits:webrtc has the best video streaming (least amount of streaming and lag). We also tried: io.github.webrtc-sdk:android and io.getstream:stream-webrtc-android.
* MediaProjection: allows us to capture device screen. Works in conjunction with WebRTC ScreenCapturer class.
* Android Accessibility Service: The ControlService element. Allows us to dispatch simple gestures such as clicks and drags from remote device to local device.
* Foreground Service: most app functionality works within the foreground service. It allows us to run code even outside of the app.

### Running the project
To test/use the Android application, a sample Unity project is available in the RemoteExpert directory. 
This gives an idea of how to set up the expert side to remotely stream and control the Android.
To connect the two sides, a signaling server is required, as is standard in WebRTC. We have implemented a very simple server in Python using WebSockets which is available in the signaling directory.
You can either run the system locally for testing by calling "python signaling/signalServerLocal.py", or you can use the system remotely over the internet for example using Heroku and the files in signaling/heroku.
Note, there is no proper authentication or encryption of the signaling messages so you should probably add that. 
To change the address of the signaling server, change it in MyConstants.java (app/src/main/java/com/example/androidcontrol/utils) and in the phoneSignaler component of the Unity project, and potentially in signalServerLocal.py. 

### Usage Suggestions
Primarily tested on an Android 13 device (API level 33). Should be compatible with devices that are API levels 29-33.
<br><br>
WebRTC is slow, particularly with devices with larger screens. 
Decreasing the resolution of the video frame sent over WebRTC will reduce lag. 
You can do this by setting PROJECTED_PIXELS_HEIGHT and PROJECTED_PIXELS_WIDTH in MainActivity.java. 
<br><br>
The sending of video frames is triggered by screen pixels changing frame-to-frame.
We have found that screen sharing where the local device screen pixels are changing a lot frame to frame 
(switching between screens or apps) causes temporary screen freezes to the remote device viewing their screen.
We recommend that the local device does not start screen sharing until they have already navigated to the app/screen that they wish to share with the remote peer.
<br><br>

### Potential Future Improvements
* Use Unity WebRTC and RenderStreaming packages:
  * These packages have built-in functionality for capturing and dispatching gestures and inputs which opens up the possibility for more complex controls
  * Unity WebRTC video streaming is significantly faster with minimal lag and frozen frames
  * Problems: 
    * Unity for Android does not have foreground or background services. 
    * Unity for Android does not have a way to listen for events while the app is not running (IOS does though)
* Implement MediaProjection changes in Android API 34
  * Current MediaProjection we need to capture the entire screen
  * In API 34 you are able to create screen capture intent with a MediaProjectionConfig
  * This way you can crop the size of the screen and therefore send a smaller video frame via WebRTC without decreasing resolution

