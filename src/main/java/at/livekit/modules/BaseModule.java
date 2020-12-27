package at.livekit.modules;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.json.JSONArray;
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

    private boolean active = false;
    private boolean enabled = false;

    private ModuleListener listener;
    
    public BaseModule(int version, String name, String permission, UpdateRate tick, ModuleListener listener) {
        this(version, name, permission, tick, listener, false, true);
    }

    public BaseModule(int version, String name, String permission, UpdateRate tick, ModuleListener listener,  boolean anonymousAllowed) {
        this(version, name, permission, tick,listener,  anonymousAllowed, true);
    }

    public BaseModule(int version, String name, String permission, UpdateRate tick,ModuleListener listener, boolean anonymousAllowed, boolean defaultActive) {
        this.version = version;
        this.name = name;
        this.permission = permission;
        this.tick = tick;
        this.listener = listener;
        this.anonymousAllowed = anonymousAllowed;
        this.active = defaultActive;
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
    
    private long lastUpdate = 0;
    public boolean canUpdate(int tickrate) {
        if(tick == UpdateRate.MAX) return true;
        if(tick == UpdateRate.HIGH && System.currentTimeMillis() - lastUpdate > tickrate*2) return true; 
        if(tick == UpdateRate.ONCE && lastUpdate == 0) return true;
        if(tick == UpdateRate.ONCE_PERSEC && System.currentTimeMillis()-lastUpdate > 1000) return true;
        if(tick == UpdateRate.TWICE_PERSEC && System.currentTimeMillis()-lastUpdate > 500) return true;
        return false;
    }

    public void update(){
        lastUpdate = System.currentTimeMillis();
    }

    public IPacket onJoinAsync(String uuid){return null;}

    public Map<String, IPacket> onUpdateAsync(List<String> uuids){return null;}

    public IPacket onChangeAsync(String uuid, IPacket packet){return null;}

    public boolean hasAccess(String uuid) {
        if(anonymousAllowed) return true;

        if(uuid == null) return false;
        
        OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
        if(player.isOp()) return true;

        return false;
    }

    /*public JSONObject toJson(String uuid) {
        return moduleInfo();
    }*/

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
        json.put("active", active);
        json.put("moduleType", this.getClass().getSimpleName());
        return json;
    }

    public static enum UpdateRate {
        NEVER, ONCE, ONCE_PERSEC, TWICE_PERSEC, HIGH, MAX
    }

    public static interface ModuleListener {
        void onDataChangeAvailable(String moduleType);
    }

    public static class ModuleUpdatePacket extends ModulePacket 
    {
        public static int PACKET_ID = 15;
        private JSONObject data;

        public ModuleUpdatePacket(BaseModule module, JSONObject data) {
            super(module.getType());
            this.data = data;
        }

        @Override
        public JSONObject toJson() {
            JSONObject json = super.toJson();
            json.put("data", data);
            json.put("packet_id", PACKET_ID);
            return json;
        }
    }

    public static class ModulesAvailablePacket implements IPacket {

        public static int PACKET_ID = 16;
        private JSONArray modules;

        public ModulesAvailablePacket(JSONArray modules) {
            this.modules = modules;
        }

        @Override
        public IPacket fromJson(String json) {return null;}

        @Override
        public JSONObject toJson() { 
            JSONObject json = new JSONObject();
            json.put("packet_id", PACKET_ID);
            json.put("modules", modules);
            return json;
        }
    }

    public static class ModulePacket implements IPacket {

        private String moduleType;

        public ModulePacket(String type) {
            this.moduleType = type;
        }

        public String getModuleType() {
            return moduleType;
        }

        @Override
        public IPacket fromJson(String json) {
            JSONObject j = new JSONObject(json);
            this.moduleType = j.getString("moduleType");
            return this;
        }

        @Override
        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("moduleType", moduleType);
            return json;
        }

    }
}
