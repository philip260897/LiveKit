package at.livekit.packets;

import org.json.JSONObject;

public class ProxyClientConnectedPacket extends Packet {
    public static int PACKETID = 1001;
    @Override
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("packet_id", PACKETID);
        return json;
    }

    @Override
    public Packet fromJson(String json) {
        JSONObject o = new JSONObject(json);
        return this;
    }
}