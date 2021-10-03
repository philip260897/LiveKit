package at.livekit.packets;

import org.json.JSONObject;

public class IdentityPacket extends RequestPacket {

    public static int PACKETID = 13;

    private String name;
    private String uuid;
    private String head;
    private String authorization;
    private String serverToken;

    public IdentityPacket(String uuid, String name, String head, String authorization, String serverToken) {
        this.name = name;
        this.uuid = uuid;
        this.head = head;
        this.authorization = authorization;
        this.serverToken = serverToken;
    }

    @Override
    public IPacket fromJson(String json) {return null;}

    @Override
    public JSONObject toJson() {
        JSONObject json = super.toJson();
        json.put("name", name);
        json.put("uuid", uuid);
        json.put("head", head);
        json.put("authorization", authorization);
        json.put("server_token", serverToken);
        json.put("packet_id", PACKETID);
        return json;
    }
}
