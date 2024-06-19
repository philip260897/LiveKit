package at.livekit.supported.essentialsx.spawn;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.spawn.IEssentialsSpawn;

import at.livekit.api.core.Color;
import at.livekit.api.core.LKLocation;
import at.livekit.api.map.POI;
import at.livekit.plugin.Config;

public class EssentialsSpawnPlugin {
    
    private Essentials essentials;
    public EssentialsSpawnPlugin(Essentials essentials) {
        this.essentials = essentials;
    }

    public void onEnable() {
        try{
            Plugin plugin = Bukkit.getPluginManager().getPlugin("EssentialsSpawn");
            if(plugin != null) {
                IEssentialsSpawn essentialsSpawn = (IEssentialsSpawn) plugin;

                for(String group : essentials.getPermissionsHandler().getGroups()) {
                    Location loc = essentialsSpawn.getSpawn(group);
                    if(loc != null) {
                        at.livekit.plugin.Plugin.getInstance().getLiveKit().addPointOfInterest(POI.create(LKLocation.fromLocation(loc), "Spawn ("+group+")", "Essentials spawn location for group "+group, Color.fromChatColor(ChatColor.RED), Config.canEssentialsTeleportSpawns(), false));
                    }
                }
            }
        }catch(Exception ex){ex.printStackTrace();}
    }

    public void onDisable() {

    }

}
