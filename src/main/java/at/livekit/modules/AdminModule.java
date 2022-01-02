package at.livekit.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


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
}
