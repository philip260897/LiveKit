package at.livekit.authentication;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import at.livekit.utils.Utils;

@DatabaseTable(tableName = "lk_pins")
public class Pin 
{
    @DatabaseField(id = true)
    private String uuid;
    @DatabaseField
    private long timestamp;
    @DatabaseField(width = 32)
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
