package at.livekit.statistics.results;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;

public class WorldUsageResult 
{
    private Map<String, Long> usage = new HashMap<String, Long>();

    public void setWorldUsage(String world, Long duration) {
        usage.put(world, duration);
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject(usage);
        return json;
    }
}
