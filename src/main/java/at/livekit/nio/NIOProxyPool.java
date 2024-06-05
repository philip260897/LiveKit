package at.livekit.nio;

import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;

import at.livekit.livekit.LiveProxy;
import at.livekit.nio.proxy.NIOProxyClient;
import at.livekit.nio.proxy.NIOProxyListener;
import at.livekit.packets.ProxyClientKeepAlivePacket;
import at.livekit.packets.ProxyConnectPacket;
import at.livekit.plugin.Plugin;

public class NIOProxyPool<T> implements NIOProxyListener<T> {
    
    private NIOServer<T> server;

    private String serverUuid;
    private String token;
    private int limit;
    private String ip;
    private int port;
    
    private List<NIOProxyClient<T>> clients = new ArrayList<NIOProxyClient<T>>();
    private NIOProxyClient<T> currentClient;
    
    private Object lock = new Object();

    
    public NIOProxyPool(NIOServer<T> server) {
        this.server = server;
        this.limit = LiveProxy.getInstance().getProxyConnectionCount();
        this.ip = LiveProxy.getInstance().getProxyIp();
        this.port = LiveProxy.getInstance().getProxyPort();
        this.serverUuid = LiveProxy.getInstance().getUuid();
        this.token = LiveProxy.getInstance().getToken();
    }

    private volatile boolean scheduled = false;
    private int reconnects = 0;
    private int reconnectsLimit = 3;
    private int seconds = 5;
    private long lastTry = 0;

    public void createClient() {
        
        if(clients.size() < limit) {
            synchronized(lock) {
                Plugin.debug("[Proxy] currentClient == null "+(currentClient == null));
                if(currentClient == null) {
                    try {
                        if(System.currentTimeMillis() - lastTry < 3000) {
                            throw new Exception("Too many retries");
                        }

                        SocketChannel channel = SocketChannel.open(new InetSocketAddress(ip, port));
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

                        Plugin.log("Proxy connection established, waiting for connection...");
                    } catch(Exception e) {
                        if(scheduled == false && reconnects < reconnectsLimit) {
                            scheduled = true;
                            reconnects++;
                            Plugin.log("Proxy connection failed, retrying in "+((int)(Math.pow((seconds), reconnects)))+" seconds...");
                            Bukkit.getScheduler().runTaskLaterAsynchronously(Plugin.getInstance(), () -> { scheduled = false; server.requestProxyClient(); }, (int)Math.pow((seconds), reconnects)*20);
                        }
                    }
                }
            }
        } else {
            Plugin.warning("[Proxy] Proxy limit reached, waiting for proxy to disconnect...");
        }
        lastTry = System.currentTimeMillis();
    }

    public void close() {
        synchronized(clients) {
            for(NIOProxyClient<T> client : clients) {
                client.close();
            }
            clients.clear();
        }
        if(currentClient != null) {
            currentClient.close();
            currentClient = null;
        }
    }

    @Override
    public void clientConnected(NIOProxyClient<T> client) {
        Plugin.debug("[Proxy|"+client.getLocalPort()+"] Proxy client connected...handing socket over to LiveKit server");

        synchronized(clients) {
            clients.add(client);
        }
        if(currentClient == client) {
            currentClient = null;
        }
        client.setClientListener(server);
        server.listener.clientConnected(client);

        server.requestProxyClient();
    }


    @Override
    public void clientDisconnected(NIOProxyClient<T> client) {
        Plugin.debug("[Proxy|"+client.getLocalPort()+"] Proxy client disconnected...");

        synchronized(clients) {
            clients.remove(client);
        }
        if(client == currentClient) {
            currentClient = null;
        }
        synchronized(server.clients) {
            server.clients.remove(client.getKey());
        }

        server.requestProxyClient();
    }
}

