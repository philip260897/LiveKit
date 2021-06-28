package at.livekit.provider;

import java.util.List;
import java.util.concurrent.Callable;

import org.bukkit.ChatColor;
import org.bukkit.scheduler.BukkitTask;

import at.livekit.api.core.Color;
import at.livekit.api.map.AsyncPOIInfoProvider;
import at.livekit.api.map.InfoEntry;
import at.livekit.api.map.POI;
import at.livekit.plugin.Plugin;
import at.livekit.utils.FutureSyncCallback;
import at.livekit.utils.Utils;

public class BasicPOIProvider extends AsyncPOIInfoProvider {

    public static Color POI_COLOR = Color.fromChatColor(ChatColor.DARK_RED);

    public BasicPOIProvider() {
        super(Plugin.getInstance(), "Basic POI Provider", "livekit.module.poi");


    }

    /*public static BukkitTask listPOIAsync(FutureSyncCallback<List<POI>> onResult, FutureSyncCallback<Exception> onError) {
        return Utils.executeAsyncForSyncResult(new Callable<List<POI>>(){
            @Override
            public List<POI> call() throws Exception {
                return Plugin.getStorage().loadPOIs();
            }

        }, onResult, onError);
    }*/

    /*public static BukkitTask setPOIAsync(POI poi, FutureSyncCallback<Void> onResult, FutureSyncCallback<Exception> onError) {
        return Utils.executeAsyncForSyncResult(new Callable<Void>(){
            @Override
            public Void call() throws Exception {
                Plugin.getStorage().savePOI(poi);
                return null;
            }

        }, onResult, onError);
    }*/

    /*public static BukkitTask removePOIAsync(POI poi, FutureSyncCallback<Void> onResult, FutureSyncCallback<Exception> onError) {
        return Utils.executeAsyncForSyncResult(new Callable<Void>(){
            @Override
            public Void call() throws Exception {
                Plugin.getStorage().deletePOI(poi);
                return null;
            }

        }, onResult, onError);
    }*/

    @Override
    public void onResolvePOIInfo(POI poi, List<InfoEntry> arg1) {
        // TODO Auto-generated method stub
        
    }
}
