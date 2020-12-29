package at.livekit.modules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.json.JSONArray;

import at.livekit.modules.BaseModule.ModuleListener;
import at.livekit.modules.BaseModule.ModulesAvailablePacket;
import at.livekit.packets.StatusPacket;
import at.livekit.plugin.Plugin;
import at.livekit.server.IPacket;

public class ModuleManager 
{
    private ModuleListener listener;

    private SettingsModule settings;
    private Map<String, BaseModule> _modules = new HashMap<String, BaseModule>();
    

    public ModuleManager(ModuleListener listener) {
        this.listener = listener;
    }

    public Collection<BaseModule> getModules() {
        return _modules.values();
    }

    public BaseModule getModule(String type) {
        synchronized(_modules) {
            return _modules.get(type);
        }
    }

    public void onEnable() {
        this.registerModule(new SettingsModule(listener));
        this.registerModule(new PlayerModule(listener));
        this.registerModule(new LiveMapModule("world", listener));
        this.registerModule(new WeatherTimeModule("world", listener));

        this.settings = (SettingsModule) _modules.get("SettingsModule");
        this.settings.onEnable();
        for(BaseModule m : _modules.values()) {
            if(!(m instanceof SettingsModule)) {
                this.settings.registerModule(m.getType(), m.moduleInfo());

                if(this.settings.modules.get(m.getType()).getBoolean("active")) {
                    m.onEnable();
                }
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

    public void disableModule(String moduleType) /*throws Exception */{
        /*Bukkit.getScheduler().callSyncMethod(Plugin.instance, new Callable<Void>(){
            @Override
            public Void call() throws Exception {*/
                if(_modules.containsKey(moduleType)) {
                    BaseModule module = _modules.get(moduleType);
                    if(module.isEnabled()) {
                        module.onDisable();
                    }   
                }
         /*       return null;
            }
        }).get();*/
    }

    public void enableModule(String moduleType) /*throws Exception*/ {
        /*Bukkit.getScheduler().callSyncMethod(Plugin.instance, new Callable<Void>(){
            @Override
            public Void call() throws Exception {*/
                if(_modules.containsKey(moduleType)) {
                    BaseModule module = _modules.get(moduleType);
                    if(!module.isEnabled()) {
                        module.onEnable();
                    }
                }
               /* return null;
           }
        }).get();*/
    }

    public Map<String,IPacket> modulesAvailableAsync(List<String> uuids)/* throws Exception */ {
        /*return Bukkit.getScheduler().callSyncMethod(Plugin.instance, new Callable<Map<String,IPacket>>(){
            @Override
            public Map<String,IPacket> call() throws Exception {*/
                Map<String,IPacket> packets = new HashMap<String,IPacket>(uuids.size());
                for(String uuid: uuids) {
                    JSONArray mods = new JSONArray();
                    for(BaseModule module : _modules.values()) {
                        if(module.isEnabled() && module.hasAccess(uuid)) {
                            mods.put(module.moduleInfo());
                        }
                    }
                    packets.put(uuid, new ModulesAvailablePacket(mods));
                }
                return packets;
            /*}
        }).get();*/
    }

    public IPacket modulesAvailableAsync(String uuid)/* throws Exception*/ {
        /*return Bukkit.getScheduler().callSyncMethod(Plugin.instance, new Callable<IPacket>(){
            @Override
            public IPacket call() throws Exception {*/
                JSONArray mods = new JSONArray();
                for(BaseModule module : _modules.values()) {
                    if(module.isEnabled() && module.hasAccess(uuid)) {
                        mods.put(module.moduleInfo());
                    }
                }
                return new ModulesAvailablePacket(mods);
           /* }
        }).get();*/
    }

    /**
     * When a new client joins, call this to get a full data update of every module
     * @param uuid identified uuid of client
     * @return  for each active module an IPacket with respective module data
     * @throws Exception yes
     */
    public List<IPacket> onJoinAsync(String uuid) /*throws Exception */{
       /* return Bukkit.getScheduler().callSyncMethod(Plugin.instance, new Callable<List<IPacket>>(){
            @Override
            public List<IPacket> call() throws Exception {*/
                List<IPacket> packets = new ArrayList<IPacket>(_modules.size());
                for(BaseModule module : _modules.values()) {
                    if(module.isEnabled() && module.hasAccess(uuid)) {
                        IPacket update = module.onJoinAsync(uuid);
                        if(update != null) packets.add( update);
                    }
                }
                return packets;
           /* }
        }).get();*/
    }

    /**
     * if an update is triggered, creates update packets for each module, for each 'context'
     * @param type module which triggered update needed
     * @param uuids connected client 'contexts'
     * @return an update packet (IPacket) for each context for the specified module
     * @throws Exception
     */
    public Map<String,IPacket> onUpdateAsync(String type, List<String> uuids)/* throws Exception*/ {
       /* return Bukkit.getScheduler().callSyncMethod(Plugin.instance, new Callable<Map<String,IPacket> >(){
            @Override
            public Map<String,IPacket>  call() throws Exception {*/
               // Map<String,IPacket> packets = new HashMap<String, IPacket>(uuids.size());

                BaseModule module = _modules.get(type);
                if(module != null && module.isEnabled()) {
                    return module.onUpdateAsync(uuids.stream().filter(uuid->module.hasAccess(uuid)).collect(Collectors.toList()));
                   /* for(String uuid : uuids) {
                        if(module.hasAccess(uuid)) {*/
                            
                            //if(update != null) packets.put(uuid, update);
                    /*    }
                    }*/
                }
                return null;
                //return packets;
           /* }
        }).get();*/
    }

    /**
     * if module is interactive (client can change data) call this with moduleType, client context (uuid), and packet for change data
     * @param type  moduleType
     * @param uuid  client context
     * @param packet    data
     * @return  response message according to action
     * @throws Exception yes
     */
    public IPacket onChangeAsync(String type, String uuid, IPacket packet) /*throws Exception */{
        /*return Bukkit.getScheduler().callSyncMethod(Plugin.instance, new Callable<IPacket>(){
            @Override
            public IPacket call() throws Exception {*/
                BaseModule module = _modules.get(type);
                if(module != null && module.isEnabled()) {
                    if(module.hasAccess(uuid)) {
                        return module.onChangeAsync(uuid, packet);
                    }
                    return new StatusPacket(0, "Access Denied");
                }
                return new StatusPacket(0, "Module not found");
           /* }
        }).get();*/
    }

    public SettingsModule getSettings() {
        return settings;
    }

    public Future<Void> updateModules() {
        return Bukkit.getScheduler().callSyncMethod(Plugin.instance, new Callable<Void>(){
			@Override
			public Void call() throws Exception {
                for(BaseModule module : getModules()) {
                    if(module.canUpdate(getSettings().liveMapTickRate)) {
                        module.update();
                    }
                }
				return null;
			}
        });
    }
}
