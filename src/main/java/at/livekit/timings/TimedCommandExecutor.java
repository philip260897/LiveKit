package at.livekit.timings;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

public class TimedCommandExecutor implements CommandExecutor {
    private final CommandExecutor originalExecutor;
    private final Plugin plugin;
    private final String commandName;

    public TimedCommandExecutor(CommandExecutor originalExecutor, Plugin plugin, String commandName) {
        this.originalExecutor = originalExecutor;
        this.plugin = plugin;
        this.commandName = commandName;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        long startTime = System.nanoTime();
        boolean result = false;

        try {
            result = originalExecutor.onCommand(sender, command, label, args);
        } finally {
            if(result) {
                String completeCommand = command.getName() + " " + String.join(" ", args);
                long endTime = System.nanoTime();
                long duration = endTime - startTime;
                plugin.getLogger().info("Command " + completeCommand + " executed in " + duration + " ns");
            }
        }

        return result;
    }
}
