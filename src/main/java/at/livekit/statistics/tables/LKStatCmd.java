package at.livekit.statistics.tables;

import java.util.UUID;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "livekit_stats_cmds")
public class LKStatCmd {
    
    @DatabaseField(id = true, uniqueCombo = true)
    public UUID uuid;

    @DatabaseField(uniqueCombo = true)
    public long timestamp;

    @DatabaseField(width = 64)
    public String cmd;

    @DatabaseField(width = 255)
    public String args;
}
