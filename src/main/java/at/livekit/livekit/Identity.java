package at.livekit.livekit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.Map.Entry;

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

    private boolean identified = false;

    private List<String> permissions = new ArrayList<String>();
    //private OfflinePlayer offlinePlayer = null;

    //TODO: how to handle disabled modules ? changed subscriptions ?
    private HashMap<String, String> subscriptions = new HashMap<>();
    private HashMap<String, String> moduleAuthentication = new HashMap<>();

    private Identity() {

    }

    public void identify(String uuid) {
        this.uuid =uuid;
        this.anonymous = false;
        this.identified = true;
    }

    public void setAnonymous() {
        this.uuid = null;
        this.anonymous = true;
        this.identified = true;
    }

    public static Identity unidentified() {
        return new Identity();
    }

    public void setModuleAuthentication(String module, String auth) {
        synchronized(moduleAuthentication) {
            if(module != null) moduleAuthentication.put(module, auth);
            else moduleAuthentication.remove(module);
        }
    }

    public void setModuleAuthentications(HashMap<String, String> map) {
        this.moduleAuthentication = map;
    }

    public boolean isModuleAuthenticated(String module, String subAuth) {
        synchronized(moduleAuthentication) {
            if(moduleAuthentication.containsKey(module)) {
                return subAuth.equals(moduleAuthentication.get(module));
            }
        }
        return false;
    }

    public void setSubscription(String baseType, String subscription) {
        synchronized(subscriptions) {
            if(subscription != null) subscriptions.put(baseType, subscription);
            else subscriptions.remove(baseType);
        }
    }

    public boolean hasSubscriptionFor(String baseType) {
        synchronized(subscriptions) {
            return subscriptions.containsKey(baseType);
        }
    }

    public boolean isSubscribed(String type, String subscription) {
        synchronized(subscription) {
            if(subscriptions.containsKey(type)) {
                return (subscriptions.get(type).equals(subscription));
            }
        }
        return false;
    }

    public boolean isIdentified() {
        return identified;
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
        if(!isIdentified()) return false;
        if(uuid == null && anonymous == false) return false;
        if(op) return true;
        return permissions.contains(permission);
    }

    public void updateSubscriptions(HashMap<String, String> defaultSub) {
        synchronized(subscriptions) {
            for(Entry<String,String> e : defaultSub.entrySet()) {
                this.subscriptions.put(e.getKey(), e.getValue());
            }
        }
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

    /*public void loadPermissionsSync() {
        if(isAnonymous()) {
            synchronized(permissions) {
                this.permissions = new ArrayList<String>();
                this.permissions.addAll(Config.getAnonymousPermissions());
            }
        } 
        else
        {
            this.offlinePlayer = Bukkit.getOfflinePlayer(UUID.fromString(uuid));

            synchronized(permissions) {
                this.permissions = new ArrayList<String>();
                for(String permission : Permissions.permissions) {
                    if(Permissions.has(offlinePlayer, permission)) {
                        permissions.add(permission);
                    }
                }
            }
        }
    }*/
}
