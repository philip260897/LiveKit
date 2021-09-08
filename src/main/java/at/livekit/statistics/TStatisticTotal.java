package at.livekit.statistics;

import java.util.UUID;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "livekit_stats_total")
public class TStatisticTotal {
    
    @DatabaseField(id = true, uniqueCombo = true)
    public UUID uuid;

    @DatabaseField(uniqueCombo = true)
    public byte action;

    @DatabaseField
    public int blockId;

    @DatabaseField
    public int count;
}
