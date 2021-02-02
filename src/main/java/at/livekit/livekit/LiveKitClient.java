package at.livekit.livekit;

import java.io.IOException;
import java.net.Socket;

import at.livekit.livekit.TCPServer.RemoteClient;
import at.livekit.livekit.TCPServer.RemoteClientListener;

public class LiveKitClient extends RemoteClient
{
    //private PlayerAuth identity;
    //private String playerUUID;
    private Identity identity;
    private String liveMapWorld;

    public LiveKitClient(Socket socket, RemoteClientListener listener) throws IOException {
        super(socket, listener);
    }

    public boolean hasIdentity() {
        return  identity != null;
    }

    public Identity getIdentity() {
        return identity;
    }

    public String getSubscribedLiveMap() {
        return liveMapWorld;
    }

    protected void setLiveMapWorld(String world) {
        this.liveMapWorld = world;
    }

    protected void dispose() {
        this.close();
    }

    protected void setIdentity(String uuid) {
        //this.identity = new Identity(uuid);
    }

    protected void setAnonymous() {
       // this.identity = new Identity(null);
    }
}
