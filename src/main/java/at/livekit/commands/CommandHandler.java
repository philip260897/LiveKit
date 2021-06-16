package at.livekit.commands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.bukkit.command.CommandSender;

import at.livekit.commands.resolvers.AnyArgumentResolver;
import at.livekit.commands.resolvers.ArgumentResolver;
import at.livekit.commands.resolvers.ModuleArgumentResolver;
import at.livekit.commands.resolvers.NumArgumentResolver;
import at.livekit.commands.resolvers.PlayerArgumentResolver;
import at.livekit.commands.resolvers.RadiusArgumentResolver;
import at.livekit.commands.resolvers.WorldArgumentResolver;

public class CommandHandler 
{

    private static HashMap<String, ArgumentResolver> resolvers = new HashMap<String, ArgumentResolver>();
    private static List<LKCommand> registered = new ArrayList<LKCommand>();
    private static CommandHandlerPermissionCallback permissionCallback;
    
    public static void initialize() {
        resolvers.put("{any}", new AnyArgumentResolver());
        resolvers.put("{message}", new AnyArgumentResolver());
        resolvers.put("{world}", new WorldArgumentResolver());
        resolvers.put("{radius}", new RadiusArgumentResolver());
        resolvers.put("{num}", new NumArgumentResolver());
        resolvers.put("{player}", new PlayerArgumentResolver());
        resolvers.put("{module}", new ModuleArgumentResolver());
    }

    public static void setPermissionCallback(CommandHandlerPermissionCallback callback) {
        CommandHandler.permissionCallback = callback;
    }

    protected static void registerCommand(LKCommand command) {
        CommandHandler.registered.add(command);
    }

    public static void registerArgumentResolver(String argument, ArgumentResolver resolver) {
        resolvers.put(argument, resolver);
    }

    public static ArgumentResolver getArgumentResolver(String argument) {
        return resolvers.get(argument);
    }

    public static MatchResult match(CommandSender sender, String label, String[] args) {
        for(LKCommand commands : registered) {
            if(commands.match(label, args, false)) {
                if(!permissionCallback.hasConsoleAccess(sender, commands.getConsoleAccess(), true)) return new MatchResult(MatchResult.RESULT_CONSOLE_NOT_ALLOWED);
                if(commands.getPermission() != null && !permissionCallback.hasPermission(sender, commands.getPermission(), true)) return new MatchResult(MatchResult.RESULT_PERMISSION_DENIED);

                commands.setContext(label, args);
                commands.execute(sender);
                commands.invalidate();
                return new MatchResult(MatchResult.RESULT_SUCCESS, commands);
            }
        }
        permissionCallback.unknownCommand(sender, true);
        return new MatchResult(MatchResult.RESULT_UNKNOWN_CMD);
    }

    public static List<String> getAutoComplete(CommandSender sender, String label, String[] args) {
        List<String> suggestions = new ArrayList<String>();


        for(LKCommand cmd : registered) {
            if(cmd.match(label, args, true)) {
                //TODO: sender has access to command?
                
                String argument = cmd.getArgumentAt(args.length-1);
                if(argument != null) {
                    CPlaceholder placeholder = CPlaceholder.fromString(argument);
                    
                    if(placeholder != null) {
                        ArgumentResolver resolver = placeholder.getResolver();
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

    public static class MatchResult 
    {
        public final static int RESULT_SUCCESS = 0;
        public final static int RESULT_UNKNOWN_CMD = 1;
        public final static int RESULT_CONSOLE_NOT_ALLOWED = 2;
        public final static int RESULT_PERMISSION_DENIED = 3;

        private LKCommand command;
        private int status;

        public MatchResult(int status) {
            this.status = status;
        }

        public MatchResult(int status, LKCommand command) {
            this.status = status;
            this.command = command;
        }

        public LKCommand getCommand() {
            return command;
        }

        public int getStatus() {
            return status;
        }
    }

    public interface CommandHandlerPermissionCallback 
    {
        public boolean hasPermission(CommandSender sender, String permission, boolean verbose);

        public boolean hasConsoleAccess(CommandSender sender, boolean consoleAccess, boolean verbose);

        public void unknownCommand(CommandSender sender, boolean verbose);
    }
}
