package at.livekit.statistics.tables;

import java.util.UUID;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "livekit_users")
public class LKUser {
    
    @DatabaseField(generatedId = true)
    public int _id;

    @DatabaseField(unique = true)
    public UUID uuid;

    @DatabaseField
    public long first;

    @DatabaseField
    public long last;

    @DatabaseField
    public boolean livekit;
}
