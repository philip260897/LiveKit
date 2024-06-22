package at.livekit.provider;

import java.util.List;

import at.livekit.api.core.IIdentity;
import at.livekit.api.map.AsyncPOILocationProvider;
import at.livekit.api.map.InfoEntry;
import at.livekit.api.map.POI;
import at.livekit.plugin.Plugin;

public class BasicPOILocationProvider extends AsyncPOILocationProvider {

    public BasicPOILocationProvider() {
        super(Plugin.getInstance(), "Basic POI Location Provider", "livekit.module.poi");
    }

    @Override
    public List<POI> onResolvePOILocations(IIdentity identity) {
        try {
            return Plugin.getStorage().loadAll(POI.class);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public List<InfoEntry> onResolvePOIInfo(IIdentity arg0, POI arg1) {
        return null;
    }
    
}
