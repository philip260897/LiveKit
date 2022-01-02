package at.livekit.modules;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.json.JSONObject;

import at.livekit.livekit.Identity;
import at.livekit.packets.ActionPacket;
import at.livekit.packets.IPacket;
import at.livekit.packets.StatusPacket;

public class WeatherTimeModule extends BaseModule
{
    private int weather = 0;
    private int weatherTime = 0;
    private int time = 0;

    private String world;

    public WeatherTimeModule(String world, ModuleListener listener) {
        super(1, "Weather/Time", "livekit.weathertime", UpdateRate.ONCE_PERSEC, listener, world);
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

    @Action(name="SetWeater", permission = "livekit.weathertime.set")
    protected IPacket actionWeather(Identity identity, ActionPacket packet) {
        String world = packet.getData().getString("world");
        String weather = packet.getData().getString("weather");

        World w = Bukkit.getWorld(world);
        if(w == null) return new StatusPacket(0, "World "+world+" is not available!");

        switch(weather) {
            case "clear": 
                w.setThundering(false);
                w.setStorm(false);
                break;
            case "rain":
                w.setThundering(false);
                w.setStorm(true);
                break;
            case "thunder":
                w.setThundering(true);
                w.setStorm(true);
                break;
            default:
                return new StatusPacket(0, "Invalid weather "+weather);
        }
        return new StatusPacket(1, "Weather set to "+weather);
    }

    @Action(name="SetTime", permission = "livekit.weathertime.set")
    protected IPacket actionSetTime(Identity identity, ActionPacket packet) {
        String world = packet.getData().getString("world");
        String time = packet.getData().getString("time");

        World w = Bukkit.getWorld(world);
        if(w == null) return new StatusPacket(0, "World "+world+" is not available!");

        switch(time) {
            case "day": 
                w.setTime(1000);
                break;
            case "midnight":
                w.setTime(18000);
                break;
            case "night":
                w.setTime(13000);
                break;
            case "noon":
                w.setTime(6000);
                break;
            default:
                return new StatusPacket(0, "Invalid time "+time);
        }
        return new StatusPacket(1, "Time set to "+time);
    }
}
