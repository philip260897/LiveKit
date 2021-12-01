package at.livekit.statistics.results;

import java.util.UUID;

import org.json.JSONObject;

public class PVPResult 
{
    private boolean kill;
    private UUID target;
    private long timestamp;  
    private int weapon;  

    public PVPResult(UUID target, long timestamp, int weapon, boolean kill) {
        this.kill = kill;
        this.target = target;
        this.timestamp = timestamp;
        this.weapon = weapon;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("kill", kill);
        json.put("target", target);
        json.put("timestamp", timestamp);
        json.put("weapon", weapon);
        return json;
    }
}
