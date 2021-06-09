package at.livekit.authentication;

import java.util.List;
import java.util.concurrent.Callable;

import org.bukkit.OfflinePlayer;
import org.bukkit.scheduler.BukkitTask;

import at.livekit.plugin.Plugin;
import at.livekit.utils.FutureSyncCallback;
import at.livekit.utils.Utils;

public class AuthenticationHandler 
{
    public static BukkitTask generatePin(OfflinePlayer player, FutureSyncCallback<Pin> onResult, FutureSyncCallback<Exception> onError) {
        return Utils.executeAsyncForSyncResult(new Callable<Pin>(){
            @Override
            public Pin call() throws Exception {
                List<Pin> existing = Plugin.getStorage().loadPinsForPlayer(player.getUniqueId().toString());
                for(Pin pin : existing) {
                    if(pin.getUUID().equals(player.getUniqueId().toString())) {
                        Plugin.getStorage().deletePin(player.getUniqueId().toString(), pin);
                    }
                }
                Pin pin = Pin.createNew(player.getUniqueId().toString());
                Plugin.getStorage().createPin(player.getUniqueId().toString(), pin);

                return pin;
            }

        }, onResult, onError);
    }

    public static BukkitTask getSessionList(OfflinePlayer player, FutureSyncCallback<List<Session>> onResult, FutureSyncCallback<Exception> onError) {
        return Utils.executeAsyncForSyncResult(new Callable<List<Session>>(){
            @Override
            public List<Session> call() throws Exception {
                List<Session> existing = Plugin.getStorage().loadSessions(player.getUniqueId().toString());
                return existing;
            }

        }, onResult, onError);
    }

    public static BukkitTask clearSessionList(OfflinePlayer player, FutureSyncCallback<Void> onResult, FutureSyncCallback<Exception> onError) {
        return Utils.executeAsyncForSyncResult(new Callable<Void>(){
            @Override
            public Void call() throws Exception {
                List<Session> existing = Plugin.getStorage().loadSessions(player.getUniqueId().toString());
                for(Session session : existing) {
                    Plugin.getStorage().deleteSession(player.getUniqueId().toString(), session);
                }
                return null;
            }

        }, onResult, onError);
    }

    /*public static BukkitTask validatePin(OfflinePlayer player, String pin) {

    }*/


}
