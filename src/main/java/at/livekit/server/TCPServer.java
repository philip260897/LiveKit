package at.livekit.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.json.JSONObject;

import at.livekit.livekit.Identity;
import at.livekit.livekit.LiveKitClient;
import at.livekit.packets.AuthorizationPacket;
import at.livekit.packets.LiveMapSubscriptionPacket;
import at.livekit.packets.RequestPacket;

public class TCPServer implements Runnable {
    private ServerSocket socket;
    private int port;
    private Thread thread;
    private boolean abort = false;


    private ServerListener listener;
    private List<LiveKitClient> clients = new ArrayList<LiveKitClient>();

    public TCPServer(int port) {
        this.port = port;
    }

    public void setServerListener(ServerListener listener) {
        this.listener = listener;
    }

    public void open() {
        thread = new Thread(this);
        thread.start();
    }

    public List<LiveKitClient> getClients() {
        return clients;
    }

    public void close() {
        System.out.println("Shutting down LiveKit Server");
        abort = true;

        try{
            synchronized(clients) {
                for(RemoteClient client : clients) client.close();
            }
        }catch(Exception ex){ex.printStackTrace();}

        try{
            socket.close();
        }catch(IOException ex){}
    }

    public List<Identity> getConnectedUUIDs() {
        List<Identity> uuids = new ArrayList<Identity>();
        synchronized(clients) {
            for(LiveKitClient c : clients) {
                if(c.hasIdentity()) {
                    uuids.add(c.getIdentity());
                }
            }
        }
        return uuids;
    }

    /*public void broadcast(IPacket packet) {
        System.out.println("[Server] Broadcasting to "+clients.size()+" clients!");
        synchronized(clients) {
            for(RemoteClient client : clients) {
                client.sendPacket(packet);
            }
        }
    }*/
    public void broadcast(Map<Identity,IPacket> packets) {
        synchronized(clients) {
            for(LiveKitClient client : clients) {
                if(client.hasIdentity()) {
                    client.sendPacket(packets.get(client.getIdentity()));
                }
            }
        }
    }

    public void broadcastForWorld(String world, IPacket packet) {
        int count = 0;
        synchronized(clients) {
            for(LiveKitClient client : clients) {
                if(client.getSubscribedLiveMap() != null && client.getSubscribedLiveMap().equals(world)) {
                    client.sendPacket(packet);
                    count++;
                }
            }
        }
        System.out.println("Broadcasted to "+count+" clients!");
    }

    @Override
    public void run() {
        try{
            System.out.println("[Server] Starting server...");
            socket = new ServerSocket(port);
            while(!abort) {
                try{
                    System.out.println("[Server] Listening for incoming connection on port "+port);
                    Socket cs = socket.accept();
                    try{
                        LiveKitClient client = new LiveKitClient(cs, new RemoteClientListener(){

                            @Override
                            public void onDisconnect(RemoteClient sender) {
                                listener.onDisconnect( (LiveKitClient)sender);
                                
                                synchronized(clients) {
                                    clients.remove(sender);
                                }
                            }

                            @Override
                            public void onDataReceived(RemoteClient sender, String data) {
                                System.out.println("RECEIVED: "+data);
                                if(listener != null) {
                                    JSONObject json = new JSONObject(data);
                                    int packetId = json.getInt("packet_id");
                                    int requestId = json.getInt("request_id");
                                    
                                    RequestPacket response = null;
                                    if(packetId == AuthorizationPacket.PACKETID) {
                                        response = listener.onPacketReceived((LiveKitClient)sender, (AuthorizationPacket) new AuthorizationPacket().fromJson(data)); 
                                    }
                                    if(packetId == LiveMapSubscriptionPacket.PACKETID) {
                                        response = listener.onPacketReceived((LiveKitClient)sender, (LiveMapSubscriptionPacket) new LiveMapSubscriptionPacket().fromJson(data));    
                                    }

                                    if(response != null) sender.sendPacket(response.setRequestId(requestId));
                                }
                            }

                        });
                        listener.onConnect(client);

                        synchronized(clients) {
                            clients.add(client);
                        }
                    }
                    catch(IOException ex){
                        ex.printStackTrace();
                    }

                }catch(IOException ex){/* Ignore socket.accept() interrupted */}
            }
            socket.close();
        }catch(Exception ex){
            ex.printStackTrace();
        }
    }


    public static class RemoteClient implements Runnable 
    {
        private Socket socket;
        private PrintWriter writer;
        private BufferedReader reader;

        private boolean abort;
        private Thread thread;
        private RemoteClientListener listener;

        private LiveMapSubscriptionPacket settings;

        public RemoteClient(Socket socket, RemoteClientListener listener) throws IOException {
            this.socket = socket;
            this.writer = new PrintWriter(socket.getOutputStream());
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            this.listener = listener;

            this.thread = new Thread(this);
            this.thread.start();
        }

        public void close() {
            abort = true;
            thread.interrupt();

            try{this.writer.close();}catch(Exception ex){}
            try{this.reader.close();}catch(Exception ex){}
            try{this.socket.close();}catch(Exception ex){}

            thread.stop();
        }

        public void sendPackets(List<IPacket> packets) {
            for(IPacket p : packets)
                sendPacket(p);
        }

        public void sendPacket(IPacket packet) {
            if(packet == null) return;
            
            writer.println(packet.toJson().toString());
            writer.flush();
        }

        @Override
        public void run() {
            System.out.println("[LiveKitClient] Connection opened. Listening");

            while(!abort) {
                try{
                    String line = reader.readLine();
                    if(listener!=null && line != null) listener.onDataReceived(this, line);
                    if(line == null) abort = true;
                }catch(IOException ex){abort = true;}
            }

            System.out.println("[LiveKitClient] Connection closed");

            if(listener != null) listener.onDisconnect(this);
        }
    }

    public static interface RemoteClientListener {
        public void onDisconnect(RemoteClient sender);
        public void onDataReceived(RemoteClient sender, String data);
    }

    public static interface ServerListener {
        public void onConnect(LiveKitClient client);
        public void onDisconnect(LiveKitClient client);
        public RequestPacket onPacketReceived(LiveKitClient client, RequestPacket packet);
        
        //public void onLiveMapSettings(LiveKitClient client, String world);
    }
}
