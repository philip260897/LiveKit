package at.livekit.livekit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import at.livekit.plugin.Config;
import at.livekit.plugin.Permissions;

public class Identity 
{
    private String uuid;
    private String name;
    private boolean anonymous = false;
    private boolean op = false;

    private List<String> permissions = new ArrayList<String>();
    
    public Identity(String uuid) {
        this.uuid = uuid;
        this.anonymous = this.uuid == null;
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

    public void loadPermissionsAsync() {
        if(!isAnonymous()) {
            try{
                OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
                op = player.isOp();
                name = player.getName();

                synchronized(permissions) {
                    permissions.clear();
                    for(String permission : Permissions.permissions) {
                        if(Permissions.has(player, permission)) {
                            permissions.add(permission);
                        }
                    }
                }
            }catch(Exception ex){};
        } else {
            op = false;
            name = "Anonymous";
            permissions.addAll(Config.getAnonymousPermissions());
        }
    }
}
