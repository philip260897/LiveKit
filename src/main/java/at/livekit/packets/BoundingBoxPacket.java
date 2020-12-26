package at.livekit.packets;

import org.json.JSONObject;

import at.livekit.main.LiveMap.BoundingBox;
import at.livekit.server.IPacket;

public class BoundingBoxPacket implements IPacket {

    public static int PACKETID = 13;

    private BoundingBox boundingBox;

    public BoundingBoxPacket(BoundingBox boundingBox) {
        this.boundingBox = boundingBox;
    }

    @Override
    public IPacket fromJson(String json) {return null;}

    @Override
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("minX", boundingBox.minX);
        json.put("maxX", boundingBox.maxX);
        json.put("minZ", boundingBox.minZ);
        json.put("maxZ", boundingBox.maxZ);
        json.put("packet_id", PACKETID);
        return json;
    }
    
}
