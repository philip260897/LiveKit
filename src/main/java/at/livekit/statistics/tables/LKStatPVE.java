package at.livekit.statistics.tables;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "livekit_stats_pve")
public class LKStatPVE 
{
    @DatabaseField(generatedId = true)
    public int _id;

    @DatabaseField(uniqueCombo = true, foreign = true)
    public LKUser user;

    @DatabaseField(uniqueCombo = true)
    public long timestamp;

    @DatabaseField(index = true)
    public int type;

    @DatabaseField
    public int count;
}
