using Microsoft.MixedReality.WebRTC.Unity;
using UnityEngine;

public class PhoneRTCManager : MonoBehaviour
{
    public PeerConnection peerConnection;
    public PhoneChannel phoneChannel;
    public bool isInitiator = false;
    private bool waitingForConnection = false;
    private bool connected = false;

    // Start is called before the first frame update
    void Start()
    {
    }

    // Update is called once per frame
    void Update()
    {
        if (waitingForConnection)
        {
            if (peerConnection != null && peerConnection.Peer != null && peerConnection.Peer.IsConnected)
            {
                Debug.Log("Connected to Peer!");

                waitingForConnection = false;
                connected = true;
            }
        }
        else
        {
            if (connected)
            {
                if (!peerConnection.Peer.IsConnected)
                    OnDisconnected();
            }
            else
            {
                OnDisconnected();
            }
        }
    }

    private void Peer_IceStateChanged(Microsoft.MixedReality.WebRTC.IceConnectionState newState)
    {
        if (newState == Microsoft.MixedReality.WebRTC.IceConnectionState.Closed ||
            newState == Microsoft.MixedReality.WebRTC.IceConnectionState.Failed)
        {
            // We should try to reconnect
            connected = false;
        }
        else if (newState == Microsoft.MixedReality.WebRTC.IceConnectionState.Connected)
            connected = true;
    }

    private void OnDisconnected()
    {
        connected = false;
        waitingForConnection = true;
        Debug.Log("Disconnected from WebRTC. Trying to reconnect.");
    }

    public void StartRTC()
    {
        peerConnection.StartConnection();
    }

    public void OnRTCReady()
    {
        if (peerConnection.Peer != null)
        {
            peerConnection.Peer.IceStateChanged += Peer_IceStateChanged;
        }
        else Debug.Log("Error, peer is null on RTC ready");

        phoneChannel.InitChannel(isInitiator);

        waitingForConnection = true;
    }

    public void OnApplicationQuit()
    {
        peerConnection.Peer.Close();
    }
}