package at.livekit.commands;

import org.bukkit.command.CommandSender;
import org.junit.runner.Describable;

import at.livekit.commands.resolvers.ArgumentResolver;

public class LKCommand {

    private String match;
    private String permission;
    private boolean consoleAllowed;
    private String description;

    private String mLabel;
    private String[] mArgs;

    private String contextLabel;
    private String[] contextArgs;

    private CExecutor executor;

    public LKCommand(String match, String permission, boolean consoleAllowed, CExecutor executor) {
        this(match, permission, consoleAllowed, executor, true);
    }

    public LKCommand(String match, String permission, boolean consoleAllowed, CExecutor executor, boolean register) {
        this(match, permission, consoleAllowed, executor, register, null);
    }

    public LKCommand(String match, String permission, boolean consoleAllowed, CExecutor executor, String description) {
        this(match, permission, consoleAllowed, executor, true, description);
    }

    public LKCommand(String match, String permission, boolean consoleAllowed, CExecutor executor, boolean register, String description) {
        if(register) CommandHandler.registerCommand(this);

        this.match = match;
        this.permission = permission;
        this.consoleAllowed = consoleAllowed;
        this.executor = executor;
        this.description = description;

        this.mLabel = match.split(" ")[0];
        this.mArgs = new String[match.split(" ").length - 1];
        for (int i = 1; i < match.split(" ").length; i++) {
            this.mArgs[i - 1] = match.split(" ")[i];
        }
    }

    public String getFormattedCommand() {
        String cmd = "/livekit";

        for(String arg : mArgs) {
            CPlaceholder holder = CPlaceholder.fromString(arg);
            if(holder == null) {
                cmd +=" "+arg;

            } else {
                cmd += " <"+holder.getFriendlyName()+">";
            }
        }

        return cmd;
    }

    public String getDescription() {
        return description;
    }

    public String getArgumentAt(int index) {
        if (index >= mArgs.length)
            return null;
        return mArgs[index];
    }

    public void execute(CommandSender sender) {
        if(executor != null) {
            executor.execute(sender, this);
        }
    }

    public <T> T get(String argument) {
        String[] split = argument.split(":");
        String placeholder = split[0];
        int id = (split.length > 1) ? Integer.parseInt(split[1])-1 : 0;
        
        int count = 0;
        for(int i = 0; i < mArgs.length; i++) {
            CPlaceholder holder = CPlaceholder.fromString(mArgs[i]);
            if(holder != null && holder.getArgument().equals(placeholder)) {
                if(count == id) {
                    if(holder.getResolver() == null) return null;

                    if(holder.getArgument().equalsIgnoreCase("message")) {
                        String message = "";
                        for(int j = i; j < contextArgs.length; j++) {
                            message += contextArgs[j] + ((j < contextArgs.length-1) ? " "  : "");
                        }
                        return (T) message;
                    } else {
                        Object o = holder.getResolver().resolveArgument(contextArgs[i]);
                        return (T) o;
                    }
                }
                count++;
            }
        }

        return null;
    }

    protected boolean getConsoleAccess() {
        return consoleAllowed;
    }

    protected String getPermission() {
        return permission;
    }

    protected void setContext(String label, String[] args) {
        this.contextArgs = args;
        this.contextLabel = label;
    }

    public void invalidate() {
        this.contextArgs = null;
        this.contextLabel = null;
    }

    protected boolean match(String label, String[] args, boolean partial) {
        CPlaceholder message = mArgs.length >= 1 ? CPlaceholder.fromString(mArgs[mArgs.length-1]) : null;
        if(message != null && !message.getArgument().equalsIgnoreCase("message")) message = null;

        if (mArgs.length != args.length && !partial && (message == null))
            return false;
        if (!mLabel.equalsIgnoreCase(label))
            return false;

        for (int i = 0; i < mArgs.length; i++) {
            if (i >= args.length)
                return partial;

            String mArg = mArgs[i];
            String arg = args[i];

            if (!(partial && i == args.length - 1) ? !mArg.equalsIgnoreCase(arg) : !mArg.startsWith(arg)) {
                CPlaceholder placeholder = CPlaceholder.fromString(mArg);
                if (placeholder == null) {
                    return false;
                }
                ArgumentResolver resolver = placeholder.getResolver();
                if (resolver == null) {
                    return false;
                }
                if (!resolver.isValid(arg) && !(partial && (i == args.length - 1))) {
                    return false;
                }
            }
        }

        return true;
    }

    public interface CExecutor {
        public void execute(CommandSender sender, LKCommand command);
    }
}