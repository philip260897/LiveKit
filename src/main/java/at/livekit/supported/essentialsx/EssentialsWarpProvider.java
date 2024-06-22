package at.livekit.supported.essentialsx;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import com.earth2me.essentials.Essentials;

import at.livekit.api.core.Color;
import at.livekit.api.core.IIdentity;
import at.livekit.api.core.LKLocation;
import at.livekit.api.map.InfoEntry;
import at.livekit.api.map.POI;
import at.livekit.api.map.POILocationProvider;
import at.livekit.plugin.Config;
import at.livekit.plugin.Plugin;
import net.essentialsx.api.v2.events.WarpModifyEvent;
import net.essentialsx.api.v2.events.WarpModifyEvent.WarpModifyCause;

public class EssentialsWarpProvider extends POILocationProvider implements Listener {

    final List<POI> essentialWarpPOIs = new ArrayList<>();

    public EssentialsWarpProvider(Essentials essentials) {
        super(essentials, essentials.getName(), "livekit.essentials.warps");

        try {
            for (String warp : essentials.getWarps().getList()) {
                registerPOI(warp, essentials.getWarps().getWarp(warp));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @Override
    public List<POI> onResolvePOILocations(IIdentity identity) {
        return essentialWarpPOIs;
    }

    @EventHandler
    public void onWarpModify(WarpModifyEvent event) {
        POI poi = getWarpPOIByName(event.getWarpName());
        if(poi == null && event.getCause() != WarpModifyCause.CREATE) return;

        if(event.getCause() == WarpModifyCause.DELETE) {
            unregisterPOI(poi);
        } else if(event.getCause() == WarpModifyCause.UPDATE) {
            unregisterPOI(poi);
            registerPOI(event.getWarpName(), event.getNewLocation());
        } else if (event.getCause() == WarpModifyCause.CREATE) {
            registerPOI(event.getWarpName(), event.getNewLocation());
        }
        Plugin.getInstance().getLiveKit().notifyPOIChange(this);
    }

    void registerPOI(String name, Location location) {
        essentialWarpPOIs.add( POI.create(LKLocation.fromLocation(location), name, "Essentials warp point "+name, Color.fromChatColor(ChatColor.RED), Config.canEssentialsTeleportWarps(), false) );
    }

    void unregisterPOI(POI poi) {
        essentialWarpPOIs.remove(poi);
    }

    POI getWarpPOIByName(String name) {
        for (POI poi : essentialWarpPOIs) {
            if (poi.getName().equals(name)) {
                return poi;
            }
        }
        return null;
    }
    @Override
    public List<InfoEntry> onResolvePOIInfo(IIdentity identity, POI poi) {
        List<InfoEntry> entries = new ArrayList<>();
        entries.add(new InfoEntry("Plugin", getRegisteringPlugin().getName()));
        entries.add(new InfoEntry("Features", "warps"));
        entries.add(new InfoEntry("Command", "/warp "+poi.getName()));
        return entries;
    }


    
}
