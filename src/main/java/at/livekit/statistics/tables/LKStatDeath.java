package at.livekit.statistics.tables;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "livekit_stats_deaths")
public class LKStatDeath {
    
    @DatabaseField(generatedId = true)
    public int _id;

    @DatabaseField(foreign = true, uniqueCombo = true)
    public LKUser user;

    @DatabaseField(uniqueCombo = true)
    public long timestamp;

    @DatabaseField
    public int count;

}
