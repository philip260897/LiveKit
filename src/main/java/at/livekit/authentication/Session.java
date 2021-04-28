package at.livekit.authentication;

import at.livekit.utils.Utils;

public class Session {
    private long timestamp;
    private long last;
    private String sessionKey;
    private String ip;
    private String data;

    private Session(){}

    public Session(long timestamp, long last, String sessionKey, String ip, String data) {
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

    public static Session createNew(String ip, String data) {
        Session session = new Session();
        session.sessionKey = Utils.generateRandom(128);
        session.timestamp = System.currentTimeMillis();
        session.ip = ip;
        session.data = data;
        return session;
    }
}