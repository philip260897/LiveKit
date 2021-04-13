package at.livekit.plugin;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import net.milkbowl.vault.permission.Permission;

public class Permissions 
{
    public static List<String> permissions = new ArrayList<String>();

    private static Permission perms;
    private static List<String> defaultPermissions = new ArrayList<String>();
    private static boolean use = true;

    public static boolean initialize() {
        Permissions.use = Config.usePermissions();
        permissions.add("livekit.commands.admin");
        permissions.add("livekit.commands.basic");
		permissions.add("livekit.module.map");
		permissions.add("livekit.module.players");
		permissions.add("livekit.module.weathertime");
        permissions.add("livekit.module.admin");
        permissions.add("livekit.module.chat");
        permissions.add("livekit.module.poi");
        
        
        permissions.add("livekit.chat.write");
        permissions.add("livekit.player.pins");

        if(use) {
            if(Bukkit.getPluginManager().getPlugin("Vault") == null) {
                Plugin.severe("Vault not found. Disable permissions in config.yml and restart!");
                return false;
            }

            RegisteredServiceProvider<Permission> rsp = Bukkit.getServer().getServicesManager().getRegistration(Permission.class);
            perms = rsp.getProvider();
            
            if(perms == null) {
                Plugin.severe("Could not find compatible permissions Plugin");
                return false;
            }
            Plugin.log("Found Vault! Using permissions ["+perms.getName()+"]");
        }
        else
        {
            defaultPermissions = Config.getDefaultPermissions();
            Plugin.log("Using default permissions from config.yml for connected LiveKit clients");
        }
        return true;
    }  
    
    public static boolean has(OfflinePlayer player, String permission) {
        if(player.isOp()) return true;
        if(use) {
            return perms.playerHas(Bukkit.getWorlds().get(0).getName(), player, permission);
        } else {
            return defaultPermissions.contains(permission);
        }
    }
}
