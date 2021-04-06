package at.livekit.provider;

import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.SpawnChangeEvent;

import at.livekit.api.core.Color;
import at.livekit.api.map.LocationProvider;
import at.livekit.api.map.Waypoint;
import at.livekit.plugin.Plugin;

public class LocationBedSpawnProvider extends LocationProvider implements Listener {

    public LocationBedSpawnProvider() {
        super(Plugin.getInstance(), "Bed Spawn Provider");
    }

    @Override
    public void onLocationRequest(OfflinePlayer player, List<Waypoint> waypoints) {

        Location location = player.getBedSpawnLocation();
        if(location != null) {
            Waypoint waypoint = new Waypoint(location, "Bed Spawn", "Bed Spawn Location of "+player.getName(), Color.fromChatColor(ChatColor.GREEN));
            waypoints.add(waypoint);
        }

    }


    
}
