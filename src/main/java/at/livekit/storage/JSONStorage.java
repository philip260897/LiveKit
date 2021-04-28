package at.livekit.storage;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.bukkit.OfflinePlayer;
import org.json.JSONArray;
import org.json.JSONObject;

import at.livekit.api.map.POI;
import at.livekit.api.map.Waypoint;
import at.livekit.authentication.Pin;
import at.livekit.authentication.Session;
import at.livekit.plugin.Plugin;
import at.livekit.utils.Legacy;
import at.livekit.utils.HeadLibraryV2.HeadInfo;

public class JSONStorage implements IStorageAdapter {

    private static boolean DEBUG_DELAY = false;
    private static long DEBUG_DELAY_MS = 3000;

    private File _fileSessions;
    private File _filePlayerHeads;
    private File _filePOIs;
    private File _filePlayerPins;
    private File _folderMap;

    private HashMap<String, List<Session>> _cachedSessions = new HashMap<String, List<Session>>();
    private HashMap<String, Pin> _cachedPins = new HashMap<String, Pin>();

    private HashMap<String, HeadInfo> _cachedHeads = new HashMap<String, HeadInfo>();
    private List<POI> _cachedPOIS = new ArrayList<POI>();
    private HashMap<String, List<Waypoint>> _cachedPlayerPins = new HashMap<>();

    public JSONStorage() throws IOException {
        
    }

    @Override
    public void initialize() throws Exception {
        initFiles();
        loadSessions();
        loadHeads();
        loadPointsOfInterest();
        loadPlayerPins();
    }

    @Override
    public void dispose() {
        try{
            saveSessions();
        }catch(Exception ex){ex.printStackTrace();}

        try{
            saveHeads();
        }catch(Exception ex){ex.printStackTrace();}

        try {
            savePointsOfInterest();
        }catch(Exception ex){ex.printStackTrace();}

        try {
            savePlayerPins();
        }catch(Exception ex){ex.printStackTrace();}
    }

    private void initFiles() throws IOException {
        if(Legacy.hasLegacySessions()) {
            Plugin.log("Detected legacy session store... deleting");
            Legacy.deleteLegacySessions();

            if(Legacy.hasLegacyWorlds()) {
                Plugin.log("Detected legacy world names... converting");
                Legacy.convertLegacyWorldNames();
            }
        }


        _fileSessions = new File(Plugin.getInstance().getDataFolder(), "sessions-v2.json");
        //if(!_fileSessions.exists()) _fileSessions.createNewFile();

        _filePlayerHeads = new File(Plugin.getInstance().getDataFolder(), "heads.json");
        //if(!_filePlayerHeads.exists()) _filePlayerHeads.createNewFile();

        _filePOIs = new File(Plugin.getInstance().getDataFolder(), "poi.json");
        //if(!_filePOIs.exists()) _filePOIs.createNewFile();

        _filePlayerPins = new File(Plugin.getInstance().getDataFolder(), "player_pins.json");
        //if(!_filePlayerPins.exists()) _filePlayerPins.createNewFile();

        _folderMap = new File(Plugin.getInstance().getDataFolder(), "map");
        //if(!_folderMap.exists()) _folderMap.mkdir();
    }

    private void loadSessions() throws Exception {
        if(!_fileSessions.exists()) return;

        String jsonText = new String(Files.readAllBytes(_fileSessions.toPath()), StandardCharsets.UTF_8);
        JSONObject root = new JSONObject(jsonText);
        JSONArray array = root.getJSONArray("sessions");
        synchronized(_cachedSessions) {
            for(int i = 0; i < array.length(); i++) {
                JSONObject playerEntry = array.getJSONObject(i);
                String playerId = playerEntry.getString("playerId");
                JSONArray sessions = playerEntry.getJSONArray("sessions");

                List<Session> ps = new ArrayList<Session>();

                for(int j = 0; j < sessions.length(); j++) {
                    JSONObject sessionEntry = sessions.getJSONObject(j);
                    Session session = new Session(sessionEntry.getLong("timestamp"), sessionEntry.getLong("last"), sessionEntry.getString("auth"), null, null);
                    ps.add(session);
                }

                _cachedSessions.put(playerId, ps);
            }
        }
    }

    private void saveSessions() throws Exception {
        if(!_fileSessions.exists()) _fileSessions.createNewFile();

        JSONObject root = new JSONObject();
        JSONArray sessions = new JSONArray();
        root.put("sessions", sessions);

        synchronized(_cachedSessions) {
            for(Entry<String, List<Session>> entry : _cachedSessions.entrySet()) {
                JSONObject jentry = new JSONObject();
                JSONArray playerSessions = new JSONArray();
                jentry.put("playerId", entry.getKey());
                for(Session session : entry.getValue()) {
                    JSONObject sess = new JSONObject();
                    sess.put("auth", session.getAuthentication());
                    sess.put("timestamp", session.getTimestamp());
                    //sess.put("ip", session.getIP());
                    //sess.put("data", session.getData());
                    sess.put("last", session.getLast());
                    playerSessions.put(sess);
                }
                jentry.put("sessions", playerSessions);
                sessions.put(jentry);
            }
        }

        Files.write(_fileSessions.toPath(), root.toString().getBytes(), new OpenOption[] { StandardOpenOption.CREATE, StandardOpenOption.WRITE });
    }

    @Override
    public void deleteSession(String uuid, Session session) throws Exception {
        if(JSONStorage.DEBUG_DELAY) Thread.sleep(JSONStorage.DEBUG_DELAY_MS);
        
        synchronized(_cachedSessions) {
            if(_cachedSessions.containsKey(uuid)) {
                List<Session> sessions = _cachedSessions.get(uuid);
                synchronized(sessions) {
                    sessions.remove(session);
                }
            }
        }
    }

    @Override
    public void createSession(String uuid, Session session) throws Exception {
        if(JSONStorage.DEBUG_DELAY) Thread.sleep(JSONStorage.DEBUG_DELAY_MS);
        
        synchronized(_cachedSessions) {
            if(!_cachedSessions.containsKey(uuid)) {
                _cachedSessions.put(uuid, new ArrayList<Session>());
            }

            List<Session> sessions = _cachedSessions.get(uuid);
            synchronized(sessions) {
                sessions.add(session);
            }
        }
    }

    @Override
    public List<Session> loadSessions(String uuid) throws Exception {
        if(JSONStorage.DEBUG_DELAY) Thread.sleep(JSONStorage.DEBUG_DELAY_MS);
       
        List<Session> _sessions = new ArrayList<Session>();
        synchronized(_cachedSessions) {
            if(!_cachedSessions.containsKey(uuid)) {
                _cachedSessions.put(uuid, new ArrayList<Session>());
            }

            for(Session s : _cachedSessions.get(uuid)) _sessions.add(s);
        }
        return _sessions;
    }


    @Override
    public void deletePin(String uuid, Pin pin) throws Exception {
        if(JSONStorage.DEBUG_DELAY) Thread.sleep(JSONStorage.DEBUG_DELAY_MS);
        
        synchronized(_cachedPins) {
            if(_cachedPins.containsKey(uuid)) {
                _cachedPins.remove(uuid);
            }
        }
    }

    @Override
    public void createPin(String uuid, Pin pin) throws Exception {
        if(JSONStorage.DEBUG_DELAY) Thread.sleep(JSONStorage.DEBUG_DELAY_MS);
        
        synchronized(_cachedPins) {
            if(_cachedPins.containsKey(uuid)) {
                _cachedPins.remove(uuid);
            }
            _cachedPins.put(uuid, pin);
        }
    }

    @Override
    public List<Pin> loadPins() throws Exception {
        if(JSONStorage.DEBUG_DELAY) Thread.sleep(JSONStorage.DEBUG_DELAY_MS);
        
        List<Pin> pins = new ArrayList<Pin>();
        //pins.add(new Pin("867678c4-391b-42a9-a4cb-3ad14089f3f6", "123456", System.currentTimeMillis()));
        synchronized(_cachedPins) {
            for(Pin pin : _cachedPins.values()) {
                pins.add(pin);
            }
        }
        return pins;
    }


    private void loadHeads() throws Exception {
        if(!_filePlayerHeads.exists()) return;

        JSONArray root = new JSONArray(new String(Files.readAllBytes(_filePlayerHeads.toPath())));
        synchronized(_cachedHeads) {
            for(int i = 0; i < root.length(); i++) {
                HeadInfo info = HeadInfo.fromJson(root.getJSONObject(i));
                _cachedHeads.put(info.getName(), info);
            }
        }

        if(Legacy.hasLegacyHeads()) {
            Plugin.log("Legacy Heads detected. Converting.");
            synchronized(_cachedHeads) {
                for(HeadInfo info : Legacy.getLegacyHeads()) {
                    if(!_cachedHeads.containsKey(info.getName())) {
                        _cachedHeads.put(info.getName(), info);
                    }
                }
            }
            Legacy.deleteLegacyHeads();
            saveHeads();
        }
    }

    private void saveHeads() throws Exception {
        if(!_filePlayerHeads.exists()) _filePlayerHeads.createNewFile();

        JSONArray array = new JSONArray();
        synchronized(_cachedHeads) {
            for(Entry<String, HeadInfo> entry : _cachedHeads.entrySet()) {
                array.put(entry.getValue().toJson());
            }
        }
    
        PrintWriter writer = new PrintWriter(_filePlayerHeads);
        writer.write(array.toString());
        writer.flush();
        writer.close();
    }

    @Override
    public void savePlayerHead(String uuid, HeadInfo info) throws Exception {
        if(JSONStorage.DEBUG_DELAY) Thread.sleep(JSONStorage.DEBUG_DELAY_MS);
        
        synchronized(_cachedHeads) {
            if(_cachedHeads.containsKey(uuid)) {
                _cachedHeads.remove(uuid);
            }
            _cachedHeads.put(uuid, info);
        }
    }

    @Override
    public HeadInfo loadPlayerHead(String uuid) throws Exception {
        if(JSONStorage.DEBUG_DELAY) Thread.sleep(JSONStorage.DEBUG_DELAY_MS);
        
        synchronized(_cachedHeads) {
            return _cachedHeads.get(uuid);
        }
    }

    @Override
    public List<HeadInfo> loadPlayerHeads() throws Exception {
        if(JSONStorage.DEBUG_DELAY) Thread.sleep(JSONStorage.DEBUG_DELAY_MS);
        
        List<HeadInfo> heads = new ArrayList<HeadInfo>();
        synchronized(_cachedHeads) {
            for(HeadInfo h : _cachedHeads.values()) heads.add(h);
        }
        return heads;
    }

    private void loadPointsOfInterest() throws Exception{
        if(!_filePOIs.exists()) return;

        String jsonText = new String(Files.readAllBytes(_filePOIs.toPath()), StandardCharsets.UTF_8);
        JSONArray root = new JSONArray(jsonText);
        synchronized(_cachedPOIS) {
            for(int i = 0; i < root.length(); i++) {
                _cachedPOIS.add(POI.fromJson(root.getJSONObject(i)));
            }
        }
    }

    private void savePointsOfInterest() throws Exception{
        if(!_filePOIs.exists()) _filePOIs.createNewFile();

        JSONArray array = new JSONArray();
        synchronized(_cachedPOIS) {
            for(POI poi : _cachedPOIS) {
                array.put(poi.toJson());
            }
        }

        PrintWriter writer = new PrintWriter(_filePOIs);
        writer.write(array.toString());
        writer.flush();
        writer.close();
    }

    @Override
    public void savePOI(POI poi) throws Exception {
        if(JSONStorage.DEBUG_DELAY) Thread.sleep(JSONStorage.DEBUG_DELAY_MS);

        synchronized(_cachedPOIS) {
            if(!_cachedPOIS.contains(poi)) {
                _cachedPOIS.add(poi);
            }
        }
    }

    @Override
    public void deletePOI(POI poi) throws Exception {
        if(JSONStorage.DEBUG_DELAY) Thread.sleep(JSONStorage.DEBUG_DELAY_MS);

        synchronized(_cachedPOIS) {
            if(_cachedPOIS.contains(poi)) {
                _cachedPOIS.remove(poi);
            }
        }
    }

    @Override
    public List<POI> loadPOIs() throws Exception {
        if(JSONStorage.DEBUG_DELAY) Thread.sleep(JSONStorage.DEBUG_DELAY_MS);

        List<POI> pois = new ArrayList<POI>();
        synchronized(_cachedPOIS) {
            for(POI poi : _cachedPOIS) {
                pois.add(poi);
            }
        }
        return pois;
    }

    private void loadPlayerPins() throws Exception{
        if(!_filePlayerPins.exists()) return;

        String jsonText = new String(Files.readAllBytes(_filePlayerPins.toPath()), StandardCharsets.UTF_8);
        JSONArray root = new JSONArray(jsonText);
        synchronized(_cachedPlayerPins) {
            for(int i = 0; i < root.length(); i++) {
                JSONObject pentry = root.getJSONObject(i);
                JSONArray wp = pentry.getJSONArray("pins");
                List<Waypoint> waypoints=  new ArrayList<Waypoint>();
                for(int j = 0; j < wp.length(); j++) {
                    waypoints.add(Waypoint.fromJson(wp.getJSONObject(j)));
                }
                _cachedPlayerPins.put(pentry.getString("playerId"), waypoints);
            }
        }
    }

    private void savePlayerPins() throws Exception{
        if(!_filePlayerPins.exists()) _filePlayerPins.createNewFile();

        JSONArray array = new JSONArray();
        synchronized(_cachedPlayerPins) {
            for(Entry<String, List<Waypoint>> entry : _cachedPlayerPins.entrySet()) {
                JSONObject jentry = new JSONObject();
                array.put(jentry);
                jentry.put("playerId", entry.getKey());
                JSONArray wparray = new JSONArray();
                jentry.put("pins", wparray);
                for(Waypoint wp : entry.getValue()) wparray.put(wp.toJson());
            }
        }

        PrintWriter writer = new PrintWriter(_filePlayerPins);
        writer.write(array.toString());
        writer.flush();
        writer.close();
    }

    @Override
    public void savePlayerPin(OfflinePlayer player, Waypoint waypoint) throws Exception {
        if(JSONStorage.DEBUG_DELAY) Thread.sleep(JSONStorage.DEBUG_DELAY_MS);

        synchronized(_cachedPlayerPins) {
            if(!_cachedPlayerPins.containsKey(player.getUniqueId().toString())) {
                _cachedPlayerPins.put(player.getUniqueId().toString(), new ArrayList<Waypoint>());
            }
            _cachedPlayerPins.get(player.getUniqueId().toString()).add(waypoint);
        }
    }

    @Override
    public void deletePlayerPin(OfflinePlayer player, Waypoint waypoint) throws Exception {
        if(JSONStorage.DEBUG_DELAY) Thread.sleep(JSONStorage.DEBUG_DELAY_MS);

        synchronized(_cachedPlayerPins) {
            if(_cachedPlayerPins.containsKey(player.getUniqueId().toString())) {
                _cachedPlayerPins.get(player.getUniqueId().toString()).remove(waypoint);
            }
        }
    }

    @Override
    public List<Waypoint> loadPlayerPins(OfflinePlayer player) throws Exception {
        if(JSONStorage.DEBUG_DELAY) Thread.sleep(JSONStorage.DEBUG_DELAY_MS);

        List<Waypoint> waypoints = new ArrayList<Waypoint>();

        synchronized(_cachedPlayerPins) {
            if(_cachedPlayerPins.containsKey(player.getUniqueId().toString())) {
                waypoints.addAll(_cachedPlayerPins.get(player.getUniqueId().toString()));
            }
        }

        return waypoints;
    }

    /*@Override
    public void saveRegion(String world, RegionData region) throws Exception {
        File file = new File(_folderMap, world+"/"+ region.getX()+"_"+region.getZ()+".region");
        if(!file.exists()) file.createNewFile();

        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(region.data);
        } catch(Exception ex){ex.printStackTrace();}
    }

    @Override
    public RegionData loadRegion(String world, int regionX, int regionZ) throws Exception{
        File file = new File(_folderMap, world+"/"+ regionX +"_"+regionZ+".region");
        if(file.exists()) {
            byte[] data = Files.readAllBytes(file.toPath());
            RegionData region = new RegionData(regionX, regionZ, data);
            region.timestamp = Utils.decodeTimestamp(data);
            return region;
        }
        return null;
    }*/
}
