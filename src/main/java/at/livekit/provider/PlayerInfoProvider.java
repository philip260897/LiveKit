package at.livekit.provider;

import java.util.List;

import org.bukkit.OfflinePlayer;

import at.livekit.api.core.Privacy;
import at.livekit.api.map.InfoEntry;
import at.livekit.api.map.InfoProvider;
import at.livekit.plugin.Plugin;

public class PlayerInfoProvider extends InfoProvider {

    public PlayerInfoProvider() {
        super(Plugin.getInstance(), "Default Player Info Provider");
    }

    @Override
    public void onPlayerInfoRequest(OfflinePlayer player, List<InfoEntry> entries) {
        InfoEntry lastJoined = new InfoEntry("Last Played", player.getLastPlayed()+"", Privacy.PUBLIC);
        InfoEntry firstJoined = new InfoEntry("First Joined", player.getFirstPlayed()+"", Privacy.PRIVATE);

        entries.add(lastJoined);
        entries.add(firstJoined);
    }
    
}
