package at.livekit.packets;
import org.json.JSONObject;

public class AuthorizationPacket extends RequestPacket {
    public static int PACKETID = 10;

    private String pin;
    private String uuid;
    private String authorization;
    private String password;
    private boolean anonymous;

    public boolean isPin() {
        return pin != null;
    }

    public String getPin() {
        return pin;
    }

    public boolean isAuthorization() {
        return authorization != null;
    }

    public boolean isAnonymous() {
        return anonymous;
    }

    public String getValue() {
        return pin != null ? pin : authorization;
    }

    public String getUUID() {
        return uuid;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public IPacket fromJson(String json) {
        super.fromJson(json);
        JSONObject o = new JSONObject(json);
        this.pin = o.has("pin")&&!o.isNull("pin") ? o.getString("pin") : null;
        this.authorization = o.has("auth")&&!o.isNull("auth") ? o.getString("auth") : null;
        this.uuid = o.has("uuid")&&!o.isNull("uuid") ? o.getString("uuid") : null;
        this.anonymous = o.has("anonymous")&&!o.isNull("anonymous") ? o.getBoolean("anonymous") : false;
        this.password = o.has("password")&&!o.isNull("password")?o.getString("password"):null;
        return this;
    }

    @Override
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("pin", pin);
        json.put("auth", authorization);
        json.put("uuid", uuid);
        json.put("packet_id", PACKETID);
        json.put("anonymous", anonymous);
        return json;
    }
}
