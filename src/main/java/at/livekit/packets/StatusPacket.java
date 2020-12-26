package at.livekit.packets;

import org.json.JSONObject;

public class StatusPacket extends RequestPacket {
    public static int PACKETID = 11;

    private int status;
    private String message;

    public StatusPacket(int status) {
        this.status = status;
    }

    public StatusPacket(int status, String message) {
        this.status = status;
        this.message = message;
    }
    
    @Override
    public JSONObject toJson() {
        JSONObject json = super.toJson();
        json.put("status", status);
        json.put("message", message);
        json.put("packet_id", PACKETID);
        return json;
    }
}
