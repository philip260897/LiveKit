package at.livekit.packets;

import java.util.Base64;

import org.apache.commons.lang.NotImplementedException;
import org.json.JSONObject;

public class ChunkPacket implements IPacket{
    public static int PACKETID = 0x01;

    private int x;
    private int z;
    private String data;
    private long timestamp;

    public ChunkPacket(int x, int z, byte[] data, long timestamp) {
        this.x = x;
        this.z = z;
        this.data = Base64.getEncoder().encodeToString(data);
        this.timestamp = timestamp;
    }

    /*public static RegionPacket fromMapEntry(String key, byte[] data) {
        return new RegionPacket(Integer.parseInt(key.split("_")[0]), Integer.parseInt(key.split("_")[1]), data);
    }*/

    @Override
    public IPacket fromJson(String json) {
        throw new NotImplementedException();
    }

    @Override
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("data", data);
        json.put("x", x);
        json.put("z", z);
        json.put("timestamp", timestamp);
        json.put("packet_id", PACKETID);
        return json;
    }
}
