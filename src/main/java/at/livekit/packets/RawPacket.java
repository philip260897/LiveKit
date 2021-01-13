package at.livekit.packets;

import org.json.JSONObject;

public class RawPacket extends RequestPacket {
    
    public static int PACKETID = 16;
    protected byte[] data;

    public RawPacket(byte[] data) {
        this.data = data;
    }
    
    public int getDataSize() {
        return data.length;
    }

    @Override
    public JSONObject toJson() {
        throw new UnsupportedOperationException();
    }

    public byte[] getRawPacket() {
        byte[] packet = new byte[10+data.length];
        packet[0] = 0x54;
        packet[1] = (byte)PACKETID;
        packet[2] = (byte) (requestId>>24);
        packet[3] = (byte) (requestId>>16);
        packet[4] = (byte) (requestId>>8);
        packet[5] = (byte) (requestId>>0);
        packet[6] = (byte) (data.length>>24);
        packet[7] = (byte) (data.length>>16);
        packet[8] = (byte) (data.length>>8);
        packet[9] = (byte) (data.length>>0);
        System.arraycopy(data, 0, packet, 10, data.length);
        return packet;
    }
}
