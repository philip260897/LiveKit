package at.livekit.livekit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import at.livekit.plugin.Plugin;

public class Identity 
{
    private String uuid;
    private String name;
    private boolean anonymous = false;
    private boolean op = false;

    private List<String> permissions = new ArrayList<String>();
    
    public Identity(String uuid) {
        this.uuid = uuid;
    }

    public Identity() {
        this.anonymous = true;
    }

    public String[] getPermissions() {
        return permissions.toArray(new String[permissions.size()]);
    }

    public String getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public boolean isAnonymous() {
        return anonymous;
    }

    public boolean hasPermission(String permission) {
        if(uuid == null && anonymous == false) return false;
        if(op) return true;
        return permissions.contains(permission);
    }

    public void loadPermissionsSync() {
        if(!isAnonymous()) {
            try{
                OfflinePlayer player = Bukkit.getScheduler().callSyncMethod(Plugin.instance, new Callable<OfflinePlayer>(){
                    @Override
                    public OfflinePlayer call() throws Exception {
                        OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
                        op = player.isOp();
                        name = player.getName();
                        return player;
                    }
                    
                }).get();
                synchronized(permissions) {
                    permissions.clear();
                    for(String permission : Plugin.permissions) {
                        if(Plugin.perms.playerHas(Bukkit.getWorlds().get(0).getName(), player, permission)) {
                            permissions.add(permission);
                        }
                    }
                }
            }catch(Exception ex){};
        } else {
            permissions.add("livekit.livemap.view");
            permissions.add("livekit.players.view");
        }
    }

    public void reloadPermissions() {
        /*OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
        op = player.isOp();
        name = player.getName();

        Bukkit.getScheduler().runTaskAsynchronously(Plugin.instance, new Runnable(){

            @Override
            public void run() {
                synchronized(permissions) {
                    permissions.clear();
                    for(String permission : Plugin.permissions) {
                        if(Plugin.perms.playerHas(Bukkit.getWorlds().get(0).getName(), player, permission)) {
                            permissions.add(permission);
                        }
                    }
                }
            }

        });*/

    }
}
