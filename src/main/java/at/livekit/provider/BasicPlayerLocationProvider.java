package at.livekit.provider;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.OfflinePlayer;

import at.livekit.api.core.IIdentity;
import at.livekit.api.core.LKLocation;
import at.livekit.api.core.Privacy;
import at.livekit.api.map.PersonalPin;
import at.livekit.api.map.PlayerLocationProvider;
import at.livekit.plugin.Config;
import at.livekit.plugin.Plugin;

public class BasicPlayerLocationProvider extends PlayerLocationProvider {
    
    public BasicPlayerLocationProvider() {
        super(Plugin.getInstance(), "Default Player Info Provider", null);
    }

    @Override
    public List<PersonalPin> onResolvePlayerLocation(IIdentity identity, OfflinePlayer player) {
        List<PersonalPin> waypoints = new ArrayList<>();
        Location location = player.getBedSpawnLocation();
        if(location != null) {
            PersonalPin waypoint = PersonalPin.create(player, LKLocation.fromLocation(location), "Bed Spawn", "Bed Spawn Location of "+player.getName(), BasicPlayerPinProvider.PLAYER_PIN_COLOR, Config.canTeleportBed(), Privacy.PRIVATE);
            waypoints.add(waypoint);
        }
        return waypoints;
    }

}
