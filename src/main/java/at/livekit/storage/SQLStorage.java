package at.livekit.storage;

import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

import at.livekit.api.map.POI;
import at.livekit.api.map.PersonalPin;
import at.livekit.authentication.Pin;
import at.livekit.authentication.Session;
import at.livekit.plugin.Plugin;
import at.livekit.utils.HeadLibraryV2.HeadInfo;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.jdbc.JdbcPooledConnectionSource;
import com.j256.ormlite.logger.LogBackendType;
import com.j256.ormlite.logger.LoggerFactory;
import com.j256.ormlite.logger.NullLogBackend;
import com.j256.ormlite.stmt.Where;


public class SQLStorage extends StorageThreadMarshallAdapter
{
    private JdbcPooledConnectionSource connectionSource;
    private Map<Class<?>, Dao<?, String>> _daos = new HashMap<Class<?>, Dao<?, String>>();  

    public SQLStorage(String connection) throws SQLException {
        this(connection, null, null);
    }

    public SQLStorage(String connection, String username, String password) throws SQLException {
        if(!Plugin.isDebug()) {
            LoggerFactory.setLogBackendFactory(new NullLogBackend.NullLogBackendFactory());
        } else {
            LoggerFactory.setLogBackendType(LogBackendType.CONSOLE);
        }
        
        if(connectionSource == null) {
            connectionSource = new JdbcPooledConnectionSource(connection, username, password);
            connectionSource.setMaxConnectionsFree(2);
            connectionSource.setMaxConnectionAgeMillis(Long.MAX_VALUE);
            connectionSource.setTestBeforeGet(true);
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
            connectionSource.close();
        }catch(Exception ex){ex.printStackTrace();}

        _daos.clear();
    }

    private <T> void registerStorageClass(Class<T> clazz) throws Exception {
        _daos.put(clazz, (Dao<T, String>) DaoManager.createDao(connectionSource, clazz));

        if(!getDao(clazz).isTableExists()) {
            
            TableUtils.createTable(connectionSource, clazz);
        }
        
        if(Plugin.isDebug()) {
            for(Field f : clazz.getDeclaredFields()) {
                if(f.getAnnotation(DatabaseField.class) != null) {
                    if(!f.getName().toLowerCase().equals(f.getName())) {
                        throw new Exception("Invalid field naming! "+(clazz.getSimpleName()+":"+f.getName()+". Only lowercase allowed!"));
                    }
                }
            }
        }
    }

    @Override
    public <T> T loadSingle(Class<T> clazz, String id) throws Exception {
        super.loadSingle(clazz, id);

        Dao<T, String> dao = getDao(clazz);
        return dao.queryForId(id);
    }

    @Override
    public <T> T loadSingle(Class<T> clazz, String key, Object value) throws Exception {
        super.loadSingle(clazz, key, value);

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
        super.load(clazz, key, value);

        Dao<T, String> dao = getDao(clazz);
        return dao.queryForEq(key, value);
    }

    @Override
    public <T> List<T> loadAll(Class<T> clazz) throws Exception {
        super.loadAll(clazz);

        Dao<T, String> dao = getDao(clazz);
        return dao.queryForAll();
    }

    @Override
    public <T> void create(T entry) throws Exception {
        super.create(entry);

        Dao<T, String> dao = getDao(entry.getClass());
        dao.create(entry);
    }

    @Override
    public <T> void update(T entry) throws Exception {
        super.update(entry);

        Dao<T, String> dao = getDao(entry.getClass());
        dao.update(entry);
    }

    @Override
    public <T> void delete(T entry) throws Exception {
        super.delete(entry);

        Dao<T, String> dao = getDao(entry.getClass());
        dao.delete(entry);
    }

    @Override
    public <T> void createOrUpdate(T entry) throws Exception {
        super.createOrUpdate(entry);

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

    @Override
    public void migrateTo(IStorageAdapterGeneric adapter) throws Exception {
        
        for(Entry<Class<?>, Dao<?,String>> entry : _daos.entrySet()) {
            Plugin.log("Migrating "+entry.getValue().getTableName()+" with "+entry.getValue().countOf()+" entries");
            for(Object o : entry.getValue()) {
                adapter.create(o);
            }
        }
    }

    @Override
    public boolean isEmpty() {
        try{
            for(Dao<?,String> conn : _daos.values()) {
                if(conn.countOf() != 0) {
                    return false;
                }
            }
        }catch(Exception ex){ex.printStackTrace(); return false; }
        return true;
    }
}
