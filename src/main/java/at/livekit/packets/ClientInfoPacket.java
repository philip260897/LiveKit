package at.livekit.packets;

import org.json.JSONObject;

public class ClientInfoPacket extends Packet {
    
    public static final int PACKETID = 20;
    private int appVersion;

    public ClientInfoPacket() {
        super(false);
    }

    public ClientInfoPacket(int appVersion) {
        super(false);
        this.appVersion = appVersion;
    }

    public int getAppVersion() {
        return appVersion;
    }

    @Override
    public IPacket fromJson(String json) {
        JSONObject o = new JSONObject(json);
        appVersion = o.getInt("app_version");
        return this;
    }

    @Override
    public JSONObject toJson() {
        JSONObject json = super.toJson();
        json.put("app_version", appVersion);
        json.put("packet_id", PACKETID);
        return json;
    }
}
