package at.livekit.statistics;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.block.Block;

import at.livekit.plugin.Plugin;
import at.livekit.plugin.Texturepack;
import at.livekit.statistics.tables.LKStatCmd;
import at.livekit.statistics.tables.LKStatDeath;
import at.livekit.statistics.tables.LKStatParameter;
import at.livekit.statistics.tables.LKStatPVP;
import at.livekit.statistics.tables.LKStatSession;
import at.livekit.statistics.tables.LKStatWorld;
import at.livekit.statistics.tables.LKUser;
import at.livekit.statistics.tables.LKStatParameter.LKParam;
import at.livekit.storage.IStorageAdapterGeneric;
import at.livekit.utils.Utils;

public class LKStatProfile {
    
    private LKUser user;
    private UUID uuid;

    private LKStatSession currentSession;
    private LKStatWorld currentWorld;
    
    private List<LKStatSession> sessionList = new ArrayList<LKStatSession>();
    private List<LKStatWorld> worldList = new ArrayList<LKStatWorld>();
    private List<LKStatCmd> commandsList = new ArrayList<LKStatCmd>();
    private List<LKStatParameter> entriesList = new ArrayList<LKStatParameter>();

    private List<LKStatPVP> pvpList = new ArrayList<LKStatPVP>();
    private List<LKStatDeath> deathList = new ArrayList<LKStatDeath>();
    

    private boolean canClanUp = false;

    public LKStatProfile(UUID uuid)
    {
        this.uuid = uuid;
    }

    public void loadUserAsync() throws Exception
    {
        canClanUp = false;
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
        //whitelist enabled -> join not permitted -> profile loaded but session not started -> onDisable session is null
        if(this.currentSession != null) 
        {
            this.currentSession.end = System.currentTimeMillis();
            synchronized(sessionList) {
                if(!sessionList.contains(currentSession)) {
                    sessionList.add(currentSession);
                }
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

    public void addPVP(LKUser target, int weapon) {
        LKStatPVP pvp = new LKStatPVP();
        pvp.user = user;
        pvp.timestamp = System.currentTimeMillis();
        pvp.target = target;
        pvp.weapon = weapon;

        synchronized(pvpList) {
            pvpList.add(pvp);
        }
    }

    public void addDeath(int cause) {
        LKStatDeath death = new LKStatDeath();
        death.user = user;
        death.timestamp = System.currentTimeMillis();
        death.cause = cause;

        synchronized(deathList) {
            deathList.add(death);
        }
    }

    public void addEntityKillStat(int entity)
    {
        try {
            incrementParamStat(LKParam.ENTITY_KILLS, entity);
        }catch(Exception ex) {
            ex.printStackTrace();
        }
    }

    public void addBlockBuildStat(Block block)
    {
        try{
            addBlockStat(Texturepack.getInstance().getTexture(block.getType()), LKParam.BLOCK_PLACE);
        }catch(Exception ex){
            ex.printStackTrace();
        }
    }

    public void addBlockBreakStat(Block block)
    {
        try{
            addBlockStat(Texturepack.getInstance().getTexture(block.getType()), LKParam.BLOCK_BREAK);
        }catch(Exception ex){
            ex.printStackTrace();
        }
    }

    private void addBlockStat(int blockId, LKParam param)
    {
        incrementParamStat(param, blockId);
    }

    public void addToolStat(int toolId)
    {
        incrementParamStat(LKParam.TOOL_USE, toolId);
    }

    public void addWeaponStat(int weaponId) {
        incrementParamStat(LKParam.WEAPON_KILL, weaponId);
    }

    public void addFishingStat() {
        incrementParamStat(LKParam.FISHING, 0);
    }

    private void incrementParamStat(LKParam param, int type) {
        addParamStat(param, type, 1, true);
    }

    private void addParamStat(LKParam param, int type, int value, boolean incrementBy)
    {
        synchronized(entriesList)
        {
            LKStatParameter entry = null;
            long day = Utils.getRoundedDayTimestamp();

            for(LKStatParameter e : entriesList)
            {
                if(e.type == type && e.param == param && e.timestamp == day) {
                    entry = e;
                    break;
                }
            }

            if(entry == null) {
                entry = new LKStatParameter();
                entry.user = user;
                entry.param = param;
                entry.type = type;
                entry.timestamp = day;
                entry.value = 0;
                entriesList.add(entry);
            }

            if(incrementBy) entry.value += value;
            else entry.value = value;
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
        List<LKStatParameter> entries;
        synchronized(entriesList) {
            entries = new ArrayList<LKStatParameter>(entriesList);
            entriesList.clear();
        }

        for(LKStatParameter entry : entries)
        {
            try {
                LKStatParameter existing = storage.loadSingle(LKStatParameter.class, new String[]{"user_id", "timestamp", "type", "param"}, new Object[]{entry.user, entry.timestamp, entry.type, entry.param});
                if(existing != null) {
                    existing.value += entry.value;
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

        //copy deaths stat for local stuffy
        List<LKStatDeath> death;
        synchronized(deathList) {
            death = new ArrayList<LKStatDeath>(deathList);
            deathList.clear();
        }

        for(LKStatDeath entry : death)
        {
            try {
                storage.create(entry);
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

    public void invalidateCleanUp() {
        canClanUp = false;
    }
}
