package at.livekit.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.json.JSONArray;
import org.json.JSONObject;

import at.livekit.livekit.Identity;
import at.livekit.packets.ActionPacket;
import at.livekit.packets.IPacket;
import at.livekit.packets.StatusPacket;
import at.livekit.utils.HeadLibrary;

public class AdminModule extends BaseModule 
{
    private List<String> _worlds = new ArrayList<String>();
    private boolean whitelist = false;

    public AdminModule(ModuleListener listener) {
        super(1, "My Admin", "livekit.admin.myadmin", UpdateRate.ONCE_PERSEC, listener);
    }

    public void update() {
        boolean update = (whitelist != Bukkit.hasWhitelist());
        whitelist = Bukkit.hasWhitelist();

        if(update) notifyChange();
        super.update();
    }

    @Override
    public void onEnable() {
        for(World world : Bukkit.getWorlds()) {
            _worlds.add(world.getName());
        }
        whitelist = Bukkit.hasWhitelist();
        
        super.onEnable();
    }

    @Override
    public void onDisable() {
        _worlds.clear();
        super.onDisable();
    }

    @Override
    public IPacket onJoinAsync(Identity identity) {
        JSONObject data = new JSONObject();
        JSONArray w = new JSONArray();
        w.put(_worlds);
        data.put("worlds", w);
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
            p.put("head", HeadLibrary.get(player.getUniqueId().toString()));
            array.put(p);
        }
        data.put("players", array);

        return packet.response(data);
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

    @Action(name="KickPlayer")
    protected IPacket actionKick(Identity identity, ActionPacket packet) {
        String uuid = packet.getData().getString("uuid");
        String message = packet.getData().getString("message");

        Player player = Bukkit.getServer().getPlayer(UUID.fromString(uuid));
        if(player == null || !player.isOnline()) return new StatusPacket(0, "Player is offline!"); 

        player.kickPlayer(message);

        return new StatusPacket(1, "Player has been kicked!");
    }

    @Action(name="BanPlayer")
    protected IPacket actionBan(Identity identity, ActionPacket packet) {
        String uuid = packet.getData().getString("uuid");
        String message = packet.getData().getString("message");

        OfflinePlayer player = Bukkit.getServer().getOfflinePlayer(UUID.fromString(uuid));
        if(player == null) return new StatusPacket(0, "Player not found!"); 

        Bukkit.getBanList(BanList.Type.NAME).addBan(player.getUniqueId().toString(), message, null, null);

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
}
