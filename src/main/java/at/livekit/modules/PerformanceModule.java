package at.livekit.modules;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;
import org.json.JSONObject;

import at.livekit.livekit.Identity;
import at.livekit.packets.IPacket;
import at.livekit.plugin.Plugin;
import at.livekit.utils.Utils;

public class PerformanceModule extends BaseModule
{
    private static int SECONDS = 60*5;

    private BukkitTask tickTask;

    private Object backlog = new Object();
    private HashMap<Long, Long> _ramBacklog = new HashMap<Long, Long>();
    private HashMap<Long, Float> _cpuBacklog = new HashMap<Long, Float>();
    private HashMap<Long, Integer> _chunksBacklog = new HashMap<Long, Integer>();
    private HashMap<Long, Integer> _tickBacklog = new HashMap<Long, Integer>();
    
    private HashMap<Long, Long> _ram = new HashMap<Long, Long>();
    private HashMap<Long, Float> _cpu = new HashMap<Long, Float>();
    private HashMap<Long, Integer> _chunks = new HashMap<Long, Integer>();
    private HashMap<Long, Integer> _tick = new HashMap<Long, Integer>();

    long currentTickTS = 0;
    int currentTick = 0;

    public PerformanceModule(ModuleListener listener) {
        super(1, "Performance", "livekit.module.map", UpdateRate.ONCE_PERSEC, listener);
    }

    @Override
    public void onEnable(Map<String,ActionMethod> signature) {
        super.onEnable(signature);

        tickTask = Bukkit.getScheduler().runTaskTimerAsynchronously(Plugin.getInstance(), new Runnable(){
            @Override
            public void run() {
               
                long secondTimestamp = ((long)(System.currentTimeMillis() / 1000))*1000;
                if(secondTimestamp != currentTickTS ) {
                    if(currentTickTS != 0) {
                        synchronized(backlog) {
                            _tick.put(currentTickTS, currentTick);
                        }
                    }
                    currentTick = 0;
                    currentTickTS = secondTimestamp;
                }
                currentTick++;

            }
        }, 0, 1);
    }

    @Override
    public void onDisable(Map<String,ActionMethod> signature) {
        super.onDisable(signature);

        tickTask.cancel();
    }

    @Override
    public void update()
    {
        super.update();

        long ts = System.currentTimeMillis();

        synchronized(backlog) {
            _ram.put(ts, Utils.getMemoryUsage());
            _cpu.put(ts, Utils.getCPUUsage());
        
            int chunks = 0;
            for(World world : Bukkit.getWorlds()) {
                chunks += world.getLoadedChunks().length;
            }
            _chunks.put(ts, chunks);
        }

        //Plugin.debug("Performance: ram="+(_ramBacklog.size()+_ram.size()) + " cpu=" + (_cpuBacklog.size()+_cpu.size())+ " chunks=" + (_chunksBacklog.size()+_chunks.size())+ " ticks=" + (_tickBacklog.size()+_tick.size())+" took: "+(System.currentTimeMillis()-ts)+"ms");

        notifyChange();
    }


    @Override
    public IPacket onJoinAsync(Identity identity)
    {
        JSONObject json = new JSONObject();
        //JSONArray messages = new JSONArray();

        synchronized(backlog) {
            json.put("cpu", _cpuBacklog);
            json.put("ram", _ramBacklog);
            json.put("chunks", _chunksBacklog);
            json.put("tick", _tickBacklog);
        }

        json.put("ram_max", Utils.getMaxMemory());
        json.put("seconds", SECONDS);
        //json.put("write", identity.hasPermission("livekit.chat.write"));
        //json.put("offline", identity.hasPermission("livekit.chat.write_offline"));

        return new ModuleUpdatePacket(this, json, true);
    }

    @Override
    public Map<Identity, IPacket> onUpdateAsync(List<Identity> identities)
    {
        Map<Identity, IPacket> responses = new HashMap<Identity,IPacket>();

        JSONObject json = new JSONObject();

        synchronized(backlog) {
            json.put("cpu", _cpu);
            json.put("ram", _ram);
            json.put("chunks", _chunks);
            json.put("tick", _tick);
        }

        IPacket response =  new ModuleUpdatePacket(this, json, false);
        for(Identity identity : identities) responses.put(identity, response);

        long ts = System.currentTimeMillis();

        synchronized(backlog) {
            _ramBacklog.putAll(_ram);
            _cpuBacklog.putAll(_cpu);
            _chunksBacklog.putAll(_chunks);
            _tickBacklog.putAll(_tick);

            _ramBacklog.entrySet().removeIf(e -> e.getKey() < ts - SECONDS*1000);
            _cpuBacklog.entrySet().removeIf(e -> e.getKey() < ts - SECONDS*1000);
            _chunksBacklog.entrySet().removeIf(e -> e.getKey() < ts - SECONDS*1000);
            _tickBacklog.entrySet().removeIf(e -> e.getKey() < ts - SECONDS*1000);

            _ram.clear();
            _cpu.clear();
            _chunks.clear();
            _tick.clear();
        }

        return responses;
    }
}
