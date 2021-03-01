package at.livekit.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import at.livekit.api.map.POIProvider;
import at.livekit.api.map.Waypoint;
import at.livekit.livekit.Identity;
import at.livekit.packets.IPacket;

public class POIModule extends BaseModule {

    private List<POIProvider> _poiProviders = new ArrayList<POIProvider>();

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

    @Override
    public IPacket onJoinAsync(Identity identity) {
        JSONObject json = new JSONObject();
        JSONArray pois = new JSONArray();

        List<Waypoint> waypoints = new ArrayList<Waypoint>();
        synchronized(_poiProviders) {
            
        }

        return new ModuleUpdatePacket(this, json, true);
    }

    @Override
    public Map<Identity,IPacket> onUpdateAsync(List<Identity> identities) {
        Map<Identity, IPacket> responses = new HashMap<Identity,IPacket>();

        JSONObject json = new JSONObject();
        JSONArray messages = new JSONArray();
    
        synchronized(_updates) {
            for(ChatMessage message : _updates) {
                messages.put(message.toJson());
            }

            while(_updates.size() > 0) {
                _backlog.add(_updates.remove(0));
            }
            while(_backlog.size() > CHAT_LOG_SIZE) {
                _backlog.remove(0);
            }
        }
        json.put("messages", messages);
        IPacket response =  new ModuleUpdatePacket(this, json, false);

        for(Identity identity : identities) responses.put(identity, response);

        return responses;
    }
}
