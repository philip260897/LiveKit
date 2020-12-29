package at.livekit.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.json.JSONArray;
import org.json.JSONObject;

import at.livekit.main.LiveSyncable;
import at.livekit.plugin.Plugin;
import at.livekit.server.IPacket;
import at.livekit.utils.HeadLibrary;

public class PlayerModule extends BaseModule implements Listener
{
    private Map<String,LPlayer> _players = new HashMap<String, LPlayer>();

    public PlayerModule(ModuleListener listener) {
        super(1, "Players", "livekit.basics.map", UpdateRate.NEVER, listener, true);
    }
     
    public LPlayer getPlayer(String uuid) {
        return _players.get(uuid);
    }

    @Override
    public void onEnable() {
        for(OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            _players.put(player.getUniqueId().toString(), LPlayer.fromOfflinePlayer(player));
        }
        for(Player player : Bukkit.getOnlinePlayers()) {
            if(!_players.containsKey(player.getUniqueId().toString())) {
                _players.put(player.getUniqueId().toString(), LPlayer.fromOfflinePlayer(player));
            }
        }
        Bukkit.getServer().getPluginManager().registerEvents(this, Plugin.instance);
        super.onEnable();
    }


    @EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) 
	{
        if(!isEnabled()) return;
        Player p = event.getPlayer();
        LPlayer player = null;
        if(_players.containsKey(event.getPlayer().getUniqueId().toString())) {
            player = _players.get(event.getPlayer().getUniqueId().toString());
        } else {
            player = LPlayer.fromOfflinePlayer(event.getPlayer());
            _players.put(player.getUUID(), player);
        }
        player.updateLastOnline(System.currentTimeMillis(), true);
        player.updateWorld(event.getPlayer().getLocation().getWorld().getName());
		player.updateLocation(p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ());
		player.updateHealth(p.getHealthScale());
		player.updateExhaustion(p.getExhaustion());

        if(!HeadLibrary.has(p.getUniqueId().toString())) { 
			HeadLibrary.resolveAsync(p.getUniqueId().toString());
		} 
		player.updateHead(HeadLibrary.get(p.getUniqueId().toString()));
       // player.assets.add(new LAsset("asset-bank-amount", "Bank Amount", "0$"));
        //player.markDirty();
        notifyChange();
    }
    
    @EventHandler
	public void onPlayerMove(PlayerMoveEvent event) {
        if(!isEnabled()) return;
        LPlayer player = null;
        if(_players.containsKey(event.getPlayer().getUniqueId().toString())) {
            player = _players.get(event.getPlayer().getUniqueId().toString());
            player.updateLocation(event.getPlayer().getLocation().getX(), event.getPlayer().getLocation().getY(), event.getPlayer().getLocation().getZ());
            notifyChange();
        }
    }

    @EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) 
	{
        if(!isEnabled()) return;
        LPlayer player = null;
        if(_players.containsKey(event.getPlayer().getUniqueId().toString())) {
            player = _players.get(event.getPlayer().getUniqueId().toString());
            player.updateLastOnline(System.currentTimeMillis(), false);

            /*LAsset bank = player.getAsset("asset-bank-amount");
            bank.updateValue("5$");
            player.markDirty();*/

            notifyChange();
        } 
    }

    @Override
    public void onDisable() {
        _players.clear();
        super.onDisable();
    }

    @Override
    public IPacket onJoinAsync(String uuid) {
        JSONObject json = new JSONObject();
        JSONArray players = new JSONArray();

        synchronized(_players) {
            for(Entry<String,LPlayer> entry : _players.entrySet()) {
                if(entry.getKey().equals(uuid)) {
                    players.put(entry.getValue().serialize());
                } else {
                    players.put(entry.getValue().censor(entry.getValue().serialize()));
                }
            }
        }

        json.put("players", players);

        return new ModuleUpdatePacket(this, json, true);
    }

    @Override
    public Map<String,IPacket> onUpdateAsync(List<String> uuids) {
        Map<String, IPacket> responses = new HashMap<String,IPacket>();
        synchronized(_players) {
            for(String uuid : uuids) {
                JSONObject json = new JSONObject();
                JSONArray players = new JSONArray();
                for(Entry<String,LPlayer> entry : _players.entrySet()) {
                    if(!entry.getValue().hasChanges()) continue;

                    if(entry.getKey().equals(uuid)) {
                        players.put(entry.getValue().serializeChanges());
                    } else {
                        players.put(entry.getValue().censor(entry.getValue().serializeChanges()));
                    }
                }
                json.put("players", players);
            
                responses.put(uuid, new ModuleUpdatePacket(this, json, false));
            //return new ModuleUpdatePacket(this, json);
           }
           for(LiveSyncable l : _players.values()) l.clearChanges();
        }
        return responses;
    }

    @Override
    public IPacket onChangeAsync(String uuid, IPacket packet) {
        return null;
    }

    public static class LPlayer extends LiveSyncable {
        public String head;
        public String name = "Unknown";
        public long lastOnline = 0;
        public long firstPlayed = 0;
        public boolean online = false;
        public String world = "Unknown";
        public double x = 0;
        public double y = 0;
        public double z = 0;
        public double health = 0;
        public int armor = 0;
        public double exhaustion = 0;

        public List<LAsset> assets = new ArrayList<LAsset>();
        public List<LMapAsset> mapAssets = new ArrayList<LMapAsset>();

        public LPlayer(String uuid) {
            super(uuid);
        }

        private LAsset getAsset(String uuid) {
            for(LAsset a : assets) {
                if(a.getUUID().equals(uuid)) {
                    return a;
                }
            }
            return null;
        }

        private LMapAsset getMapAsset(String uuid) {
            for(LMapAsset a : mapAssets) {
                if(a.getUUID().equals(uuid)) {
                    return a;
                }
            }
            return null;
        }

        private void updateLastOnline(long lastOnline, boolean online) {
            this.lastOnline = lastOnline;
            this.online = online;
            this.markDirty("lastOnline", "online");
        }

        private void updateWorld(String world) {
            this.world = world;
            this.markDirty("world");
        }
        public void updateLocation(double x, double y, double z){
            this.x = x;
            this.y = y;
            this.z = z;
            this.markDirty("x", "y", "z");
        }
    
        public void updateHead(String head) {
            this.head = head;
            this.markDirty("head");
        }
    
        public void updateArmor(int armor) {
            this.armor = armor;
            this.markDirty("armor");
        }
    
        public void updateHealth(double health) {
            this.health = health;
            this.markDirty("health");
        }
    
        public void updateExhaustion(double exhaustion) {
            this.exhaustion = exhaustion;
            this.markDirty("exhaustion");
        }

        private JSONObject censor(JSONObject json) {
            json.remove("firstPlayed");
            return json;
        }

        private static LPlayer fromOfflinePlayer(OfflinePlayer player) {
            LPlayer p = new LPlayer(player.getUniqueId().toString());
            p.name = player.getName();
            p.lastOnline = player.getLastPlayed();
            p.firstPlayed = player.getFirstPlayed();
            p.online = player.isOnline();

            if(!HeadLibrary.has(player.getUniqueId().toString())) { 
                HeadLibrary.resolveAsync(player.getUniqueId().toString());
            } 
            p.updateHead(HeadLibrary.get(player.getUniqueId().toString()));

            return p;
        }
    }

    public static class LAsset extends LiveSyncable {
        public String name;
        public String value;

        public LAsset(String uuid, String name, String value){
            super(uuid);
            this.name = name;
            this.value = value;
            markDirty("name", "value");
        }

        public void updateValue(String value) {
            this.value = value;
            markDirty("value");
        }
    }

    public static class LMapAsset extends LiveSyncable {
        public String name;
        public String description;
        public String icon;
        public double x,y,z;

        public LMapAsset(String uuid, String name, String description, String icon) {
            super(uuid);
            this.name = name;
            this.description = description;
            this.icon = icon;
            markDirty("name", "description", "icon");
        }

        public void updatePosition(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            markDirty("x", "y", "z");
        }
    }
}
