package at.livekit.statistics.tables;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "livekit_stats_serversession")
public class LKStatServerSession {
    
    @DatabaseField(generatedId = true)
    public int _id;

    @DatabaseField(index = true)
    public long start;

    @DatabaseField
    public long end;
}
