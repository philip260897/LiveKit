package at.livekit.statistics.tables;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "livekit_stats_pve")
public class LKStatPVE 
{
    @DatabaseField(index = true, foreign = true)
    public LKUser user;

    @DatabaseField(index = true)
    public int entity;

    @DatabaseField(index = true)
    public long timestamp;

    @DatabaseField(canBeNull = false)
    public int weapon = 0;

    @DatabaseField(canBeNull = false)
    public boolean kill = true;
}
