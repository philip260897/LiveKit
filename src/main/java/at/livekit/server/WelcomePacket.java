package at.livekit.server;

import java.util.List;

import org.json.JSONObject;

public class WelcomePacket implements IPacket {
    public static int PACKETID = 0x06;

    private int pluginVersion;
    private int renderVersion;
    private int tickrate;
    private String[] maps;

    public WelcomePacket(int pluginVersion, int renderVersion, int tickrate, String[] maps) {
        this.pluginVersion = pluginVersion;
        this.renderVersion = renderVersion;
        this.maps = maps;
        this.tickrate = tickrate;
    }

    @Override
    public IPacket fromJson(String json) {
        return null;
    }

    @Override
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("pluginVersion", pluginVersion);
        json.put("renderVersion", renderVersion);
        json.put("maps", maps);
        json.put("packet_id", PACKETID);
        json.put("tickrate", tickrate);
        return json;
    }

}
