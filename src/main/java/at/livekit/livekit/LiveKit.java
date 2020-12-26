package at.livekit.livekit;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.Callable;

import org.bukkit.Bukkit;
import org.json.JSONArray;

import at.livekit.main.LiveMap;
import at.livekit.modules.BaseModule;
import at.livekit.modules.SettingsModule;
import at.livekit.packets.AuthorizationPacket;
import at.livekit.packets.IdentityPacket;
import at.livekit.packets.RequestPacket;
import at.livekit.packets.ServerSettingsPacket;
import at.livekit.packets.StatusPacket;
import at.livekit.plugin.Plugin;
import at.livekit.packets.LiveMapSubscriptionPacket;
import at.livekit.packets.ModulesPacket;
import at.livekit.server.TCPServer;
import at.livekit.server.TCPServer.ServerListener;
import at.livekit.utils.HeadLibrary;

public class LiveKit {
    private static TCPServer server;
    //private static LiveKitSettings settings = new LiveKitSettings();

    //private static List<LPlayer> players = new ArrayList<LPlayer>();
    private static Map<String,LiveMap> livemaps = new HashMap<String,LiveMap>();

    private static SettingsModule settings;
    private static Map<String, BaseModule> modules = new HashMap<String, BaseModule>();

    public static void start() throws Exception{
        PlayerAuth.initialize();

        settings = new SettingsModule();
        settings.onEnable();

        modules.put(settings.getType(), settings);
                


        server = new TCPServer(settings.liveKitPort);
        server.setServerListener(new ServerListener() {

            @Override
            public void onConnect(LiveKitClient client) {
                //greet new client with current server settings
                client.sendPacket(new ServerSettingsPacket(settings.toJson(null)));
            }

            @Override
            public void onDisconnect(LiveKitClient client) {}

            @Override
            public RequestPacket onPacketReceived(LiveKitClient client, RequestPacket packet) {
                return handlePacket(client, packet);
            }
            
        });
        server.open();

        if(settings.liveMap != null) {
            LiveMap livemap = new LiveMap(settings.liveMap, server);
            livemaps.put(settings.liveMap, livemap);
        }
    }

    public static LiveMap getLiveMap(String world) {
        return livemaps.get(world);
    }

    public static String getLiveMapWorld() {
        return settings.liveMap;
    }
    /*public static String[] getLiveMapEnabledWorlds() {
        return settings.liveMaps;
    }*/

    public static void dispose() {
        try{
            PlayerAuth.save();
        }catch(Exception ex){ex.printStackTrace();}
        for(Entry<String, LiveMap> entry : livemaps.entrySet()) {
            entry.getValue().close();
        }
        try{
            server.close();
        }catch(Exception ex){ex.printStackTrace();}

        for (Entry<String,BaseModule> module : modules.entrySet()) {
            module.getValue().onDisable();
        }
    }
    
    private static RequestPacket handlePacket(LiveKitClient client, RequestPacket packet) {
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
                    identity = null;
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
            
            client.sendPacket(new ModulesPacket(buildModuleInfo(identity != null ? identity.getUUID() : null)));

            if(identity != null) {
                client.setPlayerUUID(identity.getUUID());
                return new IdentityPacket(identity.getUUID(), name, HeadLibrary.get(identity.getUUID()), identity.generateSessionKey());
            }
            return new StatusPacket(0, "Invalid authentication credentials!");
        }

        if(packet instanceof LiveMapSubscriptionPacket) {
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
        }
        return new StatusPacket(0, "Unkown request");
    }

    private static boolean hasAccess(LiveKitClient client, String resource) {
        if(settings.needsIdentity == true && !client.hasIdentity()) return false;

        return true;
    }

    private static JSONArray buildModuleInfo(String uuid) {
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
    }
}


