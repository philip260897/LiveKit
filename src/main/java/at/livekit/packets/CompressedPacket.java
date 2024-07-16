package at.livekit.packets;

import com.github.luben.zstd.Zstd;

import at.livekit.nio.INIOPacket;

public class CompressedPacket extends Packet {
    
    public static int PACKETID = 19;
   // private INIOPacket packet;
    private byte[] data;

    public CompressedPacket(byte[] header, byte[] body) {
        super(false);
        //this.packet = packet;

        byte[] data = body;
        if(header != null) {
            data = new byte[body.length+header.length];
            System.arraycopy(header, 0, data, 0, header.length);
            System.arraycopy(body, 0, data, header.length, body.length);
        }

        this.data = Zstd.compress(data);
    }

    /*public INIOPacket getPacket() {
        return packet;
    }*/

    @Override
    public byte[] data() {
        return data;
    }

    @Override
    public byte[] header() {
        byte[] packet = new byte[6];
        packet[0] = 0x55;
        packet[1] = (byte)PACKETID;
        packet[2] = (byte) (data.length>>24);
        packet[3] = (byte) (data.length>>16);
        packet[4] = (byte) (data.length>>8);
        packet[5] = (byte) (data.length>>0);
        return packet;
    }

    @Override
    public boolean hasHeader() {
        return true;
    }
}
