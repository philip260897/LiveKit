package at.livekit.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;

import org.bukkit.Bukkit;
import org.json.JSONArray;

import at.livekit.modules.BaseModule.ModuleListener;
import at.livekit.packets.StatusPacket;
import at.livekit.plugin.Plugin;
import at.livekit.server.IPacket;

public class ModuleManager 
{
    private SettingsModule settings;
    private Map<String, BaseModule> _modules = new HashMap<String, BaseModule>();
    
    public void onEnable(ModuleListener listener) {
        this.registerModule(new SettingsModule());
        this.registerModule(new PlayerModule(listener));

        this.settings = (SettingsModule) _modules.get("SettingsModule");

        for(BaseModule m : _modules.values()) {
            if(this.settings.modules.get(m.getType()).getBoolean("enabled")) {
                m.onEnable();
            }
        }
    }

    public void onDisable() {
        for(BaseModule m : _modules.values()) {
            if(m.isEnabled()) {
                m.onDisable();
            }
        }
    }

    private void registerModule(BaseModule module) {
        _modules.put(module.getType(), module);
    }

    public void disableModule(String moduleType) throws Exception {
        Bukkit.getScheduler().callSyncMethod(Plugin.instance, new Callable<Void>(){
            @Override
            public Void call() throws Exception {
                if(_modules.containsKey(moduleType)) {
                    BaseModule module = _modules.get(moduleType);
                    if(module.isEnabled()) {
                        module.onDisable();
                    }   
                }
                return null;
            }
        }).get();
    }

    public void enableModule(String moduleType) throws Exception {
        Bukkit.getScheduler().callSyncMethod(Plugin.instance, new Callable<Void>(){
            @Override
            public Void call() throws Exception {
                if(_modules.containsKey(moduleType)) {
                    BaseModule module = _modules.get(moduleType);
                    if(!module.isEnabled()) {
                        module.onEnable();
                    }
                }
                return null;
            }
        }).get();
    }

    public IPacket modulesAvailable(String uuid) throws Exception {
        return Bukkit.getScheduler().callSyncMethod(Plugin.instance, new Callable<IPacket>(){
            @Override
            public IPacket call() throws Exception {
                JSONArray mods = new JSONArray();
                for(BaseModule module : _modules.values()) {
                    if(module.isEnabled() && module.hasAccess(uuid)) {
                        mods.put(module.moduleInfo());
                    }
                }
                return ModulesAvailable(mods);
            }
        }).get();
    }

    /**
     * When a new client joins, call this to get a full data update of every module
     * @param uuid identified uuid of client
     * @return  for each active module an IPacket with respective module data
     * @throws Exception yes
     */
    public List<IPacket> onJoin(String uuid) throws Exception {
        return Bukkit.getScheduler().callSyncMethod(Plugin.instance, new Callable<List<IPacket>>(){
            @Override
            public List<IPacket> call() throws Exception {
                List<IPacket> packets = new ArrayList<IPacket>(_modules.size());
                for(BaseModule module : _modules.values()) {
                    if(module.isEnabled() && module.hasAccess(uuid)) {
                        packets.add(module.onJoin(uuid));
                    }
                }
                return packets;
            }
        }).get();
    }

    /**
     * if an update is triggered, creates update packets for each module, for each 'context'
     * @param type module which triggered update needed
     * @param uuids connected client 'contexts'
     * @return an update packet (IPacket) for each context for the specified module
     * @throws Exception
     */
    public Map<String,IPacket> onUpdate(String type, List<String> uuids) throws Exception {
        return Bukkit.getScheduler().callSyncMethod(Plugin.instance, new Callable<Map<String,IPacket> >(){
            @Override
            public Map<String,IPacket>  call() throws Exception {
                Map<String,IPacket> packets = new HashMap<String, IPacket>(uuids.size());

                BaseModule module = _modules.get(type);
                if(module != null && module.isEnabled()) {
                    for(String uuid : uuids) {
                        if(module.hasAccess(uuid)) {
                            packets.put(uuid, module.onUpdate(uuid));
                        }
                    }
                }

                return packets;
            }
        }).get();
    }

    /**
     * if module is interactive (client can change data) call this with moduleType, client context (uuid), and packet for change data
     * @param type  moduleType
     * @param uuid  client context
     * @param packet    data
     * @return  response message according to action
     * @throws Exception yes
     */
    public IPacket onChange(String type, String uuid, IPacket packet) throws Exception {
        return Bukkit.getScheduler().callSyncMethod(Plugin.instance, new Callable<IPacket>(){
            @Override
            public IPacket call() throws Exception {
                BaseModule module = _modules.get(type);
                if(module != null && module.isEnabled()) {
                    if(module.hasAccess(uuid)) {
                        return module.onJoin(uuid);
                    }
                    return new StatusPacket(0, "Access Denied");
                }
                return new StatusPacket(0, "Module not found");
            }
        }).get();
    }
}
