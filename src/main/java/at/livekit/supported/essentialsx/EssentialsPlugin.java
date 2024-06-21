package at.livekit.supported.essentialsx;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import com.earth2me.essentials.Essentials;

import at.livekit.plugin.Config;
import at.livekit.supported.essentialsx.spawn.EssentialsSpawnPlugin;

public class EssentialsPlugin {

    private EssentialsSpawnPlugin spawnPlugin;
    
    public void onEnable() {
        try{
            Plugin plugin = Bukkit.getPluginManager().getPlugin("Essentials");
            if(plugin != null) {
                Essentials essentials = (Essentials) plugin;
                EssentialsPlayerInfoProvider playerInfoProvider = new EssentialsPlayerInfoProvider(essentials);
                at.livekit.plugin.Plugin.getInstance().getLiveKit().addPlayerInfoProvider(playerInfoProvider);

                EssentialsHomeLocationProvider homeLocationProvider = new EssentialsHomeLocationProvider(essentials);
                at.livekit.plugin.Plugin.getInstance().getLiveKit().addPlayerInfoProvider(homeLocationProvider);
                Bukkit.getServer().getPluginManager().registerEvents(homeLocationProvider, at.livekit.plugin.Plugin.getInstance());

                at.livekit.plugin.Plugin.getInstance().getLiveKit().addPlayerInfoProvider(new EssentialsAdminInfoProvider(essentials));

                if(Config.canEssentialsPinWarps()) {
                    EssentialsPOIProvider poiProvider = new EssentialsPOIProvider(essentials);
                    at.livekit.plugin.Plugin.getInstance().getLiveKit().addPOIInfoProvider(poiProvider);
                    Bukkit.getServer().getPluginManager().registerEvents(poiProvider, at.livekit.plugin.Plugin.getInstance());
                }

                if(Config.canEssentialsPinSpawns()) {
                    spawnPlugin = new EssentialsSpawnPlugin(essentials);
                    spawnPlugin.onEnable();
                }
            }
        }catch(Exception ex){ex.printStackTrace();}
    }

    public void onDisable() {

    }

}
