package at.livekit.supported.essentialsx;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import com.earth2me.essentials.Essentials;

import at.livekit.api.core.Color;
import at.livekit.api.core.LKLocation;
import at.livekit.api.map.InfoEntry;
import at.livekit.api.map.POI;
import at.livekit.api.map.POIInfoProvider;
import at.livekit.plugin.Config;
import at.livekit.plugin.Plugin;
import net.essentialsx.api.v2.events.WarpModifyEvent;
import net.essentialsx.api.v2.events.WarpModifyEvent.WarpModifyCause;

public class EssentialsPOIProvider extends POIInfoProvider implements Listener {

    final List<POI> essentialWarpPOIs = new ArrayList<>();

    public EssentialsPOIProvider(Essentials essentials) {
        super(essentials, essentials.getName(), null);

        try {
            for (String warp : essentials.getWarps().getList()) {
                registerPOI(warp, essentials.getWarps().getWarp(warp));
            }

            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onResolvePOIInfo(POI poi, List<InfoEntry> entries) {
        if(essentialWarpPOIs.contains(poi)) {
            entries.add(new InfoEntry("Plugin", getRegisteringPlugin().getName()));
        }
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
    }

    POI registerPOI(String name, Location location) {
        POI poi = POI.create(LKLocation.fromLocation(location), name, "Essentials warp point "+name, Color.fromChatColor(ChatColor.RED), Config.canEssentialsTeleportWarps(), false);
        essentialWarpPOIs.add(poi);
        at.livekit.plugin.Plugin.getInstance().getLiveKit().addPointOfInterest(poi);
        return poi;
    }

    void unregisterPOI(POI poi) {
        Plugin.debug("Remove POI "+poi.getName());
        at.livekit.plugin.Plugin.getInstance().getLiveKit().removePointOfIntereset(poi);
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
    
}
