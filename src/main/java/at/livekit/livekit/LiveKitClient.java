package at.livekit.livekit;

import java.io.IOException;
import java.net.Socket;

import at.livekit.server.TCPServer.RemoteClient;
import at.livekit.server.TCPServer.RemoteClientListener;

public class LiveKitClient extends RemoteClient
{
    //private PlayerAuth identity;
    private String playerUUID;
    private String liveMapWorld;

    public LiveKitClient(Socket socket, RemoteClientListener listener) throws IOException {
        super(socket, listener);
    }

    public boolean hasIdentity() {
        return playerUUID != null;
    }

    public String getPlayerUUID() {
        return playerUUID;
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

    protected void setPlayerUUID(String playerUUID) {
        this.playerUUID = playerUUID;
    }
}
