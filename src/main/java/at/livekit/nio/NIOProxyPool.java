package at.livekit.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.net.SocketOptions;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;

import at.livekit.nio.proxy.NIOProxyClient;
import at.livekit.nio.proxy.NIOProxyListener;
import at.livekit.packets.ProxyConnectPacket;
import at.livekit.plugin.Plugin;

public class NIOProxyPool<T> implements NIOProxyListener<T> {
    
    private NIOServer<T> server;

    private String serverUuid;
    private String token;
    private int limit;
    
    private List<NIOProxyClient<T>> clients = new ArrayList<NIOProxyClient<T>>();
    private NIOProxyClient<T> currentClient;
    
    private Object lock = new Object();

    public NIOProxyPool(NIOServer<T> server, String serverUuid, String token, int limit) {
        this.serverUuid = serverUuid;
        this.token = token;
        this.server = server;
        this.limit = limit;
    }

    private boolean scheduled = false;
    private int reconnects = 0;
    private int reconnectsLimit = 3;
    private int seconds = 5;

    public void createClient() {
        
        if(clients.size() < limit) {
            synchronized(lock) {
                Plugin.debug("[Proxy] currentClient == null "+(currentClient == null));
                if(currentClient == null) {
                    try {
                        SocketChannel channel = SocketChannel.open(new InetSocketAddress("localhost", 8080));
                        channel.configureBlocking(false);
                        channel.socket().setKeepAlive(true);
                        SelectionKey key = channel.register(server.selector, SelectionKey.OP_READ);
                        currentClient = new NIOProxyClient<T>(key, channel);
                        currentClient.setProxyListener(this);
                        
                        synchronized(server.clients) {
                            server.clients.put(key, currentClient);
                        }

                        server.send(currentClient, new ProxyConnectPacket(serverUuid, token));

                        reconnects = 0;

                        Plugin.debug("[Proxy] Proxy connected, waiting for client...");
                    } catch(IOException e) {
                        if(scheduled == false && reconnects < reconnectsLimit) {
                            scheduled = true;
                            reconnects++;
                            Plugin.debug("[Proxy] Proxy connection failed, retrying in "+((Math.pow((seconds), reconnects)))+" seconds...");
                            Bukkit.getScheduler().runTaskLaterAsynchronously(Plugin.getInstance(), () -> { Plugin.debug("Running scheduler");  scheduled = false; createClient(); }, (int)Math.pow((seconds), reconnects)*20);
                        }
                    }
                }
            }
        } else {
            Plugin.debug("[Proxy] Proxy limit reached, waiting for proxy to disconnect...");
        }
    }

    @Override
    public void clientConnected(NIOProxyClient<T> client) {
        Plugin.debug("[Proxy] Proxy client connected...handing socket over to LiveKit server");

        synchronized(clients) {
            clients.add(client);
        }
        if(currentClient == client) {
            currentClient = null;
        }
        client.setClientListener(server);
        server.listener.clientConnected(client);

        try{ createClient(); } catch(Exception e) { e.printStackTrace(); }
    }


    @Override
    public void clientDisconnected(NIOProxyClient<T> client) {
        synchronized(clients) {
            clients.remove(client);
        }
        if(client == currentClient) {
            currentClient = null;
        }

        try{ createClient(); } catch(Exception e) { e.printStackTrace(); }
    }

    /*@Override
    public void messageReceived(NIOClient<T> client, String message) {
        JSONObject json = new JSONObject(message);
        int packetId = json.getInt("packet_id");

        if(packetId == ProxyClientConnectedPacket.PACKETID) {
            synchronized(currentClient) {
                currentClient = null;
            }

            client.setClientListener(server);
            server.listener.clientConnected(client);

            System.out.println("[Proxy] Proxy client connected...handing socket over to LiveKit server");

            try{ createClient(); } catch(Exception e) { e.printStackTrace(); }
        } else {
            System.out.println("[Proxy] Invalid message received: "+message);
        }

    }


    @Override
    public void connectionClosed(NIOClient<T> client) {
        synchronized(lock) {
            currentClient = null;
        }

        synchronized(server.clients) {
            server.clients.remove(client.getKey());
        }

        System.out.println("[Proxy] Proxy client disconnected...creating new instance");

        try{ createClient(); } catch(Exception e) { e.printStackTrace(); }
    }*/

 








}

