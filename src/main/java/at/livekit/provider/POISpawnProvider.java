package at.livekit.provider;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.SpawnChangeEvent;

import at.livekit.api.core.Color;
import at.livekit.api.core.LKLocation;
import at.livekit.api.map.InfoEntry;
import at.livekit.api.map.POI;
import at.livekit.api.map.POIInfoProvider;
import at.livekit.plugin.Config;
import at.livekit.plugin.Plugin;

public class POISpawnProvider extends POIInfoProvider implements Listener {

    /*public POISpawnProvider() {
        super(Plugin.getInstance(), "World Spawn Provider");
    }

    @Override
    public void onPOIRequest(World world, List<Waypoint> waypoints) {
        Waypoint waypoint = new Waypoint(world.getSpawnLocation(), "Spawn", "Spawn of "+world.getName(), Color.fromChatColor(ChatColor.RED), Privacy.PUBLIC);
        waypoints.add(waypoint);
    }*/
    
    private List<POI> _spawnPoints = new ArrayList<>();

    public POISpawnProvider() {
        super(Plugin.getInstance(), "POI Spawn Provider", null);

        for(World world : Bukkit.getWorlds()) {
            registerSpawnpoint(world);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSpawnChangeEvent(SpawnChangeEvent event) {
        World world = event.getWorld();
        POI spawn = spawnPoint(world);
        if(spawn != null) {
            _spawnPoints.remove(spawn);
            Plugin.getInstance().getLiveKit().removePointOfIntereset(spawn);
        }

        registerSpawnpoint(world);
    }

    private POI spawnPoint(World world) {
        for(POI wp : _spawnPoints) {
            if(wp.getLocation().getName().equals(world.getName())) {
                return wp;
            }
        }
        return null;
    }

    private void registerSpawnpoint(World world) {
        POI spawn = new POI(LKLocation.fromLocation(world.getSpawnLocation()), "Spawn", "Spawn point of "+world.getName(), BasicPOIProvider.POI_COLOR, Config.canTeleportSpawn());
        Plugin.getInstance().getLiveKit().addPointOfInterest(spawn);
        _spawnPoints.add(spawn);
    }

    @Override
    public void onResolvePOIInfo(POI poi, List<InfoEntry> entries) {
        /*POI worldSpawn = spawnPoint(Bukkit.getWorld("world"));
        if(poi.equals(worldSpawn)) {
            entries.add(new InfoEntry("Spawns", "96"));
            entries.add(new InfoEntry("Status", ChatColor.GREEN+"Available"));
        }*/
        entries.add(new InfoEntry("World", poi.getLocation().getName()));
    }
}
