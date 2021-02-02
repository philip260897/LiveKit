package at.livekit.packets;

import org.json.JSONObject;

public class ActionPacket extends RequestPacket
{
    public static int PACKETID = 18;

    private String module;
    private String action;
    
    private JSONObject data;

    public ActionPacket(String module, String action, JSONObject data) {
        this.action = action;
        this.module = module;
        this.data = data;
    }

    public ActionPacket() {}

    public String getActionName() {
        return action;
    }

    public String getModuleType() {
        return module;
    }

    public JSONObject getData() {
        return data;
    }

    @Override
    public IPacket fromJson(String data) {
        super.fromJson(data);
        JSONObject json = new JSONObject(data);
        this.module = json.getString("module");
        this.action = json.getString("action");
        this.data = json.has("data") && !json.isNull("data") ? json.getJSONObject("data") : null;
        return this;
    }

    @Override
    public JSONObject toJson() {
        JSONObject json = super.toJson();
        json.put("module", module);
        json.put("action", action);
        json.put("data", data);
        json.put("packet_id", PACKETID);
        return json;
    }

    public ActionPacket response(JSONObject data) {
        return (ActionPacket) new ActionPacket(this.module, this.action, data).setRequestId(this.requestId);
    }
}
