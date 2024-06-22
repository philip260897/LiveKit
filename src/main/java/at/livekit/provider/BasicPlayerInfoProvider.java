package at.livekit.provider;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;

import at.livekit.api.core.IIdentity;
import at.livekit.api.core.Privacy;
import at.livekit.api.map.InfoEntry;
import at.livekit.api.map.PlayerInfoProvider;
import at.livekit.livekit.Economy;
import at.livekit.plugin.Plugin;



public class BasicPlayerInfoProvider extends PlayerInfoProvider implements Listener {

    private static SimpleDateFormat _formatter = new SimpleDateFormat("dd MMMM yyyy");

    public BasicPlayerInfoProvider() {
        super(Plugin.getInstance(), "Default Player Info Provider", null);
    }

    @Override
    public List<InfoEntry> onResolvePlayerInfo(IIdentity identity, OfflinePlayer player) {
        List<InfoEntry> entries = new ArrayList<>();
        entries.add( new InfoEntry("Last Played", player.isOnline() ? ChatColor.GREEN+"Now" : _formatter.format(new Date(player.getLastPlayed())), Privacy.PUBLIC) );
        entries.add( new InfoEntry("First Joined", _formatter.format(new Date(player.getFirstPlayed())), Privacy.PRIVATE) );
        
        Economy economy = Economy.getInstance();
        try{
            if(economy.isAvailable()) entries.add( new InfoEntry("Balance", ""+ChatColor.GREEN+Economy.getInstance().getBalanceFormatted(player), Privacy.PRIVATE) );
        } catch(Exception e) {e.printStackTrace();}


        if(player.isOnline()) {
            Player online = player.getPlayer();
            entries.add(new InfoEntry("Gamemode", (online.getGameMode() != GameMode.SURVIVAL ? ChatColor.YELLOW : ChatColor.RESET) + online.getGameMode().toString(), Privacy.PUBLIC));
            entries.add(new InfoEntry("Level (XP)", online.getLevel()+" (" +(online.getTotalExperience())+ ")", Privacy.PRIVATE));
        }
        return entries;
    }



    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerGameModeChangeEvent(PlayerGameModeChangeEvent event) {
        Plugin.getInstance().getLiveKit().notifyPlayerInfoChange(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerExpChangeEvent(PlayerExpChangeEvent event) {
        Plugin.getInstance().getLiveKit().notifyPlayerInfoChange(event.getPlayer());
    }
}
