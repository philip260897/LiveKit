package at.livekit.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.json.JSONArray;
import org.json.JSONObject;

import at.livekit.api.core.LKLocation;
import at.livekit.api.map.AsyncPOILocationProvider;
import at.livekit.api.map.InfoEntry;
import at.livekit.api.map.POI;
import at.livekit.api.map.Waypoint;
import at.livekit.api.map.POILocationProvider;
import at.livekit.livekit.Identity;
import at.livekit.packets.ActionPacket;
import at.livekit.packets.IPacket;
import at.livekit.packets.StatusPacket;
import at.livekit.plugin.Plugin;
import at.livekit.provider.BasicPOILocationProvider;
import at.livekit.provider.BasicPOIProvider;

public class POIModule extends BaseModule {

    private List<POILocationProvider> _locationProviders = new ArrayList<POILocationProvider>();
    private Map<Identity, HashMap<POILocationProvider, List<POI>>> _pois = new HashMap<Identity, HashMap<POILocationProvider, List<POI>>>();
    private Map<Identity, List<POILocationProvider>> _currentUpdates = new HashMap<Identity, List<POILocationProvider>>();
    
    //private List<POI> _pois = new ArrayList<POI>();
    private List<String> _downstreamUpdate = new ArrayList<String>();

    public POIModule(ModuleListener listener) {
        super(1, "POI", "livekit.module.poi", UpdateRate.NEVER, listener);
    }
    
    /*public void addPOI(POI waypoint) {
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
    }*/

    public void clearProviders() {
        synchronized(_locationProviders) {
            _locationProviders.clear();
        }
    }

    public void addLocationProvider(POILocationProvider provider) {
        synchronized(_locationProviders) {
            if(!_locationProviders.contains(provider)) {
                _locationProviders.add(provider);
            }
        }
    }

    public void removeLocationProvider(POILocationProvider provider) {
        synchronized(_locationProviders) {
            if(_locationProviders.contains(provider)) {
                _locationProviders.remove(provider);
            }
        }
    }

    public void notifyDownstream(POI poi) {
        synchronized(_downstreamUpdate) {
            _downstreamUpdate.add(poi.getUUID().toString());
        }
        notifyChange();
    }

    public void notifyPOIChange(POILocationProvider provider) {
        synchronized(_locationProviders) {
            if(!_locationProviders.contains(provider)) {
                return;
            }
        }

        synchronized(_currentUpdates) {
            for(Identity identity : _pois.keySet()) {
                List<POILocationProvider> providers = _currentUpdates.get(identity);
                if(providers == null) providers = new ArrayList<POILocationProvider>();

                if(!providers.contains(provider)) {
                    providers.add(provider);
                }

                _currentUpdates.put(identity, providers);
            }
        }
        notifyChange();
    }

    private HashMap<POILocationProvider, List<POI>> updateFullPOIs(Identity identity) {

        try {
            HashMap<POILocationProvider, List<POI>> waypoints = Bukkit.getScheduler().callSyncMethod(Plugin.getInstance(), new Callable<HashMap<POILocationProvider, List<POI>>>(){
                @Override
                public HashMap<POILocationProvider, List<POI>> call() throws Exception {
                    HashMap<POILocationProvider, List<POI>> waypoints = new HashMap<POILocationProvider, List<POI>>();

                    for(POILocationProvider provider : _locationProviders) {
                        if(provider instanceof AsyncPOILocationProvider) continue;
                        if(provider.getPermission() != null && !identity.hasPermission(provider.getPermission()) && !identity.hasPermission("livekit.module.admin")) continue;

                        try {
                            List<POI> pois = provider.onResolvePOILocations(identity);
                            if(pois != null) waypoints.put(provider, pois);
                        }catch(Exception ex){ex.printStackTrace();}
                    }
                    
                    return waypoints;
                }
            }).get();

            for(POILocationProvider provider : _locationProviders) {
                if(!(provider instanceof AsyncPOILocationProvider)) continue;
                if(provider.getPermission() != null && !identity.hasPermission(provider.getPermission()) && !identity.hasPermission("livekit.module.admin")) continue;
                    
                try {
                    List<POI> pois = waypoints.containsKey(provider) ? waypoints.get(provider) : new ArrayList<POI>();
                    List<POI> points = provider.onResolvePOILocations(identity);
                    if(pois != null) pois.addAll(points);
                    waypoints.put(provider, pois);
                }catch(Exception ex){ex.printStackTrace();}
            }

            return waypoints;
        }catch(Exception ex){ex.printStackTrace();}
        return new HashMap<POILocationProvider, List<POI>>();
    }

    private List<POI> updateProviderPOIs(Identity identity, POILocationProvider provider) {

        if(provider.getPermission() != null && !identity.hasPermission(provider.getPermission()) && !identity.hasPermission("livekit.module.admin")) return new ArrayList<POI>();

        try {
            if(!(provider instanceof AsyncPOILocationProvider)) {
                return Bukkit.getScheduler().callSyncMethod(Plugin.getInstance(), new Callable<List<POI>>(){
                    @Override
                    public List<POI> call() throws Exception {
                        return provider.onResolvePOILocations(identity);
                    }
                }).get();
            } else {
                return provider.onResolvePOILocations(identity);
            }
        }catch(Exception ex){ex.printStackTrace();}
        return new ArrayList<POI>();
    }

    @Override
    public void onEnable(Map<String, ActionMethod> signature) {
        /*Bukkit.getScheduler().runTaskAsynchronously(Plugin.getInstance(), new Runnable(){
            @Override
            public void run() {
                try{
                    List<POI> pois = Plugin.getStorage().loadAll(POI.class);
                    synchronized(_pois) {
                        _pois.addAll(pois);
                    }
                }catch(Exception ex){ex.printStackTrace();}
                notifyFull();
            }
        });*/
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

        HashMap<POILocationProvider, List<POI>> waypoints = updateFullPOIs(identity);

        synchronized(_pois) {
            _pois.put(identity, waypoints);
        }
        

            for(POI waypoint : waypoints.values().stream().flatMap((list) -> list.stream()).collect(Collectors.toList())) {
                //JSONObject poi = new JSONObject();
                pois.put(waypoint.toJson());
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
                /*pois.put(waypoint.toJson());*/
            }
        

        json.put("pois", pois);
        json.put("edit", hasEditPermission(identity));
        return new ModuleUpdatePacket(this, json, true);
    }

    private boolean hasEditPermission(Identity identity) {
        return (identity.hasPermission("livekit.poi.edit"));
    }

    @Override
    public Map<Identity,IPacket> onUpdateAsync(List<Identity> identities) {
        Map<Identity, IPacket> responses = new HashMap<Identity,IPacket>();

        JSONArray downstreamUpdate = new JSONArray();
        synchronized(_downstreamUpdate) {
            for(String s : _downstreamUpdate) downstreamUpdate.put(s);
            _downstreamUpdate.clear();
        }


        for(Identity identity : identities) {
            JSONObject json = new JSONObject();
            JSONArray pois = new JSONArray();

            json.put("downstream", downstreamUpdate);

            String currentWorld = identity.getCurrentViewingWorld();
            if(currentWorld == null) continue;
            boolean fullUpdate = false;


            if(fullUpdate) {
                Plugin.debug("Updating full POIs for "+identity.getName());
                HashMap<POILocationProvider, List<POI>> waypoints = updateFullPOIs(identity);
                synchronized(_pois) {
                    _pois.put(identity, waypoints);
                }
            } else {
                List<POILocationProvider> providers;
                synchronized(_currentUpdates) {
                    providers = new ArrayList<>( _currentUpdates.get(identity) );
                    _currentUpdates.remove(identity);
                }

                Plugin.debug("Updating partial POIs for "+identity.getName());

                if(providers != null) {
                    for(POILocationProvider provider : providers) {
                        Plugin.debug("Updating POIs for "+provider.getName()+" for "+identity.getName()+"...");
                        List<POI> points = updateProviderPOIs(identity, provider);
                        if(points != null) {
                            HashMap<POILocationProvider, List<POI>> waypoints = _pois.get(identity);
                            if(waypoints == null) waypoints = new HashMap<POILocationProvider, List<POI>>();
                            waypoints.put(provider, points);
                            _pois.put(identity, waypoints);
                        }
                    }
                }
                
            }

            synchronized(_pois) {
                Map<POILocationProvider, List<POI>> waypoints = _pois.get(identity);
                if(waypoints == null) waypoints = new HashMap<POILocationProvider, List<POI>>();


                for(POI waypoint : waypoints.values().stream().flatMap((list) -> list.stream()).collect(Collectors.toList())) {
                    pois.put(waypoint.toJson());
                }
            }

            json.put("pois", pois);
            responses.put(identity, new ModuleUpdatePacket(this, json, false));
        }
        return responses;


        /*JSONObject json = new JSONObject();
        JSONArray pois = new JSONArray();



        synchronized(_pois) {
            for(POI waypoint : _pois) {*/
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
                 /*pois.put(waypoint.toJson());
            }
        }*/
        //json.put("pois", pois);
        /*json.put("downstream", downstreamUpdate);

        for(Identity identity : identities) responses.put(identity, new ModuleUpdatePacket(this, json, false));

        return responses;*/
    }

    @Override
    public void onDisconnectAsync(Identity identity) {
        Plugin.debug("Removing POIs for "+identity.getName());
        synchronized(_pois) {
            _pois.remove(identity);
        }
    }

    @Action(name="Teleport")
    protected IPacket teleportPOI(Identity identity, ActionPacket packet) {
        String wp = packet.getData().getString("waypoint");
        Waypoint waypoint = getWaypointForIdentity(identity, UUID.fromString(wp));

        if(waypoint == null) return new StatusPacket(0, "Waypoint not found!");
        if(waypoint.canTeleport() == false && !identity.hasPermission("livekit.module.admin")) return new StatusPacket(0, "Can't teleport to this waypoint");

        OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(identity.getUuid()));
        if(player == null || !player.isOnline()) return new StatusPacket(0, "Player is offline");

        Player online = player.getPlayer();
        Location location = waypoint.getLocation().toLocation();
        if(location == null) return new StatusPacket(0, "Location does not exist");

        location = location.getWorld().getHighestBlockAt(location.getBlockX(), location.getBlockZ()).getRelative(BlockFace.UP,1).getLocation();

        online.teleport(location);

        return new StatusPacket(1);
    }

    @Action(name = "GetPOIInfo", sync = false)
    public IPacket actionPlayerInfo(Identity identity, ActionPacket packet) {
        String wp = packet.getData().getString("poi");
        POI waypoint = getWaypointForIdentity(identity, UUID.fromString(wp));
        POILocationProvider provider = getProviderForWaypoint(identity, waypoint);

        if(waypoint == null) return new StatusPacket(0, "POI not found!");
        if(provider.getPermission() != null && !identity.hasPermission(provider.getPermission()) && !identity.hasPermission("livekit.module.admin")) return new StatusPacket(0, "Permission denied");

        JSONObject response = new JSONObject();
        
        JSONArray infos = new JSONArray();
        response.put("info", infos);

        List<InfoEntry> entries = new ArrayList<InfoEntry>();
        
        try{
            if(!(provider instanceof AsyncPOILocationProvider)) {
                Bukkit.getScheduler().callSyncMethod(Plugin.getInstance(), new Callable<Void>(){
                    @Override
                    public Void call() throws Exception {

                        try {
                            List<InfoEntry> infos = provider.onResolvePOIInfo(identity, waypoint);
                            if(infos != null) entries.addAll(infos);
                        }catch(Exception ex){ex.printStackTrace();}

                        return null;
                    }
                }).get();
            }
        }catch(Exception ex){ex.printStackTrace();}


            if(provider instanceof AsyncPOILocationProvider) {
                try {
                    List<InfoEntry> i = provider.onResolvePOIInfo(identity, waypoint);
                    if(i != null) entries.addAll(i);
                }catch(Exception ex){ex.printStackTrace();}
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


            POI poi = POI.create(LKLocation.fromLocation(location), name, description, BasicPOIProvider.POI_COLOR, teleport, true);
            Plugin.getStorage().create(poi);
            notifyPOIChange(_locationProviders.stream().filter((p) -> p instanceof BasicPOILocationProvider).map((p) -> (BasicPOILocationProvider)p).findFirst().orElse(null));

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
        //POI poi = getWaypointByUUID(UUID.fromString(uuid));
        try {
            POI poi = Plugin.getStorage().loadSingle(POI.class, "uuid", UUID.fromString(uuid));

            if(poi == null) return new StatusPacket(0, "POI does not exist");
            if(!poi.canEdit()) return new StatusPacket(0, "POI can't be edited");

       
            Plugin.getStorage().delete(poi);
            notifyPOIChange(_locationProviders.stream().filter((p) -> p instanceof BasicPOILocationProvider).map((p) -> (BasicPOILocationProvider)p).findFirst().orElse(null));
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

    /*private POI getWaypointByUUID(UUID uuid) {
        synchronized(_pois) {
            for(POI waypoint : _pois) {
                if(waypoint.getUUID().equals(uuid)) {
                    return waypoint;
                }
            }
        }
        return null;
    }*/

    private POI getWaypointForIdentity(Identity identity, UUID uuid) {
        synchronized(_pois) {
            if(!_pois.containsKey(identity)) return null;
            Map<POILocationProvider, List<POI>> waypoints = _pois.get(identity);
            for(POI waypoint : waypoints.values().stream().flatMap((list) -> list.stream()).collect(Collectors.toList())){
                if(waypoint.getUUID().equals(uuid)) {
                    return waypoint;
                }
            }
        }
        return null;
    }

    private POILocationProvider getProviderForWaypoint(Identity identity, POI waypoint) {
        synchronized(_pois) {
            if(!_pois.containsKey(identity)) return null;
            Map<POILocationProvider, List<POI>> waypoints = _pois.get(identity);
            for(POILocationProvider provider : waypoints.keySet()) {
                if(waypoints.get(provider).contains(waypoint)) {
                    return provider;
                }
            }
        }
        return null;
    }
}
