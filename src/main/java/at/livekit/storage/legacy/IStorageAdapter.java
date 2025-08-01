package at.livekit.storage.legacy;

import java.util.List;
import org.bukkit.OfflinePlayer;

import at.livekit.api.map.POI;
import at.livekit.api.map.PersonalPin;
import at.livekit.authentication.Pin;
import at.livekit.authentication.Session;
import at.livekit.storage.IStorageAdapterGeneric;
import at.livekit.utils.HeadLibraryV2.HeadInfo;

public interface IStorageAdapter 
{
    public void initialize() throws Exception;

    public void dispose();

    //Synced across networks

    public void deleteSession(String uuid, Session session) throws Exception;

    public void createSession(String uuid, Session session) throws Exception;

    public List<Session> loadSessions(String uuid) throws Exception;    


    public void deletePin(String uuid, Pin pin) throws Exception;

    public void createPin(String uuid, Pin pin) throws Exception;

    public Pin loadPin(String pin) throws Exception;

    public List<Pin> loadPinsForPlayer(String uuid) throws Exception;
    
    public void savePlayerHead(String uuid, HeadInfo info) throws Exception;

    public HeadInfo loadPlayerHead(String uuid) throws Exception;

    public List<HeadInfo> loadPlayerHeads() throws Exception;


    //Player 'Cloud' inventories


    //Per Server specific

    public void savePOI(POI poi) throws Exception;

    public void deletePOI(POI poi) throws Exception;

    public List<POI> loadPOIs() throws Exception;


    public void savePlayerPin(OfflinePlayer player, PersonalPin waypoints) throws Exception;

    public void deletePlayerPin(OfflinePlayer player, PersonalPin waypoint) throws Exception;

    public List<PersonalPin> loadPlayerPins(OfflinePlayer player) throws Exception;

    public void migrateTo(IStorageAdapterGeneric adapter) throws Exception;
   // public void saveRegion(String world, RegionData region) throws Exception;

    //public RegionData loadRegion(String world, int regionX, int regionZ) throws Exception;
}
