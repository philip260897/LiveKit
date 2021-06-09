package at.livekit.storage;

import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

import org.bukkit.OfflinePlayer;

import at.livekit.api.map.POI;
import at.livekit.api.map.Waypoint;
import at.livekit.authentication.Pin;
import at.livekit.authentication.Session;
import at.livekit.utils.HeadLibraryV2.HeadInfo;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.stmt.Where;


public class SQLStorage implements IStorageAdapter
{
    private static ConnectionSource connectionSource;

    private static Dao<Session, String> sessionDao;
    private static Dao<Pin, String> pinDao;
    private static Dao<HeadInfo, String> headDao;
    //private static Dao<POI, String> poiDao;

    public SQLStorage(String connection) throws SQLException {
        if(SQLStorage.connectionSource == null) {
            SQLStorage.connectionSource = new JdbcConnectionSource(connection);
        }
    }

    @Override
    public void initialize() throws Exception {
        sessionDao = DaoManager.createDao(connectionSource, Session.class);
		TableUtils.createTableIfNotExists(connectionSource, Session.class);

        pinDao = DaoManager.createDao(connectionSource, Pin.class);
		TableUtils.createTableIfNotExists(connectionSource, Pin.class);

        headDao = DaoManager.createDao(connectionSource, HeadInfo.class);
		TableUtils.createTableIfNotExists(connectionSource, HeadInfo.class);
    }

    @Override
    public void dispose() {
        try {
            connectionSource.close();
        }catch(Exception ex){ex.printStackTrace();}
    }

    @Override
    public void deleteSession(String uuid, Session session) throws Exception {
        sessionDao.delete(session);
    }

    @Override
    public void createSession(String uuid, Session session) throws Exception {
        sessionDao.create(session);
    }

    @Override
    public List<Session> loadSessions(String uuid) throws Exception {
        return sessionDao.queryForEq("uuid", uuid);
    }

    @Override
    public void deletePin(String uuid, Pin pin) throws Exception {
        pinDao.delete(pin);
    }

    @Override
    public void createPin(String uuid, Pin pin) throws Exception {
        pinDao.create(pin);
    }

    @Override
    public Pin loadPin(String pin) throws Exception {
        Where<Pin, String> where = pinDao.queryBuilder().where();
        where.eq("pin", pin);
        return pinDao.queryForFirst(where.prepare());
    }

    @Override
    public List<Pin> loadPinsForPlayer(String uuid) throws Exception {
        List<Pin> list = new ArrayList<Pin>();
        Pin pin = pinDao.queryForId(uuid);
        if(pin != null) list.add(pin);
        return list;
    }

    @Override
    public void savePlayerHead(String uuid, HeadInfo info) throws Exception {
        headDao.createOrUpdate(info);
    }

    @Override
    public HeadInfo loadPlayerHead(String uuid) throws Exception {
        Where<HeadInfo, String> where = headDao.queryBuilder().where();
        where.eq("name", uuid);
        return headDao.queryForFirst(where.prepare());
    }

    @Override
    public List<HeadInfo> loadPlayerHeads() throws Exception {
        return headDao.queryForAll();
    }

    @Override
    public void savePOI(POI poi) throws Exception {
        //poiDao.createOrUpdate(poi);
    }

    @Override
    public void deletePOI(POI poi) throws Exception {
        //poiDao.delete(poi);
    }

    @Override
    public List<POI> loadPOIs() throws Exception {
        //return poiDao.queryForAll();
        return new ArrayList<POI>();
    }

    @Override
    public void savePlayerPin(OfflinePlayer player, Waypoint waypoints) throws Exception {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void deletePlayerPin(OfflinePlayer player, Waypoint waypoint) throws Exception {
        // TODO Auto-generated method stub
        
    }

    @Override
    public List<Waypoint> loadPlayerPins(OfflinePlayer player) throws Exception {
        // TODO Auto-generated method stub
        return new ArrayList<Waypoint>();
    }



}
