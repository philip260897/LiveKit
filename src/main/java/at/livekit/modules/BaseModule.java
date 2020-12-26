package at.livekit.modules;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.json.JSONObject;

import at.livekit.livekit.Identity;
import at.livekit.server.IPacket;

public abstract class BaseModule 
{
    private int version;
    private UpdateRate tick;
    private String name;
    private String permission;
    private boolean anonymousAllowed;
    private boolean enabled = false;

    private ModuleListener listener;
    
    public BaseModule(int version, String name, String permission, UpdateRate tick, ModuleListener listener) {
        this(version, name, permission, tick, listener, false);
    }

    /*public BaseModule(int version, String name, String permission, UpdateRate tick, boolean anonymousAllowed) {
        this(version, name, permission, tick, anonymousAllowed);
    }*/

    public BaseModule(int version, String name, String permission, UpdateRate tick,ModuleListener listener, boolean anonymousAllowed) {
        this.version = version;
        this.name = name;
        this.permission = permission;
        this.tick = tick;
        this.listener = listener;
        this.anonymousAllowed = anonymousAllowed;
        //this.enabled = defaultEnabled;
    }
    
    public void onEnable() {
        enabled = true;
    }

    public void onDisable() {
        enabled = false;
    }

    protected void notifyChange() {
        if(listener != null) listener.onDataChangeAvailable(this.getType());
    }
    //public void update(){}

    public IPacket onJoin(String uuid){return null;}

    public IPacket onUpdate(String uuid){return null;}

    public IPacket onChange(String uuid, IPacket packet){return null;}

    public boolean hasAccess(String uuid) {
        if(anonymousAllowed) return true;

        if(uuid == null) return false;
        
        OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
        if(player.isOp()) return true;

        return false;
    }

    public JSONObject toJson(String uuid) {
        return moduleInfo();
    }

    public String getType() {
        return this.getClass().getSimpleName();
    }

    public String getPermission() {
        return this.permission;
    }

    public int getVersion() {
        return this.version;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public UpdateRate getTickRate() {
        return tick;
    }

    public JSONObject moduleInfo() {
        JSONObject json = new JSONObject();
        json.put("version", version);
        json.put("name", name);
        json.put("moduleType", this.getClass().getSimpleName());
        return json;
    }

    public static enum UpdateRate {
        NEVER, ONCE, ONCE_PERSEC, TWICE_PERSEC, HIGH, MAX
    }

    public static interface ModuleListener {
        void onDataChangeAvailable(String moduleType);
    }
}
