package at.livekit.modules;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
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
/*import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;*/
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.json.JSONArray;
import org.json.JSONObject;

import at.livekit.livekit.Identity;
import at.livekit.modules.BaseModule.Action;
import at.livekit.plugin.Plugin;
import at.livekit.packets.ActionPacket;
import at.livekit.packets.BlockPacket;
import at.livekit.packets.ChunkPacket;
import at.livekit.packets.IPacket;
import at.livekit.packets.RawPacket;
import at.livekit.packets.StatusPacket;

public class LiveMapModule extends BaseModule implements Listener
{
    //private List<Offset> _queueBlocks = new ArrayList<Offset>();
    private RenderingOptions _options;
    private List<Offset> _queueChunks = new ArrayList<Offset>();
    private List<Offset> _queueRegions = new ArrayList<Offset>();

    private String world;
    private BoundingBox boundingBox;
    private HashMap<String, RegionData> _regions = new HashMap<String, RegionData>();
    //private HashMap<String, LiveSyncable> _syncables = new HashMap<String, LiveSyncable>();

    private List<IPacket> _updates = new ArrayList<IPacket>();
    

    public LiveMapModule(String world, ModuleListener listener) {
        super(1, "Live Map", "livekit.module.map", UpdateRate.MAX, listener);
        this.world = world;
    }

    @Action(name = "ResolveRegion", sync = false)
    public IPacket actionResolveRegion(Identity identity, ActionPacket packet) {
        int x = packet.getData().getInt("x");
        int z = packet.getData().getInt("z");
        String world = packet.getData().getString("world");
        if(!world.equals(this.world)) return new StatusPacket(0, "World mismatch!");

        return new RawPacket(getRegionData(x, z).getData());
    }

    public RenderingOptions getOptions() {
        return _options;
    }

    public void setCPUTime(int ms) {
        _options.cpuTime = ms;
    }

    public void setRenderingMode(RenderingMode mode) {
        _options.mode = mode;
    }

    public void fullRender() throws Exception{
        if(_options.mode == RenderingMode.DISCOVER) throw new Exception("Fullrender can only be started in FORCED mode!");

        synchronized(_queueRegions) {
            for(int z = _options.limits.minZ; z < _options.limits.maxZ; z++) {
                for(int x = _options.limits.minX; x < _options.limits.maxX; x++) {
                    _queueRegions.add(new Offset(x, z));
                }
            }
        }
    }

    public void clearChunkQueue() {
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
    }

    public void updateBlock(Block block) {
        int regionX = (int) Math.floor(((double) block.getX() / 512.0));
        int regionZ = (int) Math.floor(((double) block.getZ() / 512.0));
        
        if(!_options.limits.regionInBounds(regionX, regionZ)) return;
        

        createRegion(regionX, regionZ);
        String key = regionX + "_" + regionZ;

        RegionData regionData = _regions.get(key);
        if(regionData == null) return;

        int localX = block.getX() % 512;
        if (localX < 0)
            localX += 512;

        int localZ = block.getZ() % 512;
        if (localZ < 0)
            localZ += 512;

        byte[] blockData = getBlockData(block);
        for (int i = 0; i < blockData.length; i++) {
            regionData.data[8+(localZ * 4) * 512 + (localX * 4) + i] = blockData[i];
        }

        regionData.invalidate();

        synchronized(_updates) {
            _updates.add(new BlockPacket(block.getX(), block.getZ(), blockData, regionData.timestamp));
        }
        notifyChange();
    }

    public void updateRegion(int x, int z) {
        if(!_options.limits.regionInBounds(x, z)) {
            return;
        }
        
        synchronized(_queueRegions) {
            _queueRegions.add(new Offset(x, z));
        }
    }

    public void updateChunk(Chunk chunk, boolean isChunkLoadedEvent) {
        if(/*isChunkLoadedEvent ||*/ !_options.limits.chunkInBounds(chunk.getX(), chunk.getZ())) {
            return;
        }
        

        Offset offset = new Offset();
        offset.x = chunk.getX();
        offset.z = chunk.getZ();
        offset.onlyIfAbsent = isChunkLoadedEvent;

        synchronized(_queueChunks) {
            _queueChunks.add(offset);
        }
    }

    @Override
    public void onEnable(Map<String,ActionMethod> signature) {
        try{
            boundingBox = new BoundingBox();
            loadOptions();
            load();
        }catch(Exception ex){ex.printStackTrace();}

        if(_regions.size() == 0) {
            World world = Bukkit.getWorld(this.world);
            Chunk[] chunks = world.getLoadedChunks(); 
            for(Chunk c : chunks) {
                updateChunk(c, true);
            }
        }
        Bukkit.getServer().getPluginManager().registerEvents(this, Plugin.getInstance());

        super.onEnable(signature);
    }
       
    @Override
    public void onDisable(Map<String,ActionMethod> signature) {
        
        try{
            save();
        }catch(Exception ex){ex.printStackTrace();}

        saveOptions();

        _regions.clear();
        _updates.clear();
        _queueChunks.clear();


        super.onDisable(signature);
    }

    @Override
    public IPacket onJoinAsync(Identity identity) {
        JSONObject json = new JSONObject();
        json.put("world", world);
        //JSONArray syncable = new JSONArray();
        JSONArray regions = new JSONArray();
        json.put("regions", regions);
        //json.put("syncables", syncable);
       
        //json.put("boundingBox", )

        /*synchronized(_regions) {
            for(Entry<String,byte[]> entry : _regions.entrySet()) {
                regions.put(entry.getKey(), Base64.getEncoder().encodeToString(entry.getValue()));
            }
        }*/
        synchronized(_regions) {
            for(RegionData region : _regions.values()) {
                JSONObject entry = new JSONObject();
                entry.put("x", region.x);
                entry.put("z", region.z);
                entry.put("timestamp", region.timestamp);
                regions.put(entry);
            }
        }

       /* synchronized(_syncables) {
            for(Entry<String, LiveSyncable> entry : _syncables.entrySet()) {
                syncable.put(entry.getValue().serialize());
            }
        }*/

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

        synchronized(_updates) {
            for(IPacket update : _updates) {
                upd.put(update.toJson());
            }
            _updates.clear();
        }

       /* synchronized(_syncables) {
            for(Entry<String, LiveSyncable> entry : _syncables.entrySet()) {
                syncable.put(entry.getValue().serializeChanges());
            }
        }*/

        for(Identity identity : identities) {
            response.put(identity, new ModuleUpdatePacket(this, json, false));
        }

        return response;
    }

    long _frameStart = 0;
    @Override
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
    }

    private void createRegion(int regionX, int regionZ) {
        String key = regionX + "_" + regionZ;
        synchronized (_regions) {
            if (!_regions.containsKey(key)) {
                _regions.put(key, new RegionData(regionX, regionZ, new byte[8 + 512 * 512 * 4]));
                Arrays.fill(_regions.get(key).data, (byte) 0xFF);
                boundingBox.update(regionX, regionZ);
            }
        }
    }

    //multi frame rendering stuff
    //private int cpu_time = 30;
    private Offset _chunk;
    private RegionData _currentUpdate = null;
    private byte[] _chunkData;
    private IPacket update(Offset chunk) throws Exception {
        if(chunk != null && chunk.onlyIfAbsent && loadedChunkExists(chunk)) return null;

        //long start = System.currentTimeMillis();

        if(chunk != null) {
            Plugin.debug("Updating chunk " + chunk.x + " " + chunk.z);
            int regionX = (int) Math.floor(((double) chunk.x / 32.0));
            int regionZ = (int) Math.floor(((double) chunk.z / 32.0));
            String key = regionX + "_" + regionZ;

            //RegionData _currentUpdate;
            synchronized (_regions) {
                if (!_regions.containsKey(key)) {
                    _regions.put(key, new RegionData(regionX, regionZ, new byte[8 + 512 * 512 * 4]));
                    Arrays.fill(_regions.get(key).data, (byte) 0xFF);
                    boundingBox.update(regionX, regionZ);
                }
                _currentUpdate = _regions.get(key);
                _chunk = chunk;
            }
        }

        if (_currentUpdate != null) {
            if(_currentUpdate.rendering == false) {
                _currentUpdate.rendering = true;
                _currentUpdate.renderingX = 0;
                _currentUpdate.renderingZ = 0;
                _chunkData = new byte[16*16*4];
            }
            
            boolean unload = !Bukkit.getWorld(world).isChunkLoaded(_chunk.x, _chunk.z);
            Chunk c = Bukkit.getWorld(world).getChunkAt(_chunk.x, _chunk.z);
            
            for (int z = _currentUpdate.renderingZ; z < 16; z++) {
                for (int x = _currentUpdate.renderingX; x < 16; x++) {
                    Block block = c.getWorld().getHighestBlockAt(c.getX() * 16 + x, c.getZ() * 16 + z);
                    int localX = block.getX() % 512;
                    if (localX < 0)
                        localX += 512;

                    int localZ = block.getZ() % 512;
                    if (localZ < 0)
                        localZ += 512;

                    byte[] blockData = getBlockData(block);
                    for (int i = 0; i < blockData.length; i++) {
                        _currentUpdate.data[8 + (localZ * 4) * 512 + (localX * 4) + i] = blockData[i];
                        _chunkData[z * 4 * 16 + x*4 + i] = blockData[i];
                    }

                    if(System.currentTimeMillis() - _frameStart > _options.cpuTime) {
                        _currentUpdate.renderingZ = z;
                        _currentUpdate.renderingX = x+1;
                        return null;
                    }
                }
                _currentUpdate.renderingX = 0;
            }
            


            if(unload) Bukkit.getWorld(world).unloadChunk(_chunk.x, _chunk.z, false);

            _currentUpdate.invalidate();
            _currentUpdate.rendering = false;
            long timestamp = _currentUpdate.timestamp;
            _currentUpdate = null;
            _chunk = null;

            return new ChunkPacket(c.getX(), c.getZ(), _chunkData, timestamp);
        } else {
            throw new Exception("Region null error!!!");
        }
    }

    public boolean loadedChunkExists(Offset chunk) {
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
    }

    private byte[] getBlockData(Block block) {
        Integer height = null;

        while(block.getType() == Material.AIR && block.getY() > 0) {
            block = block.getRelative(BlockFace.DOWN);
        }

        byte biome = 0x00;
        if (block.getType() == Material.WATER) {
            biome |= 0x08;
            Block b = block.getRelative(BlockFace.DOWN);
            for (int i = 0; i < 100; i++) {
                if (b.getType() != Material.WATER) {
                    block = b;
                    height = ++i;
                    break;
                }
                b = b.getRelative(BlockFace.DOWN);
            }
        }
        if (isLeave(block)) {
            biome |= 0x04;
        }

        byte[] data = new byte[4];
        int dataId = getMaterialId(block.getType());
        data[1] = (byte) dataId;
        data[0] = (byte) (dataId >> 8);

        data[2] = height != null ? (byte) height.intValue() : (byte) block.getY();
        data[3] = biome;
        return data;
    }

    private boolean isLeave(Block block) {
        Material mat = block.getType();
        return /* mat == Material.LEGACY_LEAVES || mat == Material.LEGACY_LEAVES_2 || */
        mat == Material.ACACIA_LEAVES || mat == Material.BIRCH_LEAVES || mat == Material.OAK_LEAVES
                || mat == Material.DARK_OAK_LEAVES || mat == Material.SPRUCE_LEAVES || mat == Material.JUNGLE_LEAVES;
    }

    private int getMaterialId(Material material) {
        for (int i = 0; i < Material.values().length; i++) {
            if (material == Material.values()[i]) {
                return i;
            }
        }
        return 0;
    }

    public RegionData getRegionData(int x, int z) {
        return _regions.get(x+"_"+z);
    }

    public static byte[] loadRegion(int x, int z, String world) {
        File file = new File(Plugin.getInstance().getDataFolder(), "map/"+world+"/"+x+"_"+z+".region");
        if(!file.exists()) return null;

        try{
            return Files.readAllBytes(file.toPath());
        }catch(Exception ex){ex.printStackTrace();}
        return null;
    }

    private void saveOptions() {
        try{
            File file = new File(getDir(), "data.json");
            if(!file.exists()) file.createNewFile();

            JSONObject options = _options.toJson();
            JSONArray chunks = new JSONArray(_queueChunks.stream().map(c->c.toJson()).collect(Collectors.toList()));
            if(_chunk != null) chunks.put(_chunk.toJson());

            options.put("chunk_queue", chunks);
            options.put("region_queue", _queueRegions.stream().map(c->c.toJson()).collect(Collectors.toList()));

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

                    return;
                }
            }
        }catch(Exception ex){ex.printStackTrace();}

        Plugin.debug("LiveMapModule using default options "+world);
        if(_options==null) _options = new RenderingOptions();
        _options.limits = BoundingBox.fromWorld(world);
    }

    private void load() throws Exception{
        File folder = getDir();
        if(!folder.exists()) folder.mkdirs();

        for(File file : folder.listFiles()) {
            if(file.isFile() && file.getName().endsWith(".region")) {
                RegionData data =  new RegionData(file);
                _regions.put(data.x+"_"+data.z, data);
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
            }
        }
    }

    private File getDir() {
        return new File(Plugin.getInstance().getDataFolder(), "map/"+world);
    }

    public static class RegionData {
        protected int x;
        protected int z;
        protected byte[] data;
        protected long timestamp = 0;

        protected boolean rendering = false;
        protected int renderingX=0;
        protected int renderingZ=0;

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
    }

    private static class Offset implements Serializable {
        public int x;
        public int z;
        public boolean onlyIfAbsent;
        
        public Offset() {}

        public Offset(int x, int z) {
            this.x = x;
            this.z = z;
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
        if(!event.getBlock().getWorld().getName().equals(world)) return;

        if(event.getBlock().getY() == event.getBlock().getWorld().getHighestBlockAt(event.getBlock().getX(), event.getBlock().getZ()).getY()) {
            updateBlock(event.getBlock());
        }
    }

    @EventHandler
    public void onBlockBreakEvent(BlockBreakEvent event) {
        if(!isEnabled()) return;
        if(!event.getBlock().getWorld().getName().equals(world)) return;

        if(event.getBlock().getY() == event.getBlock().getWorld().getHighestBlockAt(event.getBlock().getX(), event.getBlock().getZ()).getY()) {
            updateBlock(event.getBlock().getRelative(BlockFace.DOWN));
        }        
    }

    @EventHandler
    public void onBlockFormEvent(BlockFormEvent event) {
        if(!isEnabled()) return;
        if(!event.getBlock().getWorld().getName().equals(world)) return;

        if(event.getBlock().getY() == event.getBlock().getWorld().getHighestBlockAt(event.getBlock().getX(), event.getBlock().getZ()).getY()) {
            updateBlock(event.getBlock());
        }     
    }

    @EventHandler
    public void onBlockGrowEvent(BlockGrowEvent event) {
        if(!isEnabled()) return;
        if(!event.getBlock().getWorld().getName().equals(world)) return;

        if(event.getBlock().getY() == event.getBlock().getWorld().getHighestBlockAt(event.getBlock().getX(), event.getBlock().getZ()).getY()) {
            updateBlock(event.getBlock());
        }     
    }

    @EventHandler
    public void onBlockSpreadEvent(BlockSpreadEvent event) {
        if(!isEnabled()) return;
        if(!event.getBlock().getWorld().getName().equals(world)) return;

        if(event.getBlock().getY() == event.getBlock().getWorld().getHighestBlockAt(event.getBlock().getX(), event.getBlock().getZ()).getY()) {
            updateBlock(event.getBlock());
        }    
    }

    @EventHandler
    public void onBlockExplodeEvent(BlockExplodeEvent event) {
        if(!isEnabled()) return;
        if(!event.getBlock().getWorld().getName().equals(world)) return;

        if(event.getBlock().getY() == event.getBlock().getWorld().getHighestBlockAt(event.getBlock().getX(), event.getBlock().getZ()).getY()) {
            updateBlock(event.getBlock());
        }    
    }

    @EventHandler
    public void onBlockFadeEvent(BlockFadeEvent event) {
        if(!isEnabled()) return;
        if(!event.getBlock().getWorld().getName().equals(world)) return;
    
        if(event.getBlock().getY() == event.getBlock().getWorld().getHighestBlockAt(event.getBlock().getX(), event.getBlock().getZ()).getY()) {
            updateBlock(event.getBlock());
        }    
    }


    // WORLD EVENTS

    @EventHandler
    private void onChunkPopulateEvent(ChunkPopulateEvent event) {
        if(!isEnabled()) return;
        if(!event.getWorld().getName().equals(world)) return;

        updateChunk(event.getChunk(), true);
    }

    @EventHandler
    private void onChunkLoadEvent(ChunkLoadEvent event) {
        if(!isEnabled()) return;
        if(!event.getWorld().getName().equals(world)) return;

        updateChunk(event.getChunk(), true);
    }

    @EventHandler
    private void onStructureGrowEvent(StructureGrowEvent event) {
        if(!isEnabled()) return;
        if(!event.getWorld().getName().equals(world)) return;

        List<Chunk> chunks = new ArrayList<Chunk>();
        for(BlockState bd : event.getBlocks()) {
            if(!chunks.contains(bd.getChunk())) {
                chunks.add(bd.getChunk());
                updateChunk(bd.getChunk(), false);
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


