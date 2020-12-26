package at.livekit.packets;

import org.json.JSONObject;

import at.livekit.server.IPacket;

public class TimeWeatherPacket implements IPacket {
    
    private int weather;
    private int weatherTime;
    private int time;

    public TimeWeatherPacket(int weather, int weatherTime, int time) {
        this.weather = weather;
        this.weatherTime = weatherTime;
        this.time = time;
    }

    @Override
    public IPacket fromJson(String json) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("weather", weather);
        json.put("weatherTime", weatherTime);
        json.put("time", time);
        return json;
    }

    
}
