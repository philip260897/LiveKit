package at.livekit.commands.resolvers;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

public class PlayerArgumentResolver implements ArgumentResolver{
    @Override
    public List<String> availableArguments() {
        List<String> players = new ArrayList<String>();
        for(OfflinePlayer player : Bukkit.getOfflinePlayers()) players.add(player.getName());
        return players;
    }

    @Override
    public boolean isValid(String arg) {
        for(OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if(player.getName().equalsIgnoreCase(arg)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object resolveArgument(String arg) {
        for(OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if(player.getName().equalsIgnoreCase(arg)) {
                return player;
            }
        }
        return null;
    }
}
