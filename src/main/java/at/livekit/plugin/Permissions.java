package at.livekit.plugin;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.permission.Permission;

public class Permissions 
{
    public static List<String> permissions = new ArrayList<String>();

    private static Permission perms;
    private static Chat chat;

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
        permissions.add("livekit.module.console");
        permissions.add("livekit.module.inventory");
        permissions.add("livekit.module.economy");
        permissions.add("livekit.module.tps");
        permissions.add("livekit.module.messaging");
        
        permissions.add("livekit.chat.write");
        permissions.add("livekit.chat.write_offline");
        permissions.add("livekit.poi.personalpins");
        permissions.add("livekit.poi.edit");
        permissions.add("livekit.players.other");
        permissions.add("livekit.map.info");
        permissions.add("livekit.console.execute");
        permissions.add("livekit.essentials.homes");
        permissions.add("livekit.essentials.info");
        permissions.add("livekit.essentials.admin");
        permissions.add("livekit.essentials.warps");
        permissions.add("livekit.essentials.spawn");

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

            RegisteredServiceProvider<Chat> rspChat = Bukkit.getServer().getServicesManager().getRegistration(Chat.class);
            if(rspChat != null)
            {
                chat = rspChat.getProvider();
            }
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

    public static String getPrefix(OfflinePlayer player) {
        if(use && chat != null)
        {
            return chat.getPlayerPrefix(null , player);
        }
        
        return "";
    }

    public static String getSuffix(OfflinePlayer player) {
        if(use && chat != null)
        {
            return chat.getPlayerSuffix(null , player);
        }
        
        return "";
    }

    public static String getGroup(String world, OfflinePlayer player) {
        if(use && perms != null)
        {
            return perms.getPrimaryGroup(world, player);
        }
        
        return null;
    }

    public static void registerPermission(String permission) {
        synchronized(permissions) {
            if(!permissions.contains(permission)) {
                permissions.add(permission);
            }
        }
    }

    public static void unregisterPermission(String permission) {
        synchronized(permission) {
            if(permissions.contains(permission)) {
                permissions.remove(permission);
            }
        }
    }
}
