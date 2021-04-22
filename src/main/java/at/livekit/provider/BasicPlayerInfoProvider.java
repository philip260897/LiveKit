package at.livekit.provider;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import at.livekit.api.core.Color;
import at.livekit.api.core.Privacy;
import at.livekit.api.map.InfoEntry;
import at.livekit.api.map.PlayerInfoProvider;
import at.livekit.api.map.Waypoint;
import at.livekit.plugin.Config;
import at.livekit.plugin.Plugin;

public class BasicPlayerInfoProvider extends PlayerInfoProvider {

    private static SimpleDateFormat _formatter = new SimpleDateFormat("dd MMMM yyyy");

    public BasicPlayerInfoProvider() {
        super(Plugin.getInstance(), "Default Player Info Provider", null);
    }

    @Override
    public void onResolvePlayerInfo(OfflinePlayer player, List<InfoEntry> entries) {
        entries.add( new InfoEntry("Last Played", player.isOnline() ? ChatColor.GREEN+"Now" : _formatter.format(new Date(player.getLastPlayed())), Privacy.PUBLIC) );
        entries.add( new InfoEntry("First Joined", _formatter.format(new Date(player.getFirstPlayed())), Privacy.PRIVATE) );

        if(player.isOnline()) {
            Player online = player.getPlayer();
            entries.add(new InfoEntry("Gamemode", (online.getGameMode() != GameMode.SURVIVAL ? ChatColor.YELLOW : ChatColor.RESET) + online.getGameMode().toString(), Privacy.PUBLIC));
        }
    }

    @Override
    public void onResolvePlayerLocation(OfflinePlayer player, List<Waypoint> waypoints) {
        Location location = player.getBedSpawnLocation();
        if(location != null) {
            Waypoint waypoint = new Waypoint(location, "Bed Spawn", "Bed Spawn Location of "+player.getName(), BasicPlayerPinProvider.PLAYER_PIN_COLOR, Config.canTeleportBed(), Privacy.PRIVATE);
            waypoints.add(waypoint);
        }
    }
    
}
