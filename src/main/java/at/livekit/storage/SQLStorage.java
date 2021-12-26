package at.livekit.storage;

import com.j256.ormlite.table.TableUtils;

import at.livekit.api.map.POI;
import at.livekit.api.map.PersonalPin;
import at.livekit.authentication.Pin;
import at.livekit.authentication.Session;
import at.livekit.plugin.Plugin;
import at.livekit.statistics.results.PVPResult;
import at.livekit.statistics.results.PlayerValueResult;
import at.livekit.statistics.results.ProfileResult;
import at.livekit.statistics.results.WorldUsageResult;
import at.livekit.statistics.tables.*;
import at.livekit.statistics.tables.LKStatParameter.LKParam;
import at.livekit.utils.HeadLibraryV2.HeadInfo;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.Map.Entry;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.dao.GenericRawResults;
import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.jdbc.JdbcPooledConnectionSource;
import com.j256.ormlite.logger.LogBackendType;
import com.j256.ormlite.logger.LoggerFactory;
import com.j256.ormlite.logger.NullLogBackend;
import com.j256.ormlite.stmt.ArgumentHolder;
import com.j256.ormlite.stmt.Where;


public class SQLStorage extends StorageThreadMarshallAdapter
{
    private String sqlProvider;
    private JdbcPooledConnectionSource connectionSource;
    private Map<Class<?>, Dao<?, String>> _daos = new HashMap<Class<?>, Dao<?, String>>();  

    public SQLStorage(String connection) throws SQLException {
        this(connection, null, null);
    }

    public SQLStorage(String connection, String username, String password) throws SQLException {
        this.sqlProvider = connection.split(":")[1];
        
        if(!Plugin.isDebug()) {
            LoggerFactory.setLogBackendFactory(new NullLogBackend.NullLogBackendFactory());
        } else {
            //LoggerFactory.setLogBackendType(LogBackendType.CONSOLE);
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
        return dao.queryBuilder().where().eq("uuid", uuid).queryForFirst();
    }

    //QTP: CALL Profile(1);
    public ProfileResult getPlayerProfile(LKUser user) throws Exception
    {
        String query = "SELECT * FROM (SELECT SUM("+replaceZeroSession("end", null)+"-start) as time_played, COUNT(_id) as sessions, MAX("+replaceZeroSession("end", null)+"-start) as longest_session FROM livekit_stats_sessions WHERE user_id="+user._id+" AND start<>0 ) as s "+
        //"LEFT JOIN (SELECT SUM(count) as total_deaths, MAX(count) as most_deaths_per_day FROM livekit_stats_deaths WHERE user_id="+user._id+") as d  ON 1=1 "+
        "LEFT JOIN (SELECT COUNT(user_id) as total_deaths FROM livekit_stats_deaths WHERE user_id="+user._id+") as d  ON 1=1 "+
        "LEFT JOIN (SELECT COUNT(user_id) as total_pvp FROM livekit_stats_pvp WHERE user_id="+user._id+") as pvp ON 1=1 "+
        "LEFT JOIN (SELECT COUNT(user_id) as total_pve FROM livekit_stats_parameters WHERE user_id="+user._id+" AND param="+LKParam.ENTITY_KILLS.ordinal()+") as pve ON 1=1 "+
        "LEFT JOIN (SELECT * FROM (SELECT a.target_id as last_kill_pvp_target_id, a.timestamp as last_kill_pvp_timestamp FROM livekit_stats_pvp as a INNER JOIN ( SELECT MAX(timestamp) as timestamp FROM livekit_stats_pvp WHERE user_id = "+user._id+") as b ON a.user_id = "+user._id+" AND a.timestamp = b.timestamp) as c "+
        "LEFT JOIN (SELECT uuid as last_kill_pvp_target_uuid, _id as tid FROM livekit_users) as u ON u.tid=c.last_kill_pvp_target_id) as x ON 1=1;";
        //"LEFT JOIN (SELECT a.entity as last_kill_pve_entity, a.timestamp as last_kill_pve_timestamp FROM livekit_stats_pve as a INNER JOIN ( SELECT MAX(timestamp) as timestamp FROM livekit_stats_pve WHERE user_id = "+user._id+") as b ON a.user_id = "+user._id+" AND a.timestamp = b.timestamp) as e ON 1=1;";

        Dao<LKStatSession, String> dao = getDao(LKStatSession.class);
        GenericRawResults<String[]> rawResults = dao.queryRaw(query);
        List<String[]> rows = rawResults.getResults();
        rawResults.close();
        
        if(rows.size() != 1) throw new Exception("Unexpected result length! "+rows.size());

        String[] row = rows.get(0);

        ProfileResult result = new ProfileResult();
        result.setTotalTimePlayed(row[0] != null ? Long.parseLong(row[0]) : 0);
        result.setTotalSessions(row[1] != null ? Long.parseLong(row[1]) : 0);
        result.setLongestSession(row[2] != null ? Long.parseLong(row[2]) : 0);
        result.setTotalDeaths(row[3] != null ? Long.parseLong(row[3]) : 0);
        //result.setMostDeathsPerDay(row[4] != null ? Long.parseLong(row[4]) : 0);
        result.setTotalPVPKills(row[4] != null ? Long.parseLong(row[4]) : 0);
        result.setTotalPVEKills(row[5] != null ? Long.parseLong(row[5]) : 0);
        result.setLastKillPVPTarget(row[8] != null ? UUID.fromString(row[8]) : null);
        result.setLastKillPVPTimestamp(row[7] != null ? Long.parseLong(row[7]) : 0);
        //result.setLastKillPVETarget(row[11] != null ? Long.parseLong(row[11]) : 0);
        //result.setLastKillPVETimestamp(row[12] != null ? Long.parseLong(row[12]) : 0);

        LKStatParameter maxParameter = null;
        long maxParameterValue = -1;
        
        List<LKStatParameter> totalWeapons = getTotalParameters(user, LKParam.WEAPON_KILL);
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
        List<LKStatParameter> totalBlocks = getTotalParameters(user, LKParam.BLOCK_BREAK);
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
        List<LKStatParameter> totalTool = getTotalParameters(user, LKParam.TOOL_USE);
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
    public List<LKStatParameter> getTotalParameters(LKUser user, LKParam param) throws Exception
    {
        Dao<LKStatParameter, String> dao = getDao(LKStatParameter.class);
        Where<LKStatParameter, String> where = dao.queryBuilder().selectRaw("type", "SUM(value) as value").groupBy("type").orderBy("value", false).where();
        where.and(where.eq("user_id", user._id), where.eq("param", param));

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

    //QTP: SELECT type, SUM(value) as value FROM LiveKit.livekit_stats_parameters WHERE user_id=1 AND param = 3 AND timestamp >= from AND timestamp <= to GROUP BY type;
    public List<LKStatParameter> getPlayerParameters(LKUser user, LKParam param, long from, long to) throws Exception
    {
        Dao<LKStatParameter, String> dao = getDao(LKStatParameter.class);
        Where<LKStatParameter, String> where = dao.queryBuilder().selectRaw("type", "SUM(value) as value").groupBy("type").orderBy("value", false).where();
        where.and(where.eq("user_id", user._id), where.eq("param", param)).and().ge("timestamp", from).and().le("timestamp", to);
    
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

    public List<LKStatSession> getPlayerSessions(LKUser user, long from, long to) throws Exception
    {
        Dao<LKStatSession, String> dao = getDao(LKStatSession.class);
        Where<LKStatSession, String> where = dao.queryBuilder().where();
        where.eq("user_id", user);
        where.and().and(where.le("start", to), where.or(where.ge("end", from), where.eq("end", 0)));
        
        return where.query();
    }

    public List<LKStatCmd> getPlayerCommands(LKUser user, long from, long to) throws Exception
    {
        Dao<LKStatCmd, String> dao = getDao(LKStatCmd.class);
        Where<LKStatCmd, String> where = dao.queryBuilder().where();
        where.eq("user_id", user);
        where.and().and(where.ge("timestamp", from), where.le("timestamp", to));
        
        return where.query();
    }

    public List<LKStatDeath> getPlayerDeaths(LKUser user, long from, long to) throws Exception
    {
        Dao<LKStatDeath, String> dao = getDao(LKStatDeath.class);
        Where<LKStatDeath, String> where = dao.queryBuilder().where();
        where.eq("user_id", user);
        where.and().and(where.ge("timestamp", from), where.le("timestamp", to));
        
        return where.query();
    }

    //QTA: SELECT kills.user_id, kills.timestamp, users.uuid FROM (SELECT * FROM livekit_stats_pvp WHERE (user_id = 1) AND timestamp >= 10000000000 AND timestamp <= 9000000000000) as kills LEFT JOIN livekit_users as users on kills.target_id=users._id;
    //QTA: SELECT kills.target_id, kills.timestamp, users.uuid FROM (SELECT * FROM livekit_stats_pvp WHERE (target_id = 1) AND timestamp >= 10000000000 AND timestamp <= 9000000000000) as kills LEFT JOIN livekit_users as users on kills.user_id=users._id;
    public List<PVPResult> getPlayerPVP(LKUser user, long from, long to) throws Exception
    {
        List<PVPResult> result = new ArrayList<PVPResult>();
        
        String queryKills = "SELECT kills.timestamp, users.uuid, kills.weapon FROM (SELECT * FROM livekit_stats_pvp WHERE (user_id = "+user._id+") AND timestamp >= "+from+" AND timestamp <= "+to+") as kills LEFT JOIN livekit_users as users on kills.target_id=users._id;";

        Dao<LKStatPVP, String> dao = getDao(LKStatPVP.class);
        GenericRawResults<String[]> rawResults = dao.queryRaw(queryKills);
        List<String[]> rows = rawResults.getResults();
        rawResults.close();

        for(String[] columns : rows) result.add(new PVPResult(UUID.fromString(columns[1]), Long.parseLong(columns[0]), Integer.parseInt(columns[2]), true));
    
        String queryDeath = "SELECT kills.timestamp, users.uuid, kills.weapon FROM (SELECT * FROM livekit_stats_pvp WHERE (target_id = "+user._id+") AND timestamp >= "+from+" AND timestamp <= "+to+") as kills LEFT JOIN livekit_users as users on kills.user_id=users._id";
        rawResults = dao.queryRaw(queryDeath);
        rows = rawResults.getResults();
        rawResults.close();

        for(String[] columns : rows) result.add(new PVPResult(UUID.fromString(columns[1]), Long.parseLong(columns[0]), Integer.parseInt(columns[2]), false));

        return result;
    }

    /*public List<LKStatSession> getSessionsFromTo(long from, long to) throws Exception
    {
        Dao<LKStatSession, String> dao = getDao(LKStatSession.class);
        Where<LKStatSession, String> where = dao.queryBuilder().where();
        where.and(where.le("start", to), where.or(where.ge("end", from), where.eq("end", 0)));

        return where.query();
    }*/

    /*public List<LKStatServerSession> getServerSessionFromTo(long from, long to) throws Exception
    {
        Dao<LKStatServerSession, String> dao = getDao(LKStatServerSession.class);
        Where<LKStatServerSession, String> where = dao.queryBuilder().where();
        where.and(where.le("start", to), where.or(where.ge("end", from), where.eq("end", 0)));

        return where.query();
    }*/

    //QTP: SELECT COUNT(user_id) as count, SUM(`leave`-enter) as duration, world FROM livekit_stats_world WHERE timestamp >= from AND timestamp <= to GROUP BY world;
    public WorldUsageResult getAnalyticsWorldUsage(long from, long to) throws Exception
    {
        WorldUsageResult result = new WorldUsageResult();

        Dao<LKStatWorld, String> dao = getDao(LKStatWorld.class);
        Where<LKStatWorld, String> where = dao.queryBuilder().selectRaw("COUNT(*) as count", "SUM("+replaceZeroSession("`leave`", null)+"-`enter`) as duration", "world").groupBy("world").where();
        where.ge("enter", from).and().le("leave", to)/*.and().ne("leave", 0)*/;

        GenericRawResults<String[]> results = where.queryRaw();
        for(String[] row : results.getResults() ) {
            result.setWorldUsage(row[2], Long.parseLong(row[1]));
        }
        results.close();

        return result;
    }

    //SELECT DATE(FROM_UNIXTIME(start/1000)) as startDate, COUNT(DISTINCT(user_id)) as users FROM livekit_stats_sessions WHERE start >= from AND start <= to GROUP BY DATE(FROM_UNIXTIME(start/1000)) ORDER BY startDate DESC;
    public Map<String, Long> getAnalyticsPlayersPerDay(long from, long to) throws Exception
    {
        Map<String, Long> result = new TreeMap<String, Long>();

        Dao<LKStatSession, String> dao = getDao(LKStatSession.class);
        GenericRawResults<Object[]> results = dao.queryRaw("SELECT "+dateFunction("start")+" as startDate, COUNT(DISTINCT(user_id)) as users FROM livekit_stats_sessions WHERE start >= "+from+" AND start <= "+to+" GROUP BY "+dateFunction("start")+" ORDER BY startDate DESC", new DataType[]{DataType.STRING, DataType.STRING});
        for(Object[] row : results.getResults() ) {
            result.put((String)row[0], Long.parseLong((String)row[1]));
        }
        results.close();

        return result;
    }

    //SELECT DATE(FROM_UNIXTIME(sessions.start/1000)) as day, COUNT(*) as count FROM (SELECT user_id, MIN(start) as start FROM livekit_stats_sessions GROUP BY user_id) as firstSessions LEFT JOIN livekit_stats_sessions as sessions ON firstSessions.user_id = sessions.user_id AND firstSessions.start = sessions.start GROUP BY DATE(FROM_UNIXTIME(sessions.start/1000));
    public Map<String, Long> getAnalyticsNewPlayersPerDay(long from, long to) throws Exception
    {
        Map<String, Long> result = new TreeMap<String, Long>();

        Dao<LKStatSession, String> dao = getDao(LKStatSession.class);
        GenericRawResults<String[]> results = dao.queryRaw("SELECT "+dateFunction("sessions.start")+" as day, COUNT(*) as count FROM (SELECT user_id, MIN(start) as start FROM livekit_stats_sessions GROUP BY user_id) as firstSessions LEFT JOIN livekit_stats_sessions as sessions ON firstSessions.user_id = sessions.user_id AND firstSessions.start = sessions.start GROUP BY "+dateFunction("sessions.start")+";");
        for(Object[] row : results.getResults() ) {
            result.put((String)row[0], Long.parseLong((String)row[1]));
        }
        results.close();

        return result;
    }

    //SELECT u.uuid, COUNT(user_id) as count, SUM(end - start) as total FROM livekit_stats_sessions WHERE start >= from AND start <= to LEFT JOIN livekit_users as u ON u._id = user_id GROUP BY user_id ORDER BY total DESC;
    public List<PlayerValueResult<Long, Integer>> getAnalyticsMostActivePlayer(long from, long to) throws Exception
    {
        List<PlayerValueResult<Long, Integer>> result = new ArrayList<PlayerValueResult<Long, Integer>>();

        Dao<LKStatSession, String> dao = getDao(LKStatSession.class);
        GenericRawResults<String[]> results = dao.queryRaw("SELECT u.uuid, s.total, s.count FROM ( SELECT user_id, COUNT(user_id) as count, SUM("+replaceZeroSession("end", null)+" - start) as total FROM livekit_stats_sessions WHERE start >= "+from+" AND start <= "+to+" GROUP BY user_id ORDER BY total DESC ) as s LEFT JOIN livekit_users as u ON u._id = s.user_id;");
        for(String[] row : results.getResults() ) {
            result.add(new PlayerValueResult<Long, Integer>(UUID.fromString(row[0]), Long.parseLong(row[1]), Integer.parseInt(row[2])));
        }
        results.close();

        return result;
    }
    
    //SELECT u.uuid, d.start, (CASE (d.end) WHEN 0 THEN unix_timestamp()*1000 ELSE d.end END) as end FROM (SELECT * FROM LiveKit.livekit_stats_sessions WHERE start >= 0 AND end <= 1000000000000000) as d LEFT JOIN LiveKit.livekit_users as u ON u._id = d.user_id;
    public List<PlayerValueResult<Long, Long>> getAnalyticsSessions(long from, long to) throws Exception
    {
        List<PlayerValueResult<Long, Long>> result = new ArrayList<PlayerValueResult<Long, Long>>();

        Dao<LKStatSession, String> dao = getDao(LKStatSession.class);
        GenericRawResults<String[]> results = dao.queryRaw("SELECT u.uuid, d.start, "+replaceZeroSession("d.end", "end")+" FROM (SELECT * FROM livekit_stats_sessions WHERE start >= "+from+" AND start <= "+to+") as d LEFT JOIN livekit_users as u ON u._id = d.user_id;");
        for(String[] row : results.getResults() ) {
            result.add(new PlayerValueResult<Long, Long>(UUID.fromString(row[0]), Long.parseLong(row[1]), Long.parseLong(row[2])));
        }
        results.close();

        return result;
    }

    public Map<String, Long> getAnalyticsPlaytime(long from, long to) throws Exception
    {
        List<PlayerValueResult<Long, Long>> result = getAnalyticsSessions(from, to);
        Map<String, Long> data = new HashMap<String, Long>(); 

        for(PlayerValueResult<Long, Long> item : result) {
            if(!(item.getSecondary() > item.getValue())) continue;

            Calendar startExact = Calendar.getInstance();
            startExact.setTimeInMillis(item.getValue());

            Calendar endExact = Calendar.getInstance();
            endExact.setTimeInMillis(item.getSecondary());

            Calendar start = getCalendarWithoutTime();
            start.set(startExact.get(Calendar.YEAR), startExact.get(Calendar.MONTH), startExact.get(Calendar.DAY_OF_MONTH));

            Calendar end = getCalendarWithoutTime();
            end.set(endExact.get(Calendar.YEAR), endExact.get(Calendar.MONTH), endExact.get(Calendar.DAY_OF_MONTH));

            String formatedStart = dateFormat(start);
            String formatedEnd = dateFormat(end);

            if(start.equals(end)) {
                if(!data.containsKey(formatedStart)) data.put(formatedStart, 0L);
                data.put(formatedStart, data.get(formatedStart) + (item.getSecondary() - item.getValue()));
            } else {
                Calendar next = getCalendarWithoutTime();
                next.set(startExact.get(Calendar.YEAR), startExact.get(Calendar.MONTH), startExact.get(Calendar.DAY_OF_MONTH));
                next.add(Calendar.DATE, 1);

                if(!data.containsKey(formatedStart)) data.put(formatedStart, 0L);
                data.put(formatedStart, data.get(formatedStart) + (next.getTimeInMillis() - item.getValue()));

                while(!next.equals(end)) {
                    String formatedNext = dateFormat(next);

                    if(!data.containsKey(formatedNext)) data.put(formatedNext, 0L);
                    data.put(formatedNext, data.get(formatedNext) + 24*60*60*1000);

                    next.add(Calendar.DATE, 1);
                }

                if(!data.containsKey(formatedEnd)) data.put(formatedEnd, 0L);
                data.put(formatedEnd, data.get(formatedEnd) + (item.getSecondary() - end.getTimeInMillis()));
            }
        }
        return data;
    }

    //SELECT cause, COUNT(*) as deaths FROM LiveKit.livekit_stats_deaths GROUP BY cause ORDER BY deaths DESC;
    public Map<Integer, Long> getAnalyticsDeathCauses(long from, long to) throws Exception {

        Map<Integer, Long> deathCauses = new HashMap<Integer, Long>();
        Dao<LKStatDeath, String> dao = getDao(LKStatDeath.class);
        GenericRawResults<String[]> results = dao.queryRaw("SELECT cause, COUNT(*) as deaths FROM livekit_stats_deaths WHERE timestamp >= "+from+" AND timestamp <= "+to+" GROUP BY cause ORDER BY deaths DESC;");
        for(String[] row : results.getResults() ) {
            deathCauses.put(Integer.parseInt(row[0]), Long.parseLong(row[1]));
        }
        results.close();

        return deathCauses;
    }

    //SELECT DATE(FROM_UNIXTIME(timestamp/1000)) as deathDate, COUNT(*) as deaths FROM LiveKit.livekit_stats_deaths GROUP BY DATE(FROM_UNIXTIME(timestamp/1000)) ORDER BY deathDate DESC;
    public Map<String, Long> getAnalyticsDeathsPerDay(long from, long to) throws Exception
    {
        Map<String, Long> result = new TreeMap<String, Long>();

        Dao<LKStatSession, String> dao = getDao(LKStatSession.class);
        GenericRawResults<Object[]> results = dao.queryRaw("SELECT "+dateFunction("timestamp")+" as deathDate, COUNT(*) as deaths FROM livekit_stats_deaths WHERE timestamp >= "+from+" AND timestamp <= "+to+" GROUP BY "+dateFunction("timestamp")+" ORDER BY deathDate DESC;", new DataType[]{DataType.STRING, DataType.STRING});
        for(Object[] row : results.getResults() ) {
            result.put((String)row[0], Long.parseLong((String)row[1]));
        }
        results.close();

        return result;
    }

    //SELECT u.uuid, d.deaths FROM (SELECT user_id, COUNT(*) as deaths FROM livekit_stats_deaths GROUP BY user_id ORDER BY deaths DESC) as d LEFT JOIN livekit_users as u ON u._id=d.user_id;
    public List<PlayerValueResult<Long, Long>> getAnalyticsMostPlayerDeaths(long from, long to) throws Exception
    {
        List<PlayerValueResult<Long, Long>> result = new ArrayList<PlayerValueResult<Long, Long>>();

        Dao<LKStatSession, String> dao = getDao(LKStatSession.class);
        GenericRawResults<String[]> results = dao.queryRaw("SELECT u.uuid, d.deaths FROM (SELECT user_id, COUNT(*) as deaths FROM livekit_stats_deaths WHERE timestamp >= "+from+" AND timestamp <= "+to+" GROUP BY user_id ORDER BY deaths DESC) as d LEFT JOIN livekit_users as u ON u._id=d.user_id");
        for(String[] row : results.getResults() ) {
            result.add(new PlayerValueResult<Long, Long>(UUID.fromString(row[0]), Long.parseLong(row[1]), null));
        }
        results.close();

        return result;
    }

    //SELECT cmd, args, COUNT(user_id) as count FROM LiveKit.livekit_stats_cmds GROUP BY cmd, args ORDER BY count DESC;
    public Map<String, Long> getAnalyticsMostUsedCommands(long from, long to) throws Exception
    {
        Map<String, Long> result = new HashMap<String, Long>();

        Dao<LKStatSession, String> dao = getDao(LKStatSession.class);
        GenericRawResults<String[]> results = dao.queryRaw("SELECT cmd, args, COUNT(user_id) as count FROM livekit_stats_cmds WHERE timestamp >= "+from+" AND timestamp <= "+to+" GROUP BY cmd, args ORDER BY count DESC;");
        for(String[] row : results.getResults() ) {
            result.put(row[0] + " " + row[1], Long.parseLong(row[2]));
        }
        results.close();

        return result;
    }

    private String dateFunction(String column)
    {
        switch(sqlProvider.toUpperCase())
        {
            case "SQLITE": return "DATE("+column+"/1000, \'unixepoch\')";
            default: return "DATE(FROM_UNIXTIME("+column+"/1000))";
        }
    }

    private String unixFunction()
    {
        switch(sqlProvider.toUpperCase())
        {
            case "SQLITE": return "strftime('%s', 'now')*1000";
            case "POSTGRESQL": return "extract(epoch FROM now())*1000";
            default: return "unix_timestamp()*1000";
        }
    }

    private String replaceZeroSession(String columnName, String asName) {
        return "(CASE ("+columnName+") WHEN 0 THEN "+unixFunction()+" ELSE "+columnName+" END)"+(asName != null ?" as "+asName : "");
    }

    private static SimpleDateFormat format1 = new SimpleDateFormat("yyyy-MM-dd");
    private String dateFormat(Calendar calendar) {
        return format1.format(calendar.getTime());
    }

    private Calendar getCalendarWithoutTime() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal;
    }
}
