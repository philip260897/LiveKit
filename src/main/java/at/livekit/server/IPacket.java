package at.livekit.server;

import org.json.JSONObject;

public interface IPacket {
    
    public IPacket fromJson(String json);

    public JSONObject toJson();
}
