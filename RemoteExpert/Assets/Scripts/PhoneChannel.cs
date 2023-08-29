using UnityEngine;
using Microsoft.MixedReality.WebRTC;
using System.Threading.Tasks;

public class PhoneChannel : MonoBehaviour
{

    private DataChannel channel;
    public Microsoft.MixedReality.WebRTC.Unity.PeerConnection peerConnectionUnity;
    
    private Task<DataChannel> makeChannelTask;
    private bool waitingForChannel;
    private bool hasChannel;
    private bool channelOpen;
    private bool isMessageReceived;
    private bool isInitiator = false;
    
    public string channelName;

    public ClickCapture cc;
    public GameObject startInd;
    public GameObject endInd;

    void Start()
    {
        cc.callback += OnGestureCallback;

        waitingForChannel = false;
        hasChannel = false;
        channelOpen = false;
    }

    // Update is called once per frame
    void Update()
    {
        // Wait for channel to initialize
        if (waitingForChannel)
        {
            if (makeChannelTask.IsCompleted)
            {
                channel = makeChannelTask.Result;
                channel.StateChanged += UpdateChannelState;
                channel.MessageReceived += OnMessageReceived;
                hasChannel = true;
                Debug.Log("Data Channel " + channelName + " Created");
                waitingForChannel = false;
            }
            else if (makeChannelTask.IsCanceled || makeChannelTask.IsFaulted)
            {
                Debug.LogError("Failed to start data channel: " + channelName);
            }
        }

        //React to incoming messages
        if (isMessageReceived)
        {
            // Don't do anything rn. Not really expecting messages
            isMessageReceived = false;
        }
    }

    public void InitChannel(bool init)
    {
        isInitiator = init;
        peerConnectionUnity.Peer.DataChannelAdded += OnDataChannelAdded;
        peerConnectionUnity.Peer.DataChannelRemoved += OnDataChannelRemoved;

        if (init)
        {
            // Messages should be ordered (i.e. message that is sent first is received first, regardless of if that causes delays)
            // Messages are reliable, meaning they are resent if they are dropped (more tcp than udp)
            makeChannelTask = peerConnectionUnity.Peer.AddDataChannelAsync(channelName, true, true);
            waitingForChannel = true;
            Debug.Log("Creating Data Channel: " + channelName);
        }
    }

    public void SendMessage(Vector2 start, Vector2 end, float duration)
    {
        byte[] bmsg = new byte[11];

        byte[] sx = Float2Bytes(start.x);
        byte[] sy = Float2Bytes(start.y);
        byte[] ex = Float2Bytes(end.x);
        byte[] ey = Float2Bytes(end.y);
        byte[] d = Float2Bytes(duration);

        for (int i = 0; i < 2; i++)
        {
            bmsg[i] = sx[i];
            bmsg[i + 2] = sy[i];
            bmsg[i + 4] = ex[i];
            bmsg[i + 6] = ey[i];
            bmsg[i + 8] = d[i];
        }
        
        bmsg[10] = 0x00;

        if (hasChannel && channelOpen)
        {
            channel.SendMessage(bmsg);
        }
    }

    #region Callbacks
    public void OnGestureCallback(Vector2 startPos, Vector2 endPos, float duration)
    {
        Debug.Log("Start: " + startPos.x.ToString("N") + ", " + startPos.y.ToString("N"));
        Debug.Log("End: " + endPos.x.ToString("N") + ", " + endPos.y.ToString("N"));
        Debug.Log("Duration: " + duration.ToString("N"));

        Vector3 p = startInd.transform.position;
        Vector3 v = endInd.transform.position;

        //p.x = startPos.x * transform.localScale.x + transform.position.x - transform.localScale.x / 2;
        //p.y = startPos.y * transform.localScale.y + transform.position.y - transform.localScale.y / 2;
        //v.x = endPos.x * transform.localScale.x + transform.position.x - transform.localScale.x / 2;
        //v.y = endPos.y * transform.localScale.y + transform.position.y - transform.localScale.y / 2;

        for (int i = 0; i < 2; i++)
        {
            // Set positions of markers for visual feedback
            p[i] = startPos[i];
            v[i] = endPos[i];

            // Transform to relative screen coordinates (origin at centre of screen)
            startPos[i] = (startPos[i] - transform.position[i] + transform.localScale[i] / 2) / transform.localScale[i];
            endPos[i] = (endPos[i] - transform.position[i] + transform.localScale[i] / 2) / transform.localScale[i];
        }

        // Set positions of markers for visual feedback
        startInd.transform.position = p;
        endInd.transform.position = v;

        SendMessage(startPos, endPos, duration);
    }
    private void OnDataChannelAdded(DataChannel addedChannel)
    {
        if (!isInitiator && addedChannel.Label.Equals(channelName))
        {
            channel = addedChannel;
            channel.StateChanged += UpdateChannelState;
            channel.MessageReceived += OnMessageReceived;
            hasChannel = true;
            Debug.Log("Channel: " + channelName + " Received from Peer");
        }
    }

    private void OnDataChannelRemoved(DataChannel removedChannel)
    {
        if (hasChannel && removedChannel.Label.Equals(channelName))
        {
            hasChannel = false;
            channel = null;
            Debug.Log(channelName + " Channel Removed");
        }
    }

    private void OnMessageReceived(byte[] msg)
    {
        isMessageReceived = true;
    }

    private void UpdateChannelState()
    {
        channelOpen = channel.State == DataChannel.ChannelState.Open;
    }
    #endregion

    #region Helper Methods
    private byte[] Float2Bytes(float val)
    {
        int num = (int)(val * 1000);
        byte[] b = new byte[2];
        b[0] = (byte)((num & 0xFF00) >> 8);
        b[1] = (byte)(num & 0x00FF);
        return b;
    }
    #endregion
}
