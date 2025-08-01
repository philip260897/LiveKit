package at.livekit.packets;

import org.json.JSONObject;

public class StatusPacket extends RequestPacket {
    public static int PACKETID = 11;

    private int status;
    private String message;
    private JSONObject data;

    public StatusPacket(int status) {
        this.status = status;
    }

    public StatusPacket(int status, String message) {
        this.status = status;
        this.message = message;
    }

    public StatusPacket(int status, JSONObject data) {
        this.status = status;
        this.data = data;
    }
    
    @Override
    public JSONObject toJson() {
        JSONObject json = super.toJson();
        json.put("status", status);
        json.put("message", message);
        json.put("packet_id", PACKETID);
        if(data != null) json.put("data", data);
        return json;
    }
}
