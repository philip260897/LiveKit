package at.livekit.commands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public class BaseCommands implements CommandExecutor, TabCompleter {

    private static LKCommand renderFull = new LKCommand("lk {world} render full", "livekit.commands.basics", true);
    private static LKCommand renderMissing = new LKCommand("lk {world} render full -m", "livekit.commands.basics", true);
    private static LKCommand boundsRadius = new LKCommand("lk {world} bounds {radius}", "livekit.commands.basics", true);
    private static LKCommand boundsRadiusCircular = new LKCommand("lk {world} bounds {radius} -c", "livekit.commands.basics", true);
    private static LKCommand boundsLTRB = new LKCommand("lk {world} bounds {num} {num} {num} {num}", "livekit.commands.basics", true);
    private static LKCommand headRefresh = new LKCommand("lk headrefresh", "livekit.commands.basics", true);
    private static LKCommand headRefreshOther = new LKCommand("lk headrefresh {player}", "livekit.commands.basics", true);

    public BaseCommands() {
        LKCommand.initialize();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return LKCommand.getAutoComplete(sender, alias, args);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        
        LKCommand cmd = LKCommand.match(sender, label, args);
        if(cmd != null)
        {
            sender.sendMessage("Command matched! "+cmd.match);
        }

        System.out.println("Tried to match");

        return false;
    }
    
    public static class LKCommand 
    {
        private static HashMap<String, ArgumentResolver> resolvers = new HashMap<String, ArgumentResolver>();
        private static List<LKCommand> registered = new ArrayList<LKCommand>();
        
        public static void initialize() {
            resolvers.put("{any}", new AnyArgumentResolver());
            resolvers.put("{world}", new WorldArgumentResolver());
            resolvers.put("{radius}", new RadiusArgumentResolver());
            resolvers.put("{num}", new NumArgumentResolver());
            resolvers.put("{player}", new PlayerArgumentResolver());
        }

        public static LKCommand match(CommandSender sender, String label, String[] args) {
            for(LKCommand commands : registered) {
                if(commands.match(label, args, false)) {
                    //TODO: further checks [Permission, console sender?]
                    return commands;
                }
            }
            return null;
        }

        public static List<String> getAutoComplete(CommandSender sender, String label, String[] args) {
            List<String> suggestions = new ArrayList<String>();

            //String label = autoArgs[0];
            //String[] args = new String[autoArgs.length-1];
            //for(int i = 1; i < autoArgs.length; i++) args[i-1] = autoArgs[i];

            //List<LKCommand> commands = new ArrayList<>();
            for(LKCommand cmd : registered) {
                if(cmd.match(label, args, true)) {
                    //TODO: sender has access to command?
                    
                    String argument = cmd.getArgumentAt(args.length-1);
                    if(argument != null) {
                        if(argument.startsWith("{")&&argument.endsWith("}")) {
                            ArgumentResolver resolver = resolvers.get(argument);
                            if(resolver != null) {
                                for(String s : resolver.availableArguments()) {
                                    if(s.toLowerCase().startsWith( args[args.length-1].toLowerCase())) {
                                        if(!suggestions.contains(s)) suggestions.add(s);
                                    }
                                }
                            }
                        } else if(argument.toLowerCase().startsWith( args[args.length-1].toLowerCase() )) {
                            if(!suggestions.contains(argument)) suggestions.add(argument);
                        }
                    }
                }
            }

            return suggestions;
        }

        private String match;
        private String permission;
        private boolean consoleAllowed;

        private String mLabel;
        private String[] mArgs;

        public LKCommand(String match, String permission, boolean consoleAllowed) {
            LKCommand.registered.add(this);

            this.match = match;
            this.permission = permission;
            this.consoleAllowed = consoleAllowed;

            this.mLabel = match.split(" ")[0];
            this.mArgs = new String[match.split(" ").length-1];
            for(int i = 1; i < match.split(" ").length; i++){
                this.mArgs[i-1] = match.split(" ")[i];
            }
        }

        public String getArgumentAt(int index) {
            if(index >= mArgs.length) return null;
            return mArgs[index];
        }

        private boolean match(String label, String[] args, boolean partial) {
            if(mArgs.length != args.length && !partial) return false;
            if(!mLabel.equalsIgnoreCase(label)) return false;

            for(int i = 0; i < mArgs.length; i++) {
                if(i >= args.length) return partial;

                String mArg = mArgs[i];
                String arg = args[i];

                if(!(partial && i==args.length-1) ? !mArg.equalsIgnoreCase(arg) : !mArg.startsWith(arg)) {
                    if(!(mArg.startsWith("{") && mArg.endsWith("}"))) {
                        return false;
                    }
                    ArgumentResolver resolver = resolvers.get(mArg);
                    if(resolver == null) { return false; }
                    if(!resolver.isValid(arg) && !(partial && (i==args.length-1))) {  return false; }
                }
            }

            return true;
        }
    }

    public interface ArgumentResolver
    {
        public List<String> availableArguments();

        public boolean isValid(String arg);

        public Object resolveArgument(String arg);
    }

    public static class AnyArgumentResolver implements ArgumentResolver
    {
        @Override
        public List<String> availableArguments() {
            return new ArrayList<>();
        }

        @Override
        public boolean isValid(String arg) {
            return true;
        }

        @Override
        public Object resolveArgument(String arg) {
            return arg;
        }
    }

    public static class WorldArgumentResolver implements ArgumentResolver
    {

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

    public static class RadiusArgumentResolver implements ArgumentResolver
    {

        @Override
        public List<String> availableArguments() {
            List<String> radius = new ArrayList<String>();
            radius.add("512");
            radius.add("1024");
            radius.add("2048");
            radius.add("4096");
            radius.add("8192");
            radius.add("16384");
            radius.add("20480");
            return radius;
        }

        @Override
        public boolean isValid(String arg) {
            try{
                Integer.parseInt(arg);
                return true;
            }catch(Exception ex){}
            return false;
        }

        @Override
        public Object resolveArgument(String arg) {
            return Integer.parseInt(arg);
        }
        
    }

    public static class NumArgumentResolver implements ArgumentResolver
    {

        @Override
        public List<String> availableArguments() {
            List<String> radius = new ArrayList<String>();
            return radius;
        }

        @Override
        public boolean isValid(String arg) {
            try{
                Integer.parseInt(arg);
                return true;
            }catch(Exception ex){}
            return false;
        }

        @Override
        public Object resolveArgument(String arg) {
            return Integer.parseInt(arg);
        }
    }

    public static class PlayerArgumentResolver implements ArgumentResolver
    {

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
}
