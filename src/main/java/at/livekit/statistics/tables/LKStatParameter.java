package at.livekit.statistics.tables;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.json.JSONObject;

@DatabaseTable(tableName = "livekit_stats_parameters")
public class LKStatParameter {

    @DatabaseField(generatedId = true)
    private int _id;

    @DatabaseField(foreign = true, uniqueCombo = true)
    public LKUser user;

    @DatabaseField(uniqueCombo = true, dataType = DataType.ENUM_INTEGER)
    public LKParam param;

    @DatabaseField(uniqueCombo = true)
    public int type;

    @DatabaseField(uniqueCombo = true)
    public long timestamp;

    @DatabaseField
    public int value;

    public enum LKParam {
        BLOCK_PLACE, BLOCK_BREAK, TOOL_USE, WEAPON_KILL, FISHING, ENTITY_KILLS/*, PLAYTIME_TOTAL, SESSIONS_TOTAL, PVP_KILLS_TOTAL, PVE_KILLS_TOTAL, DEATHS_TOTAL*/
    }

    public JSONObject toJson(boolean includeTimestamp) {
        JSONObject object = new JSONObject();
        object.put("type", type);
        if(includeTimestamp) object.put("timestamp", timestamp);
        object.put("value", value);
        return object;
    }
}
