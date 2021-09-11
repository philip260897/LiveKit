package at.livekit.statistics.tables;

import java.util.UUID;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "livekit_stats_sessions")
public class LKStatSession {
    
    @DatabaseField(generatedId = true)
    private int _id;

    @DatabaseField(foreign = true, uniqueCombo = true, canBeNull = false)
    public LKUser user;

    @DatabaseField(uniqueCombo = true)
    public long start = 0;

    @DatabaseField
    public long end = 0;
}
