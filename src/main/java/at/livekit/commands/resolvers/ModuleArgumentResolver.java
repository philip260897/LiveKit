package at.livekit.commands.resolvers;

import java.util.ArrayList;
import java.util.List;

import at.livekit.livekit.LiveKit;
import at.livekit.modules.BaseModule;

public class ModuleArgumentResolver implements ArgumentResolver {

    @Override
    public List<String> availableArguments() {
        List<String> modules = new ArrayList<String>(); 
        for(BaseModule module : LiveKit.getInstance().getModules()) modules.add(module.getType());
        return modules;
    }

    @Override
    public boolean isValid(String arg) {
        return resolveArgument(arg) != null;
    }

    @Override
    public Object resolveArgument(String module) {
        BaseModule m = LiveKit.getInstance().getModuleManager().getModule(module);
        return m;
    }
    
}
