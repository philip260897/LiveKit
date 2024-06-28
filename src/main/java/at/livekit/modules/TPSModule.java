package at.livekit.modules;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.json.JSONArray;
import org.json.JSONObject;

import at.livekit.livekit.Identity;
import at.livekit.packets.IPacket;
import at.livekit.timings.TimedCommandExecutor;
import at.livekit.timings.TimedRegisteredListener;
import at.livekit.utils.Utils;

public class TPSModule extends BaseModule{
    private static int SECONDS = 60*1 + 15;

    private BukkitTask tickTask;

    private Object backlog = new Object();
    private HashMap<Long, Long> _ramBacklog = new HashMap<Long, Long>();
    private HashMap<Long, Float> _cpuBacklog = new HashMap<Long, Float>();
    private HashMap<Long, Integer> _tickBacklog = new HashMap<Long, Integer>();
    private HashMap<Long, Map<String, Long>> _pluginBacklog = new HashMap<Long, Map<String, Long>>();
    private HashMap<Long, Map<String, Map<String, Long>>> _commandBacklog = new HashMap<Long, Map<String, Map<String, Long>>>();
    private HashMap<Long, Integer> _playersBacklog = new HashMap<Long, Integer>();
    private HashMap<Long, Integer> _entitiesBacklog = new HashMap<Long, Integer>();
    private HashMap<Long, Integer> _chunksBacklog = new HashMap<Long, Integer>();
    
    private HashMap<Long, Long> _ram = new HashMap<Long, Long>();
    private HashMap<Long, Float> _cpu = new HashMap<Long, Float>();
    private HashMap<Long, Integer> _tick = new HashMap<Long, Integer>();
    private HashMap<Long, Map<String, Long>> _plugin = new HashMap<Long, Map<String, Long>>();
    private HashMap<Long, Map<String, Map<String, Long>>> _command = new HashMap<Long, Map<String, Map<String, Long>>>();
    private HashMap<Long, Integer> _players = new HashMap<Long, Integer>();
    private HashMap<Long, Integer> _entities = new HashMap<Long, Integer>();
    private HashMap<Long, Integer> _chunks = new HashMap<Long, Integer>();

    private Plugin[] plugins;

    long currentTickTS = 0;
    int currentTick = 0;

    private String os = "Unknown";
    private String javaVersion = "Unknown";
    private String javaVM = "Unkown";
    private String timezone = "Unknown";
    private int coreCount = 0;

    public TPSModule(ModuleListener listener) {
        super(1, "TPSModule", "livekit.module.tps", UpdateRate.ONCE_PERSEC, listener, "default", null);
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

        tickTask = Bukkit.getScheduler().runTaskTimerAsynchronously(at.livekit.plugin.Plugin.getInstance(), new Runnable(){
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
    public void onServerLoad() {
        plugins = Bukkit.getPluginManager().getPlugins();
        try{ 
            TimedRegisteredListener.registerListeners();
        } catch (Exception e) { e.printStackTrace(); }
        try{ 
            TimedCommandExecutor.registerTimedCommandExecutor();
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public void update() {
        long ts = System.currentTimeMillis();

        Map<Plugin, Long> timings = TimedRegisteredListener.snapshotTimings();
        Map<Plugin, Map<String, Long>>  cmdTimings = TimedCommandExecutor.snapshotTimings();
        Map<String, Long> pluginTimings = new HashMap<>();
        Map<String, Map<String, Long>> commandTimings = new HashMap<>();
        for(Plugin plugin : timings.keySet()) {
            pluginTimings.put(plugin.getName(), timings.get(plugin));
        }
        for(Plugin plugin : cmdTimings.keySet()) {
            Long time = cmdTimings.get(plugin).values().stream().mapToLong(Long::longValue).sum();

            if(pluginTimings.containsKey(plugin.getName())) {
                pluginTimings.put(plugin.getName(), pluginTimings.get(plugin.getName()) + time);
            } else {
                pluginTimings.put(plugin.getName(), time);
            }

            commandTimings.put(plugin.getName(), cmdTimings.get(plugin));
        }

        synchronized(backlog) {
            _ram.put(ts, Utils.getMemoryUsage());
            _cpu.put(ts, Utils.getCPUUsage());
            _plugin.put(ts, pluginTimings);
            _command.put(ts, commandTimings);
            _players.put(ts, Bukkit.getOnlinePlayers().size());
            _entities.put(ts, Bukkit.getWorlds().stream().mapToInt(w -> w.getEntities().size()).sum());
            _chunks.put(ts, Bukkit.getWorlds().stream().mapToInt(w -> w.getLoadedChunks().length).sum());
        }
        notifyChange();
        super.update();
    }

    @Override
    public IPacket onJoinAsync(Identity identity) {
        JSONObject data = new JSONObject();

        synchronized(backlog) {
            data.put("cpu", _cpuBacklog);
            data.put("ram", _ramBacklog);
            data.put("tick", _tickBacklog);
            data.put("plugins", _pluginBacklog);
            data.put("commands", _commandBacklog);
            data.put("players", _playersBacklog);
            data.put("entities", _entitiesBacklog);
            data.put("chunks", _chunksBacklog);
        }

        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        runtimeMXBean.getUptime();

        data.put("ram_max", Utils.getMaxMemory());
        data.put("seconds", SECONDS);
        data.put("os", os);

        data.put("processors", coreCount);
        data.put("uptime", runtimeMXBean.getUptime());
        data.put("java.version", javaVersion);
        data.put("java.vm.name", javaVM);
        data.put("user.timezone", timezone);

        JSONArray plugins = new JSONArray();
        for(Plugin plugin : this.plugins) {
            JSONObject pluginData = new JSONObject();
            pluginData.put("name", plugin.getName());
            pluginData.put("version", plugin.getDescription().getVersion());
            pluginData.put("authors", plugin.getDescription().getAuthors());
            pluginData.put("enabled", plugin.isEnabled());
            pluginData.put("description", plugin.getDescription().getDescription());
            pluginData.put("website", plugin.getDescription().getWebsite());
            pluginData.put("main", plugin.getDescription().getMain());
            plugins.put(pluginData);
        }
        data.put("pluginList", plugins);
        return new ModuleUpdatePacket(this, data, true);
    }

    @Override
    public Map<Identity,IPacket> onUpdateAsync(List<Identity> identities) {
        Map<Identity,IPacket> responses = new HashMap<Identity,IPacket>();

        JSONObject data = new JSONObject();
        
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        runtimeMXBean.getUptime();
        data.put("uptime", runtimeMXBean.getUptime());

        synchronized(backlog) {
            data.put("cpu", _cpu);
            data.put("ram", _ram);
            data.put("tick", _tick);
            data.put("plugins", _plugin);
            data.put("commands", _command);
            data.put("players", _players);
            data.put("entities", _entities);
            data.put("chunks", _chunks);
        }


        for(Identity identity : identities) responses.put(identity, new ModuleUpdatePacket(this, data, false));

        long ts = System.currentTimeMillis();
        synchronized(backlog) {
            _ramBacklog.putAll(_ram);
            _cpuBacklog.putAll(_cpu);
            _tickBacklog.putAll(_tick);
            _pluginBacklog.putAll(_plugin);
            _commandBacklog.putAll(_command);
            _playersBacklog.putAll(_players);
            _entitiesBacklog.putAll(_entities);
            _chunksBacklog.putAll(_chunks);

            _ramBacklog.entrySet().removeIf(e -> e.getKey() < ts - SECONDS*1000);
            _cpuBacklog.entrySet().removeIf(e -> e.getKey() < ts - SECONDS*1000);
            _tickBacklog.entrySet().removeIf(e -> e.getKey() < ts - SECONDS*1000);
            _pluginBacklog.entrySet().removeIf(e -> e.getKey() < ts - SECONDS*1000);
            _commandBacklog.entrySet().removeIf(e -> e.getKey() < ts - SECONDS*1000);
            _playersBacklog.entrySet().removeIf(e -> e.getKey() < ts - SECONDS*1000);
            _entitiesBacklog.entrySet().removeIf(e -> e.getKey() < ts - SECONDS*1000);
            _chunksBacklog.entrySet().removeIf(e -> e.getKey() < ts - SECONDS*1000);

            _ram.clear();
            _cpu.clear();
            _tick.clear();
            _plugin.clear();
            _command.clear();
            _players.clear();
            _entities.clear();
            _chunks.clear();
        }

        return responses;
    }
}