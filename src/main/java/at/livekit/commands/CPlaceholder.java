package at.livekit.commands;

import at.livekit.commands.resolvers.ArgumentResolver;

public class CPlaceholder {
    
    private String argument;    //world
    private String placeholder; //{world(weltName)}
    private String friendlyName; //weltName

    public CPlaceholder(String argument, String placeholder, String friendlyName) {
        this.argument = argument;
        this.placeholder = placeholder;
        this.friendlyName = friendlyName;
    }

    public String getArgument() {
        return argument;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public String getFriendlyName() {
        return friendlyName;
    }

    public String getStrippedPlaceHolder() {
        return "{"+argument+"}";
    }

    public ArgumentResolver getResolver() {
        return CommandHandler.getArgumentResolver(getStrippedPlaceHolder());
    }

    //{world(worldName)}
    public static CPlaceholder fromString(String placeholder) {
        if(isArgument(placeholder)) {
            String argument = null;
            String friendly = null;
            
            if(placeholder.contains("(") && placeholder.contains(")")) {
                friendly = placeholder.split("\\(")[1].replace(")", "").replace("}", "");
            }
            argument = placeholder.split("\\(")[0].replace("{", "").replace("}", "");
            if(friendly == null) friendly = argument;
            return new CPlaceholder(argument, placeholder, friendly);
        }
        return null;
    }

    public static boolean isArgument(String argument) {
        return argument.startsWith("{") && argument.endsWith("}");
    }
}
