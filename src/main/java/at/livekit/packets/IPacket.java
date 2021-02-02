package at.livekit.packets;

import org.json.JSONObject;

import at.livekit.nio.INIOPacket;

public interface IPacket extends INIOPacket {
    
    public IPacket fromJson(String json);

    public JSONObject toJson();
}


