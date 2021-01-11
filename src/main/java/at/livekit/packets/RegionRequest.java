package at.livekit.packets;

import org.json.JSONObject;

public class RegionRequest extends RequestPacket
{   
    public static int PACKETID = 15;

    public int x;
    public int z;
    public String world;
    
    @Override
    public IPacket fromJson(String json) {
        super.fromJson(json);
        JSONObject o = new JSONObject(json);
        this.x = o.has("x")&&!o.isNull("x") ? o.getInt("x") : null;
        this.z = o.has("z")&&!o.isNull("z") ? o.getInt("z") : null;
        this.world = o.has("world")&&!o.isNull("world") ? o.getString("world") : null;
        return this;
    }
}
