package at.livekit.commands.resolvers;

import java.util.ArrayList;
import java.util.List;

public class RadiusArgumentResolver implements ArgumentResolver {
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
