package at.livekit.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.json.JSONArray;
import org.json.JSONObject;

import at.livekit.api.core.Color;
import at.livekit.api.core.Privacy;
import at.livekit.api.map.AsyncPlayerInfoProvider;
import at.livekit.api.map.InfoEntry;
import at.livekit.api.map.PersonalPin;
import at.livekit.api.map.PlayerInfoProvider;
import at.livekit.api.map.Waypoint;
import at.livekit.livekit.Identity;
import at.livekit.plugin.Plugin;
import at.livekit.packets.ActionPacket;
import at.livekit.packets.IPacket;
import at.livekit.packets.StatusPacket;
import at.livekit.utils.HeadLibraryV2;
import at.livekit.utils.Utils;

public class PlayerModule extends BaseModule implements Listener
{

    private List<PlayerInfoProvider> _infoProviders = new ArrayList<PlayerInfoProvider>();
    private Map<String,LPlayer> _players = new HashMap<String, LPlayer>();

    private List<String> _downstreamUpdate = new ArrayList<String>();

    public PlayerModule(ModuleListener listener) {
        super(1, "Players", "livekit.module.players", UpdateRate.MAX, listener);
    }
     
    public void clearProviders() {
        synchronized(_infoProviders) {
            _infoProviders.clear();
        }
    }

    public void addInfoProvider(PlayerInfoProvider provider) {
        synchronized(_infoProviders) {
            if(!_infoProviders.contains(provider)) {
                _infoProviders.add(provider);
            }
        }
    }

    public void removeInfoProvider(PlayerInfoProvider provider) {
        synchronized(_infoProviders) {
            if(_infoProviders.contains(provider)) {
                _infoProviders.remove(provider);
            }
        }
    }

    public void notifyDownstream(OfflinePlayer player) {
        synchronized(_downstreamUpdate) {
            _downstreamUpdate.add(player.getUniqueId().toString());
        }
        notifyChange();
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
        Location _loc = null;
        boolean _visible = true;
        PlayerInventory _inventory;

        long start = System.currentTimeMillis();

        for(LPlayer player : _players.values()) {
            Player p = Bukkit.getPlayer(UUID.fromString(player.uuid));
            if(p != null) {
                _loc = p.getLocation();
                _visible = !p.hasPotionEffect(PotionEffectType.INVISIBILITY) && !Utils.isVanished(p);

                if(p.getHealth() != player.health) needsUpdate = true;
                if(p.getHealthScale() != player.healthMax) needsUpdate = true;
                if(p.getExhaustion() != player.exhaustion) needsUpdate = true;
                if(_loc.getX() != player.x || _loc.getY() != player.y || _loc.getZ() != player.z || _loc.getYaw() != player.dir) needsUpdate = true;
                if(_visible != player.visible) needsUpdate = true;
                if(p.getFoodLevel() != player.foodLevel) needsUpdate = true;


                _inventory = p.getInventory();
                player.updateExhaustion(p.getExhaustion());
                player.updateHealth(p.getHealth(), p.getHealthScale());
                player.updateLocation(_loc.getX(), _loc.getY(), _loc.getZ(), _loc.getYaw() );
                player.updateVisible(_visible);
                player.updateFoodLevel(p.getFoodLevel());

                if(player.needsArmorUpdate(_inventory)) {
                    needsUpdate = true;
                    player.updateArmor(_inventory.getHelmet(), _inventory.getChestplate(), _inventory.getLeggings(), _inventory.getBoots());
                }

                if(player.needsItemUpdate(player.itemHeld, _inventory.getItemInMainHand())) {
                    ItemStack held = _inventory.getItemInMainHand();
                    player.updateItemHeld(held);
                    needsUpdate = true;
                }
            }
        }

        

       /*synchronized(_players) {
            //List<LivingEntity> living = ;
            //System.out.println(living.size());
            for(Entity e : Bukkit.getWorld("world").getEntities()) {
                if(! (e instanceof Player) && (e instanceof LivingEntity)) {
                    LivingEntity entity = (LivingEntity) e;

                    if(entity.isDead()) {
                        if(_players.containsKey(entity.getUniqueId().toString())) _players.remove(entity.getUniqueId().toString());
                    } else {
                        LPlayer player = null;
                        if(!_players.containsKey(entity.getUniqueId().toString())) {
                            if(_players.size() >= 500) continue;

                            player = new LPlayer(entity.getUniqueId().toString());
                            player.head = HeadLibraryV2.DEFAULT;
                            player.headDirty = true;
                            player.name = entity.getType().name();
                            player.online = true;
                            player.updateWorld(entity.getWorld().getName());
                            player.updateExhaustion(0);
                            player.updateArmor(0);
                            _players.put(entity.getUniqueId().toString(), player);
                        } else {
                            player = _players.get(entity.getUniqueId().toString());
                        }
                        if(player.health != entity.getHealth()) player.updateHealth(entity.getHealth(), entity.getMaxHealth());
                        if(player.x != entity.getLocation().getX() || player.y != entity.getLocation().getY() || player.z != entity.getLocation().getZ())
                            player.updateLocation(entity.getLocation().getX(), entity.getLocation().getY(), entity.getLocation().getZ(), entity.getLocation().getYaw());
                        
                        if(player.isDirty()) needsUpdate = true;
                    }
                }
            }
        }*/

        //if(System.currentTimeMillis() - start != 0) System.out.println("Player polling took: "+(System.currentTimeMillis()-start)+"ms");
        
        if(needsUpdate) notifyChange();
        super.update();
    }

    @Override
    public void onEnable(Map<String,ActionMethod> signature) {
        for(OfflinePlayer player : Bukkit.getOfflinePlayers()) {   
            _players.put(player.getUniqueId().toString(), LPlayer.fromOfflinePlayer(player));
        }
        /*for(Player player : Bukkit.getOnlinePlayers()) {
            if(!_players.containsKey(player.getUniqueId().toString())) {
                _players.put(player.getUniqueId().toString(), LPlayer.fromOfflinePlayer(player));
            }
        }*/
        Bukkit.getServer().getPluginManager().registerEvents(this, Plugin.getInstance());
        
        super.onEnable(signature);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if(!isEnabled() || event.isCancelled()) return;

        if(event.getHand() == EquipmentSlot.HAND) {
            if(event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
                //
            }
        }
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
		player.updateLocation(p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ(), p.getLocation().getYaw() );
		player.updateHealth(p.getHealth(), p.getHealthScale());
        //player.updateArmor(p.getArmor);
		player.updateExhaustion(p.getExhaustion());
        player.updateVisible(!p.hasPotionEffect(PotionEffectType.INVISIBILITY));
        
        PlayerInventory inventory = p.getInventory();
        player.updateArmor(inventory.getHelmet(), inventory.getChestplate(), inventory.getLeggings(), inventory.getBoots());
        

        /*if(!HeadLibrary.has(p.getName())) { 
			HeadLibrary.resolveAsync(p.getName());
		} */
		player.updateHead(HeadLibraryV2.get(p.getName(), p.isOnline()));
        player.updateOnline(true);

        ItemStack itemInHand = p.getInventory().getItemInMainHand();
        if(itemInHand != null && itemInHand.getAmount() != 0) {
            player.updateItemHeld(itemInHand);
        }
       // player.assets.add(new LAsset("asset-bank-amount", "Bank Amount", "0$"));
        //player.markDirty();
        //notifyFull();
        notifyChange();
    }

    /*@EventHandler
    public void onPlayerItemHeldEvent(PlayerItemHeldEvent event) {
        updateItemInHand(event.getPlayer(), event.getPlayer().getInventory().getItem(event.getNewSlot()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerPickupItemEvent(EntityPickupItemEvent event) {
        if(event.getEntity() instanceof Player) {
            Player p = (Player)event.getEntity();

            
        }
    } */

    /*private void updateItemInHand(Player p, ItemStack itemInhand) {
        if(_players.containsKey(p.getUniqueId().toString())) {
            LPlayer player = _players.get(p.getUniqueId().toString());
            
            if(itemInHand != null && itemInHand.getAmount() != 0) {
                player.updateItemHeld(itemInHand.getType().name(), itemInHand.getAmount());
                notifyChange();
            } else {
                player.updateItemHeld(null, 0);
                notifyChange();
            }
        }
    }*/

    @EventHandler
    public void onPlayerChangedWorldEvent(PlayerChangedWorldEvent event) {
        if(_players.containsKey(event.getPlayer().getUniqueId().toString())) {
            LPlayer player = _players.get(event.getPlayer().getUniqueId().toString());
            player.updateWorld(event.getPlayer().getWorld().getName());
            notifyChange();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityPotionEffectEvent(EntityPotionEffectEvent event) {
        if(_players.containsKey(event.getEntity().getUniqueId().toString())) {
            
            if(event.getEntity() instanceof OfflinePlayer) {
                notifyDownstream((OfflinePlayer)event.getEntity());
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
            player.updateLocation(event.getPlayer().getLocation().getX(), event.getPlayer().getLocation().getY(), event.getPlayer().getLocation().getZ(), event.getPlayer().getLocation().getYaw());
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
    public void onDisable(Map<String,ActionMethod> signature) {
        HandlerList.unregisterAll(this);
        
        _players.clear();
        super.onDisable(signature);
    }

    @Override
    public IPacket onJoinAsync(Identity identity) {
        JSONObject json = new JSONObject();
        JSONArray players = new JSONArray();

        synchronized(_players) {
            for(Entry<String,LPlayer> entry : _players.entrySet()) {
                if(!entry.getValue().uuid.equals(identity.getUuid())) {
                    if(!entry.getValue().uuid.equals(identity.getUuid()) && !identity.hasPermission("livekit.players.other")) continue;
                    if(!entry.getValue().online && !identity.hasPermission("livekit.module.admin")) continue;
                    if(!entry.getValue().visible && !entry.getValue().uuid.equals(identity.getUuid()) && !identity.hasPermission("livekit.module.admin")) continue;
                }
            
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
        
        JSONArray downstreamUpdate = new JSONArray();
        synchronized(_downstreamUpdate) {
            for(String s : _downstreamUpdate) downstreamUpdate.put(s);
            _downstreamUpdate.clear();
        }
        
        synchronized(_players) {

            for(Identity identity : identities) {
                JSONObject json = new JSONObject();
                JSONArray players = new JSONArray();
                for(LPlayer player : _players.values()) {
                    if(!player.uuid.equals(identity.getUuid())) {
                        if(!player.uuid.equals(identity.getUuid()) && !identity.hasPermission("livekit.players.other")) continue;
                        if(!player.online && !identity.hasPermission("livekit.module.admin")) continue; 
                        if(!player.visible && !player.uuid.equals(identity.getUuid()) && !identity.hasPermission("livekit.module.admin")) continue;
                    }
                    
                    
                    JSONObject jp;
                    if(player.isDirty()) jp = player.toJson();
                    else {
                        jp = new JSONObject();
                        jp.put("uuid", player.uuid);
                    }
                    if(!player.headDirty)jp.remove("head");
                    if(!player.armorDirty)jp.remove("armorItems");
                    
                    players.put(jp);
                }
                json.put("players", players);
                json.put("downstream", downstreamUpdate);
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

    private Waypoint getWaypointByUUID(String playerUUID, UUID waypointUuid) {
        LPlayer player = getPlayer(playerUUID);
        if(player == null || player._cachedWaypoints == null) return null;

        synchronized(player._cachedWaypoints) {
            for(Waypoint waypoint : player._cachedWaypoints) {
                if(waypoint.getUUID().equals(waypointUuid)) {
                    return waypoint;
                }
            }
        }

        return null;
    }

    @Action(name="Teleport")
    protected IPacket teleportWaypoint(Identity identity, ActionPacket packet) {
        String wp = packet.getData().getString("waypoint");
        Waypoint waypoint = getWaypointByUUID(identity.getUuid(), UUID.fromString(wp));

        if(waypoint == null) return new StatusPacket(0, "Waypoint not found!");
        if(waypoint.canTeleport() == false && !identity.hasPermission("livekit.module.admin")) return new StatusPacket(0, "Can't teleport to this waypoint");

        OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(identity.getUuid()));
        if(player == null || !player.isOnline()) return new StatusPacket(0, "Player is offline");

        Player online = player.getPlayer();
        Location location = waypoint.getLocation().toLocation();
        if(location == null) return new StatusPacket(0, "Location does not exist");

        online.teleport(location);

        return new StatusPacket(1);
    }

    @Action(name = "GetPlayerInfo", sync = false)
    public IPacket actionPlayerInfo(Identity identity, ActionPacket packet) {
        String uuid = packet.getData().getString("uuid");
        OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
        if(player == null) return new StatusPacket(0, "Player not found");
        if(!player.isOnline() && !identity.hasPermission("livekit.module.admin")) return new StatusPacket(0, "Permission denied");

        JSONObject response = new JSONObject();
        JSONArray infoData = new JSONArray();
        response.put("info", infoData);

        JSONArray locationData = new JSONArray();
        response.put("locations", locationData);

        JSONArray potions = new JSONArray();
        response.put("potions", potions);

        List<InfoEntry> _infos = new ArrayList<>();
        List<PersonalPin> _waypoints = new ArrayList<>();

        try{
        
            Bukkit.getScheduler().callSyncMethod(Plugin.getInstance(), new Callable<Void>(){
                @Override
                public Void call() throws Exception {
                    Player onlinePlayer = (player.isOnline() ?  player.getPlayer() : null);

                    if(identity.hasPermission("livekit.module.admin") || uuid.equals(identity.getUuid())) {
                        response.put("firstPlayed", player.getFirstPlayed());
                        response.put("lastPlayed", player.getLastPlayed());
                        response.put("banned", player.isBanned());
                        if(onlinePlayer != null) response.put("gamemode", onlinePlayer.getGameMode().name());
                    }

                    synchronized(_infoProviders) {
                        for(PlayerInfoProvider iprov : _infoProviders) {
                            if(iprov instanceof AsyncPlayerInfoProvider) continue;
                            if(iprov.getPermission() != null && !identity.hasPermission(iprov.getPermission()) && !identity.hasPermission("livekit.module.admin")) continue;

                            iprov.onResolvePlayerInfo(player, _infos);
                            iprov.onResolvePlayerLocation(player, _waypoints);
                        }
                    }
                    return null;
                }
            }).get();

        }catch(Exception ex){ex.printStackTrace();}

        LPlayer lplayer = getPlayer(uuid);
        if(lplayer == null) return new StatusPacket(0, "Invalid Player requested");

        synchronized(_infoProviders) {
            for(PlayerInfoProvider iprov : _infoProviders) {
                if(!(iprov instanceof AsyncPlayerInfoProvider)) continue;
                if(iprov.getPermission() != null && !identity.hasPermission(iprov.getPermission()) && !identity.hasPermission("livekit.module.admin")) continue;

                iprov.onResolvePlayerInfo(player, _infos);
                iprov.onResolvePlayerLocation(player, _waypoints);
            }
        }
        synchronized(lplayer._cachedWaypoints) {
            lplayer._cachedWaypoints = _waypoints;
        }

        for(InfoEntry entry : _infos) {
            if(entry.getName() == null) continue;
            if(entry.getValue() == null) continue;
            if(entry.getPrivacy() == null) continue;
            
            if(entry.getPrivacy() == Privacy.PRIVATE) {
                if(!identity.hasPermission("livekit.module.admin") ) {
                    if(!uuid.equals(identity.getUuid())) continue;
                }
            }

            JSONObject jentry = new JSONObject();
            jentry.put("name", entry.getName());
            jentry.put("value", entry.getValue());
            jentry.put("priority", 50);
            infoData.put(jentry);
        }

        for(Waypoint waypoint : _waypoints) {
            if(waypoint.getLocation() == null) continue;
            if(waypoint.getName() == null) continue;
            if(waypoint.getPrivacy() == null) continue;
            
            if(waypoint.getPrivacy() == Privacy.PRIVATE) {
                if(!identity.hasPermission("livekit.module.admin") ) {
                    if(!uuid.equals(identity.getUuid())) continue;
                }
            }

            /*JSONObject bedlocation = new JSONObject();
            bedlocation.put("uuid", waypoint.getUUID().toString());
            bedlocation.put("name", waypoint.getName());
            bedlocation.put("description", waypoint.getDescription());
            bedlocation.put("x", waypoint.getLocation().getBlockX());
            bedlocation.put("y", waypoint.getLocation().getBlockY());
            bedlocation.put("z", waypoint.getLocation().getBlockZ());
            bedlocation.put("color", waypoint.getColor().getHEX());
            bedlocation.put("world", waypoint.getLocation().getWorld().getName());
            bedlocation.put("teleport", waypoint.canTeleport());*/
            locationData.put(waypoint.toJson());
        }
        
        if(player.isOnline()) {
            //adding potion info
            if(uuid.equals(identity.getUuid()) || identity.hasPermission("livekit.module.admin")) {
                for(PotionEffect potion : ((Player)player).getActivePotionEffects()) {
                    JSONObject jpotion = new JSONObject();
                    jpotion.put("type", potion.getType().getName());
                    jpotion.put("duration", (potion.getDuration()/20));
                    if(potion.getColor() != null) jpotion.put("color", Color.fromARGB(255, potion.getColor().getRed(), potion.getColor().getGreen(), potion.getColor().getBlue()).getHEX());
                    potions.put(jpotion);
                }
            }
        }

        return packet.response(response);
    }

    public static class LItem implements Serializable {
        public int amount;
        public int damage;
        public String item;

        public LItem(String item, int amount) {
            this(item, amount, 0);
        }

        public LItem(String item, int amount, int damage) {
            this.item = item;
            this.amount = amount;
            this.damage = damage;
        }

        @Override
        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("item", item);
            json.put("amount", amount);
            json.put("damage", damage);
            return json;
        }

        static LItem fromItemStack(ItemStack stack) {
            LItem item = new LItem(stack.getType().name(), stack.getAmount());
            if(stack.hasItemMeta() && (stack.getItemMeta() instanceof Damageable)) {
               item.damage = ((Damageable)stack.getItemMeta()).getDamage();
            }
            return item;
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

        private boolean armorDirty = true;
        public LItem armorHead;
        public LItem armorChest;
        public LItem armorLegs;
        public LItem armorBoots;

        public String uuid;
        public String name = "Unknown";
        public String head;
        public boolean online = false;
        public boolean visible = true;
        public String world = "Unknown";
        public double x = 0;
        public double y = 0;
        public double z = 0;
        public double dir = 0;

        public double healthMax = 0;
        public double health;
        public int foodLevel;
        public double armor = 0;
        public double exhaustion = 0;
        public LItem itemHeld;

        public List<PlayerAction> actions = new ArrayList<PlayerAction>();

        //used for caching player specific waypoints
        protected List<PersonalPin> _cachedWaypoints = new ArrayList<PersonalPin>();

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

        public void updateVisible(boolean visible) {
            if(visible != this.visible && visible == true) this.headDirty = true;
            this.visible = visible;
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

        public void updateItemHeld(ItemStack stack) {
            itemHeld = stack != null ? LItem.fromItemStack(stack) : null;
            dirty = true;
        }

        public void updateFoodLevel(int foodLevel) {
            this.foodLevel = foodLevel;
            this.dirty = true;
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
        public void updateLocation(double x, double y, double z, double dir){
            this.x = x;
            this.y = y;
            this.z = z;
            this.dir = dir;
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

        public void updateArmor(ItemStack head, ItemStack chest, ItemStack legs, ItemStack boots) {
            this.armorHead = head != null ? LItem.fromItemStack(head) : null;
            this.armorChest = chest != null ? LItem.fromItemStack(chest) : null;
            this.armorLegs = legs != null ? LItem.fromItemStack(legs) : null;
            this.armorBoots = boots != null ? LItem.fromItemStack(boots) : null;
            this.armorDirty = true;
            this.dirty = true;
        }

        public boolean needsArmorUpdate(PlayerInventory inventory) {
            if(needsItemUpdate(armorHead, inventory.getHelmet())) return true;
            if(needsItemUpdate(armorChest, inventory.getChestplate())) return true;
            if(needsItemUpdate(armorLegs, inventory.getLeggings())) return true;
            if(needsItemUpdate(armorBoots, inventory.getBoots())) return true;
            return false;
        }

        private boolean needsItemUpdate(LItem item, ItemStack stack) {
            if(item == null && stack != null) return true;
            if(item != null && stack == null) return true;
            if(item != null && stack != null) {
                if(item.amount != stack.getAmount()) return true;
                if(!item.item.equals(stack.getType().name())) return true;
                if(stack.getItemMeta() instanceof Damageable) {
                    if(item.damage != ((Damageable)stack.getItemMeta()).getDamage()) return true;
                }
            }
            return false;
        }

        private static LPlayer fromOfflinePlayer(OfflinePlayer player) {
            LPlayer p = new LPlayer(player.getUniqueId().toString());
            p.name = player.getName() != null ? player.getName() : "Unknown";
            p.online = player.isOnline();
            /*if(!HeadLibrary.has(player.getName())) { 
                HeadLibrary.resolveAsync(player.getName());
            } */
            p.updateHead(HeadLibraryV2.get(player.getName(), player.isOnline()));
            

            if(player.getPlayer() != null) {
                Player online = player.getPlayer();
                p.updateLocation(online.getLocation().getX(), online.getLocation().getY(), online.getLocation().getZ(), online.getLocation().getYaw());
                p.updateWorld(online.getLocation().getWorld().getName());
                p.updateHealth(online.getHealth(), online.getHealthScale());
                p.updateExhaustion(online.getExhaustion());
                p.updateVisible(!online.hasPotionEffect(PotionEffectType.INVISIBILITY));
                p.updateFoodLevel(online.getFoodLevel());
                ItemStack itemInHand = online.getInventory().getItemInMainHand();
                if(itemInHand != null && itemInHand.getAmount() != 0) {
                    p.updateItemHeld(itemInHand);
                }
                
                PlayerInventory inventory = online.getInventory();
                p.updateArmor(inventory.getHelmet(), inventory.getChestplate(), inventory.getLeggings(), inventory.getBoots());
            }

            return p;
        }

        public boolean isDirty() {
            return dirty || headDirty || armorDirty;
        }

        public void clean() {
            dirty = false;
            headDirty = false;
            armorDirty = false;
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("uuid", uuid);
            json.put("name", name);
            json.put("head", head);
            json.put("online", online);
            json.put("visible", visible);
            json.put("x", (float)x);
            json.put("y", (float)y);
            json.put("z", (float)z);
            json.put("dir", (float)dir+180f);
            json.put("world", world);
            json.put("health", (float)health);
            json.put("foodLevel", foodLevel);
            json.put("healthMax", (float)healthMax);
            json.put("armor", armor);
            json.put("exhaustion", exhaustion);
            if(itemHeld != null) json.put("itemHeld", itemHeld.toJson());
            if(actions != null) {
                synchronized(actions) {
                    json.put("actions", actions.stream().map(action->action.toJson()).collect(Collectors.toList()));
                }
            }
            //if(armorHead != null || armorChest != null || armorLegs != null || armorBoots != null) {
                JSONObject armorItems = new JSONObject();
                json.put("armorItems", armorItems);
                if(armorHead != null) armorItems.put("head", armorHead.toJson());
                if(armorChest != null) armorItems.put("chest", armorChest.toJson());
                if(armorLegs != null) armorItems.put("legs", armorLegs.toJson());
                if(armorBoots != null) armorItems.put("boots", armorBoots.toJson());
            //}
            return json;
        }
    }
}
