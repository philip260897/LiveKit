package at.livekit.livekit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import org.bukkit.Bukkit;
import org.json.JSONObject;

import at.livekit.modules.BaseModule;
import at.livekit.modules.ModuleManager;

import at.livekit.modules.BaseModule.ModuleListener;
import at.livekit.packets.AuthorizationPacket;
import at.livekit.packets.IdentityPacket;
import at.livekit.packets.RequestPacket;
import at.livekit.packets.ServerSettingsPacket;
import at.livekit.packets.StatusPacket;
import at.livekit.plugin.Plugin;
import at.livekit.server.IPacket;
import at.livekit.server.TCPServer;
import at.livekit.server.TCPServer.ServerListener;
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

            //handle module updates
            /*List<String> clientUUIDs = null;
            String module = null;
            synchronized(_moduleUpdates) {
                if(_moduleUpdates.size() != 0) {
                    module = _moduleUpdates.remove(0);
                }
            }
            if(module != null) {
                clientUUIDs = _server.getConnectedUUIDs();
                try{
                    Map<String,IPacket> updatePackets = _modules.onUpdate(module, clientUUIDs);
                    _server.broadcast(updatePackets);

                    if(module.equals("SettingsModule")) {
                        updatePackets = _modules.modulesAvailable(clientUUIDs);
                        _server.broadcast(updatePackets);
                    }
                }catch(Exception ex){ex.printStackTrace();}
            }*/
            if(_futureModuleUpdates != null) {
                if(_futureModuleUpdates.isDone()) {
                    _futureModuleUpdates = null;
                }
            }
            if(_futureModuleUpdates == null) {
                _futureModuleUpdates = _modules.updateModules();
            }


            String module = null;
            List<String> clientUUIDs = _server.getConnectedUUIDs();
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
                    _server.broadcast(m.onUpdateAsync(clientUUIDs));

                    if(module.equals("SettingsModule")) {
                        _server.broadcast(_modules.modulesAvailableAsync(clientUUIDs));
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
                        client.sendPacket(m.onJoinAsync(client.getPlayerUUID()));
                    }
                }
            }while(module != null);
            

            synchronized(_packetsIncoming) {
                while(_packetsIncoming.size() > 0) {
                    RequestPacket packet = (RequestPacket) _packetsIncoming.remove(0);
                    RequestPacket response = handlePacket(packet.client, packet);
                    if(response != null) packet.client.sendPacket(response.setRequestId(packet.requestId));
                }
            }

            long delta = start - System.currentTimeMillis();
            if(delta >= tickTime) System.out.println("LiveKit tick can't keep up");
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
            _thread.interrupt();
            synchronized(_shutdownLock) {
                _shutdownLock.wait(1000);
            }
        }catch(Exception ex){ex.printStackTrace();}
        try{
            PlayerAuth.save();
        }catch(Exception ex){ex.printStackTrace();}
        try{
            _modules.onDisable();
        }catch(Exception ex){ex.printStackTrace();}
        try{
            _server.close();
        }catch(Exception ex){ex.printStackTrace();}
    }
    
    private RequestPacket handlePacket(LiveKitClient client, RequestPacket packet) {
        if(packet instanceof AuthorizationPacket) {
            AuthorizationPacket auth = (AuthorizationPacket)packet;
            PlayerAuth identity = null;
            if(auth.isPin()) {
                identity = PlayerAuth.validateClaim(auth.getValue());
            } else {
                identity = PlayerAuth.get(auth.getUUID());
                if(identity.isValidSession(auth.getValue())) {
                    identity.removeSession(auth.getValue());
                } else {
                   // identity = null;
                }
            }
            String name = "";
            if(identity != null) {
                final String uuid = identity.getUUID();
                try{
                    name = Bukkit.getScheduler().callSyncMethod(Plugin.instance, new Callable<String>(){
                        @Override
                        public String call() throws Exception {
                            // TODO Auto-generated method stub
                            return  Bukkit.getOfflinePlayer(UUID.fromString(uuid)).getName();
                        }
                    }).get();
                }catch(Exception ex){ex.printStackTrace();}
            }
            
            //client.sendPacket(new ModulesPacket(buildModuleInfo(identity != null ? identity.getUUID() : null)));
            try{
                client.sendPacket(_modules.modulesAvailableAsync(identity.getUUID()));
                client.sendPackets(_modules.onJoinAsync(identity.getUUID()));
            }catch(Exception ex){ex.printStackTrace();}

            if(identity != null) {
                client.setPlayerUUID(identity.getUUID());
                return new IdentityPacket(identity.getUUID(), name, HeadLibrary.get(identity.getUUID()), identity.generateSessionKey());
            }
            return new StatusPacket(0, "Invalid authentication credentials!");
        }

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


