package at.livekit.authentication;

import java.util.UUID;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import at.livekit.utils.Utils;

@DatabaseTable(tableName = "livekit_sessions")
public class Session {

    @DatabaseField(generatedId = true)
    private int id;
    @DatabaseField(index = true)
    private UUID uuid;
    @DatabaseField
    private String sessionkey;
    @DatabaseField
    private long timestamp;
    @DatabaseField
    private long last;

    private Session(){}

    public Session(UUID uuid, long timestamp, long last, String sessionKey, String ip, String data) {
        this.uuid = uuid;
        this.timestamp = timestamp;
        this.sessionkey = sessionKey;
        this.last = last;
    }

    public String getAuthentication() {
        return sessionkey;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Long getLast() {
        return last;
    }

    public static Session createNew(UUID uuid, String ip, String data) {
        Session session = new Session();
        session.uuid = uuid;
        session.sessionkey = Utils.generateRandom(128);
        session.timestamp = System.currentTimeMillis();
        return session;
    }
}