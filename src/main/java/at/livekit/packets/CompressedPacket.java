package at.livekit.packets;

import com.github.luben.zstd.Zstd;

import at.livekit.nio.INIOPacket;

public class CompressedPacket extends Packet {
    
    public static int PACKETID = 19;
    private INIOPacket packet;
    private byte[] data;

    public CompressedPacket(INIOPacket packet) {
        this.packet = packet;

        byte[] data = packet.data();
        if(packet.hasHeader()) {
            data = new byte[packet.data().length+packet.header().length];
            System.arraycopy(packet.header(), 0, data, 0, packet.header().length);
            System.arraycopy(packet.data(), 0, data, packet.header().length, packet.data().length);
        }

        this.data = Zstd.compress(data);
    }

    public INIOPacket getPacket() {
        return packet;
    }

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
