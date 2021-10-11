package at.livekit.supported;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import at.livekit.plugin.Plugin;
import me.clip.placeholderapi.PlaceholderAPI;

public class PlaceholderAPIPlugin {
    
    private boolean enabled = false;

    public void onEnable() {
        try{
            if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                enabled = true;
                Plugin.log("Papi hook enabled!");

            }
        }catch(Exception ex){ex.printStackTrace();}
    }

    public void onDisable() {
        enabled = false;
    }

    public String replace(OfflinePlayer player, String text) {
        if(!enabled) return text;
        Plugin.log("Replacing "+text);
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}
