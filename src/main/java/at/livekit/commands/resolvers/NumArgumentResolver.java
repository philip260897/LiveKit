package at.livekit.commands.resolvers;

import java.util.ArrayList;
import java.util.List;

public class NumArgumentResolver implements ArgumentResolver
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
