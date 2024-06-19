package at.livekit.packets;

import org.json.JSONObject;

public class ProxyClientKeepAlivePacket extends Packet {
    public static int PACKETID = 1002;
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
