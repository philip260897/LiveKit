package at.livekit.modules;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.json.JSONArray;
import org.json.JSONObject;

import at.livekit.livekit.Identity;
import at.livekit.packets.IPacket;
import at.livekit.timings.TimedRegisteredListener;

public class PluginModule extends BaseModule{
    private Map<Plugin, Long> pluginTimings;
    private Plugin[] plugins;

    public PluginModule(ModuleListener listener) {
        super(1, "PluginModule", "livekit.module.plugins", UpdateRate.ONCE_PERSEC, listener, "default");
    }

    @Override
    public void onServerLoad() {
        plugins = Bukkit.getPluginManager().getPlugins();
        pluginTimings = new HashMap<Plugin, Long>();
        try{ 
            TimedRegisteredListener.registerListeners();
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public void update() {
        pluginTimings = TimedRegisteredListener.snapshotTimings();
        notifyChange();
        super.update();
    }

    @Override
    public IPacket onJoinAsync(Identity identity) {
        JSONObject data = new JSONObject();

        JSONArray plugins = new JSONArray();
        for(Plugin plugin : this.plugins) {
            JSONObject pluginData = new JSONObject();
            pluginData.put("name", plugin.getName());
            pluginData.put("version", plugin.getDescription().getVersion());
            pluginData.put("author", plugin.getDescription().getAuthors().get(0));
            pluginData.put("enabled", plugin.isEnabled());
            plugins.put(pluginData);
        }

        data.put("plugins", plugins);
        return new ModuleUpdatePacket(this, data, true);
    }

    @Override
    public Map<Identity,IPacket> onUpdateAsync(List<Identity> identities) {
        Map<Identity,IPacket> responses = new HashMap<Identity,IPacket>();
        for(Identity identity : identities) {
            JSONObject data = new JSONObject();

            for(Plugin plugin : this.pluginTimings.keySet()) {
                data.put(plugin.getName(), pluginTimings.get(plugin));
            }

            responses.put(identity, new ModuleUpdatePacket(this, data, false));
        }
        return responses;
    }
}
