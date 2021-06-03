package at.livekit.modules;

import java.util.Map;
import org.bukkit.event.Listener;
import at.livekit.livekit.Economy;

public class EconomyModule extends BaseModule implements Listener {


    public EconomyModule(ModuleListener listener) {
        super(1, "Economy", "livekit.module.economy", UpdateRate.NEVER, listener);
    }

    @Override
    public void onEnable(Map<String,ActionMethod> signature) {
        if(!Economy.getInstance().isAvailable()) return;

        super.onEnable(signature);
    }

    @Override
    public void onDisable(Map<String,ActionMethod> signature) {
        super.onDisable(signature);
    }
}
