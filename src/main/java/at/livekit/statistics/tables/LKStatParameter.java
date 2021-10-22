package at.livekit.statistics.tables;

import java.util.UUID;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "livekit_stats_parameters")
public class LKStatParameter {

    @DatabaseField(generatedId = true)
    private int _id;

    @DatabaseField(foreign = true)
    public LKUser user;

    @DatabaseField(uniqueCombo = true)
    public int param;

    @DatabaseField(uniqueCombo = true)
    public int type;

    @DatabaseField(uniqueCombo = true)
    public long timestamp;

    @DatabaseField
    public long value;

    public enum Param {
        BLOCK_PLACE, BLOCK_BREAK, TOOL_USE, WEAPON_KILL, PLAYTIME_TOTAL, SESSIONS_TOTAL, PVP_KILLS_TOTAL, PVE_KILLS_TOTAL, DEATHS_TOTAL
    }
}
