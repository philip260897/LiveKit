package at.livekit.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.json.JSONArray;
import org.json.JSONObject;

import at.livekit.livekit.Identity;
import at.livekit.plugin.Plugin;
import at.livekit.packets.IPacket;
import at.livekit.utils.HeadLibrary;
import at.livekit.utils.Utils;

public class PlayerModule extends BaseModule implements Listener
{
    private Map<String,LPlayer> _players = new HashMap<String, LPlayer>();

    public PlayerModule(ModuleListener listener) {
        super(1, "Players", "livekit.basics.map", UpdateRate.NEVER, listener, true);
    }

   /* @Override
    public void update() {
        boolean full = false;
        //System.out.println(_players.size());
        synchronized(_players) {
            for(Entity e : Bukkit.getWorld("world").getEntities()) {
                if(!(e instanceof LivingEntity)) continue;
                LivingEntity living = (LivingEntity) e;
                //if(e instanceof Chicken) {
                   // System.out.println("Updating chickens");
                   if(e.isDead() || living.isSleeping()) {
                       if(_players.remove(e.getUniqueId().toString()) != null)
                        full = true;
                       continue;
                       
                   }
                   
                    LPlayer chicken = _players.get(e.getUniqueId().toString());
                    if(chicken == null ) {
                        if(_players.size() > 500) continue;
                        chicken = new LPlayer(e.getUniqueId().toString());
                        chicken.name = e.getType().name();
                        chicken.online = true;
                        chicken.updateWorld("world");
                        chicken.updateHead(HeadLibrary.DEFAULT_HEAD);
                        _players.put(e.getUniqueId().toString(), chicken);
                        full = true;
                    }
                    chicken.updateLocation(e.getLocation().getX(), e.getLocation().getY(), e.getLocation().getZ());
                //}
            }
        }
        if(full) notifyFull(); else notifyChange();
        super.update();
    }*/
     
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
        notifyFull();
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

            //notifyChange();
            notifyFull();
        } 
    }

    @EventHandler
    public void onPlayerInteractEvent(PlayerInteractEvent event) {
        if(!isEnabled()) return;
        if(event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        System.out.println("right");
        LPlayer player = getPlayer(event.getPlayer().getUniqueId().toString());
        if(player != null) {
            if(Utils.isBed(event.getClickedBlock().getType())) {
                System.out.println("is bed");
                if(event.getPlayer().getBedSpawnLocation() != null) {
                    MapAsset spawnloc = (MapAsset) player.getAsset("spawn-bed");
                    if(spawnloc == null) {
                        AssetGroup locations = player.getAssetGroup("Locations");
                        spawnloc = new MapAsset("spawn-bed", "Bed Spawn", "Bed spawn location", null);
                        locations.addPlayerAsset(spawnloc);
                    }
                    System.out.println("spwn updated");
                    spawnloc.updatePosition(event.getPlayer().getBedSpawnLocation());
                    player.markDirty();
                    notifyChange();
                }
            }
        }
    }

    @Override
    public void onDisable() {
        _players.clear();
        super.onDisable();
    }

    @Override
    public IPacket onJoinAsync(Identity identity) {
        JSONObject json = new JSONObject();
        JSONArray players = new JSONArray();

        synchronized(_players) {
            for(Entry<String,LPlayer> entry : _players.entrySet()) {
                if(!entry.getValue().online) continue; //TODO: check for offline player permissions

                if(entry.getKey().equals(identity.getUuid()) || identity.hasPermission("livekit.players.other")) {
                    players.put(entry.getValue().serialize(true));
                } else {
                    players.put(entry.getValue().censor(entry.getValue().serialize(true)));
                }
            }
        }

        json.put("players", players);

        return new ModuleUpdatePacket(this, json, true);
    }

    @Override
    public Map<Identity,IPacket> onUpdateAsync(List<Identity> identities) {
        Map<Identity, IPacket> responses = new HashMap<Identity,IPacket>();
        synchronized(_players) {
            for(Identity identity : identities) {

                JSONObject json = new JSONObject();
                JSONArray players = new JSONArray();
                for(Entry<String,LPlayer> entry : _players.entrySet()) {
                    //if(!entry.getValue().hasChanges()) continue;
                    if(!entry.getValue().online) continue; //TODO: check for offline player permissions

                    if(entry.getKey().equals(identity.getUuid()) || identity.hasPermission("livekit.players.other")) {
                        players.put(entry.getValue().serialize(false));
                    } else {
                        players.put(entry.getValue().censor(entry.getValue().serialize(false)));
                    }
                }
                json.put("players", players);
                responses.put(identity, new ModuleUpdatePacket(this, json, false));
            //return new ModuleUpdatePacket(this, json);
           }
           for(Syncable l : _players.values()) l.clearChanges();
        }
        return responses;
    }

    @Override
    public IPacket onChangeAsync(Identity identity, IPacket packet) {
        return null;
    }

    public static class LPlayer extends Syncable {
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

        public List<AssetGroup> assetGroups = new ArrayList<AssetGroup>();

        public LPlayer(String uuid) {
            super(uuid);
        }

        public PlayerAsset getAsset(String uuid) {
            synchronized(assetGroups) {
                for(AssetGroup a : assetGroups) {
                    PlayerAsset asset = a.getAssetByUUID(uuid);
                    if(asset != null) return asset;
                }
            }
            return null;
        }

        public AssetGroup getAssetGroup(String name) {
            synchronized(assetGroups) {
                for(AssetGroup a : assetGroups) {
                    if(a.name.equals(name)) {
                        return a;
                    }
                }
            }
            return null;
        }

        public void removeAssetGroup(AssetGroup group) {
            synchronized(assetGroups) {
                assetGroups.remove(group);
            }
        }

        public void addAssetGroup(AssetGroup group) {
            synchronized(assetGroups) {
                assetGroups.add(group);
            }
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

            AssetGroup locations = new AssetGroup("Locations"); 
            if(player.getBedSpawnLocation() != null) {
                MapAsset bedspawn = new MapAsset("spawn-bed", "Bed Spawn", "Bed spawn location", null);
                bedspawn.updatePosition(player.getBedSpawnLocation());
                locations.addPlayerAsset(bedspawn);
            }

            p.assetGroups.add(locations);

            return p;
        }
    }

    public static class AssetGroup extends Syncable {
        public String name;
        public List<PlayerAsset> assets = new ArrayList<PlayerAsset>();

        public AssetGroup(String name) {
            super(name);
            this.name = name;
        }

        public PlayerAsset getAssetByUUID(String uuid) {
            synchronized(assets) {
                for(PlayerAsset asset : assets) {
                    if(asset.getUUID().equals(uuid)) return asset;
                }
            }
            return null;
        }

        public void addPlayerAsset(PlayerAsset asset) {
            synchronized(assets) {
                assets.add(asset);
            }
        }

        public void removePlayerAsset(PlayerAsset asset) {
            synchronized(assets) {
                assets.remove(asset);
            }
        }
    }

    public static class PlayerAsset extends Syncable {
        public PlayerAsset(String uuid) {
            super(uuid);
        }
    }

    public static class ValueAsset extends PlayerAsset {

        public String name;
        public String value;

        public ValueAsset(String uuid, String name, String value) {
            super(uuid);
            this.name = name;
            this.value = value;
        }

        public void updateValue(String value) {
            this.value = value;
            markDirty("value");
        }
    }

    public static class MapAsset extends PlayerAsset {
        public String name;
        public String description;
        public String icon;
        public double x;
        public double y;
        public double z;

        public MapAsset(String uuid, String name, String description, String icon){
            super(uuid);
            this.name = name;
            this.icon = icon;
        }

        public void updatePosition(Location location) {
            this.updatePosition(location.getX(), location.getY(), location.getZ());
        }

        public void updatePosition(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            markDirty("x", "y", "z");
        }
    }
}
