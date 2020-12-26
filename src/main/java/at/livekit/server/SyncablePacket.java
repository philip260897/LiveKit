package at.livekit.server;

import org.json.JSONObject;

public class SyncablePacket implements IPacket {
    public static int PACKETID = 0x03;

    private JSONObject json;

    public SyncablePacket(JSONObject json) {
        this.json = json;
        json.put("packet_id", PACKETID);
    }

    @Override
    public IPacket fromJson(String json) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public JSONObject toJson() {
        return json;
    }

}
