package at.livekit.statistics;

import java.util.UUID;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "livekit_stats_world")
public class TWorld {
    
    @DatabaseField(id = true)
    public UUID uuid;

    @DatabaseField(width = 32)
    public String world;

    @DatabaseField
    public long timestampEnter = 0;

    @DatabaseField
    public long timestampLeave = 0;

}
