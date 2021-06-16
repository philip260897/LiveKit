package at.livekit.commands;

import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import at.livekit.commands.CommandHandler.CommandHandlerPermissionCallback;
import at.livekit.commands.CommandHandler.MatchResult;
import at.livekit.plugin.Permissions;
import at.livekit.plugin.Plugin;

public class LiveKitCommandExecutor implements CommandExecutor, TabCompleter {

    private LKCommand WORLD_RENDER_FULL = new LKCommand("lk {world} render full", "livekit.commands.basics", true, this::cmdWorldRender);
    private LKCommand WORLD_RENDER_FULL_MISSING = new LKCommand("lk {world} render full -m", "livekit.commands.basics", true, this::cmdWorldRender);
    private LKCommand WORLD_BOUNDS_RADIUS = new LKCommand("lk {world} bounds {radius}", "livekit.commands.basics", true, this::cmdWorldBounds);
    private LKCommand WORLD_BOUNDS_RADIUS_CIRCULAR = new LKCommand("lk {world} bounds {radius} -c", "livekit.commands.basics", true, this::cmdWorldBounds);
    private LKCommand WORLD_BOUNDS_LTRB = new LKCommand("lk {world} bounds {num(left)} {num(top)} {num(right)} {num(bottom)}", "livekit.commands.basics", true, this::cmdWorldBoundsLTRB);
    private LKCommand HEADREFRESH = new LKCommand("lk headrefresh", "livekit.commands.basics", false, this::cmdHeadrefresh);
    private LKCommand HEADREFRESH_OTHER = new LKCommand("lk headrefresh {player}", "livekit.commands.basics", true, this::cmdHeadrefreshOther);

    public LiveKitCommandExecutor() {
        CommandHandler.setPermissionCallback(new CommandHandlerPermissionCallback(){
            @Override
            public boolean hasPermission(CommandSender sender, String permission, boolean verbose) {
                return checkPerm(sender, permission, verbose);
            }
            @Override
            public boolean hasConsoleAccess(CommandSender sender, boolean consoleAccess, boolean verbose) {
                if(consoleAccess == false && sender instanceof ConsoleCommandSender) {
                    if(verbose) sender.sendMessage(Plugin.getPrefixError()+"Command can't be executed from console");
                    return false;
                }
                return true;
            }
            @Override
            public void unknownCommand(CommandSender sender, boolean verbose) {
                if(verbose) sender.sendMessage(Plugin.getPrefixError()+"Unknown command. Try /livekit help");
            }
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return CommandHandler.getAutoComplete(sender, alias, args);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MatchResult result = CommandHandler.match(sender, label, args);
        /*LKCommand cmd = result.getCommand();

        if(cmd == renderFull || cmd == renderMissing)
        {
            World world = cmd.get("world");
            sender.sendMessage("Command matched! "+world.getName());
        }
        if(cmd == boundsLTRB) {
            World world = cmd.get("world");
            int left = cmd.get("num:1");
            int top = cmd.get("num:2");
            int right = cmd.get("num:3");
            int bottom = cmd.get("num:4");

            sender.sendMessage("Bounds matched: "+left+" " + top + " " + right + " " + bottom + " " + world.getName());
        }*/

        //if(cmd != null) cmd.invalidate();

        return true;
    }

    private void cmdWorldRender(CommandSender sender, LKCommand cmd) {
        World world = cmd.get("world");
        boolean renderMissing = (cmd == WORLD_RENDER_FULL_MISSING);

        sender.sendMessage("World rendering "+renderMissing);
    }

    private void cmdWorldBounds(CommandSender sender, LKCommand cmd) {

    }

    private void cmdWorldBoundsLTRB(CommandSender sender, LKCommand cmd) {

    }

    private void cmdHeadrefresh(CommandSender sender, LKCommand cmd) {

    }

    private void cmdHeadrefreshOther(CommandSender sender, LKCommand cmd) {

    }
    
	private boolean checkPerm(CommandSender sender, String permission, boolean verbose) {
		if(sender.isOp()) return true;

		if(sender instanceof Player) {
			Player player = (Player)sender;
			boolean access = Permissions.has(player, permission);
			if(!access && verbose) player.sendMessage(Plugin.getPrefixError()+"You need "+permission+" permission to access this command!");
			return access;
		}
		return false;
	}

	private String friendlyBool(boolean bool) {
		if(bool) return ChatColor.GREEN+"Yes"+ChatColor.RESET;
		else return ChatColor.RED+"No"+ChatColor.RESET;
	}
}
