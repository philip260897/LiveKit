package at.livekit.modules;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.json.JSONObject;

import at.livekit.livekit.Identity;
import at.livekit.packets.IPacket;

public class WeatherTimeModule extends BaseModule
{
    private int weather = 0;
    private int weatherTime = 0;
    private int time = 0;

    private String world;

    public WeatherTimeModule(String world, ModuleListener listener) {
        super(1, "Weather/Time", "livekit.module.players", UpdateRate.ONCE_PERSEC, listener, world);
        this.world = world;
    }

    @Override
    public void update() {
        World w = Bukkit.getWorld(world);
        if(w != null) {
            weather = w.isThundering() ? 2 : w.hasStorm() ? 1:0;
            weatherTime = w.getWeatherDuration();
            time = (int)(w.getTime());
            notifyChange();
        }
        super.update();
    }

    @Override
    public IPacket onJoinAsync(Identity identity) {
        JSONObject data = new JSONObject();
        data.put("world", world);
        data.put("weather", weather);
        data.put("weatherTime", weatherTime);
        data.put("time", time);
        return new ModuleUpdatePacket(this, data, true);
    }

    @Override
    public Map<Identity,IPacket> onUpdateAsync(List<Identity> identities) {
        Map<Identity,IPacket> responses = new HashMap<Identity,IPacket>();
        for(Identity identity : identities) {
            responses.put(identity, onJoinAsync(identity));
        }
        return responses;
    }
}
