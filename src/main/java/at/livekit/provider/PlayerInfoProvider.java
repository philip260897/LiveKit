package at.livekit.provider;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import at.livekit.api.core.Privacy;
import at.livekit.api.map.InfoEntry;
import at.livekit.api.map.InfoProvider;
import at.livekit.plugin.Plugin;
import net.md_5.bungee.api.ChatColor;

public class PlayerInfoProvider extends InfoProvider {

    private static SimpleDateFormat _formatter = new SimpleDateFormat("dd MMMM yyyy");

    public PlayerInfoProvider() {
        super(Plugin.getInstance(), "Default Player Info Provider");
    }

    @Override
    public void onPlayerInfoRequest(OfflinePlayer player, List<InfoEntry> entries) {
        entries.add( new InfoEntry("Last Played", player.isOnline() ? ChatColor.GREEN+"Now" : _formatter.format(new Date(player.getLastPlayed())), Privacy.PUBLIC) );
        entries.add( new InfoEntry("First Joined", _formatter.format(new Date(player.getFirstPlayed())), Privacy.PRIVATE) );

        if(player.isOnline()) {
            Player online = player.getPlayer();
            entries.add(new InfoEntry("Gamemode", (online.getGameMode() != GameMode.SURVIVAL ? ChatColor.YELLOW : ChatColor.RESET) + online.getGameMode().toString(), Privacy.PUBLIC));
        }
    }
    
}
