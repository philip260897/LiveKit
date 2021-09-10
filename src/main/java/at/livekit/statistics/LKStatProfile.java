package at.livekit.statistics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;

import org.bukkit.OfflinePlayer;

import at.livekit.livekit.LiveKit;
import at.livekit.plugin.Plugin;
import at.livekit.statistics.tables.LKStatCmd;
import at.livekit.statistics.tables.LKStatSession;

public class LKStatProfile {
    
    private UUID uuid;
    private OfflinePlayer player;

    private LKStatSession currentSession;
    private Map<Class<?>, List<Object>> cache = new HashMap<Class<?>, List<Object>>();

    private boolean canClanUp = false;

    public LKStatProfile(OfflinePlayer player)
    {
        this.uuid = player.getUniqueId();
        this.player = player;
    }

    public void startSession()
    {
        this.canClanUp = false;
        this.currentSession = new LKStatSession();
        this.currentSession.uuid = uuid;
        this.currentSession.timestamp_login = System.currentTimeMillis();
        this.commitToCache(currentSession);
    }

    public void endSession()
    {
        this.currentSession.timestamp_logout = System.currentTimeMillis();
        this.commitToCache(currentSession);
        this.canClanUp = true;
    }

    public void addCommandStat(String cmd, String[] args) 
    {
        String argsLine = "";
        for(String s : args) argsLine += s+" ";
        if(argsLine.length() > 0) argsLine = argsLine.substring(0, argsLine.length()-1);

        LKStatCmd entry = new LKStatCmd();
        entry.uuid = uuid;
        entry.cmd = cmd;
        entry.args = argsLine;

        commitToCache(entry);
    }

    private void commitToCache(Object object)
    {
        synchronized(cache)
        {
            List<Object> cacheList = null;
            if(cache.containsKey(object.getClass()))
            {
                cacheList = cache.get(object.getClass());
            }
            else
            {
                cacheList = new ArrayList<>();
                cache.put(object.getClass(), cacheList);
            }

            if(!cacheList.contains(object))
            {
                cacheList.add(object);
            }
        }
    }

    public void persistCacheAsync()
    {
        if(this.cache.size() == 0) return;

        Plugin.debug("[STAT] Commiting cache for "+getPlayer().getName());

        Map<Class<?>, List<Object>> local = this.cache;
        synchronized(cache) {
            this.cache = new HashMap<Class<?>, List<Object>>();
        }

        for(Entry<Class<?>, List<Object>> entry : local.entrySet())
        {
            for(Object e : entry.getValue())
            {
                Plugin.debug("[STAT] Commiting "+e.toString());
                try{
                    Plugin.getStorage().createOrUpdate(e);
                }
                catch(Exception ex)
                {
                    ex.printStackTrace();
                }
            }
        }
    }

    public OfflinePlayer getPlayer() {
        return player;
    }

    public boolean canCleanUp() {
        return canClanUp;
    }
}
