package at.livekit.statistics.tables;

import java.util.UUID;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "livekit_stats_total")
public class LKStatTotalEntry {
    
    @DatabaseField(id = true, uniqueCombo = true)
    public UUID uuid;

    @DatabaseField(uniqueCombo = true)
    public byte action;

    @DatabaseField
    public int block_id;

    @DatabaseField
    public int count;
}
