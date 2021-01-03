package at.livekit.packets;

import org.json.JSONObject;

public interface IPacket {
    
    public IPacket fromJson(String json);

    public JSONObject toJson();
}
