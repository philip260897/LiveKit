package at.livekit.provider;

import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.World;

import at.livekit.api.core.Color;
import at.livekit.api.map.POIProvider;
import at.livekit.api.map.Waypoint;
import at.livekit.plugin.Plugin;

public class POISpawnProvider extends POIProvider {

    public POISpawnProvider() {
        super(Plugin.getInstance(), "World Spawn Provider");
    }

    @Override
    public void onPOIRequest(World world, List<Waypoint> waypoints) {
        Waypoint waypoint = new Waypoint(world.getSpawnLocation(), "Spawn", "Spawn of "+world.getName(), Color.fromChatColor(ChatColor.RED));
        waypoints.add(waypoint);
    }
    
}
