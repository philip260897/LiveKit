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
import at.livekit.modules.LiveMapModule.RegionData;
import at.livekit.plugin.Plugin;
import at.livekit.utils.Legacy;
import at.livekit.utils.HeadLibraryV2.HeadInfo;

public class JSONStorage implements IStorageAdapter {

    private File _fileSessions;
    private File _filePlayerHeads;
    private File _filePOIs;
    private File _filePlayerPins;
    private File _folderMap;

    private HashMap<String, List<Session>> _cachedSessions = new HashMap<String, List<Session>>();
    private HashMap<String, Pin> _cachedPins = new HashMap<String, Pin>();

    private HashMap<String, HeadInfo> _cachedHeads = new HashMap<String, HeadInfo>();

    public JSONStorage() throws IOException {
        
    }

    @Override
    public void initialize() throws Exception {
        initFiles();
        loadSessions();
        loadHeads();
    }

    @Override
    public void dispose() {
        try{
            saveSessions();
        }catch(Exception ex){ex.printStackTrace();}

        try{
            saveHeads();
        }catch(Exception ex){ex.printStackTrace();}
    }

    private void initFiles() throws IOException {
        _fileSessions = new File(Plugin.getInstance().getDataFolder(), "sessions.json");
        if(!_fileSessions.exists()) _fileSessions.createNewFile();

        _filePlayerHeads = new File(Plugin.getInstance().getDataFolder(), "heads.json");
        if(!_filePlayerHeads.exists()) _filePlayerHeads.createNewFile();

        _filePOIs = new File(Plugin.getInstance().getDataFolder(), "poi.json");
        if(!_filePOIs.exists()) _filePOIs.createNewFile();

        _filePlayerPins = new File(Plugin.getInstance().getDataFolder(), "player_pins.json");
        if(!_filePlayerPins.exists()) _filePlayerPins.createNewFile();

        _folderMap = new File(Plugin.getInstance().getDataFolder(), "map");
        if(!_folderMap.exists()) _folderMap.mkdir();
    }

    private void loadSessions() throws Exception {
        String jsonText = new String(Files.readAllBytes(_fileSessions.toPath()), StandardCharsets.UTF_8);
        JSONObject root = new JSONObject(jsonText);
        JSONArray array = root.getJSONArray("sessions");
        for(int i = 0; i < array.length(); i++) {
            JSONObject playerEntry = array.getJSONObject(i);
            String playerId = playerEntry.getString("playerId");
            JSONArray sessions = playerEntry.getJSONArray("sessions");

            List<Session> ps = new ArrayList<Session>();

            for(int j = 0; j < sessions.length(); j++) {
                JSONObject sessionEntry = sessions.getJSONObject(j);
                Session session = new Session(sessionEntry.getLong("timestamp"), sessionEntry.getLong("last"), sessionEntry.getString("auth"), sessionEntry.getString("ip"), sessionEntry.getString("data"));
                ps.add(session);
            }

            _cachedSessions.put(playerId, ps);
        }
    }

    private void saveSessions() throws Exception {
        if(!_fileSessions.exists()) _fileSessions.createNewFile();

        JSONObject root = new JSONObject();
        JSONArray sessions = new JSONArray();
        root.put("sessions", sessions);
        
        for(Entry<String, List<Session>> entry : _cachedSessions.entrySet()) {
            JSONObject jentry = new JSONObject();
            JSONArray playerSessions = new JSONArray();
            jentry.put("playerId", entry.getKey());
            for(Session session : entry.getValue()) {
                JSONObject sess = new JSONObject();
                sess.put("auth", session.getAuthentication());
                sess.put("timestamp", session.getTimestamp());
                sess.put("ip", session.getIP());
                sess.put("data", session.getData());
                sess.put("last", session.getLast());
                playerSessions.put(sess);
            }
            jentry.put("sessions", playerSessions);
        }

        Files.write(_fileSessions.toPath(), root.toString().getBytes(), new OpenOption[] { StandardOpenOption.CREATE, StandardOpenOption.WRITE });
    }

    @Override
    public void deleteSession(String uuid, Session session) throws Exception {
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
        synchronized(_cachedPins) {
            if(_cachedPins.containsKey(uuid)) {
                _cachedPins.remove(uuid);
            }
        }
    }

    @Override
    public void createPin(String uuid, Pin pin) throws Exception {
        synchronized(_cachedPins) {
            if(_cachedPins.containsKey(uuid)) {
                _cachedPins.remove(uuid);
            }
            _cachedPins.put(uuid, pin);
        }
    }

    @Override
    public List<Pin> loadPins() throws Exception {
        List<Pin> pins = new ArrayList<Pin>();
        synchronized(_cachedPins) {
            for(Pin pin : _cachedPins.values()) {
                pins.add(pin);
            }
        }
        return pins;
    }


    private void loadHeads() throws Exception {
        if(_filePlayerHeads.exists()) {
            JSONArray root = new JSONArray(new String(Files.readAllBytes(_filePlayerHeads.toPath())));
            for(int i = 0; i < root.length(); i++) {
                HeadInfo info = HeadInfo.fromJson(root.getJSONObject(i));
                _cachedHeads.put(info.getName(), info);
            }
        }

        if(Legacy.hasLegacyHeads()) {
            Plugin.log("Legacy Heads detected. Converting.");
            for(HeadInfo info : Legacy.getLegacyHeads()) {
                if(!_cachedHeads.containsKey(info.getName())) {
                    _cachedHeads.put(info.getName(), info);
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
    public void savePlayerHead(String uuid, HeadInfo info) {
        synchronized(_cachedHeads) {
            if(_cachedHeads.containsKey(uuid)) {
                _cachedHeads.remove(uuid);
            }
            _cachedHeads.put(uuid, info);
        }
    }

    @Override
    public HeadInfo loadPlayerHead(String uuid) {
        synchronized(_cachedHeads) {
            return _cachedHeads.get(uuid);
        }
    }



    @Override
    public void savePOIs(List<POI> pois) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public List<POI> loadPOIs() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void savePlayerPins(OfflinePlayer player, List<Waypoint> waypoints) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public List<Waypoint> loadPlayerPins(OfflinePlayer player) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void saveRegion(RegionData region) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public RegionData loadRegion(String world, int regionX, int regionZ) {
        // TODO Auto-generated method stub
        return null;
    }



 




    
}
