package at.livekit.modules;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.json.JSONObject;

import at.livekit.server.IPacket;

public class WeatherTimeModule extends BaseModule
{
    private int weather = 0;
    private int weatherTime = 0;
    private int time = 0;

    private String world;

    public WeatherTimeModule(String world, ModuleListener listener) {
        super(1, "Weather/Time", "livekit.basics.time", UpdateRate.ONCE_PERSEC, listener, true);
        this.world = world;
    }

    @Override
    public void update() {
        World w = Bukkit.getWorld(world);
        if(w != null) {
            weather = w.hasStorm() ? 1 : w.isThundering() ? 2:0;
            weatherTime = w.getWeatherDuration();
            time = (int)(w.getTime());
            notifyChange();
        }
        super.update();
    }

    @Override
    public IPacket onJoinAsync(String uuid) {
        JSONObject data = new JSONObject();
        data.put("world", world);
        data.put("weather", weather);
        data.put("weatherTime", weatherTime);
        data.put("time", time);
        return new ModuleUpdatePacket(this, data, true);
    }

    @Override
    public Map<String,IPacket> onUpdateAsync(List<String> uuids) {
        Map<String,IPacket> responses = new HashMap<String,IPacket>();
        for(String uuid : uuids) {
            responses.put(uuid, onJoinAsync(uuid));
        }
        return responses;
    }
}
