package at.livekit.provider;

import java.util.List;
import java.util.concurrent.Callable;
import org.bukkit.OfflinePlayer;
import org.bukkit.scheduler.BukkitTask;

import at.livekit.api.core.Color;
import at.livekit.api.map.AsyncPlayerInfoProvider;
import at.livekit.api.map.InfoEntry;
import at.livekit.api.map.Waypoint;
import at.livekit.plugin.Plugin;
import at.livekit.utils.FutureSyncCallback;
import at.livekit.utils.Utils;

public class BasicPlayerPinProvider extends AsyncPlayerInfoProvider {

    public static Color PLAYER_PIN_COLOR = Color.fromHEX("#5D4037");

    public BasicPlayerPinProvider() {
        super(Plugin.getInstance(), "Player Pin provider", "livekit.poi.personalpins");
    }

    public static BukkitTask listPlayerPinsAsync(OfflinePlayer player, FutureSyncCallback<List<Waypoint>> onResult, FutureSyncCallback<Exception> onError) {
        return Utils.executeAsyncForSyncResult(new Callable<List<Waypoint>>(){
            @Override
            public List<Waypoint> call() throws Exception {
                return Plugin.getStorage().loadPlayerPins(player);
            }

        }, onResult, onError);
    }

    public static BukkitTask setPlayerPinAsync(OfflinePlayer player, Waypoint waypoint, FutureSyncCallback<Void> onResult, FutureSyncCallback<Exception> onError) {
        return Utils.executeAsyncForSyncResult(new Callable<Void>(){
            @Override
            public Void call() throws Exception {
                Plugin.getStorage().savePlayerPin(player, waypoint);
                return null;
            }

        }, onResult, onError);
    }

    public static BukkitTask removePlayerPinAsync(OfflinePlayer player, Waypoint waypoint, FutureSyncCallback<Void> onResult, FutureSyncCallback<Exception> onError) {
        return Utils.executeAsyncForSyncResult(new Callable<Void>(){
            @Override
            public Void call() throws Exception {
                Plugin.getStorage().deletePlayerPin(player, waypoint);
                return null;
            }

        }, onResult, onError);
    }

    @Override
    public void onResolvePlayerInfo(OfflinePlayer player, List<InfoEntry> entries) {

    }

    @Override
    public void onResolvePlayerLocation(OfflinePlayer player, List<Waypoint> waypoints) {
        try{
            List<Waypoint> pins = Plugin.getStorage().loadPlayerPins(player);
            waypoints.addAll(pins);
        }catch(Exception ex){
            ex.printStackTrace();
        }
    }

}
