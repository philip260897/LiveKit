package at.livekit.storage;

import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

import at.livekit.api.datapersistors.ColorPersistor;
import at.livekit.api.map.POI;
import at.livekit.api.map.PersonalPin;
import at.livekit.authentication.Pin;
import at.livekit.authentication.Session;
import at.livekit.utils.HeadLibraryV2.HeadInfo;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.stmt.Where;


public class SQLStorage implements IStorageAdapterGeneric
{
    private static ConnectionSource connectionSource;
    private static Map<Class<?>, Dao<?, String>> _daos = new HashMap<Class<?>, Dao<?, String>>();  

    public SQLStorage(String connection) throws SQLException {
        if(SQLStorage.connectionSource == null) {
            SQLStorage.connectionSource = new JdbcConnectionSource(connection);
        }
    }

    @Override
    public void initialize() throws Exception {
        if(_daos.size() != 0) return;

        registerStorageClass(Session.class);
        registerStorageClass(Pin.class);
        registerStorageClass(HeadInfo.class);
        registerStorageClass(POI.class);
        registerStorageClass(PersonalPin.class);

    }

    @Override
    public void dispose() {
        try{
            SQLStorage.connectionSource.close();
        }catch(Exception ex){ex.printStackTrace();}
    }

    private <T> void registerStorageClass(Class<T> clazz) throws SQLException {
        _daos.put(clazz, (Dao<T, String>) DaoManager.createDao(connectionSource, clazz));
        TableUtils.createTableIfNotExists(connectionSource, clazz);
    }

    @Override
    public <T> T loadSingle(Class<T> clazz, String id) throws Exception {
        Dao<T, String> dao = getDao(clazz);
        return dao.queryForId(id);
    }

    @Override
    public <T> T loadSingle(Class<T> clazz, String key, Object value) throws Exception {
        Dao<T, String> dao = getDao(clazz);
        Where<T, String> where = dao.queryBuilder().where();
        where.eq(key, value);
        return dao.queryForFirst(where.prepare());
    }

    /*@Override
    public <T> List<T> load(Class<T> clazz, String id) throws Exception {
        return load(clazz, "uuid", id);
    }*/

    @Override
    public <T> List<T> load(Class<T> clazz, String key, Object value) throws Exception {
        Dao<T, String> dao = getDao(clazz);
        return dao.queryForEq(key, value);
    }

    @Override
    public <T> List<T> loadAll(Class<T> clazz) throws Exception {
        Dao<T, String> dao = getDao(clazz);
        return dao.queryForAll();
    }

    @Override
    public <T> void create(T entry) throws Exception {
        Dao<T, String> dao = getDao(entry.getClass());
        dao.create(entry);
    }

    @Override
    public <T> void update(T entry) throws Exception {
        Dao<T, String> dao = getDao(entry.getClass());
        dao.update(entry);
    }

    @Override
    public <T> void delete(T entry) throws Exception {
        Dao<T, String> dao = getDao(entry.getClass());
        dao.delete(entry);
    }

    @Override
    public <T> void createOrUpdate(T entry) throws Exception {
        Dao<T, String> dao = getDao(entry.getClass());
        dao.createOrUpdate(entry);
    }

    private <T> Dao<T, String> getDao(Class<?> clazz) throws Exception {
        if(_daos.containsKey(clazz)) {
            Dao<T, String> dao = (Dao<T, String>) _daos.get(clazz);
            return dao;
        }
        throw new Exception("Storage not found for class "+clazz.getSimpleName());
    }
}
