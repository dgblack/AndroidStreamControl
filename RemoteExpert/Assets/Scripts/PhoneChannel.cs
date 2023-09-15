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

    public enum GestureType
    {
        WillContinue,
        Pinch,
        Normal
    }

    void Start()
    {
        cc.gestureCallback += OnGestureCallback;
        cc.pinchCallback += OnPinchCallback;

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
    public void SendMessage(Vector2 start, Vector2 end, float duration, GestureType gt)
    {
        byte[] bmsg = new byte[11];

        byte[] sx = Float2Bytes(start.x);
        byte[] sy = Float2Bytes(start.y);
        byte[] ex = Float2Bytes(end.x);
        byte[] ey = Float2Bytes(end.y);
        byte[] d = Int2Bytes(duration);

        for (int i = 0; i < 2; i++)
        {
            bmsg[i] = sx[i];
            bmsg[i + 2] = sy[i];
            bmsg[i + 4] = ex[i];
            bmsg[i + 6] = ey[i];
            bmsg[i + 8] = d[i];
        }

        if (gt == GestureType.WillContinue)
            bmsg[10] = 0x01;
        else if (gt == GestureType.Pinch)
            bmsg[10] = 0x02;
        else bmsg[10] = 0x00;

        if (hasChannel && channelOpen)
        {
            channel.SendMessage(bmsg);
        }
    }


    #region Callbacks
    public void OnGestureCallback(Vector2 startPos, Vector2 endPos, float duration, GestureType willContinue)
    {
        //Debug.Log("Start: " + startPos.x.ToString("N") + ", " + startPos.y.ToString("N"));
        //Debug.Log("End: " + endPos.x.ToString("N") + ", " + endPos.y.ToString("N"));
        //Debug.Log("Duration: " + duration.ToString("N"));

        Vector3 p = startInd.transform.position;
        p.x = startPos.x * transform.localScale.x + transform.position.x - transform.localScale.x / 2;
        p.y = startPos.y * transform.localScale.y + transform.position.y - transform.localScale.y / 2;
        startInd.transform.position = p;

        Vector3 v = endInd.transform.position;
        v.x = endPos.x * transform.localScale.x + transform.position.x - transform.localScale.x / 2;
        v.y = endPos.y * transform.localScale.y + transform.position.y - transform.localScale.y / 2;
        endInd.transform.position = v;

        SendMessage(startPos, endPos, duration, willContinue);
    }

    public void OnPinchCallback(Vector2 startPos, Vector2 dir, float dt, GestureType pinch)
    {
        // a and pinch are ignored

        Vector3 p = startInd.transform.position;
        p.x = startPos.x * transform.localScale.x + transform.position.x - transform.localScale.x / 2;
        p.y = startPos.y * transform.localScale.y + transform.position.y - transform.localScale.y / 2;
        startInd.transform.position = p;
        endInd.transform.position = p;

        SendMessage(startPos, dir, dt, GestureType.Pinch);
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
        // Only for val between 0 and 1
        return Int2Bytes((int)(val * 65535));
        
    }

    private byte[] Int2Bytes(int val)
    {
        // only for val <= 65535
        byte[] b = new byte[2];
        b[0] = (byte)((num & 0xFF00) >> 8);
        b[1] = (byte)(num & 0x00FF);
        return b;
    }
    #endregion
}
