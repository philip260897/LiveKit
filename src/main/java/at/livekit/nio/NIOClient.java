package at.livekit.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.json.JSONException;

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

    public NIOClient(SelectionKey key, SocketChannel channel) {
        this.key = key;
        this.channel = channel;
        
        this.buffer = ByteBuffer.allocate(256);
        this.builder = new StringBuilder();

        this.outputQueue = new ArrayList<byte[]>();
        
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
        synchronized(outputQueue) {
            if(packet.hasHeader()) outputQueue.add(packet.header());
            outputQueue.add(packet.data());
        }
    }

    protected void queueAll(List<? extends INIOPacket> packets) {
        synchronized(outputQueue) {
            for(INIOPacket packet : packets) {
                if(packet.hasHeader()) outputQueue.add(packet.header());
                outputQueue.add(packet.data());
            }
            //outputQueue.addAll(packets.stream().map(p->p.data()).collect(Collectors.toList()));
        }
    }

    protected void read() {
        if(!channel.isConnected()) return;

        int read = 0;
        try{
            while((read = channel.read(buffer)) > 0) {
                //Plugin.log("["+channel.getRemoteAddress().toString()+"] "+(new String(buffer.array(), 0, read, "UTF-8")));
                builder.append(new String(buffer.array(), 0, read, "UTF-8"));
                buffer.clear();

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
            channel.close();
        }catch(Exception ex){ex.printStackTrace();}
    }

    private void parsePackets() {
        while(parseOffset < builder.length()) {
            if(builder.charAt(parseOffset) == '\n') {
                if(listener != null) listener.messageReceived(this, builder.substring(0, parseOffset));

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