package at.livekit.packets;

import org.json.JSONObject;

public class ServerSettingsPacket extends Packet {
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
