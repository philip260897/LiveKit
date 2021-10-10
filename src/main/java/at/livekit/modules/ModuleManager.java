package at.livekit.modules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.json.JSONArray;
import org.json.JSONObject;

import at.livekit.livekit.Identity;
import at.livekit.map.RenderScheduler;
import at.livekit.modules.BaseModule.ActionMethod;
import at.livekit.modules.BaseModule.ModuleListener;
import at.livekit.modules.BaseModule.ModulesAvailablePacket;
import at.livekit.packets.StatusPacket;
import at.livekit.plugin.Config;
import at.livekit.plugin.Plugin;
import javafx.scene.input.MouseButton;
import at.livekit.packets.IPacket;

public class ModuleManager 
{
    private ModuleListener listener;

    private SettingsModule settings;
    private Map<String, BaseModule> _modules = new HashMap<String, BaseModule>();
    private Map<String, List<String>> _subscriptions = new HashMap<String, List<String>>();

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

    public void onEnable(Map<String,ActionMethod> signatures) throws Exception {
        //Config.getModuleString("LiveMapModule", "world")
        //Config.getModuleString("LiveMapModule", "world")
        this.registerModule(new SettingsModule(listener));
        this.registerModule(new PlayerModule(listener));

        for(String world : Config.getLiveMapWorlds().keySet()) {
            this.registerModule(new LiveMapModule(world, listener, Config.getLiveMapWorlds()));
            this.registerModule(new WeatherTimeModule(world, listener));
        }

        this.registerModule(new AdminModule(listener));
        this.registerModule(new ChatModule(listener));
        this.registerModule(new POIModule(listener));
        this.registerModule(new InventoryModule(listener));
        //this.registerModule(new EconomyModule(listener));
        
        //if(Config.getConsolePassword() != null) {
        if(Config.getConsolePassword() == null) Plugin.warning("Enabling Console access without password. UNSAFE!");
        
        boolean enableConsole = true;
        if(Config.moduleEnabled("ConsoleModule")) {
            if("change_me".equalsIgnoreCase(Config.getConsolePassword())) {
                Plugin.severe("Default Console password still 'change_me'. Disabling Console Access!");
                enableConsole = false;
            }
        }
        if(enableConsole) this.registerModule(new ConsoleModule(listener));
        //} else {
        //    Plugin.log("Console password not set. Disabling ConsoleModule");
        //}

        /*System.out.println("Subscriptions collected: ");
        for(Entry<String, List<String>> entry : _subscriptions.entrySet()) {
            System.out.println(entry.getKey()+":");
            for(String e : entry.getValue()) {
                System.out.println(" - "+e);
            }
        }*/

        this.settings = (SettingsModule) _modules.get("SettingsModule");
        this.settings.onEnable(signatures);
        for(BaseModule m : _modules.values()) {
            if(!(m instanceof SettingsModule)) {
                
                if(Config.moduleEnabled(m.getType().split(":")[0])) {
                    Plugin.debug("Enabling "+m.getType());
                    m.onEnable(signatures);

                    if(m.isEnabled()) {
                        this.settings.registerModule(m.getType(), m.moduleInfo());
                        registerSubscription(m);
                    }
                }
            }
        }
    }

    public void onDisable(Map<String,ActionMethod> signature) {
        for(BaseModule m : _modules.values()) {
            if(m.isEnabled()) {
                m.onDisable(signature);
            }

            if(m instanceof PlayerModule) {
                ((PlayerModule) m).clearProviders();
            }
            /*if(m instanceof POIModule) {
                ((POIModule) m).clearProviders();
            }*/
        }
        _modules.clear();
        _subscriptions.clear();
        settings = null;
        listener = null;
    }

    private void registerModule(BaseModule module) {
        _modules.put(module.getType(), module);
    }

    private void registerSubscription(BaseModule module) throws Exception {
        if(module.isSubscribeable()) {
            List<String> _values = _subscriptions.get(module.getClass().getSimpleName());
            if(_values == null) {
                _values = new ArrayList<>();
                _subscriptions.put(module.getClass().getSimpleName(), _values);   
            }
            if(_values.contains(module.getSubscription())) throw new Exception("Duplicate subscription for module "+module.getType());
            _values.add(module.getSubscription());

            //Order LiveMap & WeatherTime module subscriptions according to config!
            if(module.getType().startsWith(LiveMapModule.class.getSimpleName()) || module.getType().startsWith(WeatherTimeModule.class.getSimpleName())) {
                _values.sort(Comparator.comparingInt(Config.getLiveMapWorldsOrdered()::indexOf));
            }
        }
    }

    public void disableModule(String moduleType, Map<String, ActionMethod> signatures, List<Identity> identities) /*throws Exception */{
        /*Bukkit.getScheduler().callSyncMethod(Plugin.instance, new Callable<Void>(){
            @Override
            public Void call() throws Exception {*/
                if(_modules.containsKey(moduleType)) {
                    BaseModule module = _modules.get(moduleType);
                    if(module.isEnabled()) {
                        Plugin.debug("Disabling "+module.getType());
                        module.onDisable(signatures);
                

                        //handle subscription update && update identity subscriptions if some got disabled!
                        if(module.isSubscribeable()) {
                            if(_subscriptions.containsKey(module.getClass().getSimpleName())) {
                                List<String> subs = _subscriptions.get(module.getClass().getSimpleName());
                                subs.remove(module.getSubscription());
                                if(subs.size() == 0) _subscriptions.remove(module.getClass().getSimpleName());

                                for(Identity identity : identities) {
                                    if(identity.isSubscribed(module.getClass().getSimpleName(), module.getSubscription())) {
                                        identity.setSubscription(module.getClass().getSimpleName(), subs.size() == 0 ? null : subs.get(0));
                                    }
                                }
                            }
                        }
                    }   
                }
         /*       return null;
            }
        }).get();*/
    }

    public void enableModule(String moduleType, Map<String, ActionMethod> signatures, List<Identity> identities) /*throws Exception*/ {
        /*Bukkit.getScheduler().callSyncMethod(Plugin.instance, new Callable<Void>(){
            @Override
            public Void call() throws Exception {*/
                if(_modules.containsKey(moduleType)) {
                    BaseModule module = _modules.get(moduleType);
                    if(!module.isEnabled()) {
                        Plugin.debug("Enabling "+module.getType());
                        module.onEnable(signatures);

                        //handle subscription update && update identity subscriptions if some got disabled!
                        if(module.isSubscribeable()) {
                            List<String> subs = _subscriptions.get(module.getClass().getSimpleName());
                            if(subs == null) {
                                subs = new ArrayList<String>();
                                _subscriptions.put(module.getClass().getSimpleName(), subs);
                            }
                            subs.add(module.getSubscription());

                            //Order LiveMap & WeatherTime module subscriptions according to config!
                            if(moduleType.startsWith(LiveMapModule.class.getSimpleName()) || moduleType.startsWith(WeatherTimeModule.class.getSimpleName())) {
                                subs.sort(Comparator.comparingInt(Config.getLiveMapWorldsOrdered()::indexOf));
                            }

                            for(Identity identity : identities) {
                                if(!identity.hasSubscriptionFor(module.getClass().getSimpleName())) {
                                    identity.setSubscription(module.getClass().getSimpleName(), module.getSubscription());
                                }
                            }
                        }
                    }
                }
               /* return null;
           }
        }).get();*/
    }

    public Map<Identity,IPacket> modulesAvailableAsync(List<Identity> identities)/* throws Exception */ {
        /*return Bukkit.getScheduler().callSyncMethod(Plugin.instance, new Callable<Map<String,IPacket>>(){
            @Override
            public Map<String,IPacket> call() throws Exception {*/
                Map<Identity,IPacket> packets = new HashMap<Identity,IPacket>(identities.size());
                for(Identity identity : identities) {
                    JSONArray mods = new JSONArray();
                    for(BaseModule module : _modules.values()) {
                        if(module.isEnabled() && module.hasAccess(identity)) {
                            mods.put(module.moduleInfo());
                        }
                    }
                    packets.put(identity, new ModulesAvailablePacket(mods, getSubscriptionsArray()));
                }
                return packets;
            /*}
        }).get();*/
    }

    public IPacket modulesAvailableAsync(Identity identity)/* throws Exception*/ {
        /*return Bukkit.getScheduler().callSyncMethod(Plugin.instance, new Callable<IPacket>(){
            @Override
            public IPacket call() throws Exception {*/
                JSONArray mods = new JSONArray();
                for(BaseModule module : _modules.values()) {
                    if(module.isEnabled() && module.hasAccess(identity)) {
                        mods.put(module.moduleInfo());
                    }
                }
                return new ModulesAvailablePacket(mods, getSubscriptionsArray());
           /* }
        }).get();*/
    }

    /**
     * When a new client joins, call this to get a full data update of every module
     * @param uuid identified uuid of client
     * @return  for each active module an IPacket with respective module data
     * @throws Exception yes
     */
    public List<IPacket> onJoinAsync(Identity identity) /*throws Exception */{
       /* return Bukkit.getScheduler().callSyncMethod(Plugin.instance, new Callable<List<IPacket>>(){
            @Override
            public List<IPacket> call() throws Exception {*/
                List<IPacket> packets = new ArrayList<IPacket>(_modules.size());
                for(BaseModule module : _modules.values()) {
                    if(module.isEnabled() && module.hasAccess(identity) && module.isAuthenticated(identity)) {
                        IPacket update = module.onJoinAsync(identity);
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
    public Map<Identity,IPacket> onUpdateAsync(String type, List<Identity> identities)/* throws Exception*/ {
       /* return Bukkit.getScheduler().callSyncMethod(Plugin.instance, new Callable<Map<String,IPacket> >(){
            @Override
            public Map<String,IPacket>  call() throws Exception {*/
               // Map<String,IPacket> packets = new HashMap<String, IPacket>(uuids.size());

                BaseModule module = _modules.get(type);
                if(module != null && module.isEnabled()) {
                    return module.onUpdateAsync(identities.stream().filter(identity->module.hasAccess(identity)&&module.isAuthenticated(identity)).collect(Collectors.toList()));
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
     * Notifies each module a Client has disconnected
     * @param identity Client which disconnected
     */
    public void onDisconnectAsync(Identity identity) {
        for(BaseModule module : _modules.values()) {
            if(module.isEnabled() && module.hasAccess(identity) && module.isAuthenticated(identity)) {
                module.onDisconnectAsync(identity);
            }
        }
    }

    /**
     * if module is interactive (client can change data) call this with moduleType, client context (uuid), and packet for change data
     * @param type  moduleType
     * @param uuid  client context
     * @param packet    data
     * @return  response message according to action
     * @throws Exception yes
     */
    public IPacket onChangeAsync(String type, Identity identity, IPacket packet) /*throws Exception */{
        /*return Bukkit.getScheduler().callSyncMethod(Plugin.instance, new Callable<IPacket>(){
            @Override
            public IPacket call() throws Exception {*/
                BaseModule module = _modules.get(type);
                if(module != null && module.isEnabled()) {
                    if(module.hasAccess(identity)&&module.isAuthenticated(identity)) {
                        return module.onChangeAsync(identity, packet);
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
        return Bukkit.getScheduler().callSyncMethod(Plugin.getInstance(), new Callable<Void>(){
			@Override
			public Void call() throws Exception {
                //long start = System.currentTimeMillis();
                for(BaseModule module : getModules()) {
                    if(module.isEnabled() && module.canUpdate(getSettings().liveMapTickRate)) {
                        module.update();
                    }
                }
                //System.out.println((System.currentTimeMillis()-start)+"ms");
				return null;
			}
        });
    }

    public boolean hasSubscription(String baseType, String subscription) {
        synchronized(_subscriptions) {
            if(_subscriptions.containsKey(baseType)) {
                return _subscriptions.get(baseType).contains(subscription.split(":")[0]);
            }
        }
        return false;
    }

    public HashMap<String,String> getDefaultSubscriptions() {
        HashMap<String, String> _default = new HashMap<String, String>();

        synchronized(_subscriptions) {
            for(Entry<String, List<String>> entry : _subscriptions.entrySet()) {
                if(entry.getValue() != null && entry.getValue().size() != 0) {
                    _default.put(entry.getKey(), entry.getValue().get(0));
                }
            }
        }

        return _default;
    }

    public Map<String, List<String>> getSubscriptions() {
        return _subscriptions;
    }

    private JSONArray getSubscriptionsArray() {
        JSONArray json = new JSONArray();

        synchronized(_subscriptions) {
            for(Entry<String, List<String>> entry : _subscriptions.entrySet()) {
                //if(entry.getKey().equalsIgnoreCase("ConsoleModule")) continue;

                JSONObject asdf = new JSONObject();
                JSONArray module = new JSONArray();
                for(String s : entry.getValue()) module.put(s);
                asdf.put("module", entry.getKey());
                asdf.put("values", module);
                json.put(asdf);
            }
        }
        return json;
    }
}
