package at.livekit.statistics.tables;

import java.util.UUID;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "livekit_stats_world")
public class LKStatWorld {
    
    @DatabaseField(generatedId = true)
    public int _id;
    
    @DatabaseField(foreign = true, uniqueCombo = true)
    public LKUser user;

    @DatabaseField(uniqueCombo = true)
    public long enter = 0;

    @DatabaseField
    public long leave = 0;

    @DatabaseField(width = 32, index = true)
    public String world;

}
