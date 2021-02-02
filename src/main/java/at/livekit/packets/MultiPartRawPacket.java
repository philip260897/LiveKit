package at.livekit.packets;

import org.json.JSONObject;

public class MultiPartRawPacket extends RawPacket
{
    public static int PACKETID = 17;

    //private byte[] data;
    private int parts;
    private int partSize;
    private int currentPart = -1;

    public MultiPartRawPacket(byte[] data, int parts) throws Exception {
        super(data);
        //this.data = data;
        this.parts = parts;

        if(data.length%parts != 0) throw new Exception("Parts has to be able to divide size ("+data.length+")");
        this.partSize = data.length/parts;
    }

    public boolean nextPart() {
        currentPart++;
        return currentPart < parts;
    }

    @Override
    public JSONObject toJson() {
        throw new UnsupportedOperationException();
    }

    /*@Override
    public byte[] getRawPacket() {
        byte[] packet = new byte[18 + partSize];
        packet[0] = 0x54;
        packet[1] = (byte)PACKETID;
        packet[2] = (byte) (requestId>>24);
        packet[3] = (byte) (requestId>>16);
        packet[4] = (byte) (requestId>>8);
        packet[5] = (byte) (requestId>>0);
        packet[6] = (byte) ((partSize+8)>>24);
        packet[7] = (byte) ((partSize+8)>>16);
        packet[8] = (byte) ((partSize+8)>>8);
        packet[9] = (byte) ((partSize+8)>>0);
        packet[10] = (byte) (this.parts>>24);
        packet[11] = (byte) (this.parts>>16);
        packet[12] = (byte) (this.parts>>8);
        packet[13] = (byte) (this.parts>>0);
        packet[14] = (byte) (currentPart>>24);
        packet[15] = (byte) (currentPart>>16);
        packet[16] = (byte) (currentPart>>8);
        packet[17] = (byte) (currentPart>>0);

        System.arraycopy(data, currentPart * this.partSize, packet, 18, partSize);
        return packet;
    }*/
}
