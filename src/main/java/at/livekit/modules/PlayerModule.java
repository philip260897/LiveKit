package at.livekit.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.json.JSONArray;
import org.json.JSONObject;

import at.livekit.livekit.Identity;
import at.livekit.modules.BaseModule.Action;
import at.livekit.plugin.Plugin;
import at.livekit.packets.ActionPacket;
import at.livekit.packets.IPacket;
import at.livekit.packets.StatusPacket;
import at.livekit.utils.HeadLibrary;

public class PlayerModule extends BaseModule implements Listener
{
    private Map<String,LPlayer> _players = new HashMap<String, LPlayer>();

    public PlayerModule(ModuleListener listener) {
        super(1, "Players", "livekit.basics.map", UpdateRate.HIGH, listener, true);
    }
     
    public LPlayer getPlayer(String uuid) {
        return _players.get(uuid);
    }

    public LPlayer getPlayerByName(String name) {
        synchronized(_players) {
            for(LPlayer p : _players.values()) {
                if(p.name.equalsIgnoreCase(name)) return p;
            }
        }
        return null;
    }

    @Override
    public void update() {
        boolean needsUpdate = false;

        for(LPlayer player : _players.values()) {
            Player p = Bukkit.getPlayer(UUID.fromString(player.uuid));
            if(p != null) {
                if(p.getHealth() != player.health) needsUpdate = true;
                if(p.getHealthScale() != player.healthMax) needsUpdate = true;
                if(p.getExhaustion() != player.exhaustion) needsUpdate = true;

                player.updateExhaustion(p.getExhaustion());
                player.updateHealth(p.getHealth(), p.getHealthScale());
            }
        }
        
        if(needsUpdate) notifyChange();
        super.update();
    }

    @Override
    public void onEnable() {
        for(OfflinePlayer player : Bukkit.getOfflinePlayers()) {   
            _players.put(player.getUniqueId().toString(), LPlayer.fromOfflinePlayer(player));
        }
        /*for(Player player : Bukkit.getOnlinePlayers()) {
            if(!_players.containsKey(player.getUniqueId().toString())) {
                _players.put(player.getUniqueId().toString(), LPlayer.fromOfflinePlayer(player));
            }
        }*/
        Bukkit.getServer().getPluginManager().registerEvents(this, Plugin.getInstance());
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
            _players.put(player.uuid, player);
        }
        //player.updateLastOnline(System.currentTimeMillis(), true);
        player.updateWorld(event.getPlayer().getLocation().getWorld().getName());
		player.updateLocation(p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ());
		player.updateHealth(p.getHealth(), p.getHealthScale());
        //player.updateArmor(p.getArmor);
		player.updateExhaustion(p.getExhaustion());

        if(!HeadLibrary.has(p.getName())) { 
			HeadLibrary.resolveAsync(p.getName());
		} 
		player.updateHead(HeadLibrary.get(p.getName()));
        player.updateOnline(true);

        ItemStack itemInHand = p.getInventory().getItemInMainHand();
        if(itemInHand != null && itemInHand.getAmount() != 0) {
            player.updateItemHeld(itemInHand.getType().name(), itemInHand.getAmount());
        }
       // player.assets.add(new LAsset("asset-bank-amount", "Bank Amount", "0$"));
        //player.markDirty();
        //notifyFull();
        notifyChange();
    }

    @EventHandler
    public void onPlayerItemHeldEvent(PlayerItemHeldEvent event) {
        
        
        if(_players.containsKey(event.getPlayer().getUniqueId().toString())) {
            LPlayer player = _players.get(event.getPlayer().getUniqueId().toString());
            
            //ItemStack itemInHand = event.getPlayer().getInventory().getItemInMainHand();
            ItemStack itemInHand = event.getPlayer().getInventory().getItem(event.getNewSlot());
            if(itemInHand != null && itemInHand.getAmount() != 0) {
                player.updateItemHeld(itemInHand.getType().name(), itemInHand.getAmount());
                notifyChange();
            } else {
                player.updateItemHeld(null, 0);
                notifyChange();
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if(_players.containsKey(event.getPlayer().getUniqueId().toString())) {
            LPlayer player = _players.get(event.getPlayer().getUniqueId().toString());
            player.onBlockBreak(event.getBlock().getType());
            notifyChange();
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if(_players.containsKey(event.getPlayer().getUniqueId().toString())) {
            LPlayer player = _players.get(event.getPlayer().getUniqueId().toString());
            player.onBlockPlace(event.getBlock().getType());
            notifyChange();
        }
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
            player.updateOnline(false);
            //player.updateLastOnline(System.currentTimeMillis(), false);

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
    public IPacket onJoinAsync(Identity identity) {
        JSONObject json = new JSONObject();
        JSONArray players = new JSONArray();

        synchronized(_players) {
            for(Entry<String,LPlayer> entry : _players.entrySet()) {
                if(!entry.getValue().online && !identity.hasPermission("livekit.admin.myadmin")) continue;

                JSONObject j = entry.getValue().toJson();
                j.remove("actions");
                players.put(j);
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
                for(LPlayer player : _players.values()) {
                    if(!player.online && !identity.hasPermission("livekit.admin.myadmin")) continue; 
                    
                    JSONObject jp;
                    if(player.isDirty()) jp = player.toJson();
                    else {
                        jp = new JSONObject();
                        jp.put("uuid", player.uuid);
                    }
                    if(!player.headDirty)jp.remove("head");
                    
                    players.put(jp);
                }
                json.put("players", players);
                responses.put(identity, new ModuleUpdatePacket(this, json, false));
            //return new ModuleUpdatePacket(this, json);
           }
           for(LPlayer l : _players.values()) {
                l.clearPlayerActions();
                l.clean();
                //l.clearChanges();
           }
           
        }
        return responses;
    }

    @Override
    public IPacket onChangeAsync(Identity identity, IPacket packet) {
        return null;
    }

    @Action(name = "GetPlayerInfo")
    public IPacket actionPlayerInfo(Identity identity, ActionPacket packet) {
        String uuid = packet.getData().getString("uuid");
        OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
        if(player == null) return new StatusPacket(0, "Player not found");
        if(!player.isOnline() && !identity.hasPermission("livekit.admin.myadmin")) return new StatusPacket(0, "Permission denied");
        
        JSONObject response = new JSONObject();
        if(identity.hasPermission("livekit.admin.myadmin") || uuid.equals(identity.getUuid())) {
            response.put("firstPlayed", player.getFirstPlayed());
            response.put("lastPlayed", player.getLastPlayed());
            response.put("banned", player.isBanned());

            JSONArray locationData = new JSONArray();
            response.put("locations", locationData);
            Location bedspawn = player.getBedSpawnLocation();
            if(bedspawn != null) {
                JSONObject bedlocation = new JSONObject();
                bedlocation.put("type", "loc");
                bedlocation.put("name", "Bed Spawn");
                bedlocation.put("x", bedspawn.getBlockX());
                bedlocation.put("y", bedspawn.getBlockY());
                bedlocation.put("z", bedspawn.getBlockZ());
                locationData.put(bedlocation);
            }
        }

        return packet.response(response);
    }

    public static class LItem implements Serializable {
        public int amount;
        public String item;

        public LItem(String item, int amount) {
            this.item = item;
            this.amount = amount;
        }

        @Override
        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("item", item);
            json.put("amount", amount);
            return json;
        }
    }

    public static class PlayerAction implements Serializable {
        private int action;
        private Serializable data;
        public PlayerAction(int action, Serializable data) {
            this.action = action;
            this.data = data;
        }

        @Override
        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("action", action);
            json.put("data", data.toJson());
            return json;
        }
    }

    public static class LPlayer implements Serializable{
        private boolean dirty = true;
        private boolean headDirty = true;

        public String uuid;
        public String name = "Unknown";
        public String head;
        public boolean online = false;
        public String world = "Unknown";
        public double x = 0;
        public double y = 0;
        public double z = 0;

        public double healthMax = 0;
        public double health;
        public double armor = 0;
        public double exhaustion = 0;
        public LItem itemHeld;

        public List<PlayerAction> actions = new ArrayList<PlayerAction>();

       // public long lastOnline = 0;
       // public long firstPlayed = 0;
        //public List<LItem> items = new ArrayList<LItem>();
        //public List<AssetGroup> assetGroups = new ArrayList<AssetGroup>();

        public LPlayer(String uuid) {
            this.uuid = uuid;
        }

        public void onBlockBreak(Material type) {
            JSONObject data = new JSONObject();
            data.put("item", type.name());
            synchronized(actions) {                
                actions.add(new PlayerAction(0, new LItem(type.name(), 1)));
            }
            dirty = true;
        }

        public void onBlockPlace(Material type) {
            JSONObject data = new JSONObject();
            data.put("item", type.name());
            synchronized(actions) {                
                actions.add(new PlayerAction(1, new LItem(type.name(), 1)));
            }
            dirty = true;
        }

        public void updateOnline(boolean online) {
            this.online = online;
            this.dirty = true;
        }

        public void clearPlayerActions() {
            synchronized(actions) {
                actions.clear();
            }
        }

        /*public PlayerAsset getAsset(String uuid) {
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
        }*/

        public void updateItemHeld(String item, int amount) {
            itemHeld = new LItem(item, amount);
            dirty = true;
        }



       /* private void updateLastOnline(long lastOnline, boolean online) {
            this.lastOnline = lastOnline;
            this.online = online;
            this.markDirty("lastOnline", "online");
        }*/

        private void updateWorld(String world) {
            this.world = world;
            this.dirty = true;
        }
        public void updateLocation(double x, double y, double z){
            this.x = x;
            this.y = y;
            this.z = z;
            this.dirty = true;
        }
    
        public void updateHead(String head) {
            this.head = head;
            this.dirty = true;
            this.headDirty = true;
        }
    
        public void updateArmor(double armor) {
            this.armor = armor;
            this.dirty = true;
        }
    
        public void updateHealth(double health, double healthMax) {
            this.health = health;
            this.healthMax = healthMax;
            this.dirty = true;
        }
    
        public void updateExhaustion(double exhaustion) {
            this.exhaustion = exhaustion;
            this.dirty = true;
        }

        private static LPlayer fromOfflinePlayer(OfflinePlayer player) {
            LPlayer p = new LPlayer(player.getUniqueId().toString());
            p.name = player.getName();
            p.online = player.isOnline();
            if(!HeadLibrary.has(player.getName())) { 
                HeadLibrary.resolveAsync(player.getName());
            } 
            p.updateHead(HeadLibrary.get(player.getName()));
            

            if(player.getPlayer() != null) {
                Player online = player.getPlayer();
                p.updateLocation(online.getLocation().getX(), online.getLocation().getY(), online.getLocation().getZ());
                p.updateWorld(online.getLocation().getWorld().getName());
                p.updateHealth(online.getHealth(), online.getHealthScale());
                p.updateExhaustion(online.getExhaustion());
                ItemStack itemInHand = online.getInventory().getItemInMainHand();
                if(itemInHand != null && itemInHand.getAmount() != 0) {
                    p.updateItemHeld(itemInHand.getType().name(), itemInHand.getAmount());
                }
                
            }

            return p;
        }

        public boolean isDirty() {
            return dirty || headDirty;
        }

        public void clean() {
            dirty = false;
            headDirty = false;
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("uuid", uuid);
            json.put("name", name);
            json.put("head", head);
            json.put("online", online);
            json.put("x", (float)x);
            json.put("y", (float)y);
            json.put("z", (float)z);
            json.put("health", (float)health);
            json.put("healthMax", (float)healthMax);
            json.put("armor", armor);
            json.put("exhaustion", exhaustion);
            if(itemHeld != null) json.put("itemHeld", itemHeld.toJson());
            if(actions != null) {
                synchronized(actions) {
                    json.put("actions", actions.stream().map(action->action.toJson()).collect(Collectors.toList()));
                }
            }
            return json;
        }
    }

    /*public static class AssetGroup extends Syncable {
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
    }*/
}
