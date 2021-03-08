package at.livekit.plugin;

import java.io.File;
import java.util.List;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

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
        return config.getBoolean("permissions.use");
    }

    public static List<String> getDefaultPermissions() {
        return (List<String>) config.getList("permissions.default");
    }

    public static List<String> getLiveMapWorlds() {
        return (List<String>) config.getList("modules.LiveMapModule.worlds");
    }

    public static boolean moduleEnabled(String name) {
        return config.getBoolean("modules."+name+".enabled");
    }

    public static String getPassword() {
        return config.getString("server.password");
    }

    /*public static String getModuleString(String name, String setting) {
        return config.getString("modules."+name+"."+setting);
    }*/

    private static void fixMissing() {
        boolean save = false;

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
        try{
            if(save) config.save(configFile);
        }catch(Exception ex){ex.printStackTrace();}

        //TODO: Convert world => worlds
    }
}
