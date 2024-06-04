package at.livekit.livekit;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.json.JSONObject;
import java.lang.reflect.Method;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import at.livekit.api.chat.ChatMessage;
import at.livekit.api.core.ILiveKit;
import at.livekit.api.economy.IEconomyAdapter;
import at.livekit.api.map.POI;
import at.livekit.api.map.POIInfoProvider;
import at.livekit.api.map.PlayerInfoProvider;
import at.livekit.authentication.Pin;
import at.livekit.authentication.Session;
import at.livekit.modules.BaseModule;
import at.livekit.modules.ChatModule;
import at.livekit.modules.EconomyModule;
import at.livekit.modules.ModuleManager;
import at.livekit.modules.POIModule;
import at.livekit.modules.PlayerModule;
import at.livekit.modules.BaseModule.Action;
import at.livekit.modules.BaseModule.ActionMethod;
import at.livekit.modules.BaseModule.ModuleListener;
import at.livekit.nio.NIOClient;
import at.livekit.nio.NIOProxyPool;
import at.livekit.nio.NIOServer;
import at.livekit.nio.NIOServer.NIOServerEvent;
import at.livekit.packets.ActionPacket;
import at.livekit.packets.IPacket;
import at.livekit.packets.IdentityPacket;
import at.livekit.packets.RequestPacket;
import at.livekit.packets.ServerSettingsPacket;
import at.livekit.packets.StatusPacket;
import at.livekit.plugin.Config;
import at.livekit.plugin.Permissions;
import at.livekit.plugin.Plugin;
import at.livekit.utils.HeadLibraryV2;

public class LiveKit implements ILiveKit, ModuleListener, NIOServerEvent<Identity>, Runnable {

    private static LiveKit instance;

    public static LiveKit getInstance() {
        if (LiveKit.instance == null) {
            LiveKit.instance = new LiveKit();
        }
        return LiveKit.instance;
    }

    private Map<String,ActionMethod> invokationMap = new HashMap<String, ActionMethod>();


    private Thread _thread;
    // private TCPServer _server;
    private NIOServer<Identity> _server;
    private ModuleManager _modules = new ModuleManager(this);
    private List<String> _moduleUpdates = new ArrayList<String>();
    private List<String> _moduleFull = new ArrayList<String>();
    private List<String> _commands = new ArrayList<String>();

    private ExecutorService executor;


    private List<ActionPacket> _packetsIncoming = new ArrayList<ActionPacket>();

    @Override
    public void onDataChangeAvailable(String moduleType) {
        notifyQueue(moduleType);
    }

    @Override
    public void onFullUpdate(String moduleType) {
        synchronized (_moduleFull) {
            if (!_moduleFull.contains(moduleType)) {
                _moduleFull.add(moduleType);
            }
        }
    }

    protected void notifyCommand(String command) {
        synchronized (_commands) {
            if (!_commands.contains(command)) {
                _commands.add(command);
            }
        }
    }

    public void notifyQueue(String moduleType) {
        synchronized (_moduleUpdates) {
            if (!_moduleUpdates.contains(moduleType)) {
                _moduleUpdates.add(moduleType);
            }
        }
    }

    public ModuleManager getModuleManager() {
        return _modules;
    }

    public void enableModule(String module) {
        getModuleManager().enableModule(module, invokationMap, _server.getIdentifiers().stream().filter(i->i.isIdentified()).collect(Collectors.toList()));
    }

    public void disableModule(String module) {
        getModuleManager().disableModule(module, invokationMap, _server.getIdentifiers().stream().filter(i->i.isIdentified()).collect(Collectors.toList()));
    }

    public void commandReloadPermissions() {
        notifyCommand("permreload");
    }

    public List<Identity> getConnectedClients(String uuid) {
        if (_server != null) {
            return _server.getIdentifiers().stream().filter(i->i.isIdentified() && !i.isAnonymous() && uuid.equals(i.getUuid())).collect(Collectors.toList());
        }
        return null;
    }

    public void onServerLoad() {
        _modules.onServerLoad();
    }

    public NIOServer<Identity> getServer() {
        return _server;
    }

    public void onEnable() throws Exception {
        //PlayerAuth.initialize();

        

        _modules.onEnable(invokationMap);
        Method[] methods = this.getClass().getDeclaredMethods();
        
        synchronized(invokationMap) {
            for(Method method : methods) {
                if(method.isAnnotationPresent(Action.class)) {
                    Action a = method.getAnnotation(Action.class);
                    invokationMap.put("LiveKit:"+a.name(), new ActionMethod(method, a.sync()));
                }
            }
        }

        executor = Executors.newSingleThreadExecutor();

        /*synchronized(invokationMap) {
            for(Entry<String,ActionMethod> entry : invokationMap.entrySet()) {
                System.out.println(entry.getKey()+"=>"+entry.getValue().sync());
            }
        }*/

        _server = new NIOServer<Identity>(_modules.getSettings().liveKitPort);
        _server.setServerListener(this);
        _server.start();
        /*_server.setServerListener(new NIOServerEvent<Identity>() {

            @Override
            public void onClientConnect(NIOClient<Identity> client) {
                //greet new client with current server settings
                JSONObject serverSettings = new JSONObject();
                serverSettings.put("liveKitVersion", _modules.getSettings().liveKitVersion);
                serverSettings.put("liveKitTickRate", _modules.getSettings().liveMapTickRate);
                serverSettings.put("needsIdentity", _modules.getSettings().needsIdentity);
                serverSettings.put("serverName", _modules.getSettings().serverName);
                serverSettings.put("needsPassword", _modules.getSettings().needsPassword);

                client.sendPacket(new ServerSettingsPacket(serverSettings));
            }

            @Override
            public void onDisconnect(LiveKitClient client) {}

            @Override
            public RequestPacket onPacketReceived(LiveKitClient client, RequestPacket packet) {
                packet.client = client;
                synchronized(_packetsIncoming) {
                    _packetsIncoming.add(packet);
                }
                return null;
            }
            
        });*/
        //_server.open();

        abort = false;
        _thread = new Thread(this);
        _thread.setName("LiveKit worker");
        _thread.start();


        Thread _proxyTHread = new Thread(new Runnable() {
            @Override
            public void run() {
                if(LiveProxy.getInstance().enableServer()) {
                    if(LiveProxy.getInstance().canProxyConnections() && !abort) {
                        Plugin.warning("Port forwarding status for "+Config.getServerPort()+": \u001B[31mCLOSED!\u001B[0m. Enabling proxy...");
                        Plugin.warning("NOTE: You need to setup port forwarding for LiveKit port "+Config.getServerPort()+" to enable direct connections! Direct connections offer better performance and stability! Proxy connections are only a fallback if port forwarding is not possible! Only "+LiveProxy.getInstance().getProxyConnectionCount()+" proxy connections are allowed!");
                        _server.setupProxyPool();
                    } else {
                        Plugin.log("Port forwarding status for "+Config.getServerPort()+": \u001B[32mOPEN!\u001B[0m. Direct connections enabled!");
                    }
                } else {
                   if(!abort) Plugin.warning("LiveKit proxy not available. Only direct connections possible \u001B[31m(Port forwarding for LiveKit port "+Config.getServerPort()+" required!)\u001B[0m");
                }
            }
        });
        _proxyTHread.setName("LiveKit Proxy");
        _proxyTHread.start();
    }

    private boolean abort;
    private Object _shutdownLock = new Object();
    private Future<Void> _futureModuleUpdates;

    private Map<Identity, ActionPacket> syncActions = new HashMap<Identity, ActionPacket>();
    private Map<Identity, ActionPacket> asyncActions = new HashMap<Identity, ActionPacket>();
    private Future<Map<Identity,IPacket>> _futureSyncActions;
    private Future<Map<Identity,IPacket>> _futureAsyncActions;

    @Override
    public void run() {

        while(!abort) {
            int tickTime = 1000/(_modules.getSettings().liveMapTickRate);
            long start = System.currentTimeMillis();

            if(_futureModuleUpdates != null) {
                if(_futureModuleUpdates.isDone()) {
                    _futureModuleUpdates = null;
                }
            }
            if(_futureModuleUpdates == null) {
                _futureModuleUpdates = _modules.updateModules();
            }

            long module_dispatch = System.currentTimeMillis();

            String module = null;
            List<Identity> clientUUIDs = _server.getIdentifiers().stream().filter(i->i.isIdentified()).collect(Collectors.toList());
            //List<Identity> clientIdles = _server.getIdentifiers();
            /*if(clientIdles.size() != clientUUIDs.size())*/ //System.out.println("Updating "+clientIdles.size()+" clients");
            do {
                synchronized(_moduleUpdates) {
                    if(_moduleUpdates.size() > 0) {
                        module = _moduleUpdates.remove(0);
                    } else { 
                        module = null;
                    }
                }
                if(module != null) {
                    BaseModule m = _modules.getModule(module);
                    _server.send(m.onUpdateAsync(clientUUIDs.stream().filter(identity->m.hasAccess(identity)&&m.isAuthenticated(identity)).collect(Collectors.toList())));

                    if(module.equals("SettingsModule")) {
                        _server.send(_modules.modulesAvailableAsync(clientUUIDs));
                        for(Identity identity : clientUUIDs) {
                            _server.send(identity, _modules.onJoinAsync(identity));
                        }
                    }
                }
            }while(module != null);

            long module_updates = System.currentTimeMillis();
            //clientUUIDs = _server.getIdentifiers();

            module = null;
            do {
                synchronized(_moduleFull) {
                    if(_moduleFull.size() > 0) {
                        module = _moduleFull.remove(0);
                    } else { 
                        module = null;
                    }
                }
                if(module != null) {
                    BaseModule m = _modules.getModule(module);
                    for(Identity identity : clientUUIDs) {
                        if(m.hasAccess(identity) && m.isAuthenticated(identity)) {
                            _server.send(identity, m.onJoinAsync(identity));
                        }
                    }
                }
            }while(module != null);

            long module_full = System.currentTimeMillis();

            String command = null;
            do{
                synchronized(_commands) {
                    if(_commands.size() != 0) command = _commands.remove(0);
                    else command = null;
                }
                if(command != null) {
                    handleCommand(command);
                }
            }while(command != null);

            long commands = System.currentTimeMillis();

            //Map<Identity, ActionPacket> asyncActions = new HashMap<Identity, ActionPacket>();
            
            if(_futureSyncActions != null) {
                if(_futureSyncActions.isDone()) {
                    try {
                        _server.send(_futureSyncActions.get());
                    }catch(Exception ex){ex.printStackTrace();}

                    _futureSyncActions = null;
                    syncActions.clear();
                }
            }

            if(_futureAsyncActions != null) {
                if(_futureAsyncActions.isDone()) {
                    try{
                        _server.send(_futureAsyncActions.get());
                    }catch(Exception ex){ex.printStackTrace();}

                    _futureAsyncActions = null;
                    asyncActions.clear();
                }
            }

            synchronized(_packetsIncoming) {

                int i = 0;
                while(i < _packetsIncoming.size()) {
                    ActionPacket action = _packetsIncoming.get(i);
                    
                    ActionMethod method = getMethod(action.getModuleType(), action.getActionName());
                    if(method == null) {
                        _packetsIncoming.remove(i);
                        _server.send(action.client, new StatusPacket(0, "Something went wrong!").setRequestId(action.requestId));
                        continue;
                    }

                    //Plugin.debug("Action: sync="+method.sync()+" name="+action.getModuleType()+":"+action.getActionName()+" hasClient="+(action.client != null)+" hasIdentity="+(action.client.getIdentifier() != null));

                    if(method.sync()) {
                        if(syncActions.containsKey(action.client.getIdentifier()) || _futureSyncActions != null) i++;
                        else syncActions.put(action.client.getIdentifier(), _packetsIncoming.remove(i));
                    } else {
                        if(asyncActions.containsKey(action.client.getIdentifier()) || _futureAsyncActions != null) i++;
                        else asyncActions.put(action.client.getIdentifier(), _packetsIncoming.remove(i));
                    }
                }
            }
            
            //_server.send(invokeActions(asyncActions, false));

            if(_futureSyncActions == null && syncActions.size() != 0) {
                _futureSyncActions = invokeActionsSync(syncActions);
            }
            if(_futureAsyncActions == null && asyncActions.size() != 0) {
                _futureAsyncActions = invokeActionsAsync(asyncActions);
            }
                /*while(_packetsIncoming.size() > 0 && (System.currentTimeMillis()-start < tickTime)) {
                    RequestPacket packet = (RequestPacket) _packetsIncoming.remove(0);
                    RequestPacket response = handlePacket(packet.client, packet);
                    long mini = System.currentTimeMillis();
                    if(response != null) _server.send(packet.client, response.setRequestId(packet.requestId));
                   // System.out.println("Sending: "+(System.currentTimeMillis()-mini));
                }*/
            

            long packets = System.currentTimeMillis();
            

            long delta = System.currentTimeMillis() - start;
            //if( delta > 1) Plugin.debug("TICK "+delta+"ms "+tickTime+"ms [cpackets="+(packets-commands)+"ms; cmds="+(commands-module_full)+"ms; mfull="+(module_full-module_updates)+"ms; mupdate="+(module_updates-module_dispatch)+"; dispatch="+(module_dispatch-start)+"]");
            if(delta >= tickTime){
                //removing this since it only confuses people
                Plugin.debug("LiveKit tick can't keep up ("+delta+"ms/"+tickTime+"ms)");
            }
            else {
                try{
                    Thread.sleep(tickTime-delta);
                }catch(InterruptedException ex){}
            }
        }

        synchronized(_shutdownLock) {
            _shutdownLock.notifyAll();
        }
    }

    public void onDisable() {
        try{
            abort = true;
            if(_thread != null) {
                _thread.interrupt();
                synchronized(_shutdownLock) {
                    _shutdownLock.wait(1000);
                }
            }
        }catch(Exception ex){ex.printStackTrace();}
        /*try{
            PlayerAuth.save();
        }catch(Exception ex){ex.printStackTrace();}*/
        try{
            if(_modules != null)
                _modules.onDisable(invokationMap);
        }catch(Exception ex){ex.printStackTrace();}
        try{
            if(_server != null)
                _server.stop();
        }catch(Exception ex){ex.printStackTrace();}

        

        try{
            synchronized(invokationMap) {
                Iterator<Entry<String,ActionMethod>> iterator = invokationMap.entrySet().iterator();
                while(iterator.hasNext()) {
                    if(iterator.next().getKey().startsWith("LiveKit:")) {
                        iterator.remove();
                    }
                }

                if(invokationMap.size() > 0)  throw new Exception("Invokation leak?");
            }
        }catch(Exception ex){ex.printStackTrace();}

        try{
            if(executor != null) executor.shutdownNow();
        }catch(Exception ex){ex.printStackTrace();}

        LiveKit.instance = null;
    }
    
    /*private RequestPacket handlePacket(NIOClient<Identity> client, RequestPacket packet) {
        if(packet instanceof AuthorizationPacket) {
            AuthorizationPacket auth = (AuthorizationPacket)packet;
            PlayerAuth identity = null;

            if(_modules.getSettings().needsPassword) {
                if(!Config.getPassword().equals(auth.getPassword())) {
                    return new StatusPacket(0, "Invalid server password!");
                }
            }

            if(_modules.getSettings().needsIdentity) {
                if(auth.isAnonymous()) {
                    return new StatusPacket(0, "Identity required!");
                }
            }

            if(!auth.isAnonymous()) {
                if(auth.isPin()) {
                    identity = PlayerAuth.validateClaim(auth.getValue());
                } else {
                    identity = PlayerAuth.get(auth.getUUID());
                    if(identity.isValidSession(auth.getValue())) {
                        identity.removeSession(auth.getValue());
                    } else {
                        identity = null;
                    }
                }
                if(identity != null) {
                    client.setIdentifier(new Identity(identity.getUUID()));
                    client.getIdentifier().loadPermissionsAsync();
                
                    try{
                        _server.send(client.getIdentifier(), _modules.modulesAvailableAsync(client.getIdentifier()));
                        _server.send(client.getIdentifier(), _modules.onJoinAsync(client.getIdentifier()));
                    }catch(Exception ex){ex.printStackTrace();}

                
                    return new IdentityPacket(identity.getUUID(), client.getIdentifier().getName(), HeadLibrary.get(client.getIdentifier().getName()), identity.generateSessionKey());
                }
                
            } 
            else
            {
                client.setIdentifier(new Identity(null));
                client.getIdentifier().loadPermissionsAsync();

                try{
                    _server.send(client.getIdentifier(), _modules.modulesAvailableAsync(client.getIdentifier()));
                    _server.send(client.getIdentifier(), _modules.onJoinAsync(client.getIdentifier()));
                }catch(Exception ex){ex.printStackTrace();}

                return new IdentityPacket(null, client.getIdentifier().getName(), null, null);
            }

            return new StatusPacket(0, "Invalid authentication credentials!");
        }

        /*if(packet instanceof RegionRequest) {
            RegionRequest request = (RegionRequest)packet;

            LiveMapModule module = (LiveMapModule) _modules.getModule("LiveMapModule");

            if(module != null && module.getWorld().equals(request.world)) {
                byte[] data = module.getRegionData(request.x, request.z).getData();
                if(data != null) {
                    return new RawPacket(data);
                }
            }
            return new StatusPacket(0, "Region "+request.x+" "+request.z+" in "+request.world+" not found!");
        }*/

        /*if(packet instanceof LiveMapSubscriptionPacket) {
            if(hasAccess(client, "livekit.livemap.subscription")) {
               LiveMapSubscriptionPacket sub = (LiveMapSubscriptionPacket)packet;
               client.setLiveMapWorld(sub.map);

               LiveMap livemap = LiveKit.getLiveMap(sub.map);
               if(livemap != null) {
                   livemap.fullUpdate(client);
               }
               return new StatusPacket(1);
            }
            return new StatusPacket(0, "Insufficient Permissions");
        }*/
        /*return new StatusPacket(0, "Unkown request");
    }*/

    private void handleCommand(String command) {
        if(command.equalsIgnoreCase("permreload")) {
            //reload permissions for all connected clients
            List<Identity> clientUUIDs = _server.getIdentifiers().stream().filter(i->i.isIdentified()).collect(Collectors.toList());

            for(Identity identity : clientUUIDs) {
                identity.loadPermissionsAsync();
                identity.updateSubscriptions(_modules.getDefaultSubscriptions());
            }

            
            //broadcast modules available for all clients
            _server.send(_modules.modulesAvailableAsync(clientUUIDs));
            //give full update for all modules
            for(Identity identity : clientUUIDs) {
                _server.send(identity, _modules.onJoinAsync(identity));
            }
        }
    }


    /*private static boolean hasAccess(LiveKitClient client, String resource) {
        if(settings.needsIdentity == true && !client.hasIdentity()) return false;

        return true;
    }*/

    /*private static JSONArray buildModuleInfo(String uuid) {
        try{
            return Bukkit.getScheduler().callSyncMethod(Plugin.instance, new Callable<JSONArray>(){
                @Override
                public JSONArray call() throws Exception {
                    JSONArray mods = new JSONArray();
                    for(Entry<String,BaseModule> module : modules.entrySet()) {
                        if(module.getValue().hasAccess(uuid)) {
                            mods.put(module.getValue().moduleInfo());
                        }
                    }
                    return mods;
                }
            }).get();

        }catch(Exception ex){ex.printStackTrace();}
        return null;
    }*/

    public Collection<BaseModule> getModules() {
        return _modules.getModules();
    }

    @Override
    public void clientConnected(NIOClient<Identity> client) {
        //Plugin.log("Client connected");
        client.setIdentifier(Identity.unidentified());
        JSONObject serverSettings = new JSONObject();
        serverSettings.put("liveKitVersion", _modules.getSettings().liveKitVersion);
        serverSettings.put("liveKitTickRate", _modules.getSettings().liveMapTickRate);
        serverSettings.put("needsIdentity", _modules.getSettings().needsIdentity);
        serverSettings.put("serverName", _modules.getSettings().serverName);
        serverSettings.put("needsPassword", _modules.getSettings().needsPassword);

        _server.send(client, new ServerSettingsPacket(serverSettings));

        /*try {
            Bukkit.getScheduler().runTaskAsynchronously(Plugin.getInstance(), new Runnable() {
                @Override
                public void run() {
                    Plugin.getStat().onLkConnected();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }*/
    }

    @Override
    public void clientDisconnected(NIOClient<Identity> client) {
        _modules.onDisconnectAsync(client.getIdentifier());
    }

    @Override
    public void clientMessageReceived(NIOClient<Identity> client, String message) {
        JSONObject json = new JSONObject(message);
        int packetId = json.getInt("packet_id");
        int requestId = json.getInt("request_id");

        if(packetId == ActionPacket.PACKETID) {
            ActionPacket packet = (ActionPacket) new ActionPacket().fromJson(message);

            if(client.getIdentifier().isIdentified() == false && !packet.getActionName().equals("Login")) {
                _server.send(client, new StatusPacket(0, "Not Authorized").setRequestId(requestId));
                client.close();
                return;
            }

            synchronized(_packetsIncoming) {
                packet.client = client;
                _packetsIncoming.add(packet);
            }
        } else {
            Plugin.debug("Invalid packet id received "+packetId);
        }
        
        /*RequestPacket response = null;
        if(packetId == AuthorizationPacket.PACKETID) {
            RequestPacket packet = (AuthorizationPacket) new AuthorizationPacket().fromJson(message); 
            packet.client = client;
            synchronized(_packetsIncoming) {
                _packetsIncoming.add(packet);
            }
        }
        if(packetId == ActionPacket.PACKETID) {
            response = (RequestPacket) LiveKit.getInstance().getModuleManager().invokeActionSync(client.getIdentifier(), (ActionPacket)new ActionPacket().fromJson(message));
        }
        if(packetId == RegionRequest.PACKETID) {
            RegionRequest request = (RegionRequest) new RegionRequest().fromJson(message);

            LiveMapModule module = (LiveMapModule) LiveKit.getInstance().getModuleManager().getModule("LiveMapModule");

            if(module != null) {
                byte[] d = module.getRegionData(request.x, request.z).getData();
                RawPacket raw = new RawPacket(d);
                _server.send(client, raw.setRequestId(requestId));
                /*if(d != null) {
                    try{
                        MultiPartRawPacket multi = new MultiPartRawPacket(d, 12);
                        multi.setRequestId(requestId);
                        while(multi.nextPart()) 
                        {
                            Thread.sleep(15);
                            sender.sendPacket(multi);
                        }
                    }catch(Exception ex){ex.printStackTrace();}
                }*/
           /* }
            else
            {
                response = new StatusPacket(0, "Region "+request.x+" "+request.z+" in "+request.world+" not found!");
            }
        }
        /*if(packetId == LiveMapSubscriptionPacket.PACKETID) {
            response = listener.onPacketReceived((LiveKitClient)sender, (LiveMapSubscriptionPacket) new LiveMapSubscriptionPacket().fromJson(data));    
        }*/

        //if(response != null) _server.send(client, response.setRequestId(requestId));
    }

    @Action(name = "Login", sync = false)
    public IPacket login(Identity in, ActionPacket action) {
        NIOClient<Identity> client = action.client;
        String identity = null;
        JSONObject o = action.getData();

        String pin = o.has("pin")&&!o.isNull("pin") ? o.getString("pin") : null;
        String authorization = o.has("auth")&&!o.isNull("auth") ? o.getString("auth") : null;
        String uuid = o.has("uuid")&&!o.isNull("uuid") ? o.getString("uuid") : null;
        boolean anonymous = o.has("anonymous")&&!o.isNull("anonymous") ? o.getBoolean("anonymous") : false;
        String password = o.has("password")&&!o.isNull("password")?o.getString("password"):null;

        HashMap<String, String> subscriptions = new HashMap<String,String>();
        //HashMap<String, String> moduleAuths = new HashMap<String, String>();
        if(o.has("subscriptions") && !o.isNull("subscriptions")) {
            JSONObject subs = o.getJSONObject("subscriptions");
            for(String key : subs.keySet()) {
                //String[] split = subs.getString(key).split(":");
                //String subscription = split[0];
                String subscription = subs.getString(key);
                //String subAuth = split.length > 1 ? split[1] : null;

                if(_modules.hasSubscription(key, subscription)) {
                    subscriptions.put(key, subscription);
                    /*if(subAuth != null) {
                        moduleAuths.put(key, subAuth);
                    }*/
                }
            }
        }

        if(_modules.getSettings().needsPassword) {
            if(!Config.getPassword().equals(password)) {
                return new StatusPacket(0, "Invalid server password!");
            }
        }

        if(_modules.getSettings().needsIdentity) {
            if(anonymous) {
                return new StatusPacket(0, "Identity required!");
            }
        }

        
        if(!anonymous) {
            Session currentSession = null;

            if(pin != null) {
                //identity = PlayerAuth.validateClaim(pin);

                try{
                    Pin p = Plugin.getStorage().loadSingle(Pin.class, "pin", pin);
                    if(p != null) {
                        identity = p.getUUID().toString();
                        Plugin.getStorage().delete(p);
                    }

                    /*if(pin.equals("123456")) {
                        identity = "867678c4-391b-42a9-a4cb-3ad14089f3f6";
                    }*/

                    if(identity != null) {
                        List<Session> sessions = Plugin.getStorage().load(Session.class, "uuid", UUID.fromString(identity));
                        while(sessions.size() >= 5) {
                            Session session = sessions.remove(0);
                            Plugin.getStorage().delete(session);
                        }
                    }
                }catch(Exception ex){ex.printStackTrace();}

            } else {
                //identity = PlayerAuth.get(uuid);
                identity = uuid;
                
                boolean success = false;
                

                try{
                    List<Session> sessions = Plugin.getStorage().load(Session.class, "uuid", UUID.fromString(identity));
                    for(Session session : sessions) {
                        
                        if(session.getAuthentication().equals(authorization)) {
                            //Plugin.getStorage().delete(session);
                            currentSession = session;
                            success = true;
                        }
                    }


                }catch(Exception ex){ex.printStackTrace();}

                if(!success) identity = null;
                //System.out.println(identity);

                /*if(identity.isValidSession(authorization)) {
                    identity.removeSession(authorization);
                } else {
                    identity = null;
                }*/
            }
            if(identity != null) {
                //client.setIdentifier(new Identity(identity.getUUID()));
                client.getIdentifier().identify(identity);
                client.getIdentifier().loadPermissionsAsync();
                client.getIdentifier().updateSubscriptions(_modules.getDefaultSubscriptions());
                client.getIdentifier().updateSubscriptions(subscriptions);
                //client.getIdentifier().setModuleAuthentications(moduleAuths);
            


                try{
                    _server.send(client.getIdentifier(), _modules.modulesAvailableAsync(client.getIdentifier()));
                    _server.send(client.getIdentifier(), _modules.onJoinAsync(client.getIdentifier()));
                }catch(Exception ex){ex.printStackTrace();}

                Session session = currentSession; 
                if(session == null) {
                    try{
                        session = Session.createNew(UUID.fromString(identity), null, null);
                        Plugin.getStorage().create(session);
                    }catch(Exception ex){ex.printStackTrace();}
                }
            
                return new IdentityPacket(identity, client.getIdentifier().getName(), HeadLibraryV2.get(client.getIdentifier().getName(), true), session.getAuthentication());
            }
        } 
        else
        {
            client.getIdentifier().setAnonymous();
            client.getIdentifier().loadPermissionsAsync();
            client.getIdentifier().updateSubscriptions(_modules.getDefaultSubscriptions());
            client.getIdentifier().updateSubscriptions(subscriptions);
            //client.getIdentifier().setModuleAuthentications(moduleAuths);

            try{
                _server.send(client.getIdentifier(), _modules.modulesAvailableAsync(client.getIdentifier()));
                _server.send(client.getIdentifier(), _modules.onJoinAsync(client.getIdentifier()));
            }catch(Exception ex){ex.printStackTrace();}

            return new IdentityPacket(null, client.getIdentifier().getName(), null, null);
        }

        return new StatusPacket(0, "Invalid authentication credentials!");
    }

    @Action(name = "Subscribe", sync = false)
    public IPacket subscribe(Identity identity, ActionPacket action) {
        String baseType = action.getData().getString("baseType");
        String subscription = action.getData().getString("subscription");
        String password = action.getData().has("password")&&!action.getData().isNull("password") ? action.getData().getString("password") : null;

        if(!_modules.hasSubscription(baseType, subscription)) return new StatusPacket(0, "Subscription not available!");

        BaseModule module = _modules.getModule(baseType+":"+subscription);
        if(module == null) return new StatusPacket(0, "Module with subscription not found!");

        if(module.needsAuth()) {
            if(!module.getAuth().equals(password)) {
                return new StatusPacket(0, "Permission denied!");
            }
            //subscription += ":"+password;
        }

        //TODO: permission check ?
        identity.setSubscription(baseType, subscription);
        identity.setModuleAuthentication(module.getType(), password);
        _server.send(identity, module.onJoinAsync(identity));

        return new StatusPacket(1);
    }

    @Action(name = "Unsubscribe", sync = false)
    public IPacket unsubscribe(Identity identity, ActionPacket action) {
        String baseType = action.getData().getString("baseType");
        identity.setSubscription(baseType, null);

        return new StatusPacket(1);
    }

    @Action(name = "TestSync", sync = true)
    public IPacket testSync(Identity identity, ActionPacket action) {
        return new StatusPacket(1, "This just computed on the main thread!");
    }

    
    private Future<Map<Identity, IPacket>> invokeActionsAsync(Map<Identity, ActionPacket> actions) {
        return executor.submit(()->{
            return invokeActions(actions, false);
        });
    }

    private Future<Map<Identity,IPacket>> invokeActionsSync(Map<Identity, ActionPacket> actions) {
        return Bukkit.getScheduler().callSyncMethod(Plugin.getInstance(), new Callable<Map<Identity,IPacket>>(){
            @Override
            public Map<Identity, IPacket> call() throws Exception {
                return invokeActions(actions, true);
            }
        });
    }

    private Map<Identity, IPacket> invokeActions(Map<Identity, ActionPacket> actions, boolean sync) {
        HashMap<Identity, IPacket> results = new HashMap<Identity, IPacket>();
        
        

        for(Entry<Identity, ActionPacket> entry : actions.entrySet()) {
            ActionPacket action = entry.getValue();
            Identity identity = entry.getKey();

            BaseModule module = null;
            if(!action.getModuleType().equals("LiveKit")) {
                module = this._modules.getModule(entry.getValue().getModuleType());
                if(module == null) {
                    results.put(identity, new StatusPacket(0, "Invalid module "+action.getModuleType()+" specified.").setRequestId(action.requestId));
                    continue;
                }
                if(!module.isEnabled()) {
                    results.put(identity, new StatusPacket(0, "Requested module is not enabled!").setRequestId(action.requestId));
                    continue;
                }
                if(!module.hasAccess(identity) || !module.isAuthenticated(identity)) {
                    results.put(identity, new StatusPacket(0, "Permission denied!").setRequestId(action.requestId));
                    continue;  
                }
            }
            
            try{
                ActionMethod method = getMethod(action.getModuleType(), action.getActionName());
                RequestPacket packet =  ((RequestPacket) method.invoke(action.getModuleType().equals("LiveKit") ? this : module, sync, identity, action));
                //Plugin.debug("Action Resolved: "+action.getModuleType()+":"+action.getActionName()+" packet="+(packet != null)+"; ");
                if(packet != null) {
                    results.put(identity,packet.setRequestId(action.requestId));
                }
                
            }catch(Exception ex) {
                ex.printStackTrace();
                results.put(identity, new StatusPacket(0, "Something went wrong!").setRequestId(action.requestId));
            }
        }

        return results;
    }

    private ActionMethod getMethod(String module, String action) {
        synchronized(invokationMap) {
            return invokationMap.get(module+":"+action);
        }
    }

    @Override
	public void addPlayerInfoProvider(PlayerInfoProvider provider) {
		PlayerModule module = (PlayerModule)_modules.getModule("PlayerModule");
        if(module != null) {
            if(provider.getPermission() != null) {
                Permissions.registerPermission(provider.getPermission());
            }
            module.addInfoProvider(provider);
        }
	}

	@Override
	public void removePlayerInfoProvider(PlayerInfoProvider provider) {
		PlayerModule module = (PlayerModule)_modules.getModule("PlayerModule");
        if(module != null) {
            if(provider.getPermission() != null) {
                Permissions.unregisterPermission(provider.getPermission());
            }
            module.removeInfoProvider(provider);
        }
	}

    @Override
    public void addPointOfInterest(POI waypoint) {
        POIModule module = (POIModule) _modules.getModule("POIModule");
        if(module != null && module.isEnabled()) {
            module.addPOI(waypoint);
        }
    }

    @Override
    public void removePointOfIntereset(POI waypoint) {
        POIModule module = (POIModule) _modules.getModule("POIModule");
        if(module != null && module.isEnabled()) {
            module.removePOI(waypoint);
        }
    }

    @Override
    public void addPOIInfoProvider(POIInfoProvider provider) {
		POIModule module = (POIModule)_modules.getModule("POIModule");
        if(module != null) {
            if(provider.getPermission() != null) {
                Permissions.registerPermission(provider.getPermission());
            }
            module.addInfoProvider(provider);
        }
    }

    @Override
    public void removePOIInfoProvider(POIInfoProvider provider) {
		POIModule module = (POIModule)_modules.getModule("POIModule");
        if(module != null) {
            if(provider.getPermission() != null) {
                Permissions.unregisterPermission(provider.getPermission());
            }
            module.removeInfoProvider(provider);
        }
    }

    @Override
    public void notifyPOIInfoChange(POI poi) {
        POIModule module = (POIModule)_modules.getModule("POIModule");
        if(module != null) {
            module.notifyDownstream(poi);
        }
    }

    @Override
    public void notifyPlayerInfoChange(OfflinePlayer player) {
        PlayerModule module = (PlayerModule)_modules.getModule("PlayerModule");
        if(module != null) {
            module.notifyDownstream(player);
        }
    }

    @Override
    public void setEconomyAdapter(IEconomyAdapter adapter) {
        Economy.getInstance().setEconomyAdapter(adapter);
        EconomyModule module = (EconomyModule) _modules.getModule("EconomyModule");
        if(module != null) {
            if(!module.isEnabled() && Config.moduleEnabled(module.getType())) {
                enableModule("EconomyModule");
            }
        }
    }

    @Override
    public void sendChatMessage(ChatMessage message) {
        ChatModule module = (ChatModule)_modules.getModule("ChatModule");
        if(module != null && module.isEnabled()) {
            module.sendChatMessage(message);
        }
    }

	/*@Override
	public void addPOIProvider(POIProvider arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void removePOIProvider(POIProvider arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updatePOIs() {
        POIModule module = (POIModule) _modules.getModule("POIModule");
        if(module != null && module.isEnabled()) {
            this.onFullUpdate(module.getType());
        }
	}*/
}


