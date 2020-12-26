package at.livekit.server;

import java.util.List;
import java.util.stream.Collectors;

import org.json.JSONObject;

public class MultiPacket implements IPacket {
    public static int PACKETID = 0x05;

    private List<IPacket> packets;

    public MultiPacket(List<IPacket> packets) {
        this.packets = packets;
    }

    @Override
    public IPacket fromJson(String json) { return null; }

    @Override
    public JSONObject toJson() { 
        JSONObject json = new JSONObject();
        json.put("packets", packets.stream().map(packet->packet.toJson()).collect(Collectors.toList()));
        json.put("packet_id", PACKETID);
        return json;
    }


}