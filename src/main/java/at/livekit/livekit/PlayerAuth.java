package at.livekit.livekit;

import at.livekit.plugin.Plugin;
import at.livekit.utils.Utils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONObject;


public class PlayerAuth
{
    private String uuid;

    private String claimPin;
    private long claimPinTimestamp;

    private List<String> sessionKeys;

    public PlayerAuth(String uuid, List<String> sessionKeys) {
        this.uuid = uuid;
        this.sessionKeys = sessionKeys;
    }

    public String[] getSessionKeys() {
        return sessionKeys.toArray(new String[sessionKeys.size()]);
    }

    public String generateClaimPin() {
        claimPinTimestamp = System.currentTimeMillis();
        claimPin = Utils.generateRandom(6);
        return claimPin;
    }

    public boolean isValidSession(String authorization) {
        return sessionKeys.contains(authorization);
    }

    public boolean isValidClaim(String claim) {
        if(claimPinTimestamp + (2*60*1000) > System.currentTimeMillis()) {
            if( claim.equals(claimPin) ) {
                claimPin = null;
                return true;
            }
        }
        return false;
    }

    public String getUUID() {
        return uuid;
    }

    public void removeSession(String sessionKey) {
        sessionKeys.remove(sessionKey);
    }

    public void clearSessionKeys() {
        sessionKeys.clear();
    }

    public String generateSessionKey() {
        String key = Utils.generateRandom(128);
        sessionKeys.add(key);
        return key;
    }

    private static Map<String, PlayerAuth> auth = new HashMap<String, PlayerAuth>();

    public static void initialize() throws Exception {
        if((new File(Plugin.getInstance().getDataFolder().getAbsolutePath()+"/sessions.json")).exists()) {
            String jsonText = new String(Files.readAllBytes(Paths.get(Plugin.getInstance().getDataFolder().getAbsolutePath()+"/sessions.json")), StandardCharsets.UTF_8);
            JSONObject root = new JSONObject(jsonText);
            JSONArray array = root.getJSONArray("sessions");
            for(int i = 0; i < array.length(); i++) {
                JSONObject userSession = array.getJSONObject(i);
                String playerId = userSession.getString("playerId");
                List<String> sessions = new ArrayList<>();
                JSONArray playerSessions = userSession.getJSONArray("sessionKeys");
                for(int j = 0; j < playerSessions.length(); j++) {
                    sessions.add(playerSessions.getString(j));
                }
                auth.put(playerId, new PlayerAuth(playerId, sessions));
            }
        }
    }

    public static void save() throws Exception {
        if(auth.size() == 0) return;

        JSONObject root = new JSONObject();
        JSONArray sessions = new JSONArray();
        root.put("sessions", sessions);

        for(Entry<String, PlayerAuth> entry : auth.entrySet()) {
            JSONObject userEntry = new JSONObject();
            userEntry.put("playerId", entry.getKey());
            userEntry.put("sessionKeys", entry.getValue().sessionKeys);
            sessions.put(userEntry);    
        }
        Files.write(Paths.get(Plugin.getInstance().getDataFolder().getAbsolutePath()+"/sessions.json"), root.toString().getBytes(), new OpenOption[] { StandardOpenOption.CREATE, StandardOpenOption.WRITE });
    }

    public static PlayerAuth get(String uuid) {
        if(auth.containsKey(uuid)) return auth.get(uuid);
        PlayerAuth playerAuth = new PlayerAuth(uuid, new ArrayList<String>());
        auth.put(uuid, playerAuth);
        return playerAuth;
    }

    public static PlayerAuth validateClaim(String pin) {
        for(Entry<String,PlayerAuth> entry : auth.entrySet() ) {
            if(entry.getValue().isValidClaim(pin)) {
                return entry.getValue();
            }
        }
        return null;
    }
}