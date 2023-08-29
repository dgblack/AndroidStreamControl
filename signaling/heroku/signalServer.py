import asyncio
import websockets
import signal
import os

# Map of which peers connect to each other
peerMap = {"android":"rexpert","rexpert":"android"}

clients = {}
async def handler(websocket):
        global clients
        
        # Obtain the new client's credentials
        print("Waiting for auth-token")
        try:
                token = await asyncio.wait_for(websocket.recv(), timeout=5)
        except websockets.exceptions.ConnectionClosedOK:
                return
        except asyncio.TimeoutError:
                print("Client failed to provide auth code")
                await websocket.close()
                return
        
        # Decode the authentication if need be
        if type(token) is str:
        	auth = token        
        else:
        	auth = token.decode("utf-8")

        # Check the authentication token
        if auth != None and auth[:-7] == "AWefkbasflaLWIKWBE28357al>??AVLSIsdgauugwei37":
        		# Determine which peers are present
        		clientKey = auth[-7:]
        		peerKey = peerMap[clientKey]

        		# Check if the peer is already here and notify both to start signaling
        		if peerKey in clients:
        			await websocket.send("@@@")
        			await clients[peerKey].send("@@@")
        		else:
        			await websocket.send("###")
        else:
                print("Unauthorized connection attempt blocked")
                await websocket.close(1011, "authentication failed")
                return

        print(clientKey + " joined")
        clients[clientKey] = websocket
                
        try:
                async for message in websocket:
                        try:
                                await clients[peerKey].send(message)
                        except KeyError:
                                await clients[clientKey].send("***Peer unavailable")
                        except asyncio.exceptions.CancelledError:
                                print("Asyncio cancelled error")
        except websockets.exceptions.ConnectionClosedError:
                print("Connection of " + clientKey + " to " + peerKey + " closed.")
        finally:
                print(clientKey + " left")
                try:
                	await clients[peerKey].send('###')
                except KeyError:
                	print("No clients present")
                del clients[clientKey]

async def setUpServer():
        loop = asyncio.get_running_loop()
        stop = loop.create_future()
        loop.add_signal_handler(signal.SIGTERM, stop.set_result, None)

        port = int(os.environ.get("PORT","8001")) #also tried 3000
        async with websockets.serve(handler,"",port): # also tried 0.0.0.0
                print("Started server")
                await stop


if __name__ == "__main__":
        print("Starting WebSocket server")
        asyncio.run(setUpServer())
