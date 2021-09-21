package at.livekit.modules;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.json.JSONArray;
import org.json.JSONObject;

import at.livekit.livekit.Identity;
import at.livekit.livekit.LiveKit;
import at.livekit.map.RenderBounds;
import at.livekit.map.RenderJob;
import at.livekit.map.RenderScheduler;
import at.livekit.map.RenderWorld;
import at.livekit.map.RenderWorld.RegionInfo;
import at.livekit.plugin.Plugin;
import at.livekit.utils.Utils;
import at.livekit.packets.ActionPacket;
import at.livekit.packets.IPacket;
import at.livekit.packets.RawPacket;
import at.livekit.packets.StatusPacket;

public class LiveMapModule extends BaseModule implements Listener
{
    //private static String DEFAULT_WORLD = "world";
    //public static int CPU_TIME = 20;


    private String world;
    private Map<String, String> availableWorlds;

    private UUID worldUUID;
    private RenderWorld renderWorld = null;
    private List<IPacket> _updates = new ArrayList<IPacket>();

    private boolean waitingForWorld = false;

    public LiveMapModule(String world, ModuleListener listener, Map<String, String> availableWorlds) {
        super(1, "Live Map", "livekit.module.map", UpdateRate.MAX, listener, world);
        this.world = world;
        this.availableWorlds = availableWorlds;
    }

    @Action(name = "ResolveRegion", sync = false)
    public IPacket actionResolveRegion(Identity identity, ActionPacket packet) throws Exception {
        int x = packet.getData().getInt("x");
        int z = packet.getData().getInt("z");
        String world = packet.getData().getString("world");
        if(!world.equals(world)) return new StatusPacket(0, "World mismatch!");

        return new RawPacket(renderWorld.getRegionDataAsync(x, z));
    }

    public void setRenderBounds(RenderBounds bounds) {
       renderWorld.setRenderBounds(bounds, true);
       notifyFull();
    }

    public String getWorldInfo() {
        return renderWorld.getWorldInfoString();
    }

    public void startRenderJob(RenderJob job) throws Exception {
        renderWorld.startJob(job);
    }

    public void stopRenderJob() {
        renderWorld.stopJob();
    }

    public RenderWorld getRenderWorld() {
        return renderWorld;
    }

    public String getWorldName() {
        return world;
    }

    /*public void fullRender() throws Exception{
        if(_options.mode == RenderingMode.DISCOVER) throw new Exception("Fullrender can only be started in FORCED mode!");

        synchronized(_queueRegions) {
            for(int z = _options.limits.minZ; z < _options.limits.maxZ; z++) {
                for(int x = _options.limits.minX; x < _options.limits.maxX; x++) {
                    _queueRegions.add(new Offset(x, z));
                }
            }
        }
    }*/

    /*public void clearChunkQueue() {
        synchronized(_queueChunks) {
            _queueChunks.clear();
        }
    }

    public void clearRegionQueue() {
        synchronized(_queueRegions) {
            _queueRegions.clear();
        }
    }

    public int getChunkQueueSize() {
        synchronized(_queueChunks) {
            return _queueChunks.size();
        }
    }

    public int getRegionQueueSize() {
        synchronized(_queueRegions) {
            return _queueRegions.size();
        }
    }

    public String getWorld() {
        return world;
    }*/

    /*public RenderWorld getRenderWorld(String world) {
        for(RenderWorld w : worlds) {
            if(w.getWorldName().equals(world)) {
                return w;
            }
        }
        return null;
    }*/

    @Override
    public void onEnable(Map<String,ActionMethod> signature) {
        Bukkit.getServer().getPluginManager().registerEvents(this, Plugin.getInstance());
        
        World w = Bukkit.getWorld(world);
        if(w == null) {
            Plugin.debug("World "+world+" not found!");
            waitingForWorld = true;
            return;
        }
        worldUUID = w.getUID();

        RenderScheduler.setTotalWorkers(RenderScheduler.getTotalWorkers()+1);

        renderWorld = new RenderWorld(world, w.getUID().toString());
        Chunk[] chunks = w.getLoadedChunks();
        for(Chunk c : chunks) renderWorld.updateChunk(c, true);

        
        super.onEnable(signature);
    }
       
    @Override
    public void onDisable(Map<String,ActionMethod> signature) {
        renderWorld.shutdown();

        RenderScheduler.unregisterWork(this);
        RenderScheduler.setTotalWorkers(RenderScheduler.getTotalWorkers()-1);
        
        super.onDisable(signature);
    }

    @Override
    public IPacket onJoinAsync(Identity identity) {

        JSONObject json = new JSONObject();
        json.put("world", world);

        JSONArray availableWorlds = new JSONArray();
        json.put("worldNames", availableWorlds);
        json.put("blockInfo", identity.hasPermission("livekit.map.info"));
        JSONArray regions = new JSONArray();
        json.put("regions", regions);

        synchronized(this.availableWorlds) {
            for(Entry<String,String> entry : this.availableWorlds.entrySet()) {
                JSONObject wentry = new JSONObject();
                wentry.put("index", Integer.parseInt(entry.getValue().split(":")[0]));
                wentry.put("world", entry.getKey());
                wentry.put("friendly", entry.getValue().split(":")[1]);
                availableWorlds.put(wentry);
            }
        }

        synchronized(renderWorld.getRegions()) {
            for(RegionInfo region : renderWorld.getRegions()) {
                JSONObject entry = new JSONObject();
                entry.put("x", region.x);
                entry.put("z", region.z);
                entry.put("t", region.timestamp);
                regions.put(entry);
            }
        }

        return new ModuleUpdatePacket(this, json, true);
    }

    @Override 
    public Map<Identity,IPacket> onUpdateAsync(List<Identity> identities) {
        Map<Identity,IPacket> response = new HashMap<Identity, IPacket>();
        
        JSONObject json = new JSONObject();
        json.put("world", world);
        JSONArray syncable = new JSONArray();
        JSONArray upd = new JSONArray();
        json.put("updates", upd);
        json.put("syncables", syncable);

        //TODO: world specific updates!

        synchronized(_updates) {
            for(IPacket update : _updates) {
                upd.put(update.toJson());
            }
            _updates.clear();
        }

        for(Identity identity : identities) {
            response.put(identity, new ModuleUpdatePacket(this, json, false));
        }

        return response;
    }

    long _frameStart = 0;
    int _u = -1;
    int cpu_time = 1;
    @Override
    public void update() {
        _frameStart = System.currentTimeMillis();
        boolean tick = true;

        cpu_time = RenderScheduler.getTimeAllocation(this);
        renderWorld._needsUpdate = renderWorld.needsUpdate();
        while(System.currentTimeMillis() - _frameStart < cpu_time && renderWorld._needsUpdate) {

            IPacket packet = renderWorld.update(_frameStart, cpu_time, tick);
            if(packet != null) {
                synchronized(_updates) {
                    _updates.add(packet);
                    notifyChange();
                }
                renderWorld._needsUpdate = renderWorld.needsUpdate();
            }

            tick = false;
        }

        if(renderWorld.needsUpdate()) RenderScheduler.registerWork(this);
        else RenderScheduler.unregisterWork(this);

        renderWorld.checkUnload();
        long delta = System.currentTimeMillis() - _frameStart;
       // if(delta != 0) System.out.println(delta+"ms/"+cpu_time+"ms used of tick ("+world+") needsUpdate="+renderWorld.needsUpdate());
    }

    public static class RegionData {
        protected int x;
        protected int z;
        public byte[] data;
        public long timestamp = 0;
        public long lastChange = 0;

        protected boolean dead = false;

        public RegionData(int x, int z, byte[] data) {
            this.x = x;
            this.z = z;
            this.data = data;
            //invalidate();
        }

        public RegionData(File file) {
            try{
                x = Integer.parseInt(file.getName().split("_")[0]);
                z = Integer.parseInt(file.getName().split("_")[1].replace(".region", ""));
                data = Files.readAllBytes(file.toPath());
                timestamp = Utils.decodeTimestamp(data);

            }catch(Exception ex){ex.printStackTrace();}
        }

        public void invalidate() {
            timestamp = System.currentTimeMillis();
            byte[] encoded = Utils.encodeTimestamp(timestamp);
            for(int i = 0; i < 8; i++) {
                data[i] = encoded[i];
            }
        }

        public byte[] getData() {
            return data;
        }

        /*public void save(File dir) {
            try{
                File file = new File(dir, x+"_"+z+".region");
                if(!file.exists()) file.createNewFile();

                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(data);
                } catch(Exception ex){ex.printStackTrace();}
            }catch(Exception ex){
                ex.printStackTrace();
            }
        }*/

        public int getX() {
            return x;
        }

        public int getZ() {
            return z;
        }

        public boolean isDead() {
            return dead;
        }

        public void setDead(boolean dead) {
            this.dead = dead;
        }

        public boolean loadedChunkExists(Offset chunk) {
            int localX = chunk.x*16 % 512;
            if (localX < 0)
                localX += 512;

            int localZ = chunk.z*16 % 512;
            if (localZ < 0)
                localZ += 512;

            int edges = 0;
            boolean hasEdge = false;
            for(int i = 0; i < 4; i++) {
                if( data[8 + ((localZ+0) * 4 * 512) + ((localX+0) * 4) + i] != (byte)0xFF) hasEdge = true;
            }
            if(hasEdge) edges++;
            hasEdge = false;
            for(int i = 0; i < 4; i++) {
                if( data[8 + ((localZ+15) * 4 * 512) + ((localX+0) * 4) + i] != (byte)0xFF) hasEdge = true;
            }
            if(hasEdge) edges++;
            hasEdge = false;
            for(int i = 0; i < 4; i++) {
                if( data[8 + ((localZ+0) * 4 * 512) + ((localX+15) * 4) + i] != (byte)0xFF) hasEdge = true;
            }
            if(hasEdge) edges++;
            hasEdge = false;
            for(int i = 0; i < 4; i++) {
                if( data[8 + ((localZ+15) * 4 * 512) + ((localX+15) * 4) + i] != (byte)0xFF) hasEdge = true;
            }
            if(hasEdge) edges++;
            return edges == 4;
        }
    }

    public static class Offset implements Serializable {
        public int x;
        public int z;
        public boolean onlyIfAbsent;
        
        public Offset() {}

        public Offset(int x, int z) {
            this.x = x;
            this.z = z;
        }

        public Offset(int x, int z, boolean chunkLoadEvent) {
            this.x = x;
            this.z = z;
            this.onlyIfAbsent = chunkLoadEvent;
        }

        public static Offset fromJson(JSONObject json) {
            Offset offset = new Offset();
            offset.x = json.getInt("x");
            offset.z = json.getInt("z");
            if(json.has("absent")) offset.onlyIfAbsent = (json.has("absent")&&!json.isNull("absent")?json.getBoolean("absent"):false);
            if(json.has("a")) offset.onlyIfAbsent = (json.has("a")&&!json.isNull("a")?json.getBoolean("a"):false);
            return offset;
        }

        @Override
        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("x", x);
            json.put("z", z);
            if(onlyIfAbsent == true)json.put("a", onlyIfAbsent);
            return json;
        }
    }

    public static class BoundingBox implements Serializable {
        public int minX = 0;
        public int minZ = 0;
        public int maxX = 0;
        public int maxZ = 0;
        private boolean _initialized = false;

        public BoundingBox() {

        }

        public BoundingBox(int minX, int maxX, int minZ, int maxZ) {
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
            _initialized = true;
        }
      
        public void update(int x, int z) {
          if(_initialized == false) {
            minX = x;
            maxX = x+1;
            minZ = z;
            maxZ = z+1;
            _initialized = true;
          }
      
          if(x < minX) minX = x;
          if(x >= maxX) maxX = x+1;
          if(z < minZ) minZ = z;
          if(z >= maxZ) maxZ = z+1;
        }

        public boolean regionInBounds(int x, int z) {
            if(x >= minX && x < maxX) {
                if(z >= minZ && z < maxZ) {
                    return true;
                }
            }
            return false;
        }

        public boolean chunkInBounds(int x, int z) {
            int regionX = (int) Math.floor(((double) x / 32.0));
            int regionZ = (int) Math.floor(((double) z / 32.0));
            return regionInBounds(regionX, regionZ);
        }

        public static BoundingBox fromJson(JSONObject json) {
            BoundingBox box = new BoundingBox();
            box.minX = json.getInt("minX");
            box.maxX = json.getInt("maxX");
            box.minZ = json.getInt("minZ");
            box.maxZ = json.getInt("maxZ");
            return box;
        }

        public static BoundingBox fromWorld(String world) {
            BoundingBox box = new BoundingBox();

            File worldFolder =  new File(Plugin.getInstance().getDataFolder(), "../../"+world+"/region");
            if(worldFolder.exists()) {
               for(File file : worldFolder.listFiles()) {
                   if(file.isFile() && file.getName().endsWith(".mca")) {
                        int x = Integer.parseInt(file.getName().split("\\.")[1]);
                        int z = Integer.parseInt(file.getName().split("\\.")[2]);
                        //Plugin.log("Region discovered "+x+" "+z);
                        box.update(x, z);
                   }
               }
            }
            if(box.maxX == 0 && box.minX == 0) { box.minX = -1; box.maxX = 1;}
            if(box.minZ == 0 && box.maxZ == 0) { box.minZ = -1; box.maxZ = 1;}

            if(box.maxX - box.minX > 50) { box.minX = -25; box.maxX = 25; }
            if(box.maxZ - box.minZ > 50) { box.minZ = -25; box.maxZ = 25; }

            return box;
        }

        @Override
        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("minX", minX);
            json.put("maxX", maxX);
            json.put("minZ", minZ);
            json.put("maxZ", maxZ);
            return json;
        }

        @Override
        public String toString() {
            return "BoundingBox[x="+minX+"; z="+minZ+"; width="+(maxX-minX)+" ("+maxX+"); height="+(maxZ-minZ)+" ("+maxZ+")]";
        }
    }

    //BLOCK EVENTS

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockPlaceEvent(BlockPlaceEvent event) {
        if(!isEnabled() || event.isCancelled() || !event.getBlock().getWorld().getName().equals(world)) return;

        if(event.getBlock().getY() == event.getBlock().getWorld().getHighestBlockAt(event.getBlock().getX(), event.getBlock().getZ()).getY()) {
            renderWorld.updateBlock(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreakEvent(BlockBreakEvent event) {
        if(!isEnabled() || event.isCancelled() || !event.getBlock().getWorld().getName().equals(world)) return;

        if(event.getBlock().getY() == event.getBlock().getWorld().getHighestBlockAt(event.getBlock().getX(), event.getBlock().getZ()).getY()) {
            renderWorld.updateBlock(event.getBlock().getRelative(BlockFace.DOWN));
        }        
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockFormEvent(BlockFormEvent event) {
        if(!isEnabled() || event.isCancelled() || !event.getBlock().getWorld().getName().equals(world)) return;

        if(event.getBlock().getY() == event.getBlock().getWorld().getHighestBlockAt(event.getBlock().getX(), event.getBlock().getZ()).getY()) {
            renderWorld.updateBlock(event.getBlock());
        }     
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockGrowEvent(BlockGrowEvent event) {
        if(!isEnabled() || event.isCancelled() || !event.getBlock().getWorld().getName().equals(world)) return;

        if(event.getBlock().getY() == event.getBlock().getWorld().getHighestBlockAt(event.getBlock().getX(), event.getBlock().getZ()).getY()) {
            renderWorld.updateBlock(event.getBlock());
        }     
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockSpreadEvent(BlockSpreadEvent event) {
        if(!isEnabled() || event.isCancelled() || !event.getBlock().getWorld().getName().equals(world)) return;

        if(event.getBlock().getY() == event.getBlock().getWorld().getHighestBlockAt(event.getBlock().getX(), event.getBlock().getZ()).getY()) {
            renderWorld.updateBlock(event.getBlock());
        }    
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockExplodeEvent(BlockExplodeEvent event) {
        if(!isEnabled() || event.isCancelled() || !event.getBlock().getWorld().getName().equals(world)) return;

        if(event.getBlock().getY() == event.getBlock().getWorld().getHighestBlockAt(event.getBlock().getX(), event.getBlock().getZ()).getY()) {
            renderWorld.updateBlock(event.getBlock());
        }    
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockFadeEvent(BlockFadeEvent event) {
        if(!isEnabled() || event.isCancelled() || !event.getBlock().getWorld().getName().equals(world)) return;
    
        if(event.getBlock().getY() == event.getBlock().getWorld().getHighestBlockAt(event.getBlock().getX(), event.getBlock().getZ()).getY()) {
            renderWorld.updateBlock(event.getBlock());
        }    
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLeavesDecayEvent(LeavesDecayEvent event) {
        if(!isEnabled() || event.isCancelled() || !event.getBlock().getWorld().getName().equals(world)) return;
    
        if(event.getBlock().getY() == event.getBlock().getWorld().getHighestBlockAt(event.getBlock().getX(), event.getBlock().getZ()).getY()) {
            renderWorld.updateBlock(event.getBlock());
        }    
    }


    // WORLD EVENTS
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoadEvent(WorldLoadEvent event) {
       //Plugin.debug("World loading "+event.getWorld().getName());
       if(!isEnabled() && waitingForWorld) {
           if(event.getWorld().getName().equals(world)) {
               Plugin.debug("World "+world+" just loaded, enabling Live map");
               LiveKit.getInstance().enableModule(getType());
           }
       } 
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onChunkPopulateEvent(ChunkPopulateEvent event) {
        if(!isEnabled() || !event.getWorld().getName().equals(world)) return;

        renderWorld.updateChunk(event.getChunk(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onChunkLoadEvent(ChunkLoadEvent event) {
        if(!isEnabled() || !event.getWorld().getName().equals(world)) return;

        renderWorld.updateChunk(event.getChunk(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onStructureGrowEvent(StructureGrowEvent event) {
        if(!isEnabled() || event.isCancelled() || !event.getWorld().getName().equals(world)) return;

        List<Chunk> chunks = new ArrayList<Chunk>();
        for(BlockState bd : event.getBlocks()) {
            if(!chunks.contains(bd.getChunk())) {
                chunks.add(bd.getChunk());
                renderWorld.updateChunk(bd.getChunk(), false);
            }
        }
    }

    public static class RenderingOptions implements Serializable {
        
        private int cpuTime = 20;
        private RenderingMode mode = RenderingMode.DISCOVER;
        private BoundingBox limits = null;
            
        public int getCpuTime() {
            return cpuTime;
        }

        public RenderingMode getMode() {
            return mode;
        }

        public BoundingBox getLimits() {
            return limits;
        }

        public void setLimits(BoundingBox limits) {
            this.limits = limits;
        }

        public static RenderingOptions fromJson(JSONObject json) {
            RenderingOptions options = new RenderingOptions();
            options.cpuTime = json.getInt("cpuTime");
            options.mode = RenderingMode.fromValue(json.getInt("mode"));
            options.limits = (json.has("limits")&&!json.isNull("limits")) ? BoundingBox.fromJson(json.getJSONObject("limits")) : null;
            return options;
        }

        @Override
        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("cpuTime", cpuTime);
            json.put("mode", mode.value());
            if(limits != null)json.put("limits", limits.toJson());
            return json;
        }
    }

    public static enum RenderingMode {
        DISCOVER(0), FORCED(1);
    
        private int value;

        RenderingMode(int value) {
            this.value = value;
        }

        int value() {
            return value;
        }

        static RenderingMode fromValue(int value) {
            for(RenderingMode m : RenderingMode.values()) {
                if(m.value() == value) {
                    return m;
                }
            }
            return null;
        }
    }
}


