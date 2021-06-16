package at.livekit.commands.resolvers;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.World;

public class WorldArgumentResolver implements ArgumentResolver {
    
    @Override
    public List<String> availableArguments() {
        List<String> worlds = new ArrayList<String>();
        for(World w : Bukkit.getWorlds()) {
            worlds.add(w.getName());
        }
        return worlds;
    }

    @Override
    public boolean isValid(String arg) {
        return Bukkit.getWorld(arg) != null;
    }

    @Override
    public Object resolveArgument(String arg) {
        return Bukkit.getWorld(arg);
    }
}
