package at.livekit.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.scheduler.BukkitTask;

import at.livekit.plugin.Plugin;
import at.livekit.statistics.LKStatProfile;

public class StatisticsModule extends BaseModule implements Listener
{
    private BukkitTask storageTask;
    private List<LKStatProfile> profiles = new ArrayList<LKStatProfile>();

    public StatisticsModule(ModuleListener listener) {
        super(1, "Statistics", "livekit.module.statistics", UpdateRate.NEVER, listener);
    } 

    private LKStatProfile createProfile(OfflinePlayer player)
    {
        LKStatProfile profile = getStatisticProfile(player);
        if(profile == null) {
            profile = new LKStatProfile(player);
            synchronized(profiles) {
                profiles.add(profile);
            }
        }
        return profile;
    }

    private LKStatProfile getStatisticProfile(OfflinePlayer player)
    {
        synchronized(profiles)
        {
            for(LKStatProfile profile : profiles)
            {
                if(profile.getPlayer() == player) {
                    return profile;
                }
            }
        }
        return null;
    }

    private void commitCacheToStorageAsync() 
    {
        LKStatProfile[] profiles = null;
        synchronized(this.profiles) {
            profiles = this.profiles.toArray(new LKStatProfile[this.profiles.size()]);
        }

        for(LKStatProfile profile : profiles)
        {
            profile.save();
        }

        for(LKStatProfile profile : profiles)
        {
            if(profile.canCleanUp()) {
                LKStatProfile current = getStatisticProfile(profile.getPlayer());
                if(current.canCleanUp())
                {
                    synchronized(this.profiles)
                    {
                        Plugin.debug("[STAT] Deleted Profile for "+profile.getPlayer().getName());
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
            LKStatProfile profile = createProfile(player);
            profile.startSession();
        }

        Bukkit.getServer().getPluginManager().registerEvents(this, Plugin.getInstance());

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

        commitCacheToStorageAsync();

        super.onDisable(signature);
    }


    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event)
    {
        if(!isEnabled()) return;

        LKStatProfile profile = createProfile(event.getPlayer());
        profile.startSession();
    }

    @EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) 
	{
        if(!isEnabled()) return;

        LKStatProfile profile = getStatisticProfile(event.getPlayer());
        if(profile != null)
        {
            profile.endSession();
        }
    }

    @EventHandler
    public void onPlayerBlockBreak(BlockBreakEvent event)
    {
        if(!isEnabled()) return;

        LKStatProfile profile = getStatisticProfile(event.getPlayer());
        if(profile != null)
        {
            profile.addBlockBreakStat(event.getBlock());
        }
    }

    @EventHandler
    public void onPlayerBlockPlace(BlockPlaceEvent event)
    {
        if(!isEnabled()) return;

        LKStatProfile profile = getStatisticProfile(event.getPlayer());
        if(profile != null)
        {
            profile.addBlockBuildStat(event.getBlock());
        }
    }
}
