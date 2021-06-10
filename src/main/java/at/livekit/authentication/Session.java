package at.livekit.authentication;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import at.livekit.utils.Utils;

@DatabaseTable(tableName = "lk_sessions")
public class Session {

    @DatabaseField(id = true)
    private int id;
    @DatabaseField(index = true)
    private String uuid;
    @DatabaseField
    private long timestamp;
    @DatabaseField
    private long last;
    @DatabaseField
    private String sessionKey;
    @DatabaseField
    private String ip;
    @DatabaseField
    private String data;

    private Session(){}

    public Session(String uuid, long timestamp, long last, String sessionKey, String ip, String data) {
        this.uuid = uuid;
        this.timestamp = timestamp;
        this.sessionKey = sessionKey;
        this.ip = ip;
        this.data = data;
        this.last = last;
    }

    public String getAuthentication() {
        return sessionKey;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getIP() {
        return ip;
    }

    public String getData() {
        return data;
    }

    public Long getLast() {
        return last;
    }

    public static Session createNew(String uuid, String ip, String data) {
        Session session = new Session();
        session.uuid = uuid;
        session.sessionKey = Utils.generateRandom(128);
        session.timestamp = System.currentTimeMillis();
        session.ip = ip;
        session.data = data;
        return session;
    }
}