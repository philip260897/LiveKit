package at.livekit.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
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
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.scheduler.BukkitTask;

import at.livekit.plugin.Plugin;
import at.livekit.statistics.LKStatProfile;
import at.livekit.statistics.tables.LKStatServerSession;
import at.livekit.storage.StorageThreadMarshallAdapter;
import at.livekit.utils.Utils;

public class StatisticsModule extends BaseModule implements Listener
{
    private BukkitTask storageTask;
    private boolean saveServerSession = false;
    private LKStatServerSession serverSession;
    private List<LKStatProfile> profiles = new ArrayList<LKStatProfile>();

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
                if(profile.getUUID() == uuid) {
                    return profile;
                }
            }
        }
        return null;
    }

    private void commitCacheToStorageAsync() 
    {
        Utils.performance();
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
                Plugin.debug("[STAT] Storage task awaking");
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
        if(!isEnabled()) return;

        LKStatProfile profile = getStatisticProfile(event.getPlayer().getUniqueId());
        if(profile != null)
        {
            profile.addBlockBreakStat(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerBlockPlace(BlockPlaceEvent event)
    {
        if(!isEnabled()) return;

        LKStatProfile profile = getStatisticProfile(event.getPlayer().getUniqueId());
        if(profile != null)
        {
            profile.addBlockBuildStat(event.getBlock());
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
                if(event.getEntity() instanceof Player) {
                    profile.addPVP(getStatisticProfile(event.getEntity().getUniqueId()).getUser());
                } else {
                    profile.addPVE(event.getEntityType());
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
