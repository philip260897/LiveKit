package at.livekit.statistics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;

import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;

import at.livekit.livekit.LiveKit;
import at.livekit.plugin.Plugin;
import at.livekit.plugin.Texturepack;
import at.livekit.statistics.tables.LKStatCmd;
import at.livekit.statistics.tables.LKStatEntry;
import at.livekit.statistics.tables.LKStatPVE;
import at.livekit.statistics.tables.LKStatPVP;
import at.livekit.statistics.tables.LKStatSession;
import at.livekit.statistics.tables.LKStatWorld;
import at.livekit.statistics.tables.LKUser;
import at.livekit.storage.IStorageAdapterGeneric;
import at.livekit.utils.Utils;
import dev.vankka.mcdiscordreserializer.text.Text;

public class LKStatProfile {
    
    private LKUser user;

    private UUID uuid;
    //private OfflinePlayer player;

    private LKStatSession currentSession;
    private LKStatWorld currentWorld;
    
    private List<LKStatSession> sessionList = new ArrayList<LKStatSession>();
    private List<LKStatWorld> worldList = new ArrayList<LKStatWorld>();
    private List<LKStatCmd> commandsList = new ArrayList<LKStatCmd>();
    private List<LKStatEntry> entriesList = new ArrayList<LKStatEntry>();

    private List<LKStatPVP> pvpList = new ArrayList<LKStatPVP>();
    private List<LKStatPVE> pveList = new ArrayList<LKStatPVE>();
    //private List<LKStatTotalEntry> totalEntriesList = new ArrayList<LKStatTotalEntry>();
    
    //private boolean initialized = false;
    private boolean canClanUp = false;

    public LKStatProfile(UUID uuid)
    {
        this.uuid = uuid;
        this.user = new LKUser();
        this.user._id = 1;
    }

    public void loadUserAsync() throws Exception
    {
        user = Plugin.getStorage().loadSingle(LKUser.class, "uuid", uuid);
        if(user == null)
        {
            user = new LKUser();
            user.uuid = uuid;
            user.first = System.currentTimeMillis();
            user.livekit = false;
            Plugin.getStorage().create(user);
        }
        user.last = System.currentTimeMillis();
        Plugin.getStorage().update(user);
    }

    public void startSession()
    {
        this.canClanUp = false;
        this.currentSession = new LKStatSession();
        this.currentSession.user = user;
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

        if(currentWorld != null)
        {
            currentWorld.leave = System.currentTimeMillis();
            synchronized(worldList) {
                worldList.add(currentWorld);
            }
        }
        this.canClanUp = true;
    }

    public void enterWorld(String world) 
    {
        if(currentWorld != null) {
            currentWorld.leave = System.currentTimeMillis();
            synchronized(worldList){
                worldList.add(currentWorld);
            }
        }
        currentWorld = new LKStatWorld();
        currentWorld.user = user;
        currentWorld.enter = System.currentTimeMillis();
        currentWorld.world = world;

        synchronized(worldList) {
            worldList.add(currentWorld);
        }
    }

    public void addCommandStat(String cmd, String args) 
    {
        LKStatCmd entry = new LKStatCmd();
        entry.user = user;
        entry.cmd = cmd;
        entry.args = args;
        entry.timestamp = System.currentTimeMillis();

        synchronized(commandsList) {
            commandsList.add(entry);
        }
    }

    public void addPVP(LKUser target) {
        LKStatPVP pvp = new LKStatPVP();
        pvp.user = user;
        pvp.timestamp = System.currentTimeMillis();
        pvp.target = target;

        synchronized(pvpList) {
            pvpList.add(pvp);
        }
    }

    public void addPVE(EntityType type) {
        try{
            LKStatPVE pvp = new LKStatPVE();
            pvp.user = user;
            pvp.timestamp = Utils.getRoundedDayTimestamp();
            pvp.type = Texturepack.getInstance().getEntity(type);
            pvp.count = 1;

            synchronized(pveList) {
                pveList.add(pvp);
            }
        }catch(Exception ex){ex.printStackTrace();}
    }

    public void addBlockBuildStat(Block block)
    {
        try{
            addBlockStat(Texturepack.getInstance().getTexture(block.getType()), LKStatEntry.ACTION_PLACE);
        }catch(Exception ex){
            ex.printStackTrace();
        }
    }

    public void addBlockBreakStat(Block block)
    {
        try{
            addBlockStat(Texturepack.getInstance().getTexture(block.getType()), LKStatEntry.ACTION_BREAK);
        }catch(Exception ex){
            ex.printStackTrace();
        }
    }

    private void addBlockStat(int blockId, byte type)
    {
        LKStatEntry entry = new LKStatEntry();
        entry.user = user;
        entry.action = type;
        entry.blockid = blockId;
        entry.count = 1;
        entry.timestamp = Utils.getRoundedDayTimestamp();

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
                LKStatSession existing = storage.loadSingle(LKStatSession.class, new String[]{"user_id", "start"}, new Object[]{session.user, session.start});
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
                LKStatEntry existing = storage.loadSingle(LKStatEntry.class, new String[]{"user_id", "timestamp", "blockid", "action"}, new Object[]{entry.user, entry.timestamp, entry.blockid, entry.action});
                if(existing != null) {
                    existing.count += entry.count;
                    storage.update(existing);
                } else {
                    storage.create(entry);
                }
            }catch(Exception ex){ex.printStackTrace();}
        }

        //copy data for new thread
        List<LKStatCmd> commands;
        synchronized(commandsList) {
            commands = new ArrayList<LKStatCmd>(commandsList);
            commandsList.clear();
        }

        for(LKStatCmd entry : commands)
        {
            try {
                storage.create(entry);
            }catch(Exception ex){ex.printStackTrace();}
        }

        //copy data for new thread
        List<LKStatWorld> worlds;
        synchronized(worldList) {
            worlds = new ArrayList<LKStatWorld>(worldList);
            worldList.clear();
        }
        
        for(LKStatWorld entry : worlds)
        {
            try {
                storage.createOrUpdate(entry);
            }catch(Exception ex){ex.printStackTrace();}
        }

        //copy data for new thread
        List<LKStatPVP> pvp;
        synchronized(pvpList) {
            pvp = new ArrayList<LKStatPVP>(pvpList);
            pvpList.clear();
        }
        
        for(LKStatPVP entry : pvp)
        {
            try {
                storage.create(entry);
            }catch(Exception ex){ex.printStackTrace();}
        }

        //copy sessions to this thread, clear cached entries
        List<LKStatPVE> pve;
        synchronized(pveList) {
            pve = new ArrayList<LKStatPVE>(pveList);
            pveList.clear();
        }

        for(LKStatPVE entry : pve)
        {
            try {
                LKStatPVE existing = storage.loadSingle(LKStatPVE.class, new String[]{"user_id", "timestamp", "type"}, new Object[]{entry.user, entry.timestamp, entry.type});
                if(existing != null) {
                    existing.count += entry.count;
                    storage.update(existing);
                } else {
                    storage.create(entry);
                }
            }catch(Exception ex){ex.printStackTrace();}
        }
    }

    public UUID getUUID() {
        return uuid;
    }

    public LKUser getUser() {
        return user;
    }

    public boolean canCleanUp() {
        return canClanUp;
    }
}
