package at.livekit.livekit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.json.JSONObject;

import at.livekit.modules.BaseModule;
import at.livekit.modules.LiveMapModule;
import at.livekit.modules.ModuleManager;

import at.livekit.modules.BaseModule.ModuleListener;
import at.livekit.nio.NIOClient;
import at.livekit.nio.NIOServer;
import at.livekit.nio.NIOServer.NIOServerEvent;
import at.livekit.packets.ActionPacket;
import at.livekit.packets.AuthorizationPacket;
import at.livekit.packets.IPacket;
import at.livekit.packets.IdentityPacket;
import at.livekit.packets.RawPacket;
import at.livekit.packets.RegionPacket;
import at.livekit.packets.RegionRequest;
import at.livekit.packets.RequestPacket;
import at.livekit.packets.ServerSettingsPacket;
import at.livekit.packets.StatusPacket;
import at.livekit.plugin.Config;
import at.livekit.plugin.Plugin;
import at.livekit.livekit.TCPServer.ServerListener;
import at.livekit.utils.HeadLibrary;

public class LiveKit implements ModuleListener, NIOServerEvent<Identity>, Runnable {

    private static LiveKit instance;

    public static LiveKit getInstance() {
        if (LiveKit.instance == null) {
            LiveKit.instance = new LiveKit();
        }
        return LiveKit.instance;
    }

    private Thread _thread;
    // private TCPServer _server;
    private NIOServer<Identity> _server;
    private ModuleManager _modules = new ModuleManager(this);
    private List<String> _moduleUpdates = new ArrayList<String>();
    private List<String> _moduleFull = new ArrayList<String>();
    private List<String> _commands = new ArrayList<String>();
    private List<RequestPacket> _packetsIncoming = new ArrayList<RequestPacket>();

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

    public void commandReloadPermissions() {
        notifyCommand("permreload");
    }

    public Identity getIdentity(String uuid) {
        if (_server != null) {
            for (Identity identity : _server.getIdentifiers()) {
                if (identity.isAnonymous() && identity.getUuid().equals(uuid)) {
                    return identity;
                }
            }
        }
        return null;
    }

    public void onEnable() throws Exception {
        PlayerAuth.initialize();

        _modules.onEnable();

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
        _thread.start();
    }

    private boolean abort;
    private Object _shutdownLock = new Object();
    private Future<Void> _futureModuleUpdates;
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
            List<Identity> clientUUIDs = _server.getIdentifiers();
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
                    _server.send(m.onUpdateAsync(clientUUIDs.stream().filter(identity->m.hasAccess(identity)).collect(Collectors.toList())));

                    if(module.equals("SettingsModule")) {
                        _server.send(_modules.modulesAvailableAsync(clientUUIDs));
                        for(Identity identity : _server.getIdentifiers()) {
                            _server.send(identity, _modules.onJoinAsync(identity));
                        }
                    }
                }
            }while(module != null);

            long module_updates = System.currentTimeMillis();

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
                    for(Identity identity : _server.getIdentifiers()) {
                        _server.send(identity, m.onJoinAsync(identity));
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

            synchronized(_packetsIncoming) {
                while(_packetsIncoming.size() > 0 && (System.currentTimeMillis()-start < tickTime)) {
                    RequestPacket packet = (RequestPacket) _packetsIncoming.remove(0);
                    RequestPacket response = handlePacket(packet.client, packet);
                    long mini = System.currentTimeMillis();
                    if(response != null) _server.send(packet.client, response.setRequestId(packet.requestId));
                   // System.out.println("Sending: "+(System.currentTimeMillis()-mini));
                }
            }

            long packets = System.currentTimeMillis();
            

            long delta = System.currentTimeMillis() - start;
            if( delta > 1) Plugin.debug("TICK "+delta+"ms "+tickTime+"ms [cpackets="+(packets-commands)+"ms; cmds="+(commands-module_full)+"ms; mfull="+(module_full-module_updates)+"ms; mupdate="+(module_updates-module_dispatch)+"; dispatch="+(module_dispatch-start)+"]");
            if(delta >= tickTime) Plugin.severe("LiveKit tick can't keep up ("+delta+"ms/"+tickTime+"ms)");
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
        try{
            PlayerAuth.save();
        }catch(Exception ex){ex.printStackTrace();}
        try{
            if(_modules != null)
                _modules.onDisable();
        }catch(Exception ex){ex.printStackTrace();}
        try{
            if(_server != null)
                _server.stop();
        }catch(Exception ex){ex.printStackTrace();}
    }
    
    private RequestPacket handlePacket(NIOClient<Identity> client, RequestPacket packet) {
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
        return new StatusPacket(0, "Unkown request");
    }

    private void handleCommand(String command) {
        if(command.equalsIgnoreCase("permreload")) {
            //reload permissions for all connected clients
            List<Identity> clientUUIDs = _server.getIdentifiers();

            for(Identity identity : clientUUIDs) {
                identity.loadPermissionsAsync();
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
        Plugin.log("Client connected");
        JSONObject serverSettings = new JSONObject();
        serverSettings.put("liveKitVersion", _modules.getSettings().liveKitVersion);
        serverSettings.put("liveKitTickRate", _modules.getSettings().liveMapTickRate);
        serverSettings.put("needsIdentity", _modules.getSettings().needsIdentity);
        serverSettings.put("serverName", _modules.getSettings().serverName);
        serverSettings.put("needsPassword", _modules.getSettings().needsPassword);

        _server.send(client, new ServerSettingsPacket(serverSettings));
    }

    @Override
    public void clientDisconnected(NIOClient<Identity> client) {
        Plugin.log("Client disconnected");
    }

    @Override
    public void clientMessageReceived(NIOClient<Identity> client, String message) {
        JSONObject json = new JSONObject(message);
        int packetId = json.getInt("packet_id");
        int requestId = json.getInt("request_id");

        if(client.getIdentifier() == null && packetId != AuthorizationPacket.PACKETID) {
            _server.send(client, new StatusPacket(0, "Not Authorized").setRequestId(requestId));
            client.close();
            return;
        }
        
        RequestPacket response = null;
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
            }
            else
            {
                response = new StatusPacket(0, "Region "+request.x+" "+request.z+" in "+request.world+" not found!");
            }
        }
        /*if(packetId == LiveMapSubscriptionPacket.PACKETID) {
            response = listener.onPacketReceived((LiveKitClient)sender, (LiveMapSubscriptionPacket) new LiveMapSubscriptionPacket().fromJson(data));    
        }*/

        if(response != null) _server.send(client, response.setRequestId(requestId));
    }


}


