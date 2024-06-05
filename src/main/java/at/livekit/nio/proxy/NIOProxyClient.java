package at.livekit.nio.proxy;

import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import org.json.JSONObject;

import at.livekit.nio.NIOClient;
import at.livekit.packets.ProxyClientConnectedPacket;
import at.livekit.packets.ProxyConnectPacket;
import at.livekit.plugin.Plugin;
import at.livekit.nio.NIOClient.NIOClientEvent;

public class NIOProxyClient<T> extends NIOClient<T> implements NIOClientEvent<T> {

    private NIOProxyListener<T> proxyListener;
    private NIOClientEvent<T> clientListener;
    private boolean proxyConnected = false;


    public NIOProxyClient(SelectionKey key, SocketChannel channel) {
        super(key, channel);
        super.listener = this;
    }

    public void setProxyListener(NIOProxyListener<T> proxyListener) {
        this.proxyListener = proxyListener;
    }

    @Override
    public void setClientListener(NIOClientEvent<T> listener) {
        this.clientListener = listener;
    }

    @Override
    public void messageReceived(NIOClient<T> client, String message) {
        Plugin.debug("[Proxy|"+client.getLocalPort()+"] Message received: "+message);
        if(proxyConnected == false) {
            JSONObject json = new JSONObject(message);
            int packetId = json.getInt("packet_id");

            if(packetId == ProxyClientConnectedPacket.PACKETID) {
                proxyConnected = true;
                if(proxyListener != null) proxyListener.clientConnected(this);
                Plugin.debug("[Proxy|"+client.getLocalPort()+"] Proxy client established connection");
            } else {
                Plugin.debug("[Proxy|"+client.getLocalPort()+"] Invalid message received: "+message);
            }
        } else {
            if(clientListener != null) {
                clientListener.messageReceived(client, message);
            }
        }
    }

    @Override
    public void connectionClosed(NIOClient<T> client) {
        if(clientListener != null) clientListener.connectionClosed(client);
        if(proxyListener != null) proxyListener.clientDisconnected(this);
    }

    public boolean getProxyConnected() {
        return proxyConnected;
    }
}