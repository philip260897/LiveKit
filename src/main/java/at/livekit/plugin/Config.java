package at.livekit.plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import at.livekit.storage.StorageManager.StorageSettings;
import at.livekit.utils.Utils;

public class Config 
{
    private static File configFile;
    private static FileConfiguration config;

    public static void initialize() {
        configFile = new File(Plugin.getInstance().getDataFolder(), "config.yml");
        if(!configFile.exists()) {
            Plugin.getInstance().saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);

        fixMissing();
    }    

    public static int getServerPort() {
        return config.getInt("server.port");
    }

    public static String getServerName() {
        return config.getString("server.name");
    }

    public static String getProxyHostname() {
        return getNullableString("proxy.hostname");
    }

    public static boolean isProxyEnabled() {
        return config.getBoolean("proxy.enabled");
    } 

    public static int getTickRate() {
        int tickrate = config.getInt("server.tickrate");
        if(tickrate <= 0) return 1;
        if(tickrate >= 20) return 20;
        return tickrate;
    }

    public static boolean allowAnonymous() {
        return config.getBoolean("anonymous.allow");
    }

    public static List<String> getAnonymousPermissions() {
        return (List<String>) config.getList("anonymous.permissions");
    }

    public static boolean usePermissions() {
        return config.getBoolean("permissions.useVault");
    }

    public static List<String> getDefaultPermissions() {
        return (List<String>) config.getList("permissions.default");
    }

    public static Map<String, String> getLiveMapWorlds() {
        HashMap<String, String> map = new HashMap<String, String>();
        int count = 0;
        for(String world : (List<String>) config.getList("modules.LiveMapModule.worlds")) {
            if(world.contains(":")) {
                map.put(world.split(":")[0], count+":"+world.split(":")[1]);
            } else {
                map.put(world, count+":"+world);
            }
            count++;
        }
        return map;
    }

    public static List<String> getLiveMapWorldsOrdered() {
        List<String> stripped = new ArrayList<String>();

        for(String world : (List<String>) config.getList("modules.LiveMapModule.worlds")) {
            stripped.add(world.split(":")[0]);
        }

        return stripped;
    }

    public static boolean moduleEnabled(String name) {
        return config.getBoolean("modules."+name+".enabled");
    }

    public static String getPassword() {
        return getNullableString("server.password");
    }

    public static String getConsolePassword() {
        return config.getString("modules.ConsoleModule.password");
    }

    public static int getPersonalPinLimit() {
        return config.getInt("modules.POIModule.personalpins");
    }

    public static boolean canTeleportBed() {
        return config.getBoolean("modules.POIModule.teleport_bed");
    }

    public static boolean canTeleportSpawn() {
        return config.getBoolean("modules.POIModule.teleport_spawn");
    }

    public static StorageSettings getStorageSettings() {
        return getStorageSettings("storage");
    }

    public static StorageSettings getMigrateStorage() {
        return getStorageSettings("migrate");
    }

    private static StorageSettings getStorageSettings(String base) {
        if(config.contains(base)) {
            return new StorageSettings(config.getString(base+".type"), config.getString(base+".sql.host"), config.getString(base+".sql.database"), config.getString(base+".sql.user"), config.getString(base+".sql.password"));
        }
        return null;
    }

    public static boolean isDiscordEnabled() {
        return config.getBoolean("plugins.DiscordSRV.enabled");
    }

    public static List<String> getDiscordWhitelist() {
        return (List<String>) config.getList("plugins.DiscordSRV.channelIDs");
    }

    public static String getChatOfflineFormat() {
        return config.getString("modules.ChatModule.offlineFormat");
    }

    public static void resetMigration() {
        try{
            config.set("migrate", null);
            config.save(configFile);
        }catch(Exception ex){ex.printStackTrace();}
    }
    /*public static String getStorageType() {
        return config.getString("storage.type");
    }

    public static String getStorageUser() {
        return getNullableString("storage.user");
    }

    public static String getStoragePassword() {
        return getNullableString("storage.password");
    }

    public static String getStorageHost() {
        return getNullableString("storage.host");
    }*/

    public static String getNullableString(String path) {
        String string = config.getString(path);
        if(string == null) return null;
        if(string.equalsIgnoreCase("null")) return null;
        return string;
    }
    
    /*public static String getModuleString(String name, String setting) {
        return config.getString("modules."+name+"."+setting);
    }*/

    private static void fixMissing() {
        boolean save = false;


        if(!config.contains("proxy.hostname")) {
            Plugin.log("Updating config with proxy hostname");
            config.set("proxy.enabled", true);
            config.set("proxy.hostname", "NULL");
            save = true;
        }

        if(!config.contains("modules.ChatModule.enabled")) {
            config.set("modules.ChatModule.enabled", true);
            Plugin.log("Updating config with ChatModule");
            if(!getDefaultPermissions().contains("livekit.modules.chat")) {
                getDefaultPermissions().add("livekit.modules.chat");
                if(usePermissions()) {
                    Plugin.log("Friendly reminder, use livekit.modules.chat permission to enable chat in App!");
                }
            }
            save = true;
        }
        
        if(!config.contains("modules.LiveMapModule.worlds")) {
            Plugin.log("Updating config to Multi-World format :)");
            List<String> worlds = new ArrayList<String>();
            for(int i = 0; i < (Bukkit.getWorlds().size() < 3 ? Bukkit.getWorlds().size() : 3); i++) {
                worlds.add(Bukkit.getWorlds().get(i).getName());
            }
            config.set("modules.LiveMapModule.worlds", worlds);
            save = true;
        }

        if(config.contains("modules.LiveMapModule.world")) {
            config.set("modules.LiveMapModule.world", null);
        }

        if(config.contains("permissions.use")) {
            boolean value = config.getBoolean("permissions.use");
            config.set("permissions.useVault", value);
            config.set("permissions.use", null);
            save = true;
        }

        List<String> permissions = getDefaultPermissions();
        if(permissions.contains("livekit.modules.chat")) {
            Plugin.debug("Fixing chat module permissions");
            permissions.remove("livekit.modules.chat");
            permissions.add("livekit.module.chat");
            config.set("permissions.default", permissions);
            save = true;
        }

        if(config.get("modules.POIModule") == null) {
            Plugin.log("Upgrading config to new version...");
            config.set("modules.POIModule.enabled", true);
            config.set("modules.POIModule.personalpins", 5);
            config.set("modules.POIModule.teleport_spawn", false);
            config.set("modules.POIModule.teleport_bed", false);
            List<String> perms = getDefaultPermissions();
            if(!perms.contains("livekit.module.poi")) perms.add("livekit.module.poi");
            if(!perms.contains("livekit.poi.personalpins")) perms.add("livekit.poi.personalpins");
            config.set("permissions.default", perms);
            save = true;
        }

        if(config.get("modules.ConsoleModule") == null) {
            Plugin.log("Patching config with new Console module...");
            config.set("modules.ConsoleModule.enabled", true);
            config.set("modules.ConsoleModule.password", "change_me");

            List<String> perms = getDefaultPermissions();
            if(!perms.contains("livekit.players.other")) perms.add("livekit.players.other");
            config.set("permissions.default", perms);

            List<String> permsAnonymous = getAnonymousPermissions();
            if(!permsAnonymous.contains("livekit.players.other")) permsAnonymous.add("livekit.players.other");
            config.set("anonymous.permissions", permsAnonymous);

            save = true;
        }
        if("change_me".equals(config.get("modules.ConsoleModule.password"))) {
            Plugin.log("Generating secure console password");
            config.set("modules.ConsoleModule.password", Utils.generateRandom(10));

            save = true;
        }

        if(config.get("modules.InventoryModule") == null) {
            Plugin.log("Patching config with new Inventory module...");
            config.set("modules.InventoryModule.enabled", true);

            save = true;
        }

        if(config.get("modules.TPSModule") == null) {
            Plugin.log("Patching config with new TPSModule...");
            config.set("modules.TPSModule.enabled", true);

            save = true;
        }

        if(config.get("storage.type") == null) {
            Plugin.log("Patching storage config...");
            config.set("storage.type", "SQLITE");
            config.set("storage.sql.host", "NULL");
            config.set("storage.sql.user", "NULL");
            config.set("storage.sql.database", "NULL");
            config.set("storage.sql.password", "NULL");

            if(getPassword() == null) {
                config.set("server.password", "NULL");
            }

            save = true;
        }

        if(config.get("modules.ChatModule.offlineFormat") == null)
        {
            Plugin.log("Patching config with new entries");
            config.set("modules.ChatModule.offlineFormat", "&7[Offline]&r <{prefix}&r{name}&r{suffix}&r> {message}");

            save = true;
        }

        if(config.get("plugins.DiscordSRV.enabled") == null)
        {
            Plugin.log("Patching config with new entries");
            config.set("plugins.DiscordSRV.enabled", false);

            save = true;
        }

        if(config.get("plugins.DiscordSRV.channelIDs") == null)
        {
            List<String> channels = new ArrayList<String>();
            channels.add("all");

            Plugin.log("Patching config with new entries");
            config.set("plugins.DiscordSRV.channelIDs", channels);

            save = true;
        }

        try{
            if(save) {
                //config.options().header(config.options().header());
                config.save(configFile);
            }
        }catch(Exception ex){ex.printStackTrace();}
    }
}
