package at.livekit.commands.resolvers;

import java.util.ArrayList;
import java.util.List;

public class AnyArgumentResolver implements ArgumentResolver {
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
