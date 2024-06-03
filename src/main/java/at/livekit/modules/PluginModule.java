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
import at.livekit.timings.TimedRegisteredListener;

public class PluginModule extends BaseModule{
    private static int SECONDS = 60*1 + 15;

    private BukkitTask tickTask;

    private Object backlog = new Object();
    private HashMap<Long, Long> _ramBacklog = new HashMap<Long, Long>();
    private HashMap<Long, Float> _cpuBacklog = new HashMap<Long, Float>();
    private HashMap<Long, Integer> _tickBacklog = new HashMap<Long, Integer>();
    
    private HashMap<Long, Long> _ram = new HashMap<Long, Long>();
    private HashMap<Long, Float> _cpu = new HashMap<Long, Float>();
    private HashMap<Long, Integer> _tick = new HashMap<Long, Integer>();

    private Map<Plugin, Long> pluginTimings;
    private Plugin[] plugins;

    long currentTickTS = 0;
    int currentTick = 0;

    private String os = "Unknown";
    private String javaVersion = "Unknown";
    private String javaVM = "Unkown";
    private String timezone = "Unknown";
    private int coreCount = 0;

    public PluginModule(ModuleListener listener) {
        super(1, "PluginModule", "livekit.module.plugins", UpdateRate.ONCE_PERSEC, listener, "default");
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
        pluginTimings = new HashMap<Plugin, Long>();
        try{ 
            TimedRegisteredListener.registerListeners();
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public void update() {
        synchronized(pluginTimings) {
            pluginTimings = TimedRegisteredListener.snapshotTimings();
        }


        long ts = System.currentTimeMillis();

        synchronized(backlog) {
            _ram.put(ts, Utils.getMemoryUsage());
            _cpu.put(ts, Utils.getCPUUsage());
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
            pluginData.put("author", plugin.getDescription().getAuthors().get(0));
            pluginData.put("enabled", plugin.isEnabled());
            plugins.put(pluginData);
        }
        data.put("plugins", plugins);
        return new ModuleUpdatePacket(this, data, true);
    }

    @Override
    public Map<Identity,IPacket> onUpdateAsync(List<Identity> identities) {
        Map<Identity,IPacket> responses = new HashMap<Identity,IPacket>();

        JSONObject data = new JSONObject();

        synchronized(pluginTimings) {
            for(Plugin plugin : this.pluginTimings.keySet()) {
                data.put(plugin.getName(), pluginTimings.get(plugin));
            }
        }

        
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        runtimeMXBean.getUptime();
        data.put("uptime", runtimeMXBean.getUptime());

        synchronized(backlog) {
            data.put("cpu", _cpu);
            data.put("ram", _ram);
            data.put("tick", _tick);
        }


        for(Identity identity : identities) responses.put(identity, new ModuleUpdatePacket(this, data, false));

        long ts = System.currentTimeMillis();
        synchronized(backlog) {
            _ramBacklog.putAll(_ram);
            _cpuBacklog.putAll(_cpu);
            _tickBacklog.putAll(_tick);

            _ramBacklog.entrySet().removeIf(e -> e.getKey() < ts - SECONDS*1000);
            _cpuBacklog.entrySet().removeIf(e -> e.getKey() < ts - SECONDS*1000);
            _tickBacklog.entrySet().removeIf(e -> e.getKey() < ts - SECONDS*1000);

            _ram.clear();
            _cpu.clear();
            _tick.clear();
        }

        return responses;
    }
}