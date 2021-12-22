package at.livekit.statistics.results;

import java.util.UUID;

import org.json.JSONObject;

public class PlayerValueResult<T, J> {
    
    private UUID uuid;
    private T value;
    private J secondary;

    public PlayerValueResult(UUID uuid, T t, J j) {
        this.uuid = uuid;
        this.value = t;
        this.secondary = j;
    }

    public JSONObject toJson() {
        JSONObject data = new JSONObject();
        data.put("uuid", uuid.toString());
        data.put("value", value);
        if(secondary != null) data.put("sec", secondary);
        return data;
    }
}
