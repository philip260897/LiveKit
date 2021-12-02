package at.livekit.statistics.tables;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.json.JSONObject;

@DatabaseTable(tableName = "livekit_stats_deaths")
public class LKStatDeath {
    
    @DatabaseField(generatedId = true)
    public int _id;

    @DatabaseField(foreign = true, uniqueCombo = true)
    public LKUser user;

    @DatabaseField(uniqueCombo = true)
    public long timestamp;

    @DatabaseField(index = true)
    public int cause;

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("timestamp", timestamp);
        json.put("cause", cause);
        return json;
    }
}
