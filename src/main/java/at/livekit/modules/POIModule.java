package at.livekit.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.json.JSONArray;
import org.json.JSONObject;

import at.livekit.api.map.POIProvider;
import at.livekit.api.map.Waypoint;
import at.livekit.livekit.Identity;
import at.livekit.packets.IPacket;
import at.livekit.plugin.Config;
import at.livekit.plugin.Plugin;

public class POIModule extends BaseModule {

    private List<POIProvider> _poiProviders = new ArrayList<POIProvider>();

    private List<Waypoint> _waypoints = new ArrayList<Waypoint>();

    public POIModule(ModuleListener listener) {
        super(1, "POI", "livekit.module.poi", UpdateRate.NEVER, listener);
    }
    
    public void clearProviders() {
        _poiProviders.clear();
    }

    public void addPOIProvider(POIProvider provider) {
        if(!_poiProviders.contains(provider)) {
            _poiProviders.add(provider);
        }
    }

    public void removePOIProvider(POIProvider provider) {
        if(_poiProviders.contains(provider)) {
            _poiProviders.remove(provider);
        }
    }

    public void updatePOIs() {
        if(!this.isEnabled()) return; 

        /*World world = Bukkit.getWorld(Config.getModuleString("LiveMapModule", "world"));
        if(world != null) {
            synchronized(_waypoints) {
                _waypoints.clear();
                for(POIProvider provider : _poiProviders) {
                    provider.onPOIRequest(world, _waypoints);
                }
            }
        }*/

        notifyFull();
    }

    @Override
    public IPacket onJoinAsync(Identity identity) {
        JSONObject json = new JSONObject();
        JSONArray pois = new JSONArray();

        synchronized(_waypoints) {
            for(Waypoint waypoint : _waypoints) {
                JSONObject poi = new JSONObject();
                poi.put("type", "loc");
                poi.put("name", waypoint.getName());
                poi.put("description", waypoint.getDescription());
                poi.put("x", waypoint.getLocation().getBlockX());
                poi.put("y", waypoint.getLocation().getBlockY());
                poi.put("z", waypoint.getLocation().getBlockZ());
                poi.put("color", waypoint.getColor().getHEX());
                poi.put("world", waypoint.getLocation().getWorld().getName());
                pois.put(poi);
            }
        }

        json.put("pois", pois);
        return new ModuleUpdatePacket(this, json, true);
    }

    @Override
    public Map<Identity,IPacket> onUpdateAsync(List<Identity> identities) {
        Map<Identity, IPacket> responses = new HashMap<Identity,IPacket>();

        JSONObject json = new JSONObject();
        JSONArray pois = new JSONArray();

        synchronized(_waypoints) {
            for(Waypoint waypoint : _waypoints) {
                JSONObject poi = new JSONObject();
                poi.put("type", "loc");
                poi.put("name", waypoint.getName());
                poi.put("description", waypoint.getDescription());
                poi.put("x", waypoint.getLocation().getBlockX());
                poi.put("y", waypoint.getLocation().getBlockY());
                poi.put("z", waypoint.getLocation().getBlockZ());
                poi.put("color", waypoint.getColor().getHEX());
                poi.put("world", waypoint.getLocation().getWorld().getName());
                pois.put(poi);
            }
        }
        json.put("pois", pois);

        for(Identity identity : identities) responses.put(identity, new ModuleUpdatePacket(this, json, false));

        return responses;
    }
}
