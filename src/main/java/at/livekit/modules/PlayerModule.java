package at.livekit.modules;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.json.JSONArray;
import org.json.JSONObject;

import at.livekit.main.LiveSyncable;
import at.livekit.server.IPacket;

public class PlayerModule extends BaseModule 
{
    private Map<String,LPlayer> _players = new HashMap<String, LPlayer>();

    public PlayerModule(ModuleListener listener) {
        super(1, "Players", "livekit.basics.map", UpdateRate.NEVER, listener, true);
    }
     
    @Override
    public void onEnable() {
        for(OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            _players.put(player.getUniqueId().toString(), LPlayer.fromOfflinePlayer(player));
        }
        super.onEnable();
    }

    @Override
    public void onDisable() {
        _players.clear();
        super.onDisable();
    }

    @Override
    public IPacket onJoin(String uuid) {
        JSONObject json = super.toJson(uuid);
        JSONArray players = new JSONArray();
        for(Entry<String,LPlayer> entry : _players.entrySet()) {
            if(entry.getKey().equals(uuid)) {
                players.put(entry.getValue().serialize());
            } else {
                players.put(entry.getValue().censor(entry.getValue().serialize()));
            }
        }
        json.put("players", players);

        return new ModuleUpdatePacket(this.moduleInfo(), json);
    }

    @Override
    public IPacket onUpdate(String uuid) {
        //Map<String, IPacket> responses = new HashMap<String,IPacket>();
        //for(String uuid : uuids) {

            JSONObject json = super.toJson(uuid);
            JSONArray players = new JSONArray();
            for(Entry<String,LPlayer> entry : _players.entrySet()) {
                if(entry.getKey().equals(uuid)) {
                    players.put(entry.getValue().serializeChanges());
                } else {
                    players.put(entry.getValue().censor(entry.getValue().serializeChanges()));
                }
            }
            json.put("players", players);
            //responses.put(uuid, new ModuleUpdatePacket(json));
            return new ModuleUpdatePacket(this.moduleInfo(), json);
        //}
        //return responses;
    }

    @Override
    public IPacket onChange(String uuid, IPacket packet) {
        return null;
    }

    private static class LPlayer extends LiveSyncable {
        
        private String uuid;
        private String name;
        private long lastOnline;
        private long firstPlayed;
        //private String displayName;

        public LPlayer(String uuid) {
            super(uuid);
        }

        private void updateLastOnline(long lastOnline) {
            this.lastOnline = lastOnline;
            this.markDirty("lastOnline");
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
            

            return p;
        }
    }
}
