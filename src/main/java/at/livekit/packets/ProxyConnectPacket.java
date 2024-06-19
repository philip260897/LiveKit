package at.livekit.packets;

import org.json.JSONObject;

public class ProxyConnectPacket extends Packet {
    public static int PACKETID = 1000;
    private String serverUUID;
    private String token;
    public ProxyConnectPacket(String serverUUID, String token) {
        this.serverUUID = serverUUID;
        this.token = token;
    }
    @Override
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("packet_id", PACKETID);
        json.put("uuid", serverUUID.toString());
        json.put("token", token);
        return json;
    }

    @Override
    public Packet fromJson(String json) {
        JSONObject o = new JSONObject(json);
        serverUUID = o.getString("uuid");
        token = o.getString("token");
        return this;
    }
}
