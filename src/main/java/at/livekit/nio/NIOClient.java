package at.livekit.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.Buffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import at.livekit.packets.ClientInfoPacket;
import at.livekit.packets.CompressedPacket;
import at.livekit.packets.ProxyClientKeepAlivePacket;
import at.livekit.packets.ProxyConnectPacket;
import at.livekit.plugin.Plugin;


public class NIOClient<T> {

    protected NIOClientEvent<T> listener;
    private SocketChannel channel;
    private SelectionKey key;
    private ByteBuffer buffer;

    private StringBuilder builder;
    private int parseOffset = 0;

    protected List<byte[]> outputQueue;
    private ByteBuffer outputBuffer;
    private T identifier;

    private long lastKeepAliveSent = 0;
    private long lastKeepAliveReceived = 0;

    private int appVersion = 0;

    public NIOClient(SelectionKey key, SocketChannel channel) {
        this.key = key;
        this.channel = channel;
        
        this.buffer = ByteBuffer.allocate(256);
        this.builder = new StringBuilder();

        this.outputQueue = new ArrayList<byte[]>();
        
    }

    private boolean supportsCompression() {
        return appVersion >= 58;
    }

    public int getLocalPort() {
        return channel.socket().getLocalPort();
    }

    public int getRemotePort() {
        return channel.socket().getPort();
    }

    public String getRemoteAddress() {
        return channel.socket().getInetAddress().getHostAddress();
    }

    public T getIdentifier() {
        return identifier;
    }

    public void setIdentifier(T identifier) {
        this.identifier = identifier;
    }

    public void setClientListener(NIOClientEvent<T> listener) {
        this.listener = listener;
    }

    public SelectionKey getKey() {
        return key;
    }

    protected void queueData(INIOPacket packet) {
        byte[] header = packet.hasHeader() ? packet.header() : null;
        byte[] data = packet.data();

        if(supportsCompression() == true && packet.supportsCompression() == true && data.length >= 2048) {
            packet = new CompressedPacket(header, data);
            header = packet.header();
            data = packet.data();
        }

        synchronized(outputQueue) {
            if(header != null) outputQueue.add(header);
            outputQueue.add(data);
        }
    }

    protected void queueAll(List<? extends INIOPacket> packets) {
        for(INIOPacket packet : packets) {
            queueData(packet);
        }
    }

    protected boolean canKeepAlive() {
        if(System.currentTimeMillis() - lastKeepAliveSent > (1000 * 30)) {
            lastKeepAliveSent = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    protected void read() {
        if(!channel.isConnected()) return;

        int read = 0;
        try{
            while((read = channel.read(buffer)) > 0) {
                builder.append(new String(buffer.array(), 0, read, "UTF-8"));
                ((Buffer)buffer).clear();

                parsePackets();
            }
        }catch(ClosedChannelException ex) {
            read = -1;
        }catch(IOException ex) {
            read = -1;
        }catch(JSONException ex) {
            read = -1;
        }catch(Exception ex) {
            ex.printStackTrace();
            /*try{
                Plugin.log("["+channel.getRemoteAddress().toString()+"] Disconnecting client!");
            }catch(Exception exx){exx.printStackTrace();}*/
        }

        if(read == -1 || builder.length() > 1024*1024) {
            close();
            if(listener != null) listener.connectionClosed(this);
        }
    }

    public boolean isConnected() {
        return channel.isConnected();
    }

    protected boolean write() throws IOException {
        if(!channel.isConnected()) return false;

        if(outputBuffer == null) {
            synchronized(outputQueue) {
                if(outputQueue.size() > 0) {
                    outputBuffer = ByteBuffer.wrap(outputQueue.remove(0));
                } else {
                    return false;
                }
            }
        }

        //try{
        channel.write(outputBuffer);
        /*}catch(IOException ex){
            //ex.printStackTrace();
            close();
            if(listener != null) listener.connectionClosed(this);
            return false;
        }*/

        if(outputBuffer.remaining() == 0) {
            outputBuffer = null;
            synchronized(outputQueue) {
                if(outputQueue.size() > 0 ) return true;
                return false;
            }
        }

        return true;
    }

    public void close() {
        try{ 
            key.cancel();
            channel.close();
        }catch(Exception ex){ex.printStackTrace();}
    }

    private void parsePackets() {
        while(parseOffset < builder.length()) {
            if(builder.charAt(parseOffset) == '\n') {
                boolean surpress = false;
                try {
                    JSONObject json = new JSONObject(builder.substring(0, parseOffset));
                    if(json.getInt("packet_id") == 1002) {
                        Plugin.debug("[Proxy|"+getLocalPort()+"] Keep alive received from "+channel.getRemoteAddress().toString());
                        lastKeepAliveReceived = System.currentTimeMillis();
                        surpress = true;
                    }
                    if(json.getInt("packet_id") == ClientInfoPacket.PACKETID) {
                        Plugin.debug("[Proxy|"+getLocalPort()+"] Client info received from "+channel.getRemoteAddress().toString());
                        ClientInfoPacket packet = (ClientInfoPacket) new ClientInfoPacket().fromJson(builder.substring(0, parseOffset));
                        appVersion = packet.getAppVersion();
                        surpress = true;
                    }
                }catch(Exception ex) {
                    ex.printStackTrace();
                }
                
                
                if(!surpress && listener != null) {
                     listener.messageReceived(this, builder.substring(0, parseOffset));
                }

                builder.delete(0, parseOffset+1);
                parseOffset = 0;
                continue;
            }
            parseOffset++;
        }
    }

    public interface NIOClientEvent<T> {
        public void messageReceived(NIOClient<T> client, String message);
        public void connectionClosed(NIOClient<T> client);
    }
}