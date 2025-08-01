package at.livekit.provider;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.bukkit.OfflinePlayer;
import org.bukkit.scheduler.BukkitTask;

import at.livekit.api.core.Color;
import at.livekit.api.core.IIdentity;
import at.livekit.api.map.AsyncPlayerInfoProvider;
import at.livekit.api.map.AsyncPlayerLocationProvider;
import at.livekit.api.map.InfoEntry;
import at.livekit.api.map.POI;
import at.livekit.api.map.PersonalPin;
import at.livekit.api.map.Waypoint;
import at.livekit.plugin.Plugin;
import at.livekit.utils.FutureSyncCallback;
import at.livekit.utils.Utils;

public class BasicPlayerPinProvider extends AsyncPlayerLocationProvider {

    public static Color PLAYER_PIN_COLOR = Color.fromHEX("#5D4037");

    public BasicPlayerPinProvider() {
        super(Plugin.getInstance(), "Player Pin provider", "livekit.poi.personalpins");
    }

    public static BukkitTask listPlayerPinsAsync(OfflinePlayer player, FutureSyncCallback<List<PersonalPin>> onResult, FutureSyncCallback<Exception> onError) {
        return Utils.executeAsyncForSyncResult(new Callable<List<PersonalPin>>(){
            @Override
            public List<PersonalPin> call() throws Exception {
                return Plugin.getStorage().load(PersonalPin.class, "playeruuid", player.getUniqueId());
            }

        }, onResult, onError);
    }

    public static BukkitTask setPlayerPinAsync(OfflinePlayer player, PersonalPin waypoint, FutureSyncCallback<Void> onResult, FutureSyncCallback<Exception> onError) {
        return Utils.executeAsyncForSyncResult(new Callable<Void>(){
            @Override
            public Void call() throws Exception {
                Plugin.getStorage().create(waypoint);
                return null;
            }

        }, onResult, onError);
    }

    public static BukkitTask removePlayerPinAsync(OfflinePlayer player, PersonalPin waypoint, FutureSyncCallback<Void> onResult, FutureSyncCallback<Exception> onError) {
        return Utils.executeAsyncForSyncResult(new Callable<Void>(){
            @Override
            public Void call() throws Exception {
                Plugin.getStorage().delete(waypoint);
                return null;
            }

        }, onResult, onError);
    }

    @Override
    public List<PersonalPin> onResolvePlayerLocation(IIdentity identity, OfflinePlayer player) {
        try{
            return Plugin.getStorage().load(PersonalPin.class, "playeruuid", player.getUniqueId());
        }catch(Exception ex){
            ex.printStackTrace();
        }
        return new ArrayList<>();
    }

}
