package at.livekit.packets;

import java.nio.charset.StandardCharsets;

import org.apache.commons.lang.NotImplementedException;
import org.json.JSONObject;

public class Packet implements IPacket {

    @Override
    public IPacket fromJson(String json) {
        throw new NotImplementedException();
    }

    @Override
    public JSONObject toJson() {
        throw new NotImplementedException();
    }

    @Override
    public byte[] data() {
        return (toJson().toString()+"\n").getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] header() {
        return null;
    }

    @Override
    public boolean hasHeader() {
        return false;
    }
}
