package at.livekit.storage;

import com.j256.ormlite.table.TableUtils;

import org.bukkit.OfflinePlayer;

import at.livekit.api.map.POI;
import at.livekit.api.map.PersonalPin;
import at.livekit.authentication.Pin;
import at.livekit.authentication.Session;
import at.livekit.plugin.Plugin;
import at.livekit.statistics.results.ProfileResult;
import at.livekit.statistics.tables.*;
import at.livekit.statistics.tables.LKStatParameter.LKParam;
import at.livekit.utils.HeadLibraryV2.HeadInfo;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.dao.GenericRawResults;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.jdbc.JdbcPooledConnectionSource;
import com.j256.ormlite.logger.LogBackendType;
import com.j256.ormlite.logger.LoggerFactory;
import com.j256.ormlite.logger.NullLogBackend;
import com.j256.ormlite.stmt.ArgumentHolder;
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

        registerStorageClass(LKStatParameter.class);
        registerStorageClass(LKStatCmd.class);
        registerStorageClass(LKStatSession.class);
        registerStorageClass(LKStatWorld.class);
        registerStorageClass(LKStatPVP.class);
        registerStorageClass(LKStatPVE.class);
        registerStorageClass(LKStatDeath.class);
        registerStorageClass(LKStatServerSession.class);
        registerStorageClass(LKUser.class);
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

    @Override
    public <T> T loadSingle(Class<T> clazz, String[] keys, Object[] values) throws Exception {
        if(keys.length != values.length) throw new Exception("Key Value lengths missmatch!");
        
        super.loadSingle(clazz, keys, values);
        
        Dao<T, String> dao = getDao(clazz);
        Where<T, String> where = dao.queryBuilder().where();
        
        for(int i = 0; i < keys.length; i++){
            if(i!=0) {
               where.and(); 
            }
            where.eq(keys[i], values[i]);
        }
        
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
    public <T> List<T> loadWhere(Class<T> clazz, String query, ArgumentHolder[] args) throws Exception {
        super.loadWhere(clazz, query, args);

        Dao<T, String> dao = getDao(clazz);
        return dao.queryBuilder().where().raw(query, args).query();
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

    //statistics queries

    public LKUser getLKUser(UUID uuid) throws Exception {
        Dao<LKUser, String> dao = getDao(LKUser.class);
        return dao.queryBuilder().where().eq("uuid", uuid.toString()).queryForFirst();
    }

    //QTP: CALL Profile(1);
    public ProfileResult getPlayerProfile(int id) throws Exception
    {
        Dao<LKStatSession, String> dao = getDao(LKStatSession.class);
        GenericRawResults<String[]> rawResults = dao.queryRaw("CALL Profile("+id+")");
        List<String[]> rows = rawResults.getResults();
        rawResults.close();
        
        if(rows.size() != 1) throw new Exception("Unexpected result length! "+rows.size());

        String[] row = rows.get(0);

        ProfileResult result = new ProfileResult();
        result.setTotalTimePlayed(row[0] != null ? Long.parseLong(row[0]) : 0);
        result.setTotalSessions(row[1] != null ? Long.parseLong(row[1]) : 0);
        result.setLongestSession(row[2] != null ? Long.parseLong(row[2]) : 0);
        result.setTotalDeaths(row[3] != null ? Long.parseLong(row[3]) : 0);
        result.setMostDeathsPerDay(row[4] != null ? Long.parseLong(row[4]) : 0);
        result.setTotalPVPKills(row[5] != null ? Long.parseLong(row[5]) : 0);
        result.setTotalPVEKills(row[6] != null ? Long.parseLong(row[6]) : 0);
        result.setLastKillPVPTarget(row[9] != null ? UUID.fromString(row[9]) : null);
        result.setLastKillPVPTimestamp(row[8] != null ? Long.parseLong(row[8]) : 0);
        result.setLastKillPVETarget(row[11] != null ? Long.parseLong(row[11]) : 0);
        result.setLastKillPVETimestamp(row[12] != null ? Long.parseLong(row[12]) : 0);

        LKStatParameter maxParameter = null;
        long maxParameterValue = -1;
        
        List<LKStatParameter> totalWeapons = getTotalParameters(id, LKParam.WEAPON_KILL);
        for(LKStatParameter parameter : totalWeapons) {
            if(parameter.value > maxParameterValue) {
                maxParameter = parameter;
                maxParameterValue = parameter.value;
            }
        }

        if(maxParameter != null)
        {
            result.setMostUsedWeapon(new Long(maxParameter.type));
            result.setMostUsedWeaponKills(new Long(maxParameter.value));
        }

        maxParameter = null;
        maxParameterValue = 0;
        List<LKStatParameter> totalBlocks = getTotalParameters(id, LKParam.BLOCK_BREAK);
        for(LKStatParameter parameter : totalBlocks) {
            if(parameter.value > maxParameterValue) {
                maxParameter = parameter;
                maxParameterValue = parameter.value;
            }
        }

        if(maxParameter != null)
        {
            result.setMostFarmedBlock(new Long(maxParameter.type));
            result.setMostFarmedBlockValue(new Long(maxParameter.value));
        }

        maxParameter = null;
        maxParameterValue = 0;
        List<LKStatParameter> totalTool = getTotalParameters(id, LKParam.TOOL_USE);
        for(LKStatParameter parameter : totalTool) {
            if(parameter.value > maxParameterValue) {
                maxParameter = parameter;
                maxParameterValue = parameter.value;
            }
        }

        if(maxParameter != null)
        {
            result.setMostUsedTool(new Long(maxParameter.type));
            result.setMostUsedToolValue(new Long(maxParameter.value));
        }

        
        
        return result;
    }

    //QTP: SELECT type, SUM(value) as value FROM LiveKit.livekit_stats_parameters WHERE user_id=1 AND param = 3 GROUP BY type;
    public List<LKStatParameter> getTotalParameters(int user, LKParam param) throws Exception
    {
        Dao<LKStatParameter, String> dao = getDao(LKStatParameter.class);
        Where<LKStatParameter, String> where = dao.queryBuilder().selectRaw("type", "SUM(value)").groupBy("type").where();
        where.and(where.eq("user_id", user), where.eq("param", param));

        List<LKStatParameter> parameters = new ArrayList<LKStatParameter>();

        GenericRawResults<String[]> result = where.queryRaw();
        for(String[] row : result.getResults() ) {
            LKStatParameter stat = new LKStatParameter();
            stat.param = param;
            stat.type = Integer.parseInt(row[0]);
            stat.value = Integer.parseInt(row[1]);
            stat.timestamp = 0;
            parameters.add(stat);
        }
        result.close();
        
        return parameters;
    }

    public List<LKStatSession> getSessionsFromTo(long from, long to) throws Exception
    {
        Dao<LKStatSession, String> dao = getDao(LKStatSession.class);
        Where<LKStatSession, String> where = dao.queryBuilder().where();
        where.and(where.le("start", to), where.or(where.ge("end", from), where.eq("end", 0)));

        return where.query();
    }

    public List<LKStatServerSession> getServerSessionFromTo(long from, long to) throws Exception
    {
        Dao<LKStatServerSession, String> dao = getDao(LKStatServerSession.class);
        Where<LKStatServerSession, String> where = dao.queryBuilder().where();
        where.and(where.le("start", to), where.or(where.ge("end", from), where.eq("end", 0)));

        return where.query();
    }

}
