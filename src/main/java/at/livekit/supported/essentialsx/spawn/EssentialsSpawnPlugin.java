package at.livekit.supported.essentialsx.spawn;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.spawn.IEssentialsSpawn;

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

                EssentialsSpawnLocationProvider provider = new EssentialsSpawnLocationProvider(essentialsSpawn);
                at.livekit.plugin.Plugin.getInstance().getLiveKit().addPOILocationProvider(provider);
            }
        }catch(Exception ex){ex.printStackTrace();}
    }

    public void onDisable() {

    }

}
