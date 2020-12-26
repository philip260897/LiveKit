package at.livekit.packets;

import org.json.JSONObject;

import at.livekit.server.IPacket;

public class ServerSettingsPacket implements IPacket {
    public static int PACKETID = 12;

    private JSONObject settings;
    public ServerSettingsPacket(JSONObject settings) {
        this.settings = settings;
    }

    @Override
    public IPacket fromJson(String json) {return null;}

    @Override
    public JSONObject toJson() {
        settings.put("packet_id", PACKETID);
        return settings;
    }
}
