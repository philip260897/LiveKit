package at.livekit.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.json.JSONArray;
import org.json.JSONObject;

import at.livekit.livekit.Identity;
import at.livekit.packets.ActionPacket;
import at.livekit.packets.IPacket;
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
    protected IPacket actionWhitelist(ActionPacket packet) {
        JSONObject data = new JSONObject();
        JSONArray array = new JSONArray();
        for(OfflinePlayer player : Bukkit.getWhitelistedPlayers()) {
            JSONObject p = new JSONObject();
            p.put("uuid", player.getUniqueId().toString());
            p.put("name", player.getName() );
            p.put("head", HeadLibrary.get(player.getUniqueId().toString()));
        }
        data.put("players", array);

        return packet.response(data);
    }
}
