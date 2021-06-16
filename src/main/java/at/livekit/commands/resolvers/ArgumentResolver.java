package at.livekit.commands.resolvers;

import java.util.List;

public interface ArgumentResolver 
{
    public List<String> availableArguments();

    public boolean isValid(String arg);

    public Object resolveArgument(String arg);
}
