package at.livekit.provider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.SpawnChangeEvent;

import at.livekit.api.core.IIdentity;
import at.livekit.api.core.LKLocation;
import at.livekit.api.map.InfoEntry;
import at.livekit.api.map.POI;
import at.livekit.api.map.POILocationProvider;
import at.livekit.plugin.Config;
import at.livekit.plugin.Plugin;

public class POISpawnProvider extends POILocationProvider implements Listener {
    
    private Map<String, POI> _spawnPoints = new HashMap<String, POI>();

    public POISpawnProvider() {
        super(Plugin.getInstance(), "POI Spawn Provider", null);

        for(World world : Bukkit.getWorlds()) {
            _spawnPoints.put(world.getName(), POI.create(LKLocation.fromLocation(world.getSpawnLocation()), "Spawn", "Spawn point of "+world.getName(), BasicPOIProvider.POI_COLOR, Config.canTeleportSpawn(), false));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSpawnChangeEvent(SpawnChangeEvent event) {
        World world = event.getWorld();
        POI spawn = _spawnPoints.containsKey(world.getName()) ? _spawnPoints.get(world.getName()) : null;

        if(spawn == null) {
            _spawnPoints.put(world.getName(), POI.create(LKLocation.fromLocation(world.getSpawnLocation()), "Spawn", "Spawn point of "+world.getName(), BasicPOIProvider.POI_COLOR, Config.canTeleportSpawn(), false));
        }

        Plugin.getInstance().getLiveKit().notifyPOIChange(this);
    }

    @Override
    public List<POI> onResolvePOILocations(IIdentity identity) {
        return new ArrayList<>(_spawnPoints.values());
    }

    @Override
    public List<InfoEntry> onResolvePOIInfo(IIdentity identity, POI poi) {
        List<InfoEntry> entries = new ArrayList<>();
        World world = Bukkit.getWorld(poi.getLocation().getWorld());
        if(world != null) {
            entries.add(new InfoEntry("World", world.getName()));
        }
        return entries;
    }
}
