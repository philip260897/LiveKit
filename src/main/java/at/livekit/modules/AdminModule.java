package at.livekit.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.json.JSONArray;
import org.json.JSONObject;

import at.livekit.livekit.Identity;
import at.livekit.livekit.LiveKit;
import at.livekit.map.Renderer;
import at.livekit.packets.ActionPacket;
import at.livekit.packets.IPacket;
import at.livekit.packets.StatusPacket;
import at.livekit.utils.HeadLibraryV2;

public class AdminModule extends BaseModule 
{
    private List<String> _worlds = new ArrayList<String>();
    private boolean whitelist = false;


    public AdminModule(ModuleListener listener) {
        super(1, "My Admin", "livekit.module.admin", UpdateRate.ONCE_PERSEC, listener);
    }

    public void update() {
        boolean update = (whitelist != Bukkit.hasWhitelist());
        whitelist = Bukkit.hasWhitelist();

        if(update) notifyChange();
        super.update();
    }

    @Override
    public void onEnable(Map<String,ActionMethod> signature) {
        for(World world : Bukkit.getWorlds()) {
            _worlds.add(world.getName());
        }
        whitelist = Bukkit.hasWhitelist();
        
        super.onEnable(signature);
    }

    @Override
    public void onDisable(Map<String,ActionMethod> signature) {
        _worlds.clear();
        super.onDisable(signature);
    }

    @Override
    public IPacket onJoinAsync(Identity identity) {
        JSONObject data = new JSONObject();
        JSONArray w = new JSONArray();
        data.put("worlds", _worlds);
        data.put("whitelist", whitelist);

        JSONArray enchants = new JSONArray();
        for(Enchantment enchantment : Enchantment.values()) {
            JSONObject json = new JSONObject();
            json.put("namespace", enchantment.getKey().getNamespace());
            json.put("name", enchantment.getKey().getKey());
            json.put("min", enchantment.getStartLevel());
            json.put("max", enchantment.getMaxLevel());
            enchants.put(json);
        }

        data.put("enchantments", enchants);

        return new ModuleUpdatePacket(this, data, true);
    }

    @Override
    public Map<Identity,IPacket> onUpdateAsync(List<Identity> identities) {
        Map<Identity, IPacket> response = new HashMap<Identity, IPacket>();
        IPacket packet = onJoinAsync(null);

        for(Identity identity : identities) {
            response.put(identity, packet);
        }
        return response;
    }

    @Action(name="Teleport")
    protected IPacket teleportPlayer(Identity identity, ActionPacket packet) {
        double x = packet.getData().getDouble("x");
        double y = packet.getData().getDouble("y");
        double z = packet.getData().getDouble("z");
        String world = packet.getData().getString("world");
        String target = packet.getData().getString("uuid");

        World w = Bukkit.getWorld(world);
        if(w == null) return new StatusPacket(0, "World does not exist");

        OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(target));
        if(player == null || !player.isOnline()) return new StatusPacket(0, "Can't teleport offline player");

        Player online = player.getPlayer();

        Block block = Renderer.getBlockForRendering(Bukkit.getWorld(world).getHighestBlockAt((int)x, (int)z));
        online.teleport(block.getRelative(BlockFace.UP, 1).getLocation());

        //PlayerModule playerModule = () LiveKit.getInstance().getModuleManager().getModule("PlayerModule");

        return new StatusPacket(1);
    }

    @Action(name="ListWhitelist")
    protected IPacket actionWhitelist(Identity identity, ActionPacket packet) {
        JSONObject data = new JSONObject();
        JSONArray array = new JSONArray();
        for(OfflinePlayer player : Bukkit.getWhitelistedPlayers()) {
            JSONObject p = new JSONObject();
            p.put("uuid", player.getUniqueId().toString());
            p.put("name", player.getName() );
            p.put("head", HeadLibraryV2.get(player.getName(), player.isOnline()));
            array.put(p);
        }
        data.put("players", array);

        return packet.response(data);
    }

    @Action(name="SendMessage")
    protected IPacket actionSendMessage(Identity identity, ActionPacket packet) {
        String uuid = packet.getData().getString("uuid");
        String message = packet.getData().getString("message");
        OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
        if(player == null) return new StatusPacket(0, "Player not found!");
        if(player.getPlayer() == null) return new StatusPacket(0, "Player not online!");
        player.getPlayer().sendMessage(ChatColor.GREEN+"["+ChatColor.WHITE+"LiveKit"+ChatColor.GREEN+":"+ChatColor.WHITE+identity.getName()+ChatColor.GREEN+"]"+ChatColor.WHITE+" "+message);
        return new StatusPacket(1);
    }

    @Action(name="SetWhitelist")
    protected IPacket actionEnableWhitelist(Identity identity, ActionPacket packet) {
        boolean enable = packet.getData().getBoolean("enable");
        Bukkit.getServer().setWhitelist(enable);
        notifyChange();
        
        return new StatusPacket(1);
    }

    @Action(name="WhitelistPlayer")
    protected IPacket actionWhitelistPlayer(Identity identity, ActionPacket packet) {
        String uuid = packet.getData().getString("uuid");
        boolean enable = packet.getData().getBoolean("enable");

        OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
        if(player == null) return new StatusPacket(0);
            
        player.setWhitelisted(enable);
        return new StatusPacket(1);
    }

    @Action(name="SetWeater")
    protected IPacket actionWeather(Identity identity, ActionPacket packet) {
        String world = packet.getData().getString("world");
        String weather = packet.getData().getString("weather");

        World w = Bukkit.getWorld(world);
        if(w == null) return new StatusPacket(0, "World "+world+" is not available!");

        switch(weather) {
            case "clear": 
                w.setThundering(false);
                w.setStorm(false);
                break;
            case "rain":
                w.setThundering(false);
                w.setStorm(true);
                break;
            case "thunder":
                w.setThundering(true);
                w.setStorm(true);
                break;
            default:
                return new StatusPacket(0, "Invalid weather "+weather);
        }
        return new StatusPacket(1, "Weather set to "+weather);
    }

    @Action(name="SetTime")
    protected IPacket actionSetTime(Identity identity, ActionPacket packet) {
        String world = packet.getData().getString("world");
        String time = packet.getData().getString("time");

        World w = Bukkit.getWorld(world);
        if(w == null) return new StatusPacket(0, "World "+world+" is not available!");

        switch(time) {
            case "day": 
                w.setTime(1000);
                break;
            case "midnight":
                w.setTime(18000);
                break;
            case "night":
                w.setTime(13000);
                break;
            case "noon":
                w.setTime(6000);
                break;
            default:
                return new StatusPacket(0, "Invalid time "+time);
        }
        return new StatusPacket(1, "Time set to "+time);
    }

    @Action(name="KickPlayer")
    protected IPacket actionKick(Identity identity, ActionPacket packet) {
        String uuid = packet.getData().getString("uuid");
        String message = packet.getData().has("message")&&!packet.getData().isNull("message") ? packet.getData().getString("message") : null;

        Player player = Bukkit.getServer().getPlayer(UUID.fromString(uuid));
        if(player == null || !player.isOnline()) return new StatusPacket(0, "Player is offline!"); 

        player.kickPlayer(message);

        return new StatusPacket(1, "Player has been kicked!");
    }

    @Action(name="KillPlayer")
    protected IPacket actionKill(Identity identity, ActionPacket packet) {
        String uuid = packet.getData().getString("uuid");

        Player player = Bukkit.getServer().getPlayer(UUID.fromString(uuid));
        if(player == null || !player.isOnline()) return new StatusPacket(0, "Player is offline!"); 

        player.damage(player.getHealth());

        return new StatusPacket(1, "Player has been killed!");
    }

    @Action(name="SlapPlayer")
    protected IPacket actionSlap(Identity identity, ActionPacket packet) {
        String uuid = packet.getData().getString("uuid");

        Player player = Bukkit.getServer().getPlayer(UUID.fromString(uuid));
        if(player == null || !player.isOnline()) return new StatusPacket(0, "Player is offline!"); 

        player.damage(0.5);

        return new StatusPacket(1, "Player has been slapped!");
    }

    @Action(name="StrikePlayer")
    protected IPacket actionStrike(Identity identity, ActionPacket packet) {
        String uuid = packet.getData().getString("uuid");

        Player player = Bukkit.getServer().getPlayer(UUID.fromString(uuid));
        if(player == null || !player.isOnline()) return new StatusPacket(0, "Player is offline!"); 

        World world = player.getWorld();
        world.strikeLightningEffect(player.getLocation());
        player.damage(1);

        return new StatusPacket(1, "Player has been slapped!");
    }

    @Action(name="GameModePlayer")
    protected IPacket actionGamemode(Identity identity, ActionPacket packet) {
        String uuid = packet.getData().getString("uuid");
        String gamemode = packet.getData().getString("gamemode");

        Player player = Bukkit.getServer().getPlayer(UUID.fromString(uuid));
        if(player == null || !player.isOnline()) return new StatusPacket(0, "Player is offline!"); 

        GameMode mode = null;
        for(GameMode m : GameMode.values()) {
            if(m.name().toUpperCase().equals(gamemode.toUpperCase())) {
                mode = m;
            }
        }
        if(mode == null) return new StatusPacket(0, "Gamemode "+gamemode+" not found!"); 

        player.setGameMode(mode);

        return new StatusPacket(1, "Player has been slapped!");
    }

    @Action(name="ListBannedPlayers")
    protected IPacket bannedPlayers(Identity identity, ActionPacket packet) {
        JSONObject data = new JSONObject();
        JSONArray array = new JSONArray();
        for(OfflinePlayer player : Bukkit.getBannedPlayers()) {
            JSONObject p = new JSONObject();
            p.put("uuid", player.getUniqueId().toString());
            p.put("name", player.getName() );
            p.put("head", HeadLibraryV2.get(player.getName(), player.isOnline()));
            array.put(p);
        }
        data.put("players", array);

        return packet.response(data);
    }

    @Action(name="BanPlayer")
    protected IPacket actionBan(Identity identity, ActionPacket packet) {
        String uuid = packet.getData().getString("uuid");
        String message = packet.getData().has("message")&&!packet.getData().isNull("message") ? packet.getData().getString("message") : null;

        OfflinePlayer player = Bukkit.getServer().getOfflinePlayer(UUID.fromString(uuid));
        if(player == null) return new StatusPacket(0, "Player not found!"); 

        Bukkit.getBanList(BanList.Type.NAME).addBan(player.getUniqueId().toString(), message, null, null);
        if(player.isOnline())player.getPlayer().kickPlayer(message);

        return new StatusPacket(1, "Player has been kicked!");
    }

    @Action(name="UnbanPlayer")
    protected IPacket actionUnban(Identity identity, ActionPacket packet) {
        String uuid = packet.getData().getString("uuid");

        OfflinePlayer player = Bukkit.getServer().getOfflinePlayer(UUID.fromString(uuid));
        if(player == null) return new StatusPacket(0, "Player not found!"); 

        Bukkit.getBanList(BanList.Type.NAME).pardon(player.getUniqueId().toString());
        
        return new StatusPacket(1, "Player has been kicked!");
    }

    @Action(name="GiveItem")
    protected IPacket actionGiveItem(Identity identity, ActionPacket packet) {
        String uuid = packet.getData().getString("uuid");
        String material = packet.getData().getString("material");
        int amount = packet.getData().getInt("amount");

        JSONArray enchantments = new JSONArray();
        if(packet.getData().has("enchantments")) {
            enchantments = packet.getData().getJSONArray("enchantments");
        }
        
        OfflinePlayer offline = Bukkit.getServer().getOfflinePlayer(UUID.fromString(uuid));
        if(offline == null) return new StatusPacket(0, "Player not found!"); 

        Material mat = Material.getMaterial(material);
        if(mat == null) return new StatusPacket(0, "Material not found!");

        ItemStack itemStack = new ItemStack(Material.getMaterial(material), amount);
        for(int i = 0; i < enchantments.length(); i++) {
            String[] s = enchantments.getString(i).split(":");
            int level = Integer.parseInt(s[2]);
            Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(s[1]));
            if(enchantment.canEnchantItem(itemStack)) {
                itemStack.addEnchantment(enchantment, level);
            }
        }

        Player player = offline.getPlayer();
        player.getInventory().addItem(itemStack);

        InventoryModule inventoryModule = (InventoryModule)LiveKit.getInstance().getModuleManager().getModule("InventoryModule");
        if(inventoryModule != null && inventoryModule.isEnabled()) {
            inventoryModule.updateInventory(player);
        }

        return new StatusPacket(1);
    }

    @Action(name="ClearInventory")
    protected IPacket actionClearInventory(Identity identity, ActionPacket packet) {
        String uuid = packet.getData().getString("uuid");
        
        OfflinePlayer offline = Bukkit.getServer().getOfflinePlayer(UUID.fromString(uuid));
        if(offline == null) return new StatusPacket(0, "Player not found!"); 

        offline.getPlayer().getInventory().clear();

        InventoryModule inventoryModule = (InventoryModule)LiveKit.getInstance().getModuleManager().getModule("InventoryModule");
        if(inventoryModule != null && inventoryModule.isEnabled()) {
            inventoryModule.updateInventory(offline.getPlayer());
        }

        return new StatusPacket(1);
    }

    @Action(name="RemoveItem")
    protected IPacket actionRemoveItem(Identity identity, ActionPacket packet) {
        String uuid = packet.getData().getString("uuid");
        String material = packet.getData().getString("material");
        int amount = packet.getData().getInt("amount");
        int slot = packet.getData().getInt("slot");
        
        OfflinePlayer offline = Bukkit.getServer().getOfflinePlayer(UUID.fromString(uuid));
        if(offline == null) return new StatusPacket(0, "Player not found!"); 

        Player player = offline.getPlayer();
        PlayerInventory inventory = player.getInventory();
        
        ItemStack stack = inventory.getItem(slot);
        if(stack == null) return new StatusPacket(0, "Slot was empty");

        if(!stack.getType().name().equals(material) || stack.getAmount() != amount) return new StatusPacket(0, "ItemStack missmatch!");

        inventory.clear(slot);
        InventoryModule inventoryModule = (InventoryModule)LiveKit.getInstance().getModuleManager().getModule("InventoryModule");
        if(inventoryModule != null && inventoryModule.isEnabled()) {
            inventoryModule.updateInventory(player);
        }

        return new StatusPacket(1);
    }

    @Action(name="RemoveEnchantment")
    protected IPacket actionRemoveEnchantment(Identity identity, ActionPacket packet) {
        String uuid = packet.getData().getString("uuid");
        String material = packet.getData().getString("material");
        int amount = packet.getData().getInt("amount");
        int slot = packet.getData().getInt("slot");
        String senchant = packet.getData().getString("enchantment");

        Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(senchant.split(":")[1]));
        if(enchantment == null) return new StatusPacket(0, "An error occured!"); 
        
        OfflinePlayer offline = Bukkit.getServer().getOfflinePlayer(UUID.fromString(uuid));
        if(offline == null) return new StatusPacket(0, "Player not found!"); 

        Player player = offline.getPlayer();
        PlayerInventory inventory = player.getInventory();
        
        ItemStack stack = inventory.getItem(slot);
        if(stack == null) return new StatusPacket(0, "Slot was empty");

        if(!stack.getType().name().equals(material) || stack.getAmount() != amount) return new StatusPacket(0, "ItemStack missmatch!");

        stack.removeEnchantment(enchantment);

        //inventory.clear(slot);
        InventoryModule inventoryModule = (InventoryModule)LiveKit.getInstance().getModuleManager().getModule("InventoryModule");
        if(inventoryModule != null && inventoryModule.isEnabled()) {
            inventoryModule.updateInventory(player);
        }

        return new StatusPacket(1);
    }
}
