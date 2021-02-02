package at.livekit.packets;

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
        return (toJson().toString()+"\n").getBytes();
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
