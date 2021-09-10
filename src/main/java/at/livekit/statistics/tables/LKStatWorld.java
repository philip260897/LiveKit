package at.livekit.statistics.tables;

import java.util.UUID;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "livekit_stats_world")
public class LKStatWorld {
    
    @DatabaseField(id = true)
    public UUID uuid;

    @DatabaseField(width = 32)
    public String world;

    @DatabaseField
    public long timestamp_enter = 0;

    @DatabaseField
    public long timestamp_leave = 0;

}
