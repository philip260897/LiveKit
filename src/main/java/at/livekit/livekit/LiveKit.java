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
import at.livekit.packets.AuthorizationPacket;
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

public class LiveKit implements ModuleListener, Runnable {

    private static LiveKit instance;
    public static LiveKit getInstance() {
        if(LiveKit.instance == null) {
            LiveKit.instance = new LiveKit();
        }
        return LiveKit.instance;
    }
 
    private Thread _thread;
    private TCPServer _server;
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
        synchronized(_moduleFull) {
            if(!_moduleFull.contains(moduleType)) {
                _moduleFull.add(moduleType);
            }
        }
    }

    protected void notifyCommand(String command) {
        synchronized(_commands) {
            if(!_commands.contains(command)) {
                _commands.add(command);
            }
        }
    }

    public void notifyQueue(String moduleType) {
        synchronized(_moduleUpdates) {
            if(!_moduleUpdates.contains(moduleType)) {
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
        if(_server != null) {
            for(LiveKitClient client : _server.getClients()) {
                if(client.hasIdentity() && client.getIdentity().getUuid().equals(uuid)) {
                    return client.getIdentity();
                }
            }
        }
        return null;
    }

    public void onEnable() throws Exception{
        PlayerAuth.initialize();

        _modules.onEnable();


        _server = new TCPServer(_modules.getSettings().liveKitPort);
        _server.setServerListener(new ServerListener() {

            @Override
            public void onConnect(LiveKitClient client) {
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
            
        });
        _server.open();

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


            String module = null;
            List<Identity> clientUUIDs = _server.getConnectedUUIDs();
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
                    _server.broadcast(m.onUpdateAsync(clientUUIDs.stream().filter(identity->m.hasAccess(identity)).collect(Collectors.toList())));

                    if(module.equals("SettingsModule")) {
                        _server.broadcast(_modules.modulesAvailableAsync(clientUUIDs));
                        for(LiveKitClient client : _server.getClients()) {
                            client.sendPackets(_modules.onJoinAsync(client.getIdentity()));
                        }
                    }
                }
            }while(module != null);
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
                    for(LiveKitClient client : _server.getClients()) {
                        if(client.hasIdentity()) {
                            client.sendPacket(m.onJoinAsync(client.getIdentity()));
                        }
                    }
                }
            }while(module != null);

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
            

            synchronized(_packetsIncoming) {
                while(_packetsIncoming.size() > 0 && (System.currentTimeMillis()-start < tickTime)) {
                    RequestPacket packet = (RequestPacket) _packetsIncoming.remove(0);
                    RequestPacket response = handlePacket(packet.client, packet);
                    long mini = System.currentTimeMillis();
                    if(response != null) packet.client.sendPacket(response.setRequestId(packet.requestId));
                   // System.out.println("Sending: "+(System.currentTimeMillis()-mini));
                }
            }
            

            long delta = System.currentTimeMillis() - start;
            if( delta > 1) Plugin.debug("TICK "+delta+"ms "+tickTime+"ms");
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
                _server.close();
        }catch(Exception ex){ex.printStackTrace();}
    }
    
    private RequestPacket handlePacket(LiveKitClient client, RequestPacket packet) {
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
                    client.setIdentity(identity.getUUID());
                    client.getIdentity().loadPermissionsAsync();
                
                    try{
                        client.sendPacket(_modules.modulesAvailableAsync(client.getIdentity()));
                        client.sendPackets(_modules.onJoinAsync(client.getIdentity()));
                    }catch(Exception ex){ex.printStackTrace();}

                
                    return new IdentityPacket(identity.getUUID(), client.getIdentity().getName(), HeadLibrary.get(client.getIdentity().getName()), identity.generateSessionKey());
                }
                
            } 
            else
            {
                client.setAnonymous();
                client.getIdentity().loadPermissionsAsync();

                try{
                    client.sendPacket(_modules.modulesAvailableAsync(client.getIdentity()));
                    client.sendPackets(_modules.onJoinAsync(client.getIdentity()));
                }catch(Exception ex){ex.printStackTrace();}

                return new IdentityPacket(null, client.getIdentity().getName(), null, null);
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
            for(LiveKitClient client : _server.getClients()) {
                if(client.hasIdentity()) {
                    client.getIdentity().loadPermissionsAsync();
                }
            }

            List<Identity> clientUUIDs = _server.getConnectedUUIDs();
            //broadcast modules available for all clients
            _server.broadcast(_modules.modulesAvailableAsync(clientUUIDs));
            //give full update for all modules
            for(LiveKitClient client : _server.getClients()) {
                client.sendPackets(_modules.onJoinAsync(client.getIdentity()));
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


}


