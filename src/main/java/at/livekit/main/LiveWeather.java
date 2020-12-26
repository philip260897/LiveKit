package at.livekit.main;

public class LiveWeather extends LiveSyncable 
{
    protected int weather = 0;
    protected int weatherTime = 0;
    protected int time = 0;

    public LiveWeather(String world) {
        super(world+"-time-weather-syncable");
    }

    public void update(int[] data) {
        if(weather != data[0]) {
            weather = data[0];
            markDirty("weather");
        }
        if(weatherTime != data[1]) {
            weatherTime = data[1];
            markDirty("weatherTime");
        }
        if(time != data[2]) {
            time = data[2];
            markDirty("time");
        }
    }
}
