package at.livekit.statistics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;

import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;

import at.livekit.livekit.LiveKit;
import at.livekit.plugin.Plugin;
import at.livekit.plugin.Texturepack;
import at.livekit.statistics.tables.LKStatCmd;
import at.livekit.statistics.tables.LKStatEntry;
import at.livekit.statistics.tables.LKStatSession;
import at.livekit.statistics.tables.LKStatWorld;
import at.livekit.statistics.tables.LKUser;
import at.livekit.storage.IStorageAdapterGeneric;
import at.livekit.utils.Utils;

public class LKStatProfile {
    
    private LKUser user;

    private UUID uuid;
    private OfflinePlayer player;

    private LKStatSession currentSession;
    private LKStatWorld currentWorld;
    
    private List<LKStatSession> sessionList = new ArrayList<LKStatSession>();
    private List<LKStatWorld> worldList = new ArrayList<LKStatWorld>();
    private List<LKStatCmd> commandsList = new ArrayList<LKStatCmd>();
    private List<LKStatEntry> entriesList = new ArrayList<LKStatEntry>();
    //private List<LKStatTotalEntry> totalEntriesList = new ArrayList<LKStatTotalEntry>();
    
    private boolean initialized = false;
    private boolean canClanUp = false;

    public LKStatProfile(OfflinePlayer player)
    {
        this.uuid = player.getUniqueId();
        this.player = player;

        this.user = new LKUser();
        this.user._id = 1;
    }

    public void startSession()
    {
        this.canClanUp = false;
        this.currentSession = new LKStatSession();
        this.currentSession.uuid = uuid;
        this.currentSession.start = System.currentTimeMillis();
        
        synchronized(sessionList) {
            if(!sessionList.contains(currentSession)) {
                sessionList.add(currentSession);
            }
        }
    }

    public void endSession()
    {
        this.currentSession.end = System.currentTimeMillis();
        synchronized(sessionList) {
            if(!sessionList.contains(currentSession)) {
                sessionList.add(currentSession);
            }
        }
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

        synchronized(commandsList) {
            commandsList.add(entry);
        }
    }

    public void addBlockBuildStat(Block block)
    {
        try{
            addBlockStat(Texturepack.getInstance().getTexture(block.getType()), System.currentTimeMillis(), LKStatEntry.ACTION_PLACE);
        }catch(Exception ex){
            ex.printStackTrace();
        }
    }

    public void addBlockBreakStat(Block block)
    {
        try{
            addBlockStat(Texturepack.getInstance().getTexture(block.getType()), System.currentTimeMillis(), LKStatEntry.ACTION_BREAK);
        }catch(Exception ex){
            ex.printStackTrace();
        }
    }

    private void addBlockStat(int blockId, long timestamp, byte type)
    {
        LKStatEntry entry = new LKStatEntry();
        entry.uuid = uuid;
        entry.action = type;
        entry.blockid = blockId;
        entry.count = 1;
        entry.timestamp = ((((timestamp/1000)/60)/60)/24) * 1000 * 60 * 60 * 24 + Utils.getTimezoneOffset();

        synchronized(entriesList)
        {
            entriesList.add(entry);
        }
    }

    public void save()
    {
        IStorageAdapterGeneric storage = Plugin.getStorage();

        //copy sessions to this thread, clear cached entries
        List<LKStatSession> sessions;
        synchronized(sessionList) {
            sessions = new ArrayList<LKStatSession>(sessionList);
            sessionList.clear();
        }

        for(LKStatSession session : sessions)
        {
            try{
                LKStatSession existing = storage.loadSingle(LKStatSession.class, new String[]{"uuid", "start"}, new Object[]{session.uuid, session.start});
                if(existing != null) {
                    existing.end = session.end;
                    storage.update(existing);
                } else {
                    storage.create(session);
                }
            }catch(Exception ex){ex.printStackTrace();}
        }

        //copy sessions to this thread, clear cached entries
        List<LKStatEntry> entries;
        synchronized(entriesList) {
            entries = new ArrayList<LKStatEntry>(entriesList);
            entriesList.clear();
        }

        for(LKStatEntry entry : entries)
        {
            try {
                LKStatEntry existing = storage.loadSingle(LKStatEntry.class, new String[]{"uuid", "timestamp", "blockid", "action"}, new Object[]{entry.uuid, entry.timestamp, entry.blockid, entry.action});
                if(existing != null) {
                    existing.count += entry.count;
                    storage.update(existing);
                } else {
                    storage.create(entry);
                }
            }catch(Exception ex){ex.printStackTrace();}
        }
    }

    /*private void commitToCache(Object object)
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
    }*/

    public OfflinePlayer getPlayer() {
        return player;
    }

    public boolean canCleanUp() {
        return canClanUp;
    }
}
