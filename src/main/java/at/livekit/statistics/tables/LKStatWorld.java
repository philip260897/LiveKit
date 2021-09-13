package at.livekit.statistics.tables;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "livekit_stats_world")
public class LKStatWorld {
    
    @DatabaseField(generatedId = true)
    public int _id;
    
    @DatabaseField(foreign = true, uniqueCombo = true, indexName = "index_uid_leave")
    public LKUser user;

    @DatabaseField(uniqueCombo = true)
    public long enter = 0;

    @DatabaseField(indexName = "index_uid_leave")
    public long leave = 0;

    @DatabaseField(width = 32, index = true)
    public String world;

}
