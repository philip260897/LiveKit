package at.livekit.plugin;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
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
        /*permissions.add("livekit.commands.admin");
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
        permissions.add("livekit.module.performance");
        permissions.add("livekit.module.statistics");
        
        permissions.add("livekit.chat.write");
        permissions.add("livekit.chat.write_offline");
        permissions.add("livekit.poi.personalpins");
        permissions.add("livekit.poi.edit");
        permissions.add("livekit.players.other");
        permissions.add("livekit.map.info");
        permissions.add("livekit.console.execute");*/

        //new
        permissions.add("livekit.commands.basic");  //access commands to connect to livekit
        permissions.add("livekit.commands.admin");  //access commands to render map

        permissions.add("livekit.map");             //acesss to map
        permissions.add("livekit.map.info");        //access to block info indicator
        permissions.add("livekit.map.compass");     //access to compass

        permissions.add("livekit.players");         //see self on map
        permissions.add("livekit.players.other");   //see other players
        permissions.add("livekit.players.hidden");  //see invisible (potion) players
        permissions.add("livekit.players.kick");
        permissions.add("livekit.players.ban");
        permissions.add("livekit.players.unban");
        permissions.add("livekit.players.kill");
        permissions.add("livekit.players.slap");
        permissions.add("livekit.players.whitelist");
        permissions.add("livekit.players.strike");
        permissions.add("livekit.players.gamemode");
        permissions.add("livekit.players.selfteleport");
        permissions.add("livekit.players.teleport");

        permissions.add("livekit.players.give");
        permissions.add("livekit.players.give.enchanted");

        permissions.add("livekit.weathertime");     //display weather and time
        permissions.add("livekit.weathertime.set"); //set weather and time

        permissions.add("livekit.chat");            //read chat from app
        permissions.add("livekit.chat.write");      //write from app when online
        permissions.add("livekit.chat.offline");    //write from app when offline

        permissions.add("livekit.poi");             //able to see POIs
        permissions.add("livekit.poi.personal");    //set personal pins
        permissions.add("livekit.poi.create");      //create public POIs
        permissions.add("livekit.poi.teleportable");//create public POIs to teleport

        permissions.add("livekit.console");         //able to see console
        permissions.add("livekit.console.execute"); //able to execute commands

        permissions.add("livekit.inventory");       //open own inventory
        permissions.add("livekit.inventory.other"); //open other inventory
        permissions.add("livekit.inventory.delete");//delete items from inventory

        permissions.add("livekit.performance");     //access to performance view
        

        

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
