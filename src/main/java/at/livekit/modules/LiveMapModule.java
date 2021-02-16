package at.livekit.modules;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.json.JSONArray;
import org.json.JSONObject;

import at.livekit.livekit.Identity;
import at.livekit.map.RenderJob;
import at.livekit.map.RenderWorld;
import at.livekit.plugin.Plugin;
import at.livekit.packets.ActionPacket;
import at.livekit.packets.IPacket;
import at.livekit.packets.RawPacket;
import at.livekit.packets.StatusPacket;

public class LiveMapModule extends BaseModule implements Listener
{
    private static String DEFAULT_WORLD = "world";
    private static int CPU_TIME = 50;

    private String[] _worlds;
    private RenderingOptions _options;
    
    private List<RenderWorld> worlds;
    private List<IPacket> _updates = new ArrayList<IPacket>();

    public LiveMapModule(String[] worlds, ModuleListener listener) {
        super(1, "Live Map", "livekit.module.map", UpdateRate.MAX, listener);
        this._worlds = worlds;
        this.worlds = new ArrayList<RenderWorld>(worlds.length);
    }

    @Action(name = "ResolveRegion", sync = false)
    public IPacket actionResolveRegion(Identity identity, ActionPacket packet) throws Exception {
        int x = packet.getData().getInt("x");
        int z = packet.getData().getInt("z");
        String world = packet.getData().getString("world");
        //if(!world.equals(DEFAULT_WORLD)) return new StatusPacket(0, "World mismatch!");

        RenderWorld renderWorld = getRenderWorld(DEFAULT_WORLD);
        if(renderWorld == null) return new StatusPacket(0, "World mismatch!");

        return new RawPacket(renderWorld.getRegionDataAsync(x, z));
    }

    public RenderingOptions getOptions() {
        return _options;
    }

    public void setCPUTime(int ms) {
        _options.cpuTime = ms;
        //saveProperties();
    }

    public void setRenderingMode(RenderingMode mode) {
        _options.mode = mode;
        //saveProperties();
    }

    public void setBounds(int minX, int maxX, int minZ, int maxZ) {
        _options.setLimits(new BoundingBox(minX, maxX, minZ, maxZ));
        //saveProperties();
    }

    public String getWorldInfo() {
        return getRenderWorld(DEFAULT_WORLD).getWorldInfoString();
    }

    public void startRenderJob(RenderJob job) throws Exception {
        getRenderWorld(DEFAULT_WORLD).startJob(job);
    }

    public void stopRenderJob() {
        getRenderWorld(DEFAULT_WORLD).stopJob();
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

    public RenderWorld getRenderWorld(String world) {
        for(RenderWorld w : worlds) {
            if(w.getWorldName().equals(world)) {
                return w;
            }
        }
        return null;
    }

    @Override
    public void onEnable(Map<String,ActionMethod> signature) {
       /* try{
            //TODO: option loading => renderworld
            loadOptions();
            //load();
        }catch(Exception ex){ex.printStackTrace();}*/

        for(String world : _worlds) {
            World w = Bukkit.getWorld(world);
            if(w != null) {
                RenderWorld renderWorld = new RenderWorld(world);
                
                if(renderWorld.regionCount() == 0) {
                    Chunk[] chunks = w.getLoadedChunks();
                    for(Chunk c : chunks) renderWorld.updateChunk(c, true);
                }

                worlds.add(renderWorld);
            }
        }

        Bukkit.getServer().getPluginManager().registerEvents(this, Plugin.getInstance());
        super.onEnable(signature);

        if(worlds.size() == 0) onDisable(signature);
    }
       
    @Override
    public void onDisable(Map<String,ActionMethod> signature) {
        
        /*try{
            save();
        }catch(Exception ex){ex.printStackTrace();}

        saveCache();

        _regions.clear();
        _updates.clear();
        _queueChunks.clear();*/
        for(RenderWorld world : worlds) world.shutdown();


        super.onDisable(signature);
    }

    @Override
    public IPacket onJoinAsync(Identity identity) {

        JSONObject json = new JSONObject();
        json.put("world", DEFAULT_WORLD);
        JSONArray regions = new JSONArray();
        json.put("regions", regions);

        RenderWorld world = getRenderWorld(DEFAULT_WORLD);

        synchronized(world.getRegions()) {
            for(Offset region : world.getRegions()) {
                JSONObject entry = new JSONObject();
                entry.put("x", region.x);
                entry.put("z", region.z);
                //entry.put("timestamp", region.timestamp);
                entry.put("timestamp", 0);
                //TODO: fix timestamp
                regions.put(entry);
            }
        }

        return new ModuleUpdatePacket(this, json, true);
    }

    @Override 
    public Map<Identity,IPacket> onUpdateAsync(List<Identity> identities) {
        Map<Identity,IPacket> response = new HashMap<Identity, IPacket>();
        
        JSONObject json = new JSONObject();
        json.put("world", DEFAULT_WORLD);
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
    @Override
    public void update() {
        _frameStart = System.currentTimeMillis();
        boolean tick = true;

        _u = 0;
        for(RenderWorld world : worlds) {
            world._needsUpdate = world.needsUpdate();
            if(world._needsUpdate) _u++;
        }

        while(System.currentTimeMillis() - _frameStart < CPU_TIME && _u != 0) {
            _u = 0;
            for(RenderWorld world : worlds) {
                if(!world._needsUpdate) continue;

                IPacket packet = world.update(_frameStart, CPU_TIME, tick);
                if(packet != null) {
                    synchronized(_updates) {
                        _updates.add(packet);
                        notifyChange();
                    }
                    world._needsUpdate = world.needsUpdate();
                }

                _u++;
            }
            tick = false;
        }

        for(RenderWorld world : worlds) world.checkUnload();

        long delta = System.currentTimeMillis() - _frameStart;
        if(delta != 0) System.out.println(delta+"ms used of tick");
    }
    /*@Override
    public void update() {
        _frameStart = System.currentTimeMillis();
        //only do chunk update, queue result packet
        while(System.currentTimeMillis() - _frameStart < _options.cpuTime) {
            try{
                Offset next = null;
                if(_currentUpdate == null) { 
                    synchronized(_queueChunks) {
                        if(_queueChunks.size() == 0) {
                            synchronized(_queueRegions) {
                                if(_queueRegions.size() != 0) {
                                    Offset region = _queueRegions.remove(0);
                                    for(int z = 0; z < 32; z++) {
                                        for(int x = 0; x < 32; x++) {
                                            _queueChunks.add(new Offset(region.x*32 + x, region.z*32+z));
                                        }
                                    }
                                }
                            }
                        }

                        if(_queueChunks.size() != 0) next = _queueChunks.remove(0);
                        
                    }
                    if(next == null) return;

                    if(next.onlyIfAbsent) {
                        if(this.loadedChunkExists(next)) {
                            continue;
                        }
                    }
                }

                IPacket result = null;
                if(_currentUpdate != null) result = update(null);
                else if(_options.mode == RenderingMode.FORCED || (_options.mode == RenderingMode.DISCOVER && Bukkit.getWorld(world).isChunkGenerated(next.x, next.z)) ) result = update(next);
                //if(result == null) update();

                

                if(result != null) {
                    synchronized(_updates) {
                        _updates.add(result);
                    }
                    notifyChange();
                }
            }catch(Exception ex){ex.printStackTrace();}
        }
    }*/

    /*private void createRegion(int regionX, int regionZ) {
        String key = regionX + "_" + regionZ;
        synchronized (_regions) {
            if (!_regions.containsKey(key)) {
                _regions.put(key, new RegionData(regionX, regionZ, new byte[8 + 512 * 512 * 4]));
                Arrays.fill(_regions.get(key).data, (byte) 0xFF);
                boundingBox.update(regionX, regionZ);
            }
        }
    }*/

   

   /* public boolean loadedChunkExists(Offset chunk) {
        int regionX = (int) Math.floor(((double) chunk.x / 32.0));
        int regionZ = (int) Math.floor(((double) chunk.z / 32.0));
        String key = regionX + "_" + regionZ;

        if(!_regions.containsKey(key)) return false;

        int localX = chunk.x*16 % 512;
        if (localX < 0)
            localX += 512;

        int localZ = chunk.z*16 % 512;
        if (localZ < 0)
            localZ += 512;

        RegionData data = _regions.get(key);

        for(int i = 0; i < 4; i++) {
            if( data.data[8 + ((localZ+15) * 4 * 512) + ((localX+15) * 4) + i] != (byte)0xFF) return true;
        }
        return false;
    }*/

 


    /*public static byte[] loadRegion(int x, int z, String world) {
        File file = new File(Plugin.getInstance().getDataFolder(), "map/"+world+"/"+x+"_"+z+".region");
        if(!file.exists()) return null;

        try{
            return Files.readAllBytes(file.toPath());
        }catch(Exception ex){ex.printStackTrace();}
        return null;
    }

    private void saveCache() {
        try{
            File file = new File(getDir(), "cache.json");
            if(!file.exists()) file.createNewFile();

            JSONObject options = new JSONObject();
            JSONArray chunks = new JSONArray(_queueChunks.stream().map(c->c.toJson()).collect(Collectors.toList()));
            if(_chunk != null) chunks.put(_chunk.toJson());

            options.put("chunk_queue", chunks);
            options.put("region_queue", _queueRegions.stream().map(c->c.toJson()).collect(Collectors.toList()));

            Files.write(file.toPath(), options.toString().getBytes());
        }catch(Exception ex){ex.printStackTrace();}
    }

    private void saveProperties() {
        try{
            File file = new File(getDir(), "properties.json");
            if(!file.exists()) file.createNewFile();

            JSONObject options = _options.toJson();

            Files.write(file.toPath(), options.toString().getBytes());
        }catch(Exception ex){ex.printStackTrace();}
    }

    private void loadOptions() {
        try{
            File folder = getDir();
            if(folder.exists()) {
                File optionsFile = new File(folder, "data.json");
                if(optionsFile.exists()) {
                    JSONObject data = new JSONObject(new String(Files.readAllBytes(optionsFile.toPath())));
                    _options = RenderingOptions.fromJson(data);

                    if(data.has("chunk_queue")) {
                        JSONArray cqueue = data.getJSONArray("chunk_queue");
                        for(int i = 0; i < cqueue.length(); i++) {
                            _queueChunks.add(Offset.fromJson(cqueue.getJSONObject(i)));
                        }
                    }

                    if(data.has("region_queue")) {
                        JSONArray rqueue = data.getJSONArray("region_queue");
                        for(int i = 0; i < rqueue.length(); i++) {
                            _queueRegions.add(Offset.fromJson(rqueue.getJSONObject(i)));
                        }
                    }

                    Plugin.log("Converting to new rendering data format");
                    optionsFile.delete();
                    saveCache();
                    saveProperties();
                    return;
                }

                //v 0.0.5 split render queue cache and properties file!
                optionsFile = new File(folder, "cache.json");
                if(optionsFile.exists()) {
                    JSONObject data = new JSONObject(new String(Files.readAllBytes(optionsFile.toPath())));
                    if(data.has("chunk_queue")) {
                        JSONArray cqueue = data.getJSONArray("chunk_queue");
                        for(int i = 0; i < cqueue.length(); i++) {
                            _queueChunks.add(Offset.fromJson(cqueue.getJSONObject(i)));
                        }
                    }

                    if(data.has("region_queue")) {
                        JSONArray rqueue = data.getJSONArray("region_queue");
                        for(int i = 0; i < rqueue.length(); i++) {
                            _queueRegions.add(Offset.fromJson(rqueue.getJSONObject(i)));
                        }
                    }
                }
                optionsFile = new File(folder, "properties.json");
                if(optionsFile.exists()) {
                    JSONObject data = new JSONObject(new String(Files.readAllBytes(optionsFile.toPath())));
                    _options = RenderingOptions.fromJson(data);

                    return;
                }
            }
        }catch(Exception ex){ex.printStackTrace();}

        Plugin.debug("LiveMapModule using default options "+world);
        if(_options==null) _options = new RenderingOptions();
        _options.limits = BoundingBox.fromWorld(world);
        if(_options.limits.maxX - _options.limits.minX > 20) { _options.limits.minX = -5; _options.limits.maxX = 5; }
        if(_options.limits.maxZ - _options.limits.minZ > 20) { _options.limits.minZ = -5; _options.limits.maxZ = 5; }
    }

    private void load() throws Exception{
        File folder = getDir();
        if(!folder.exists()) folder.mkdirs();

        for(File file : folder.listFiles()) {
            if(file.isFile() && file.getName().endsWith(".region")) {
                RegionData data =  new RegionData(file);
                if(_options.getLimits().regionInBounds(data.x, data.z)) {
                    _regions.put(data.x+"_"+data.z, data);
                }
            }
        }
        Plugin.debug("LiveMapModule loaded "+_regions.size()+" regions for "+world);
    }

    private void save() throws FileNotFoundException, IOException { 
        synchronized(_regions) {
            for(Entry<String, RegionData> entry : _regions.entrySet()) {
                entry.getValue().save(getDir());
                /*File file = new File(getDir(), entry.getKey()+".region");
                if(!file.exists()) file.createNewFile();

                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(entry.getValue());
                }*/
       /*     }
        }
    }

    private File getDir() {
        return new File(Plugin.getInstance().getDataFolder(), "map/"+world);
    }*/

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
            invalidate();
        }

        public RegionData(File file) {
            try{
                x = Integer.parseInt(file.getName().split("_")[0]);
                z = Integer.parseInt(file.getName().split("_")[1].replace(".region", ""));
                data = Files.readAllBytes(file.toPath());
                timestamp = 0;
                for(int i = 0; i < 8; i++) {
                    timestamp <<= 8;
                    timestamp |= data[i];
                }

            }catch(Exception ex){ex.printStackTrace();}
        }

        public void invalidate() {
            timestamp = System.currentTimeMillis();
            for(int i = 0; i < 8; i++) {
                data[i] = (byte)(0xFF & (timestamp >> (56-(i*8))));
            }
        }

        public byte[] getData() {
            return data;
        }

        public void save(File dir) {
            try{
                File file = new File(dir, x+"_"+z+".region");
                if(!file.exists()) file.createNewFile();

                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(data);
                }
            }catch(Exception ex){
                ex.printStackTrace();
            }
        }

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

            for(int i = 0; i < 4; i++) {
                if( data[8 + ((localZ+15) * 4 * 512) + ((localX+15) * 4) + i] != (byte)0xFF) return true;
            }
            return false;
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
            offset.onlyIfAbsent = (json.has("absent")&&!json.isNull("absent")?json.getBoolean("absent"):false);
            return offset;
        }

        @Override
        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("x", x);
            json.put("z", z);
            if(onlyIfAbsent == true)json.put("absent", onlyIfAbsent);
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

    @EventHandler
    public void onBlockPlaceEvent(BlockPlaceEvent event) {
        if(!isEnabled()) return;
        RenderWorld world = getRenderWorld(event.getBlock().getWorld().getName());
        if(world == null) return;
        

        if(event.getBlock().getY() == event.getBlock().getWorld().getHighestBlockAt(event.getBlock().getX(), event.getBlock().getZ()).getY()) {
            world.updateBlock(event.getBlock());
        }
    }

    @EventHandler
    public void onBlockBreakEvent(BlockBreakEvent event) {
        if(!isEnabled()) return;
        RenderWorld world = getRenderWorld(event.getBlock().getWorld().getName());
        if(world == null) return;

        if(event.getBlock().getY() == event.getBlock().getWorld().getHighestBlockAt(event.getBlock().getX(), event.getBlock().getZ()).getY()) {
            world.updateBlock(event.getBlock().getRelative(BlockFace.DOWN));
        }        
    }

    @EventHandler
    public void onBlockFormEvent(BlockFormEvent event) {
        if(!isEnabled()) return;
        RenderWorld world = getRenderWorld(event.getBlock().getWorld().getName());
        if(world == null) return;

        if(event.getBlock().getY() == event.getBlock().getWorld().getHighestBlockAt(event.getBlock().getX(), event.getBlock().getZ()).getY()) {
            world.updateBlock(event.getBlock());
        }     
    }

    @EventHandler
    public void onBlockGrowEvent(BlockGrowEvent event) {
        if(!isEnabled()) return;
        RenderWorld world = getRenderWorld(event.getBlock().getWorld().getName());
        if(world == null) return;

        if(event.getBlock().getY() == event.getBlock().getWorld().getHighestBlockAt(event.getBlock().getX(), event.getBlock().getZ()).getY()) {
            world.updateBlock(event.getBlock());
        }     
    }

    @EventHandler
    public void onBlockSpreadEvent(BlockSpreadEvent event) {
        if(!isEnabled()) return;
        RenderWorld world = getRenderWorld(event.getBlock().getWorld().getName());
        if(world == null) return;

        if(event.getBlock().getY() == event.getBlock().getWorld().getHighestBlockAt(event.getBlock().getX(), event.getBlock().getZ()).getY()) {
            world.updateBlock(event.getBlock());
        }    
    }

    @EventHandler
    public void onBlockExplodeEvent(BlockExplodeEvent event) {
        if(!isEnabled()) return;
        RenderWorld world = getRenderWorld(event.getBlock().getWorld().getName());
        if(world == null) return;

        if(event.getBlock().getY() == event.getBlock().getWorld().getHighestBlockAt(event.getBlock().getX(), event.getBlock().getZ()).getY()) {
            world.updateBlock(event.getBlock());
        }    
    }

    @EventHandler
    public void onBlockFadeEvent(BlockFadeEvent event) {
        if(!isEnabled()) return;
        RenderWorld world = getRenderWorld(event.getBlock().getWorld().getName());
        if(world == null) return;
    
        if(event.getBlock().getY() == event.getBlock().getWorld().getHighestBlockAt(event.getBlock().getX(), event.getBlock().getZ()).getY()) {
            world.updateBlock(event.getBlock());
        }    
    }


    // WORLD EVENTS

    @EventHandler
    private void onChunkPopulateEvent(ChunkPopulateEvent event) {
        if(!isEnabled()) return;
        RenderWorld world = getRenderWorld(event.getChunk().getWorld().getName());
        if(world == null) return;

        world.updateChunk(event.getChunk(), true);
    }

    @EventHandler
    private void onChunkLoadEvent(ChunkLoadEvent event) {
        if(!isEnabled()) return;
        RenderWorld world = getRenderWorld(event.getChunk().getWorld().getName());
        if(world == null) return;

        world.updateChunk(event.getChunk(), true);
    }

    @EventHandler
    private void onStructureGrowEvent(StructureGrowEvent event) {
        if(!isEnabled()) return;
        RenderWorld world = getRenderWorld(event.getWorld().getName());
        if(world == null) return;

        List<Chunk> chunks = new ArrayList<Chunk>();
        for(BlockState bd : event.getBlocks()) {
            if(!chunks.contains(bd.getChunk())) {
                chunks.add(bd.getChunk());
                world.updateChunk(bd.getChunk(), false);
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


