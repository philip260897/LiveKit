package at.livekit.packets;

import org.json.JSONObject;

import at.livekit.livekit.LiveKitClient;
import at.livekit.server.IPacket;

public class RequestPacket implements IPacket {
    public int requestId = -1;
    public LiveKitClient client;

    public RequestPacket() {
    }

    public RequestPacket(int requestId) {
        this.requestId = requestId;
    }

    public RequestPacket setRequestId(int requestId) {
        this.requestId = requestId;
        return this;
    }

    @Override
    public IPacket fromJson(String json) {
        requestId = new JSONObject(json).getInt("request_id");
        return this;
    }

    @Override
    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        o.put("request_id", requestId);
        return o;
    }
}
