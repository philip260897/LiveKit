package at.livekit.statistics;

import java.util.UUID;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "livekit_stats_sessions")
public class TSession {
    
    @DatabaseField(id = true)
    public UUID uuid;

    @DatabaseField
    public long timestampLogin = 0;

    @DatabaseField
    public long timestampLogout = 0;
}
