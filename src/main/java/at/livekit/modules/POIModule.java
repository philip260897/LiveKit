package at.livekit.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.json.JSONArray;
import org.json.JSONObject;

import at.livekit.api.map.AsyncPOIInfoProvider;
import at.livekit.api.map.InfoEntry;
import at.livekit.api.map.POI;
import at.livekit.api.map.POIInfoProvider;
import at.livekit.api.map.Waypoint;
import at.livekit.livekit.Identity;
import at.livekit.packets.ActionPacket;
import at.livekit.packets.IPacket;
import at.livekit.packets.StatusPacket;
import at.livekit.plugin.Plugin;
import at.livekit.provider.BasicPOIProvider;

public class POIModule extends BaseModule {

    private List<POIInfoProvider> _infoProviders = new ArrayList<POIInfoProvider>();
    private List<POI> _pois = new ArrayList<POI>();
    private List<String> _downstreamUpdate = new ArrayList<String>();

    public POIModule(ModuleListener listener) {
        super(1, "POI", "livekit.module.poi", UpdateRate.NEVER, listener);
    }
    
    public void addPOI(POI waypoint) {
        synchronized(_pois) {
            _pois.add(waypoint);
        }
        notifyFull();
    }

    public void removePOI(POI waypoint) {
        synchronized(_pois) {
            _pois.remove(waypoint);
        }
        notifyFull();
    }

    public void clearProviders() {
        _infoProviders.clear();
    }

    public void addInfoProvider(POIInfoProvider provider) {
        synchronized(_infoProviders) {
            if(!_infoProviders.contains(provider)) {
                _infoProviders.add(provider);
            }
        }
    }

    public void removeInfoProvider(POIInfoProvider provider) {
        synchronized(_infoProviders) {
            if(_infoProviders.contains(provider)) {
                _infoProviders.remove(provider);
            }
        }
    }

    public void notifyDownstream(POI poi) {
        synchronized(_downstreamUpdate) {
            _downstreamUpdate.add(poi.getUUID().toString());
        }
        notifyChange();
    }

    @Override
    public void onEnable(Map<String, ActionMethod> signature) {
        Bukkit.getScheduler().runTaskAsynchronously(Plugin.getInstance(), new Runnable(){
            @Override
            public void run() {
                try{
                    List<POI> pois = Plugin.getStorage().loadPOIs();
                    synchronized(_pois) {
                        _pois.addAll(pois);
                    }
                }catch(Exception ex){ex.printStackTrace();}
                notifyFull();
            }
        });
        super.onEnable(signature);
    }

    /*public void clearProviders() {
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
    }*/

    /*public void updatePOIs() {
        if(!this.isEnabled()) return; 

        World world = Bukkit.getWorld(Config.getModuleString("LiveMapModule", "world"));
        if(world != null) {
            synchronized(_waypoints) {
                _waypoints.clear();
                for(POIProvider provider : _poiProviders) {
                    provider.onPOIRequest(world, _waypoints);
                }
            }
        }

        notifyFull();
    }*/

    @Override
    public IPacket onJoinAsync(Identity identity) {
        JSONObject json = new JSONObject();
        JSONArray pois = new JSONArray();

        synchronized(_pois) {
            for(POI waypoint : _pois) {
                JSONObject poi = new JSONObject();
                /*poi.put("uuid", waypoint.getUUID().toString());
                poi.put("name", waypoint.getName());
                poi.put("description", waypoint.getDescription());
                poi.put("x", waypoint.getLocation().getBlockX());
                poi.put("y", waypoint.getLocation().getBlockY());
                poi.put("z", waypoint.getLocation().getBlockZ());
                poi.put("color", waypoint.getColor().getHEX());
                poi.put("world", waypoint.getLocation().getWorld().getName());
                poi.put("teleport", waypoint.canTeleport());
                pois.put(poi);*/
                pois.put(waypoint.toJson());
            }
        }

        json.put("pois", pois);
        json.put("edit", hasEditPermission(identity));
        return new ModuleUpdatePacket(this, json, true);
    }

    private boolean hasEditPermission(Identity identity) {
        return (identity.hasPermission("livekit.poi.edit") || identity.hasPermission("livekit.module.admin"));
    }

    @Override
    public Map<Identity,IPacket> onUpdateAsync(List<Identity> identities) {
        Map<Identity, IPacket> responses = new HashMap<Identity,IPacket>();

        JSONArray downstreamUpdate = new JSONArray();
        synchronized(_downstreamUpdate) {
            for(String s : _downstreamUpdate) downstreamUpdate.put(s);
            _downstreamUpdate.clear();
        }

        JSONObject json = new JSONObject();
        JSONArray pois = new JSONArray();

        synchronized(_pois) {
            for(POI waypoint : _pois) {
                /*JSONObject poi = new JSONObject();
                poi.put("uuid", waypoint.getUUID().toString());
                poi.put("name", waypoint.getName());
                poi.put("description", waypoint.getDescription());
                poi.put("x", waypoint.getLocation().getBlockX());
                poi.put("y", waypoint.getLocation().getBlockY());
                poi.put("z", waypoint.getLocation().getBlockZ());
                poi.put("color", waypoint.getColor().getHEX());
                poi.put("world", waypoint.getLocation().getWorld().getName());
                poi.put("teleport", waypoint.canTeleport());*/
                pois.put(waypoint.toJson());
            }
        }
        json.put("pois", pois);
        json.put("downstream", downstreamUpdate);

        for(Identity identity : identities) responses.put(identity, new ModuleUpdatePacket(this, json, false));

        return responses;
    }

    @Action(name="Teleport")
    protected IPacket teleportPOI(Identity identity, ActionPacket packet) {
        String wp = packet.getData().getString("waypoint");
        Waypoint waypoint = getWaypointByUUID(UUID.fromString(wp));

        if(waypoint == null) return new StatusPacket(0, "Waypoint not found!");
        if(waypoint.canTeleport() == false && !identity.hasPermission("livekit.module.admin")) return new StatusPacket(0, "Can't teleport to this waypoint");

        OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(identity.getUuid()));
        if(player == null || !player.isOnline()) return new StatusPacket(0, "Player is offline");

        Player online = player.getPlayer();
        online.teleport(waypoint.getLocation());

        return new StatusPacket(1);
    }

    @Action(name = "GetPOIInfo", sync = false)
    public IPacket actionPlayerInfo(Identity identity, ActionPacket packet) {
        String wp = packet.getData().getString("poi");
        POI waypoint = getWaypointByUUID(UUID.fromString(wp));

        if(waypoint == null) return new StatusPacket(0, "POI not found!");

        JSONObject response = new JSONObject();
        
        JSONArray infos = new JSONArray();
        response.put("info", infos);

        List<InfoEntry> entries = new ArrayList<InfoEntry>();
        
        try{
            Bukkit.getScheduler().callSyncMethod(Plugin.getInstance(), new Callable<Void>(){
                @Override
                public Void call() throws Exception {
                    synchronized(_infoProviders) {
                        for(POIInfoProvider provider : _infoProviders) {
                            if((provider instanceof AsyncPOIInfoProvider)) continue;
                            if(provider.getPermission() != null && !identity.hasPermission(provider.getPermission()) && !identity.hasPermission("livekit.module.admin")) continue;

                            provider.onResolvePOIInfo(waypoint, entries);
                        }
                    }
                    return null;
                }
            }).get();
        }catch(Exception ex){ex.printStackTrace();}

        synchronized(_infoProviders) {
            for(POIInfoProvider provider : _infoProviders) {
                if(!(provider instanceof AsyncPOIInfoProvider)) continue;
                if(provider.getPermission() != null && !identity.hasPermission(provider.getPermission()) && !identity.hasPermission("livekit.module.admin")) continue;

                provider.onResolvePOIInfo(waypoint, entries);
            }
        }

        for(InfoEntry entry : entries) {
            JSONObject jentry = new JSONObject();
            jentry.put("name", entry.getName());
            jentry.put("value", entry.getValue());
            jentry.put("priority", 50);
            infos.put(jentry);
        }

        return packet.response(response);
    }

    @Action(name = "AddPOI", sync = false)
    public IPacket addPOI(Identity identity, ActionPacket action) {
        if(!hasEditPermission(identity)) return new StatusPacket(0, "Permission denied");

        double x = action.getData().getDouble("x");
        double z = action.getData().getDouble("z");
        String world = action.getData().getString("world");
        String name = action.getData().getString("name");
        String description = action.getData().getString("description");
        boolean teleport = action.getData().getBoolean("teleport");

        try {

            Location location = Bukkit.getScheduler().callSyncMethod(Plugin.getInstance(), new Callable<Location>(){
                @Override
                public Location call() throws Exception {
                    World w = Bukkit.getWorld(world);
                    return w.getHighestBlockAt((int)x, (int)z).getLocation();
                }
            }).get();

            POI poi = new POI(location, name, description, BasicPOIProvider.POI_COLOR, teleport, true);
            Plugin.getStorage().savePOI(poi);
            addPOI(poi);

        }catch(Exception ex) {
            ex.printStackTrace();
            return new StatusPacket(0, "POI creation failed! "+ex.getMessage());
        }

        return new StatusPacket(1);
    }

    @Action(name = "RemovePOI", sync = false)
    public IPacket removePOI(Identity identity, ActionPacket action) {
        if(!hasEditPermission(identity)) return new StatusPacket(0, "Permission denied");
        
        String uuid = action.getData().getString("poi");
        POI poi = getWaypointByUUID(UUID.fromString(uuid));

        if(poi == null) return new StatusPacket(0, "POI does not exist");
        if(!poi.canEdit()) return new StatusPacket(0, "POI can't be edited");

        try {
            removePOI(poi);
            Plugin.getStorage().deletePOI(poi);
        }catch(Exception ex){
            ex.printStackTrace();
            return new StatusPacket(0, "POI removale failed! "+ex.getMessage());
        }

        return new StatusPacket(1);
    }

    /*@Action(name = "EditPOI", sync = false)
    public IPacket editPOI(Identity identity, ActionPacket action) {
        if(!hasEditPermission(identity)) return new StatusPacket(0, "Permission denied");
        return null;
    }*/

    private POI getWaypointByUUID(UUID uuid) {
        synchronized(_pois) {
            for(POI waypoint : _pois) {
                if(waypoint.getUUID().equals(uuid)) {
                    return waypoint;
                }
            }
        }
        return null;
    }
}
