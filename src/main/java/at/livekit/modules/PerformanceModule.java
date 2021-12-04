package at.livekit.modules;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

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
    private static int SECONDS = 60*1 + 15;

    private BukkitTask tickTask;

    private Object backlog = new Object();
    private HashMap<Long, Long> _ramBacklog = new HashMap<Long, Long>();
    private HashMap<Long, Float> _cpuBacklog = new HashMap<Long, Float>();
    private HashMap<Long, Integer> _tickBacklog = new HashMap<Long, Integer>();

    /*private HashMap<Long, Integer> _chunksBacklog = new HashMap<Long, Integer>();
    private HashMap<Long, Integer> _entitiesBacklog = new HashMap<Long, Integer>();
    private HashMap<Long, Integer> _playersBacklog = new HashMap<Long, Integer>();*/
    
    private HashMap<Long, Long> _ram = new HashMap<Long, Long>();
    private HashMap<Long, Float> _cpu = new HashMap<Long, Float>();
    private HashMap<Long, Integer> _tick = new HashMap<Long, Integer>();

    /*private HashMap<Long, Integer> _chunks = new HashMap<Long, Integer>();
    private HashMap<Long, Integer> _entities = new HashMap<Long, Integer>();
    private HashMap<Long, Integer> _players = new HashMap<Long, Integer>();*/

    long currentTickTS = 0;
    int currentTick = 0;

    private String os = "Unknown";
    private String javaVersion = "Unknown";
    private String javaVM = "Unkown";
    private String timezone = "Unknown";
    private int coreCount = 0;

    public PerformanceModule(ModuleListener listener) {
        super(1, "Performance", "livekit.module.performance", UpdateRate.ONCE_PERSEC, listener);
    }

    private void initializeSystemProperties() {
        os = System.getProperty("os.name");
        javaVersion = System.getProperty("java.version");
        javaVM = System.getProperty("java.vm.name");
        timezone = System.getProperty("user.timezone");
        try{
            com.sun.management.OperatingSystemMXBean operatingSystemMXBean = (com.sun.management.OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean();
            coreCount = operatingSystemMXBean.getAvailableProcessors();
        }catch(Exception ex){ex.printStackTrace();}
    }

    @Override
    public void onEnable(Map<String,ActionMethod> signature) {
        super.onEnable(signature);

        initializeSystemProperties();

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
            /*_players.put(ts, Bukkit.getServer().getOnlinePlayers().size());
            
            int entities = 0;
            int chunks = 0;
            for(World world : Bukkit.getWorlds()) {
                chunks += world.getLoadedChunks().length;
                entities += world.getEntities().size();
            }
            _chunks.put(ts, chunks);
            _entities.put(ts, entities);*/
        }

        notifyChange();
    }


    @Override
    public IPacket onJoinAsync(Identity identity)
    {
        JSONObject json = new JSONObject();

        synchronized(backlog) {
            json.put("cpu", _cpuBacklog);
            json.put("ram", _ramBacklog);
            //json.put("chunks", _chunksBacklog);
            json.put("tick", _tickBacklog);
            //json.put("entities", _entitiesBacklog);
            //json.put("players", _playersBacklog);
        }


        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        runtimeMXBean.getUptime();

        json.put("ram_max", Utils.getMaxMemory());
        json.put("seconds", SECONDS);
        json.put("os", os);

        json.put("processors", coreCount);
        json.put("uptime", runtimeMXBean.getUptime());
        json.put("java.version", javaVersion);
        json.put("java.vm.name", javaVM);
        json.put("user.timezone", timezone);

        return new ModuleUpdatePacket(this, json, true);
    }

    @Override
    public Map<Identity, IPacket> onUpdateAsync(List<Identity> identities)
    {
        Map<Identity, IPacket> responses = new HashMap<Identity,IPacket>();

        JSONObject json = new JSONObject();

        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        runtimeMXBean.getUptime();
        json.put("uptime", runtimeMXBean.getUptime());

        synchronized(backlog) {
            json.put("cpu", _cpu);
            json.put("ram", _ram);
            json.put("tick", _tick);


            /*json.put("chunks", _chunks);
            json.put("entities", _entities);
            json.put("players", _players);*/
        }

        IPacket response =  new ModuleUpdatePacket(this, json, false);
        for(Identity identity : identities) responses.put(identity, response);

        long ts = System.currentTimeMillis();

        synchronized(backlog) {
            _ramBacklog.putAll(_ram);
            _cpuBacklog.putAll(_cpu);
            _tickBacklog.putAll(_tick);

            /*_chunksBacklog.putAll(_chunks);
            _entitiesBacklog.putAll(_entities);
            _playersBacklog.putAll(_players);*/

            _ramBacklog.entrySet().removeIf(e -> e.getKey() < ts - SECONDS*1000);
            _cpuBacklog.entrySet().removeIf(e -> e.getKey() < ts - SECONDS*1000);
            _tickBacklog.entrySet().removeIf(e -> e.getKey() < ts - SECONDS*1000);

            /*_chunksBacklog.entrySet().removeIf(e -> e.getKey() < ts - SECONDS*1000);
            _entitiesBacklog.entrySet().removeIf(e -> e.getKey() < ts - SECONDS*1000);
            _playersBacklog.entrySet().removeIf(e -> e.getKey() < ts - SECONDS*1000);*/

            _ram.clear();
            _cpu.clear();
            _tick.clear();

            /*_chunks.clear();
            _entities.clear();
            _players.clear();*/
        }

        return responses;
    }
}
