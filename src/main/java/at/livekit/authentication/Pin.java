package at.livekit.authentication;

import java.util.UUID;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import at.livekit.utils.Utils;

@DatabaseTable(tableName = "lk_pins")
public class Pin 
{
    @DatabaseField(id = true)
    private UUID uuid;
    @DatabaseField
    private long timestamp;
    @DatabaseField(width = 32, index = true)
    private String pin;
    
    private Pin(){}

    public Pin(UUID uuid, String pin, long timestamp) {
        this.uuid = uuid;
        this.pin = pin;
        this.timestamp = timestamp;
    }

    public boolean isValid() {
        return System.currentTimeMillis() - timestamp < 2*1000*60;
    }

    public UUID getUUID() {
        return uuid;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getPin() {
        return pin;
    }

    public static Pin createNew(UUID uuid) {
        Pin pin = new Pin();
        pin.uuid = uuid;
        pin.timestamp = System.currentTimeMillis();
        pin.pin = Utils.generateRandomNumbers(6);
        return pin;
    }
}
