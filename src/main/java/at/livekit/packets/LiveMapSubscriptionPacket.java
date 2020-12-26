package at.livekit.packets;

import org.json.JSONObject;
import at.livekit.server.IPacket;

public class LiveMapSubscriptionPacket extends RequestPacket {
    public static int PACKETID = 0x07;

    public String map;

    public LiveMapSubscriptionPacket(int appVersion, String map) {
        this.map = map;
    }

    public LiveMapSubscriptionPacket(){}

    @Override
    public IPacket fromJson(String json) {
        super.fromJson(json);
        JSONObject j = new JSONObject(json);
        map = j.getString("map");
        return this;
    }

    @Override
    public JSONObject toJson() {
        return null;
    }
}
