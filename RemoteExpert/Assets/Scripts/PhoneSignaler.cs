// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

using System;
using System.Collections;
using System.Threading.Tasks;
using UnityEngine;
using WebSocketSharp;

namespace Microsoft.MixedReality.WebRTC.Unity
{
    /// <summary>
    /// Simple signaler using WebSocket-sharp
    /// </summary>
    [AddComponentMenu("MixedReality-WebRTC/WebSocket Signaler")]
    public class PhoneSignaler : Signaler
    {
        /// <summary>
        /// <summary>
        /// Automatically log all errors to the Unity console.
        /// </summary>
        [Tooltip("Automatically log all errors to the Unity console")]
        public bool AutoLogErrors = true;

        /// <summary>
        /// Unique identifier of the local peer.
        /// </summary>
        [Tooltip("Unique identifier of the local peer")]
        public string LocalPeerId;

        /// <summary>
        /// Unique identifier of the remote peer.
        /// </summary>
        [Tooltip("Unique identifier of the remote peer")]
        public string RemotePeerId;

        /// <summary>
        /// The WebSocket server address to connect to
        /// </summary>
        [Header("Server")]
        [Tooltip("The WebSocket server to connect to")]
        public string WSServerAddress = "ws://127.0.0.1:8001/";

        /// <summary>
        /// The interval (in ms) that the server is polled at
        /// </summary>
        [Tooltip("The interval (in ms) that the server is polled at")]
        public float PollTimeMs = 500f;

        private WebSocket ws;
        [NonSerialized] public bool isOpen = false;
        private Queue messages;
        private bool keepClosed = false;
        private bool sendPing = false;

        private enum SslProtocolsHack
        {
            Tls = 192,
            Tls11 = 768,
            Tls12 = 3072
        }

        /// <summary>
        /// Message exchanged with a <c>WebSocket</c> server, serialized as JSON.
        /// </summary>
        /// <remarks>
        /// The names of the fields is critical here for proper JSON serialization.
        /// </remarks>
        [Serializable]
        private class WebSocketMessage
        {
            /// <summary>
            /// Separator for ICE messages.
            /// </summary>
            public const string IceSeparatorChar = "|";

            /// <summary>
            /// Possible message types as-serialized on the wire to <c>WebSocket</c>.
            /// </summary>
            public enum Type
            {
                /// <summary>
                /// An unrecognized message.
                /// </summary>
                Unknown = 0,

                /// <summary>
                /// A SDP offer message.
                /// </summary>
                Offer,

                /// <summary>
                /// A SDP answer message.
                /// </summary>
                Answer,

                /// <summary>
                /// A trickle-ice or ice message.
                /// </summary>
                Ice
            }

            /// <summary>
            /// Convert a message type from <see xref="string"/> to <see cref="Type"/>.
            /// </summary>
            /// <param name="stringType">The message type as <see xref="string"/>.</param>
            /// <returns>The message type as a <see cref="Type"/> object.</returns>
            public static Type MessageTypeFromString(string stringType)
            {
                if (string.Equals(stringType, "offer", StringComparison.OrdinalIgnoreCase))
                {
                    return Type.Offer;
                }
                else if (string.Equals(stringType, "answer", StringComparison.OrdinalIgnoreCase))
                {
                    return Type.Answer;
                }
                throw new ArgumentException($"Unkown signaler message type '{stringType}'", "stringType");
            }

            public static Type MessageTypeFromSdpMessageType(SdpMessageType type)
            {
                switch (type)
                {
                    case SdpMessageType.Offer: return Type.Offer;
                    case SdpMessageType.Answer: return Type.Answer;
                    default: return Type.Unknown;
                }
            }

            public IceCandidate ToIceCandidate()
            {
                if (MessageType != Type.Ice)
                {
                    throw new InvalidOperationException("The WebSocket message is not an ICE candidate message.");
                }
                var parts = Data.Split(new string[] { IceSeparatorChar }, StringSplitOptions.RemoveEmptyEntries);
                // Note the inverted arguments; candidate is last in IceCandidate, but first in the nWebSocket wire message
                return new IceCandidate
                {
                    SdpMid = parts[2],
                    SdpMlineIndex = int.Parse(parts[1]),
                    Content = parts[0]
                };
            }

            public WebSocketMessage(SdpMessage message)
            {
                MessageType = MessageTypeFromSdpMessageType(message.Type);
                Data = message.Content;
                IceDataSeparator = string.Empty;
            }

            public WebSocketMessage(IceCandidate candidate)
            {
                MessageType = Type.Ice;
                Data = string.Join(IceSeparatorChar, candidate.Content, candidate.SdpMlineIndex.ToString(), candidate.SdpMid);
                IceDataSeparator = IceSeparatorChar;
            }

            /// <summary>
            /// The message type.
            /// </summary>
            public Type MessageType = Type.Unknown;

            /// <summary>
            /// The primary message contents.
            /// </summary>
            public string Data;

            /// <summary>
            /// The data separator needed for proper ICE serialization.
            /// </summary>
            public string IceDataSeparator;
        }


        #region ISignaler interface

        /// <inheritdoc/>
        public override Task SendMessageAsync(SdpMessage message)
        {
            return SendMessageImplAsync(new WebSocketMessage(message));
        }

        /// <inheritdoc/>
        public override Task SendMessageAsync(IceCandidate candidate)
        {
            return SendMessageImplAsync(new WebSocketMessage(candidate));
        }

        #endregion

        private Task SendMessageImplAsync(WebSocketMessage message)
        {
            // This method needs to return a Task object which gets completed once the signaler message
            // has been sent. Because the implementation uses a Unity coroutine, use a reset event to
            // signal the task to complete from the coroutine after the message is sent.
            // Note that the coroutine is a Unity object so needs to be started from the main Unity app thread.
            // Also note that TaskCompletionSource<bool> is used as a no-result variant; there is no meaning
            // to the bool value.
            // https://stackoverflow.com/questions/11969208/non-generic-taskcompletionsource-or-alternative
            var tcs = new TaskCompletionSource<bool>();
            _mainThreadWorkQueue.Enqueue(() => StartCoroutine(PostToServerAndWait(message, tcs)));
            return tcs.Task;
        }

        /// <summary>
        /// Unity Engine Start() hook
        /// </summary>
        /// <remarks>
        /// https://docs.unity3d.com/ScriptReference/MonoBehaviour.Start.html
        /// </remarks>
        private void Start()
        {
            if (string.IsNullOrEmpty(WSServerAddress))
            {
                throw new ArgumentNullException("WSServerAddress");
            }
            if (!WSServerAddress.EndsWith("/"))
            {
                WSServerAddress += "/";
            }

            // If not explicitly set, default local ID to some unique ID generated by Unity
            if (string.IsNullOrEmpty(LocalPeerId))
            {
                LocalPeerId = SystemInfo.deviceName;
            }

            messages = new Queue();

            InitWebSocket();
        }

        private void InitWebSocket()
        {
            //Start WebSocket client
            ws = new WebSocket(WSServerAddress);
            ws.OnMessage += (sender, e) =>
            {
                //Debug.Log(e.IsPing? e.Data : "Received a ping.");
                //if (!e.IsPing)
                HandleMessage(e);
            };

            ws.OnError += (sender, e) =>
            {
                Debug.Log("Error in WebSocket: " + e.Message);
            };

            ws.OnOpen += (sender, e) =>
            {
                ws.Send(System.Text.Encoding.UTF8.GetBytes("AWefkbasflaLWIKWBE28357al>??AVLSIsdgauugwei37" + LocalPeerId));
                isOpen = true;
                Debug.Log("WebSocket signaling connection opened");
            };

            ws.OnClose += (sender, e) =>
            {
                isOpen = false;
                if (keepClosed) return;

                var sslProtocolsHack = (System.Security.Authentication.SslProtocols)(SslProtocolsHack.Tls12 | SslProtocolsHack.Tls11 | SslProtocolsHack.Tls);
                if (e.Code == 1015 && ws.SslConfiguration.EnabledSslProtocols != sslProtocolsHack)
                {
                    ws.SslConfiguration.EnabledSslProtocols = sslProtocolsHack;
                    ws.ConnectAsync();
                }
                else if (e.Code == 1002)
                {
                    Debug.Log("Failed handshake. Trying again...");
                    ws.ConnectAsync();
                }
                else
                {
                    Debug.Log("WebSocket closed. Trying to reconnect...");
                    ws.ConnectAsync();
                }
            };

            ws.ConnectAsync();
        }

        /// <summary>
        /// Internal helper to wrap a coroutine into a synchronous call for use inside
        /// a <see cref="Task"/> object.
        /// </summary>
        /// <param name="msg">the message to send</param>
        private IEnumerator PostToServerAndWait(WebSocketMessage message, TaskCompletionSource<bool> tcs)
        {
            if (RemotePeerId.Length == 0)
            {
                throw new InvalidOperationException("Cannot send SDP message to remote peer; invalid empty remote peer ID.");
            }

            // Encode data in JSON
            string encryptedMsg = JsonUtility.ToJson(message);

            // Should encrypt/decrypt these messages
            //string encryptedMsg = Encrypter.SimpleEncryptWithPassword(unencryptedMsg, cryptKey);
            //var data = System.Text.Encoding.UTF8.GetBytes(encryptedMsg);

            var data = System.Text.Encoding.UTF8.GetBytes(encryptedMsg);

            // Set up function for websocket to call when its done
            bool isDone = false;
            Action<bool> act = delegate (bool b) {
                if (b)
                    tcs.SetResult(true);
                else
                    tcs.SetResult(false);
                isDone = true;
            };

            // Send asynchronously
            //ws.SendAsync(data,act);
            ws.SendAsync(encryptedMsg, act);

            // Wait for send to complete
            while (!isDone)
            {
                yield return null;
            }
        }

        private IEnumerator SendMsg(string message)
        {
            if (RemotePeerId.Length == 0)
            {
                throw new InvalidOperationException("Cannot send SDP message to remote peer; invalid empty remote peer ID.");
            }

            // Encode data in JSON
            var data = System.Text.Encoding.UTF8.GetBytes(message);

            // Set up function for websocket to call when its done
            bool isDone = false;
            Action<bool> act = delegate (bool b) {
                isDone = true;
            };

            // Send asynchronously
            ws.SendAsync(message, act);

            // Wait for send to complete
            while (!isDone)
            {
                yield return null;
            }
        }

        private void HandleMessage(MessageEventArgs e)
        {
            string encryptedMsg = System.Text.Encoding.UTF8.GetString(e.RawData);

            //Debug.Log(encryptedMsg);

            if (encryptedMsg.StartsWith("***")) return;
            else if (encryptedMsg.StartsWith("PING"))
            {
                sendPing = true;
                return;
            }
            else if (encryptedMsg.StartsWith("@@@"))
            {
                // peer has arrived. do nothing for now
                // @TODO implement possibility for expert to be initiator
                return;
            } else if (encryptedMsg.StartsWith("###"))
            {
                Debug.Log("Phone left signaling server");
                return;
            }
            
            // Should encrypt these messages
            string decryptedMsg = encryptedMsg;// Encrypter.DumbDecrypt(e.RawData);
            var msg = JsonUtility.FromJson<WebSocketMessage>(decryptedMsg);

            if (msg != null)
                messages.Enqueue(msg);
            else if (AutoLogErrors)
                Debug.LogError($"Failed to deserialize JSON message : {e.Data}");
        }

        /// <summary>
        /// Internal helper which deals with new messages from the server
        /// </summary>
        /// <returns>the message</returns>
        private void HandleMessageMainThread(WebSocketMessage msg)
        {
            // depending on what type of message we get, we'll handle it differently
            // this is the "glue" that allows two peers to establish a connection.
            Debug.Log("Establishing communication with Follower");
            switch (msg.MessageType)
            {
                case WebSocketMessage.Type.Offer:
                    // Apply the offer coming from the remote peer to the local peer
                    var sdpOffer = new SdpMessage { Type = SdpMessageType.Offer, Content = msg.Data };
                    Debug.Log(_nativePeer == null);
                    PeerConnection.HandleConnectionMessageAsync(sdpOffer).ContinueWith(_ =>
                    {
                        // If the remote description was successfully applied then immediately send
                        // back an answer to the remote peer to acccept the offer.
                        _nativePeer.CreateAnswer();
                    }, TaskContinuationOptions.OnlyOnRanToCompletion | TaskContinuationOptions.RunContinuationsAsynchronously);
                    break;

                case WebSocketMessage.Type.Answer:
                    // No need to wait for completion; there is nothing interesting to do after it.
                    var sdpAnswer = new SdpMessage { Type = SdpMessageType.Answer, Content = msg.Data };
                    _ = PeerConnection.HandleConnectionMessageAsync(sdpAnswer);
                    break;

                case WebSocketMessage.Type.Ice:
                    // this "parts" protocol is defined above, in OnIceCandidateReadyToSend listener
                    _nativePeer.AddIceCandidate(msg.ToIceCandidate());
                    break;

                default:
                    Debug.Log("Unknown message: " + msg.MessageType + ": " + msg.Data);
                    break;
            }
        }

        /// <inheritdoc/>
        protected override void Update()
        {
            // Do not forget to call the base class Update(), which processes events from background
            // threads to fire the callbacks implemented in this class.
            base.Update();

            if (sendPing)
            {
                sendPing = true;
                StartCoroutine(SendMsg("PONG"));
            }

            try
            {
                WebSocketMessage msg = (WebSocketMessage)messages.Dequeue();
                if (msg != null)
                    HandleMessageMainThread(msg);
            }
            catch (InvalidOperationException)
            { }
        }

        public void OnApplicationQuit()
        {
            keepClosed = true;
            ws.Close();
        }

        private void DebugLogLong(string str)
        {
#if !UNITY_EDITOR && UNITY_ANDROID
            // On Android, logcat truncates to ~1000 characters, so split manually instead.
            const int maxLineSize = 1000;
            int totalLength = str.Length;
            int numLines = (totalLength + maxLineSize - 1) / maxLineSize;
            for (int i = 0; i < numLines; ++i)
            {
                int start = i * maxLineSize;
                int length = Math.Min(start + maxLineSize, totalLength) - start;
                Debug.Log(str.Substring(start, length));
            }
#else
            Debug.Log(str);
#endif
        }
    }
}
