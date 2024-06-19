package at.livekit.timings;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.plugin.Plugin;

import at.livekit.utils.Utils;

public class TimedCommandExecutor implements CommandExecutor {
    private final CommandExecutor originalExecutor;
    private final Plugin plugin;

    private static Map<Plugin, Map<String, Long>> commandTimings = new ConcurrentHashMap<>();

    public TimedCommandExecutor(CommandExecutor originalExecutor, Plugin plugin) {
        this.originalExecutor = originalExecutor;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        long start = System.nanoTime();
        boolean result = false;

        try {
            result = originalExecutor.onCommand(sender, command, label, args);
        } finally {
            long end = System.nanoTime();
            if(result == true) {
                String cmd = command.getName() + " " + String.join(" ", args);
                if(!commandTimings.containsKey(plugin)) {
                    commandTimings.put(plugin, new HashMap<>());
                }
                if(!commandTimings.get(plugin).containsKey(cmd)) {
                    commandTimings.get(plugin).put(cmd, (end-start));
                }
                else {
                    commandTimings.get(plugin).put(cmd, commandTimings.get(plugin).get(cmd) + (end-start));
                }
            }
        }

        return result;
    }

    public static Map<Plugin, Map<String, Long>> snapshotTimings() {
        Map<Plugin, Map<String, Long>>  snapshot = new HashMap<>(commandTimings);
        commandTimings.clear();
        return snapshot;
    }

    public static void registerTimedCommandExecutor() throws Exception {
        Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
        commandMapField.setAccessible(true);
        SimpleCommandMap commandMap = (SimpleCommandMap) commandMapField.get(Bukkit.getServer());


        Field knownCommandsField = Utils.getField(commandMap.getClass(), "knownCommands");
        knownCommandsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);

        for (Map.Entry<String, Command> entry : knownCommands.entrySet()) {
            Command command = entry.getValue();
            if(command instanceof PluginCommand) {
                PluginCommand pluginCommand = (PluginCommand) command;
                CommandExecutor originalExecutor = pluginCommand.getExecutor();
                if(!(originalExecutor instanceof TimedCommandExecutor)) {
                    if (originalExecutor != null) {
                        TimedCommandExecutor timedExecutor = new TimedCommandExecutor(originalExecutor, pluginCommand.getPlugin());
                        pluginCommand.setExecutor(timedExecutor);
                    }
                }
            }
        }
    }
}
