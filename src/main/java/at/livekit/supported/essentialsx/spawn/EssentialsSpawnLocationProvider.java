package at.livekit.supported.essentialsx.spawn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.ChatColor;

import com.earth2me.essentials.spawn.IEssentialsSpawn;

import at.livekit.api.core.Color;
import at.livekit.api.core.IIdentity;
import at.livekit.api.core.LKLocation;
import at.livekit.api.map.InfoEntry;
import at.livekit.api.map.POI;
import at.livekit.api.map.POILocationProvider;
import at.livekit.plugin.Config;

public class EssentialsSpawnLocationProvider extends POILocationProvider {

    final IEssentialsSpawn essentialsSpawn;
    final Map<String, POI> essentialSpawnPOIs = new HashMap<>();

    public EssentialsSpawnLocationProvider(IEssentialsSpawn essentials) {
        super(essentials, essentials.getName(), "livekit.essentials.spawn");
        this.essentialsSpawn = essentials;
    }

    @Override
    public List<POI> onResolvePOILocations(IIdentity identity) {
        String group = identity.getGroup(identity.getCurrentViewingWorld());
        if(group == null) group = "default";

        essentialSpawnPOIs.put(group, POI.create(LKLocation.fromLocation(essentialsSpawn.getSpawn(group)), "Spawn", "Essentials spawn location for group "+group, Color.fromChatColor(ChatColor.RED), Config.canEssentialsTeleportSpawns(), false));
        
        List<POI> pois = new ArrayList<>();
        pois.add(essentialSpawnPOIs.get(group));
        return pois;
    }
    
    @Override
    public List<InfoEntry> onResolvePOIInfo(IIdentity identity, POI poi) {
        List<InfoEntry> entries = new ArrayList<>();

        String group = essentialSpawnPOIs.entrySet().stream().filter(e -> e.getValue().equals(poi)).map(Map.Entry::getKey).findFirst().orElse(null);
        if(group != null) {
            entries.add(new InfoEntry("Group", group));
        }

        return entries;
    }
}
