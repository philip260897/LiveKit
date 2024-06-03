package at.livekit.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import at.livekit.livekit.LiveProxy;
import at.livekit.nio.NIOClient.NIOClientEvent;
import at.livekit.plugin.Config;
import at.livekit.plugin.Plugin;

public class NIOServer<T> implements Runnable, NIOClientEvent<T> {
    
    private int port;
    public Map<SelectionKey, NIOClient<T>> clients;

    protected Selector selector;
    private ServerSocketChannel server;
    private boolean abort = false;

    private Thread thread;
    protected NIOServerEvent<T> listener;

    private NIOProxyPool<T> proxyPool;
    
    public NIOServer(int port) {
        this.port = port;
        this.clients = new HashMap<SelectionKey, NIOClient<T>>();

        
    }

    public void setServerListener(NIOServerEvent<T> listener) {
        this.listener = listener;
    }

    public void start() throws Exception {
        if(selector != null || server != null) throw new Exception("Server already started!");
        selector = Selector.open();
        server = ServerSocketChannel.open();
        server.bind(new InetSocketAddress("0.0.0.0", port));
        server.configureBlocking(false);
        server.register(selector, SelectionKey.OP_ACCEPT);

        thread = new Thread(this);
        thread.setName("LiveKit server");
        thread.start();
    }

    public void stop() {
        abort = true;

        synchronized(clients) {
            for(NIOClient<T> client : clients.values())client.close();
            clients.clear();
        }



        try{
            selector.close();
        }catch(Exception ex){ex.printStackTrace();}
    }

    public void send(Map<T, ? extends INIOPacket> data) {
        if(data.size() == 0) return;

        synchronized(clients) {
            for(NIOClient<T> client : clients.values()) {
                if(client.getIdentifier() != null) {
                    if(data.containsKey(client.getIdentifier())) {
                        client.queueData(data.get(client.getIdentifier()));
                    }
                }
            }
        }
        selector.wakeup();
    }

    public void send(T identifier, List<? extends INIOPacket> data) {
        if(data.size() == 0) return;

        NIOClient<T> client = get(identifier);
        if(client != null) {
            client.queueAll(data);
            selector.wakeup();
        }
    }

    public void send(T identifier, INIOPacket data) {
        NIOClient<T> client = get(identifier);
        if(client != null) {
            send(client, data);
        }
    }

    public void send(NIOClient<T> client, INIOPacket data) {
        if(data == null) return;
        client.queueData(data);
        selector.wakeup();
    }

    public NIOClient<T> get(T identifier) {
        synchronized(clients) {
            for(NIOClient<T> client : clients.values()) {
                if(identifier.equals(client.getIdentifier())) {
                    return client;
                }
            }
        }
        return null;
    }

    public List<T> getIdentifiers() {
        synchronized(clients) {
            return clients.values().stream().filter(c->c.getIdentifier() != null).map(c->c.getIdentifier()).collect(Collectors.toList());
        }
    }

    public List<T> getIdleIdentifiers() {
        synchronized(clients) {
            return clients.values().stream().filter(c->c.getIdentifier() != null && c.outputQueue.size() <= 5).map(c->c.getIdentifier()).collect(Collectors.toList());
        }
    }

    /*public void enableProxy(String uuid, String token, int limit, String ip, int port) { 
        try {
            this.proxyPool = new NIOProxyPool<T>(this, uuid, token, limit, ip, port);
            this.proxyPool.createClient();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }*/

    private volatile boolean proxyClientRequest = false;
    public void requestProxyClient() {
        proxyClientRequest = true;
        selector.wakeup();
    }

    public void setupProxyPool() {
        this.proxyPool = new NIOProxyPool<T>(this);
        this.requestProxyClient();
    }

    @Override
    public void run() {
        try
        {
            Plugin.log("Server listening on "+port+" for incoming connections");
            boolean writable = false;


            while(!abort) {

                if(!writable && !proxyClientRequest) {
                    selector.select();
                    if(!selector.isOpen()) continue;
                } else {
                    Thread.sleep(20);
                    selector.selectNow();
                }

                if(proxyClientRequest) {
                    proxyClientRequest = false;
                    if(proxyPool != null) {
                        proxyPool.createClient();
                    }
                }

                //System.out.println("Selector working");

                Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
                while(iter.hasNext()) {
                    SelectionKey key = iter.next();
                    
                    
                    if(key.isAcceptable()) {
                        acceptIncoming();
                    }

                    if(key.isReadable()) {
                        NIOClient<T> client = clients.get(key);
                        if(client != null) client.read();
                        else Plugin.debug("ERROR!!!!!! Client not found for key: "+key);
                    }

                    iter.remove();
                }
                

                writable = false;
                synchronized(clients) {
                    Iterator<NIOClient<T>> iterator = clients.values().iterator();
                    while(iterator.hasNext()) {
                        NIOClient<T> client = iterator.next();
                        try{
                            if(client.write()) writable = true;
                        }catch(IOException ex){
                            client.close();
                            iterator.remove();
                            //ex.printStackTrace();
                        }
                    }
                }
                //printStats();
            }
        }
        catch(Exception ex)
        {
            ex.printStackTrace();
        }

        if(proxyPool != null) {
            proxyPool.close();
        }

        try{
            server.close();
        }catch(Exception ex){ex.printStackTrace();}

        Plugin.log("Server shutdown");
    }

    private long last = 0;
    private void printStats() {
        int max = 0;
        int min = 100000;

        int size = 0;
        int clientss = 1;
        
        synchronized(clients) {
            for(NIOClient<T> client : clients.values()) {
                synchronized(client.outputQueue) {
                    if(client.outputQueue.size() > max) max = client.outputQueue.size();
                    if(client.outputQueue.size() < min) min = client.outputQueue.size();
                    size += client.outputQueue.size();
                }
            }
            if( clients.size() > 1) clientss = clients.size();
            if(System.currentTimeMillis()-last > 1000) {
                last = System.currentTimeMillis();
                System.out.println("Avg: "+ (size/clientss)+" max: "+max+" min: "+min);
            }
        }
    }

    private void acceptIncoming() throws Exception {
        SocketChannel client = server.accept();
        if(client != null) {
            client.configureBlocking(false);
            SelectionKey key = client.register(selector, SelectionKey.OP_READ);
            NIOClient<T> nio = new NIOClient<T>(key, client);
            nio.setClientListener(this);

            //Plugin.log("===Accepting Incoming Client: "+client.getRemoteAddress().toString()+"===");

            synchronized(clients) {
                clients.put(key, nio);
            }

            if(listener != null) listener.clientConnected(nio);
        }
    }

    @Override
    public void messageReceived(NIOClient<T> client, String message) {
       if(listener != null) listener.clientMessageReceived(client, message);
    }

    @Override
    public void connectionClosed(NIOClient<T> client) {
        synchronized(clients) {
            clients.remove(client.getKey());
        }
        if(listener != null) listener.clientDisconnected(client);
    }

    public interface NIOServerEvent<T> {
        public void clientConnected(NIOClient<T> client);
        public void clientDisconnected(NIOClient<T> client);
        public void clientMessageReceived(NIOClient<T> client, String message);
    }
}