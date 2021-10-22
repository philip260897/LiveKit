package at.livekit.statistics.tables;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.json.JSONObject;

@DatabaseTable(tableName = "livekit_stats_sessions")
public class LKStatSession {
    
    @DatabaseField(generatedId = true)
    private int _id;

    @DatabaseField(foreign = true, uniqueCombo = true, canBeNull = false)
    public LKUser user;

    @DatabaseField(uniqueCombo = true, index = true)
    public long start = 0;

    @DatabaseField(index = true)
    public long end = 0;

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("user", user._id);
        json.put("start", start);
        json.put("end", end);
        return json;
    }

    /*public static JSONObject playerChartData(List<LKStatSession> sessions, long start, long end)
    {
        Map<Long, Integer> entries = new TreeMap<Long, Integer>();
        entries.put(start, 0);
        entries.put(end, 0);
        
        for(LKStatSession session : sessions)
        {
            if(session.start <= start) {
                entries.put(start, entries.get(start)+1);
            }else {
                if(!entries.containsKey(session.start)) {
                    entries.put(session.start, 1);
                } else {
                    entries.put(session.start, entries.get(session.start)+1);
                }
            }

            if(!entries.containsKey(session.end)) {
                entries.put(session.end, -1);
            } else {
                entries.put(session.end, entries.get(session.end)-1);
            }
        }

        Map<Long, Integer> data = new TreeMap<Long, Integer>();
        int count = 0;
        int total = 0;

        Integer lastValue = null;
        Long lastTimestamp = null;

        for(Long timestamp : entries.keySet()) {
            if(lastTimestamp != null) {
                total += (timestamp-lastTimestamp) * lastValue;
            }
            
            int val = count+entries.get(timestamp);
            data.put(timestamp, val);

            lastValue = val;
            lastTimestamp = timestamp;
        }
        
    }*/
}
