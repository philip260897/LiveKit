package at.livekit.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.scheduler.BukkitTask;

import at.livekit.livekit.Identity;
import at.livekit.packets.ActionPacket;
import at.livekit.packets.IPacket;
import at.livekit.packets.StatusPacket;
import at.livekit.plugin.Plugin;
import at.livekit.plugin.Texturepack;
import at.livekit.statistics.LKStatProfile;
import at.livekit.statistics.results.ProfileResult;
import at.livekit.statistics.tables.LKStatServerSession;
import at.livekit.statistics.tables.LKUser;
import at.livekit.storage.SQLStorage;
import at.livekit.storage.StorageThreadMarshallAdapter;

import org.json.JSONObject;
import org.json.JSONArray;

public class StatisticsModule extends BaseModule implements Listener
{
    private BukkitTask storageTask;
    private boolean saveServerSession = false;
    private LKStatServerSession serverSession;
    private List<LKStatProfile> profiles = new ArrayList<LKStatProfile>();
    private Texturepack texturepack;

    public StatisticsModule(ModuleListener listener) {
        super(1, "Statistics", "livekit.module.statistics", UpdateRate.NEVER, listener);
    } 

    private LKStatProfile createProfile(UUID uuid)
    {
        LKStatProfile profile = getStatisticProfile(uuid);
        if(profile == null) {
            profile = new LKStatProfile(uuid);
            synchronized(profiles) {
                profiles.add(profile);
            }
        }
        return profile;
    }

    private LKStatProfile getStatisticProfile(UUID uuid)
    {
        synchronized(profiles)
        {
            for(LKStatProfile profile : profiles)
            {
                System.out.print(profile.getUUID() + " - " + uuid);
                if(profile.getUUID().equals(uuid)) {
                    return profile;
                }
            }
        }
        return null;
    }

    private void commitCacheToStorageAsync() 
    {
        //Utils.performance();
        LKStatProfile[] profiles = null;
        synchronized(this.profiles) {
            profiles = this.profiles.toArray(new LKStatProfile[this.profiles.size()]);
        }

        for(LKStatProfile profile : profiles)
        {
            profile.save();
        }

        if(saveServerSession)
        {
            try{
                Plugin.getStorage().createOrUpdate(serverSession);
            }catch(Exception ex){ex.printStackTrace();}
            saveServerSession = false;
        }

        for(LKStatProfile profile : profiles)
        {
            if(profile.canCleanUp()) {
                LKStatProfile current = getStatisticProfile(profile.getUUID());
                if(current.canCleanUp())
                {
                    synchronized(this.profiles)
                    {
                        Plugin.debug("[STAT] Deleted Profile for "+profile.getUUID());
                        this.profiles.remove(current);
                    }
                }
            }
        }
    }

    @Override
    public void onEnable(Map<String,ActionMethod> signature) {
        super.onEnable(signature);

        Plugin.debug("[STAT] ENABLED");
        try{
            texturepack = Texturepack.getInstance();
        }catch(Exception ex){ex.printStackTrace();}

        //create statistic profiles for each online players after a reload!
        for(Player player : Bukkit.getServer().getOnlinePlayers())
        {
            Plugin.debug("[STAT] ONLINE PLAYERS "+player.getName());
            LKStatProfile profile = createProfile(player.getUniqueId());
            profile.startSession();
            profile.enterWorld(player.getLocation().getWorld().getName());
        }

        Bukkit.getServer().getPluginManager().registerEvents(this, Plugin.getInstance());

        serverSession = new LKStatServerSession();
        serverSession.start = System.currentTimeMillis();
        saveServerSession = true;

        storageTask = Bukkit.getScheduler().runTaskTimerAsynchronously(Plugin.getInstance(), new Runnable(){
            @Override
            public void run() {
                //Plugin.debug("[STAT] Storage task awaking");
                commitCacheToStorageAsync();
            }
        }, 20*5, 5*20);
    }

    @Override
    public void onDisable(Map<String,ActionMethod> signature) {
        if(storageTask != null && !storageTask.isCancelled())
        {
            storageTask.cancel();
        }

        synchronized(profiles) {
            for(LKStatProfile profile : profiles)
            {
                profile.endSession();
            }
        }

        serverSession.end = System.currentTimeMillis();
        saveServerSession = true;

        StorageThreadMarshallAdapter.DISABLE = true;
        commitCacheToStorageAsync();
        StorageThreadMarshallAdapter.DISABLE = false;

        super.onDisable(signature);
    }

    @Action(name="PlayerProfile", sync = false)
    protected IPacket getPlayerProfile(Identity identity, ActionPacket packet) throws Exception
    {
        if(identity.isAnonymous()) return new StatusPacket(0, "Permission denied!");
        String playerUid = packet.getData().getString("uuid");
        Plugin.debug("PlayerUid: "+playerUid+"; parsed: "+UUID.fromString(playerUid));
        //TODO: Permission handling/check if player is friends if not self uuid
        
        SQLStorage storage = getSQLStorage();
        //LKStatProfile profile = getStatisticProfile(UUID.fromString(playerUid));
        LKUser user = storage.getLKUser(UUID.fromString(playerUid));

        ProfileResult pr = storage.getPlayerProfile(user);

        Bukkit.getScheduler().callSyncMethod(Plugin.getInstance(), new Callable<Void>(){
            @Override
            public Void call() throws Exception {
                OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(playerUid));
                pr.setLastSeen(op.getLastPlayed());
                pr.setFirstSeen(op.getFirstPlayed());
                return null;
            }}
        ).get();
        
        JSONObject data = new JSONObject();
        data.put("result", pr.toJson());
                    
        return new StatusPacket(1, data);
    }


    @Action(name="ListAllSessions", sync = false)
    protected IPacket listAllSessions(Identity identity, ActionPacket packet) throws Exception
    {
        //TODO: Permission handling
        SQLStorage storage = getSQLStorage();

        long from = packet.getData().getLong("from");
        long to = packet.getData().getLong("to");

        JSONObject data = new JSONObject();
        JSONArray result = new JSONArray(storage.getSessionsFromTo(from, to).stream().map(item->item.toJson()).collect(Collectors.toList()));
        data.put("result", result);
            
        return new StatusPacket(1, data);
    }

    @Action(name="ListAllServerSessions", sync = false)
    protected IPacket listAllServerSessions(Identity identity, ActionPacket packet) throws Exception
    {
        //TODO: Permission handling
        SQLStorage storage = getSQLStorage();

        long from = packet.getData().getLong("from");
        long to = packet.getData().getLong("to");

        JSONObject data = new JSONObject();
        JSONArray result = new JSONArray(storage.getServerSessionFromTo(from, to));
        data.put("result", result);
            
        return packet.response(data);
    }

    private SQLStorage getSQLStorage() throws Exception {
        if(Plugin.getStorage() instanceof SQLStorage) {
            return (SQLStorage) Plugin.getStorage();
        }
        throw new Exception("Storage adapter not supported! Make sure to use SQL storage for anlaytics!");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event)
    {
        if(event.getLoginResult() == Result.ALLOWED)
        {
            LKStatProfile profile = createProfile(event.getUniqueId());
            if(profile != null) {
                try{
                    profile.loadUserAsync();
                }catch(Exception ex){ex.printStackTrace();}
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event)
    {
        if(!isEnabled()) return;

        LKStatProfile profile = createProfile(event.getPlayer().getUniqueId());
        if(profile != null) {
            profile.startSession();
            profile.enterWorld(event.getPlayer().getLocation().getWorld().getName());
        }
        
    }

    @EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerQuit(PlayerQuitEvent event) 
	{
        if(!isEnabled()) return;

        LKStatProfile profile = getStatisticProfile(event.getPlayer().getUniqueId());
        if(profile != null)
        {
            profile.endSession();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerBlockBreak(BlockBreakEvent event)
    {
        if(!isEnabled() || event.isCancelled()) return;

        LKStatProfile profile = getStatisticProfile(event.getPlayer().getUniqueId());
        if(profile != null)
        {
            profile.addBlockBreakStat(event.getBlock());

            Material tool = event.getPlayer().getInventory().getItemInMainHand().getType();
            if(texturepack.isTool(tool)) {
                profile.addToolStat(texturepack.getTexture(tool));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerBlockPlace(BlockPlaceEvent event)
    {
        if(!isEnabled() || event.isCancelled()) return;

        LKStatProfile profile = getStatisticProfile(event.getPlayer().getUniqueId());
        if(profile != null)
        {
            profile.addBlockBuildStat(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerFishEvent(PlayerFishEvent event) {
        if(!isEnabled() || event.isCancelled() || event.getCaught() == null) return;

        LKStatProfile profile = getStatisticProfile(event.getPlayer().getUniqueId());
        if(profile != null)
        {
            profile.addFishingStat();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event)
    {
        if(!isEnabled()) return;

        LKStatProfile profile = getStatisticProfile(event.getPlayer().getUniqueId());
        if(profile != null)
        {
            profile.enterWorld(event.getPlayer().getWorld().getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeathEvent(EntityDeathEvent event)
    {
        if(!isEnabled()) return;

        if(event.getEntity().getKiller() != null)
        {

            LKStatProfile profile = getStatisticProfile(event.getEntity().getKiller().getUniqueId());
            if(profile != null)
            {
                Material weapon = event.getEntity().getKiller().getInventory().getItemInMainHand().getType();

                if(event.getEntity() instanceof Player) {
                    profile.addPVP(getStatisticProfile(event.getEntity().getUniqueId()).getUser());
                } else {
                    profile.addPVE(texturepack.getEntity(event.getEntityType()));
                }

                if(texturepack.isWeapon(weapon)) {
                    profile.addWeaponStat(texturepack.getTexture(weapon));
                }
            }
        }

        if(event.getEntity() instanceof Player) {
            LKStatProfile profile = getStatisticProfile(event.getEntity().getUniqueId());
            if(profile != null) {
                profile.addDeath();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerCommandPreprocessEvent(PlayerCommandPreprocessEvent event)
    {
        if(!isEnabled() || event.getMessage().length() < 2) return;

        LKStatProfile profile = getStatisticProfile(event.getPlayer().getUniqueId());
        if(profile != null)
        {
            String[] args = event.getMessage().split(" ");
            String sargs = "";
            if(args.length > 0) {
                for(int i = 1; i < args.length; i++) {
                    if(i != 1) sargs += " ";
                    sargs += args[i];
                }
            }

            profile.addCommandStat(args[0].replace("/", ""), sargs);
        }
    }
}
