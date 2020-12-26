package at.livekit.server;

import org.json.JSONObject;

public class SyncableRemovedPacket implements IPacket {
    public static int PACKETID = 0x04;

    private String uuid;

    public SyncableRemovedPacket(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public IPacket fromJson(String json) { return null; }

    @Override
    public JSONObject toJson() { 
        JSONObject json = new JSONObject();
        json.put("uuid", uuid);
        json.put("packet_id", PACKETID);
        return json;
     }

}
