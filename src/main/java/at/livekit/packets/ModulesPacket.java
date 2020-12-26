package at.livekit.packets;

import org.json.JSONArray;
import org.json.JSONObject;

import at.livekit.server.IPacket;

public class ModulesPacket implements IPacket
{
    public static int PACKETID = 14;

    private JSONArray modules;
    public ModulesPacket(JSONArray modules) {
        this.modules = modules;
    }

    @Override
    public IPacket fromJson(String json) {return null;}

    @Override
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("modules", modules);
        json.put("packet_id", PACKETID);
        return json;
    }
}
