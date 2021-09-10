package at.livekit.statistics.tables;

import java.util.UUID;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "livekit_stats_entries")
public class LKStatEntry {
    
    public final byte ACTION_PLACE = 0x00;
    public final byte ACTION_BREAK = 0x01;

    @DatabaseField(id = true, uniqueCombo = true)
    public UUID uuid;

    @DatabaseField(uniqueCombo = true)
    public long timestamp;

    @DatabaseField(uniqueCombo = true)
    public byte action;

    @DatabaseField
    public int blockid;

    @DatabaseField
    public int count;


}
