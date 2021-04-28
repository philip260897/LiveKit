package at.livekit.authentication;

import at.livekit.utils.Utils;

public class Pin 
{
    private String uuid;
    private long timestamp;
    private String pin;
    
    private Pin(){}

    public Pin(String uuid, String pin, long timestamp) {
        this.uuid = uuid;
        this.pin = pin;
        this.timestamp = timestamp;
    }

    public boolean isValid() {
        return System.currentTimeMillis() - timestamp < 2*1000*60;
    }

    public String getUUID() {
        return uuid;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getPin() {
        return pin;
    }

    public static Pin createNew(String uuid) {
        Pin pin = new Pin();
        pin.uuid = uuid;
        pin.timestamp = System.currentTimeMillis();
        pin.pin = Utils.generateRandomNumbers(6);
        return pin;
    }
}
