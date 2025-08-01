package at.livekit.map;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitScheduler;
import org.json.JSONArray;
import org.json.JSONObject;

import at.livekit.modules.LiveMapModule.Offset;
import at.livekit.modules.LiveMapModule.RegionData;
import at.livekit.packets.IPacket;
import at.livekit.plugin.Plugin;
import at.livekit.utils.Legacy;
import at.livekit.utils.Utils;

public class RenderWorld 
{   
    private static int MAX_LOADED_REGIONS = 16;
    private static int TIMEOUT_LOADING = 15 * 1000;

    private String world;
    private String worldUID;
    private File workingDirectory;

    private List<Offset> _blockQueue = new ArrayList<Offset>();
    private List<Offset> _chunkQueue = new ArrayList<Offset>();
    private List<Offset> _regionQueue = new ArrayList<Offset>();

    private List<RegionInfo> _regions = new ArrayList<RegionInfo>(); 
    private List<RegionData> _loadedRegions = new ArrayList<RegionData>();

    private Object _taskLock = new Object();
    private RenderTask _task = null;
    
    private RenderBounds _bounds;
    private RenderJob _job;

    public RenderWorld(String world, String worldUID) {
        this.world = world;
        this.worldUID = worldUID;
        this.workingDirectory = new File(Plugin.getInstance().getDataFolder(), "map/"+worldUID);
        if(!this.workingDirectory.exists()) this.workingDirectory.mkdirs();

        RenderBounds bounds = null;
        if(Legacy.hasLegacySettingsCache(workingDirectory)) {
            bounds = Legacy.getRenderBoundsFromLegacySettingsCache(workingDirectory);
            Legacy.deleteLegacySettingsCache(workingDirectory);
            Plugin.log("Legacy data.json converted:");
            Plugin.log(bounds.toString());
        } else {
            File settings = new File(workingDirectory, "settings.json");
            if(settings.exists()) {
                try{
                    bounds = RenderBounds.fromJson(new JSONObject(new String(Files.readAllBytes(settings.toPath()))));
                }catch(Exception ex){ex.printStackTrace();}
            }
        }

        if(bounds != null && bounds.valid()) {
            this.setRenderBounds(bounds, false);
        } else {
            Plugin.log("RenderBounds are invalid for "+world+". Falling back to default bounds");
            Plugin.log(RenderBounds.DEFAULT.toString());
            this.setRenderBounds(RenderBounds.DEFAULT, true);
        }

        try{
            File cache = new File(workingDirectory, "cache.json");
            if(cache.exists()) {
                JSONObject root = new JSONObject(new String(Files.readAllBytes(cache.toPath())));
                JSONArray blocks = root.getJSONArray("blocks");
                synchronized(_blockQueue) {
                    for(int i = 0; i < blocks.length(); i++) {
                        _blockQueue.add(Offset.fromJson(blocks.getJSONObject(i)));
                    }
                }
                JSONArray chunks = root.getJSONArray("chunks");
                synchronized(_chunkQueue) {
                    for(int i = 0; i < chunks.length(); i++) {
                        _chunkQueue.add(Offset.fromJson(chunks.getJSONObject(i)));
                    }
                }
                if(root.has("job")) {
                    _job = RenderJob.fromJson(root.getJSONObject("job"));
                }
            }
        }catch(Exception ex){
            ex.printStackTrace();
        }


    }

    public void setRenderBounds(RenderBounds bounds, boolean save) {
        synchronized(_regions) {
            _regions.clear();
            long start = System.currentTimeMillis();
            for(File file : workingDirectory.listFiles()) {
                if(file.getAbsolutePath().endsWith(".region")) {
                    int x = Integer.parseInt(file.getName().split("_")[0]);
                    int z = Integer.parseInt(file.getName().split("_")[1].replace(".region", ""));

                    if(bounds.regionInBounds(x, z)) {
                        byte[] ts = readRegionHeader(file);
                        _regions.add(new RegionInfo(x, z, Utils.decodeTimestamp(ts)));
                    }
                }
            }
            _bounds = bounds;
            Plugin.log(_regions.size()+" regions detected for world "+world+ " took "+(System.currentTimeMillis()-start)+"ms");
        }
        try {
            if(save) Utils.writeFile(new File(workingDirectory, "settings.json"), bounds.toJson().toString());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        
        Bukkit.getScheduler().runTaskAsynchronously(Plugin.getInstance(), new Runnable() {
            @Override
            public void run() {
                try{
                    List<Offset> availableRegions = new ArrayList<Offset>();
                    for(File file : workingDirectory.listFiles()) {
                        if(file.getAbsolutePath().endsWith(".region")) {
                            int x = Integer.parseInt(file.getName().split("_")[0]);
                            int z = Integer.parseInt(file.getName().split("_")[1].replace(".region", ""));
                            availableRegions.add(new Offset(x, z));
                        }
                    }

                    List<Offset> worldRegions = Utils.getWorldRegions(world);
                    List<Offset> missingRegions = new ArrayList<Offset>();
                    for(Offset region : worldRegions) {
                        //Plugin.log("Checking region "+region.x+" "+region.z+" for "+world);
                        Offset matching = availableRegions.stream().filter(r->r.x == region.x && r.z == region.z).findFirst().orElse(null);
                        if(matching == null) {
                            missingRegions.add(region);
                        }
                    }

                    synchronized(_regionQueue) {
                        _regionQueue.addAll(missingRegions);
                        Plugin.debug("Added "+missingRegions.size()+" regions to queue for "+world);
                    }
                }catch(Exception ex){
                    ex.printStackTrace();
                }
            }
        });
    }

    public void startJob(RenderJob job) throws Exception {
        if(_job != null) throw new Exception("Renderjob still active");
        _job = job;
    }

    public void stopJob() {
        _job = null;
    }

    public RenderJob getRenderJob() {
        return _job;
    }

    public List<RegionInfo> getRegions() {
        return _regions;
    }

    public String getWorldName() {
        return world;
    }

    public RenderBounds getRenderBounds() {
        return _bounds;
    }

    public int regionCount() {
        synchronized(_regions) {
            return _regions.size();
        }
    }

    public boolean _needsUpdate = false;
    public boolean needsUpdate() {
        if(_task != null) return true;
        if(_job != null) return true;

        synchronized(_blockQueue) {
            if(_blockQueue.size() != 0) return true;
        }

        synchronized(_chunkQueue) {
            if(_chunkQueue.size() != 0) return true;
        }

        return false;
    }

    public void updateBlock(Block block) {
        if(!_bounds.blockInBounds(block.getX(), block.getZ())) return;

        synchronized(_blockQueue) {
            _blockQueue.add(new Offset(block.getX(), block.getZ()));
        }
    }

    public void updateChunk(Chunk chunk, boolean chunkLoadEvent) {
        updateChunk(chunk.getX(), chunk.getZ(), chunkLoadEvent);
    }

    private void updateChunk(int x, int z, boolean chunkLoadEvent) {
        if(!_bounds.chunkInBounds(x, z)) return;

        synchronized(_taskLock) {
            if(_task != null && chunkLoadEvent) {
                if(_task.offset.x == x && _task.offset.z == z) return;
            }
        }

        synchronized(_chunkQueue) {
            _chunkQueue.add(new Offset(x, z, chunkLoadEvent));
        }
    }

    public IPacket update(long frameStart, long cpuTime, boolean tick) {
        IPacket result = null;
        
        //long timestamp = System.currentTimeMillis();

        synchronized(_taskLock) {
            if(_task == null) {
                synchronized(_blockQueue) {
                    if(_blockQueue.size() != 0) {
                        Offset block = _blockQueue.remove(0);
                        _task = new BlockRenderTask(block);
                        if(!tick) _task.tickCount++;
                    }
                }
                if(_task == null) {
                    synchronized(_chunkQueue) {
                        if(_chunkQueue.size() != 0) {
                            Offset chunk = _chunkQueue.remove(0);
                            _task = new ChunkRenderTask(chunk);

                            if(!tick) _task.tickCount++;
                        }
                    }
                }
                if(_task == null) {
                    synchronized(_regionQueue) {
                        if(_regionQueue.size() != 0) {
                            Offset region = _regionQueue.remove(0);
                            _task = new RegionRenderTask(region);
                            if(!tick) _task.tickCount++;
                        }
                    }
                }
            }
        

            if(_task == null) {
                if(_job != null) {
                    Offset next = _job.next();
                    if(next == null) _job = null;
                    else { _task = new ChunkRenderTask(next); if(!tick) _task.tickCount++; }
                }
            }
        }

            if(_task != null) {
                if(tick) _task.tickCount++;

                if(_task instanceof RegionRenderTask) {
                    Plugin.debug("Region "+_task.regionX+" "+_task.regionZ+" State: "+_task.state.toString() + " offset: "+_task.offset.x+":"+_task.offset.z + " Is in bounds: "+_bounds.regionInBounds(_task.regionX, _task.regionZ));
                    Plugin.debug("Bounds: "+_bounds.getChunkLeft() + " "+_bounds.getChunkTop() + " "+_bounds.getChunkRight() + " "+_bounds.getChunkBottom());
                }
                //Filtering out of bounds regions
                if(!_bounds.regionInBounds(_task.regionX, _task.regionZ)) {
                    _task.state = RenderTaskState.DONE;
                }
                
                if(_task.state == RenderTaskState.IDLE) {
                    _task.state = RenderTaskState.LOADING_REGION;
                    _task.loadingWatchdog = System.currentTimeMillis();
                    if((_task.region = getLoadedRegion(_task.regionX, _task.regionZ)) != null) {
                        _task.loadingWatchdog = System.currentTimeMillis() - _task.loadingWatchdog;
                        if(_task instanceof RegionRenderTask) {
                            _task.state = RenderTaskState.DONE;
                        } else {
                            _task.state = RenderTaskState.RENDERING;
                        }
                    } else {
                        loadRegionAsync(_task.regionX, _task.regionZ);
                    }
                }
                if(_task.state == RenderTaskState.LOADING_REGION) {
                    _task.region = getLoadedRegion(_task.regionX, _task.regionZ);
                    if(_task.region != null) {
                        _task.loadingWatchdog = System.currentTimeMillis() - _task.loadingWatchdog;
                        if(_task instanceof RegionRenderTask) {
                            _task.state = RenderTaskState.DONE;
                        } else {
                            _task.state = RenderTaskState.RENDERING;
                        }
                    } else {
                        _needsUpdate = false;
                        if(System.currentTimeMillis() - _task.loadingWatchdog > TIMEOUT_LOADING) {
                            Plugin.severe("Region Loading watchdog! Failed to load region in time");
                            _task.state = RenderTaskState.DONE;
                        }
                    }
                }
                if(_task.state == RenderTaskState.RENDERING) {
                    if(_task.renderingWatchdog == 0) _task.renderingWatchdog = System.currentTimeMillis();
                    
                    try{
                        //long start = System.currentTimeMillis();

                        boolean done = Renderer.render(world, _task, cpuTime, frameStart, _bounds);

                        //System.out.println((System.currentTimeMillis()-start)+" rendereding  ms");

                        if(done == true) {
                            if(_task.offset.x == -28 && _task.offset.z == 37) {
                                Plugin.debug("chunk redered");
                            }

                            result = _task.result;
                            _task.state = RenderTaskState.DONE;
                        }
                    }catch(Exception ex){
                        ex.printStackTrace();
                        _task.state = RenderTaskState.DONE;
                    }
                }
                if(_task.state == RenderTaskState.DONE) {
                    if(_task.renderingWatchdog != 0) _task.renderingWatchdog = System.currentTimeMillis() - _task.renderingWatchdog;
                    
                    if(_task.region != null) {
                        /*if(_task.result != null && _task.isChunk()) {
                            _chunkCount++;
                        }*/
                        if(_task.offset.x == -28 && _task.offset.z == 37) {
                            Plugin.debug("Chunk done");
                        }

                        synchronized(_regions) {
                            RegionInfo info = _regions.stream().filter(i->i.x == _task.regionX && i.z == _task.regionZ).findFirst().orElse(null);
                            if(info != null) info.timestamp = _task.region.timestamp;
                        }
                    }
                    
                    //Plugin.debug(_task.toString());
                    synchronized(_taskLock) {
                        _task = null;
                    }
                }
            }
        

        //long current = System.currentTimeMillis();

        //if(current-timestamp > 0) System.out.println((current-timestamp)+"ms");
        /*if(System.currentTimeMillis() - _startMetric > 60*1000) {
            
        }*/

        return result;
    }

    /*double chunkPerSec = 0;
    int _chunkCount = 0;
    long _startMetric = 0;*/

    public void checkUnload() {
        RegionData _unloadTarget = null;
        synchronized(_loadedRegions) {
            boolean needsUnload = _loadedRegions.size() > MAX_LOADED_REGIONS;
            for(RegionData region : _loadedRegions) {
                if(System.currentTimeMillis() - region.timestamp > 30*1000) {
                    _unloadTarget = region;
                    break;
                }
            }
            if(_unloadTarget == null && needsUnload) {
                //System.out.println("Limit "+MAX_LOADED_REGIONS+" reached, forcing unload: "+_loadedRegions.size());
                _unloadTarget = _loadedRegions.get(0);
            }
        }
        if(_unloadTarget != null) unloadRegionAsync(_unloadTarget);
    }

    /*private boolean regionExists(int x, int z) {
        synchronized(_regions) {
            return _regions.stream().filter(r->r.x == x && r.z == z).findFirst().orElse(null) != null;
        }
    }*/

    private RegionData getLoadedRegion(int x, int z) {
        synchronized(_loadedRegions) {
            return _loadedRegions.stream().filter(r->r.getX() == x && r.getZ() == z && !r.isDead()).findFirst().orElse(null);
        }
    }

    private RegionData createRegion(int x, int z) {
        byte[] regionBuffer = new byte[8 + 512 * 512 * 4];
        Arrays.fill(regionBuffer, (byte)0xFF);

        RegionData region = new RegionData(x, z, regionBuffer);
        region.invalidate();
        synchronized(_loadedRegions) {
            _loadedRegions.add(region);
        }
        synchronized(_regions) {
            _regions.add(new RegionInfo(x, z, 0));
        }
        saveRegion(region);
        return region;
    }

    private void loadRegionAsync(int x, int z) {
        Bukkit.getScheduler().runTaskAsynchronously(Plugin.getInstance(), new Runnable(){
			@Override
			public void run() {
                boolean failedToLoad = false;
				boolean createNew = true;
                RegionData region = null;

                /*try {
                    long start = System.currentTimeMillis();
                    fastrenderRegion(x, z);
                    Plugin.debug("Fastrendering took "+(System.currentTimeMillis()-start)+"ms");
                }catch(Exception ex){ex.printStackTrace();};*/
                

                RegionData dead = null;
                synchronized(_loadedRegions) {
                    dead = _loadedRegions.stream().filter(r->r.getX() == x && r.getZ() == z).findFirst().orElse(null);
                }
                if(dead != null) {
                    Plugin.debug("Reviving dead region "+x + " " + z);
                    region = new RegionData(x, z, dead.data);
                    region.timestamp = dead.timestamp;
                    region.invalidate();
                    createNew = false;
                    synchronized(_loadedRegions) {
                        _loadedRegions.remove(dead);
                        _loadedRegions.add(region);
                    }
                    return;
                }
                
                try{
                    long start = System.currentTimeMillis();
                    File file = new File(workingDirectory,x +"_"+z+".region");
                    if(file.exists()) {
                        byte[] data = Files.readAllBytes(file.toPath());
                        if(data.length == 1048584) {
                            region = new RegionData(x, z, data);
                            region.timestamp = Utils.decodeTimestamp(data);
                            region.invalidate();

                        } else {
                            Plugin.debug("Invalid region detected! Creating new "+data.length);
                            failedToLoad = true;
                        }
                    }
                    
                    if(Utils.hasRegion(world, x, z) && (region == null || failedToLoad)) {
                        try {
                            byte[] data = new byte[8 + 512 * 512 * 4];
                            Arrays.fill(data, (byte)0xFF);
                            region = new FastRegionData(world, x, z, data);
                            ((FastRegionData)region).fastrender();
                            region.timestamp = Utils.decodeTimestamp(region.data);
                            region.invalidate();
                            failedToLoad = false;
                            synchronized(_regions) {
                                _regions.add(new RegionInfo(x, z, region.timestamp));
                            }
                            if(((FastRegionData)region).getFoundBlock() != null) {
                                synchronized(_blockQueue) {
                                    _blockQueue.add(((FastRegionData)region).getFoundBlock());
                                }
                            }
                        } catch(Exception ex) {
                            ex.printStackTrace();
                            region = null;
                            failedToLoad = true;
                        }
                        
                    }

                    Plugin.debug("Loading region "+x+" "+z+" took "+(System.currentTimeMillis()-start)+"ms ("+world+") " + (region != null ? (file.exists() ? " From File " : " Fast Rendered") : "Failed, creating new"));


                    if(region != null) {
                        synchronized(_loadedRegions) {
                            createNew = false;
                            _loadedRegions.add(region);
                        }
                        return;
                    }
                }catch(Exception ex){ex.printStackTrace();};

                if(createNew) {
                     region = createRegion(x, z);

                     /*if(failedToLoad) {
                        for(int cz = 0; cz < 32; cz++) {
                            for(int cx = 0; cx < 32; cx++) {
                                updateChunk(x*32 + cx, z*32 + cz, true);
                            }
                        }
                    }*/
                }
			}
        });
    }

    private void unloadRegionAsync(final RegionData region) {
        Bukkit.getScheduler().runTaskAsynchronously(Plugin.getInstance(), new Runnable(){
            @Override
            public void run() {
                synchronized(_taskLock) {
                    if(_task != null && _task.region == region) return;
                    region.setDead(true);
                }

                Plugin.debug("Unloading region "+region.getX()+" "+region.getZ());
                if(SHUTDOWN) return;
                saveRegion(region);
                
                synchronized(_loadedRegions) {
                    _loadedRegions.remove(region);
                }
            }
        });
    }

    private static boolean SHUTDOWN = false;

    private void saveRegion(RegionData region) {
        try{
            File file = new File(workingDirectory,region.getX()+"_"+region.getZ()+".region");
            if(!file.exists()) file.createNewFile();
    
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(region.data);
            } catch(Exception ex){ex.printStackTrace();}
        }catch(Exception ex){ex.printStackTrace();}
    }

    public void shutdown() {
        SHUTDOWN = true;
        Bukkit.getScheduler().cancelTasks(Plugin.getInstance());

        JSONObject root = new JSONObject();
        synchronized(_taskLock) {
            synchronized(_blockQueue) {
                if(_task != null && (_task instanceof BlockRenderTask)) _blockQueue.add(_task.offset);
                root.put("blocks", _blockQueue.stream().map(b->b.toJson()).collect(Collectors.toList()));
            }
        }
        synchronized(_taskLock) {
            synchronized(_chunkQueue) {
                _chunkQueue.clear();
                if(_task != null && (_task instanceof ChunkRenderTask)) _chunkQueue.add(_task.offset);
                root.put("chunks", _chunkQueue.stream().map(c->c.toJson()).collect(Collectors.toList()));
            }
        }
        if(_job != null) root.put("job", _job.toJson());
        Utils.tryWriteFile(new File(workingDirectory, "cache.json"), root.toString());

        synchronized(_loadedRegions) {
            for(RegionData region : _loadedRegions) saveRegion(region);
            _loadedRegions.clear();
        }
        synchronized(_blockQueue) {
            _blockQueue.clear();
        }
        synchronized(_chunkQueue) {
            _chunkQueue.clear();
        }
        synchronized(_regions) {
            _regions.clear();
        }
    }

    public byte[] getRegionDataAsync(int x, int z) throws Exception {
        RegionData region = getLoadedRegion(x, z);
        if(region != null) return region.data;

        File file = new File(workingDirectory, x+"_"+z+".region");
        try{
            if(file.exists()) {
                return Files.readAllBytes(file.toPath());
            }
        }catch(Exception ex){ex.printStackTrace();}

        throw new Exception("Invalid region requested");
    }

    private byte[] readRegionHeader(File file) {
        byte[] buffer = new byte[8];
        try{
            FileInputStream in = new FileInputStream(file);
            in.read(buffer, 0 , buffer.length);
            in.close();
        }catch(Exception ex){
            ex.printStackTrace();
        }
        return buffer;
    }

    public String getWorldInfoString() {
        int queueSize = 0;
        int regionSize = 0;

        String result = "";
        synchronized(_regions) {
            result += "Regions: "+_regions.size();
            queueSize += _regions.size();
        }
        synchronized(_loadedRegions) {
            result += " ["+_loadedRegions.size()+" Loaded]";
            regionSize += _loadedRegions.size();
        }
        synchronized(_blockQueue) {
            result += "\nBlock Queue: "+_blockQueue.size();
            queueSize += _blockQueue.size();
        }
        synchronized(_chunkQueue) {
            result += "\nChunk Queue: "+_chunkQueue.size();
            queueSize += _chunkQueue.size();
        }
        /*if(_task != null) {
            result += "\nCurrent Task: "+_task.toString();
        }
        result +="\n"+_bounds.toString();
        if(_job != null) {
            result += "\n" + _job.toString();
        }*/

        long ramQueues = queueSize * (4 + 4 + 1) + regionSize * (512*512*4+8+4+4+8);
        result += "\nRAM Estimate: "+(ramQueues/1024)+"kB";

        return result;
    }

    /*private void fastrenderRegion(int x, int z) throws Exception{
        Plugin.debug("Fastrendering region "+x+" "+z);
        File regionFile = new File(Plugin.getInstance().getDataFolder().getAbsolutePath()+"/../../"+world+"/region/r."+x+"."+z+".mca");
        if(!regionFile.exists()) {
            Plugin.debug("Region file not found: "+regionFile.getAbsolutePath());
            return;
        }

        try (RandomAccessFile raf = new RandomAccessFile(regionFile, "r")) {
            byte[] header = new byte[8192];
            raf.readFully(header);

            for (int i = 0; i < 1024; i++) {
                int offset = ((header[i * 4 + 0] & 0xFF) << 16) | ((header[i * 4 + 1] & 0xFF) << 8)  | (header[i * 4 + 2] & 0xFF);
                int sectorCount = header[i * 4 + 3] & 0xFF;
                int timestamp = ((header[i * 4 + 4096] & 0xFF) << 24) | ((header[i * 4 + 4096 + 1] & 0xFF) << 16) | ((header[i * 4  + 4096 + 2] & 0xFF) << 8)  | (header[i * 4 + 4096 + 3] & 0xFF);

                //Plugin.debug("Chunk "+i+" Offset: "+offset+" Timestamp: "+timestamp);


                if (offset == 0 || sectorCount == 0) {
                    continue;
                }

                raf.seek(offset * 4096);

                int length = raf.readInt();

                byte compressionType = raf.readByte();
                //Plugin.debug("Compression: "+compressionType);
                if (compressionType != 2) {
                    System.err.println("Unsupported compression type at index " + i + ": " + compressionType);
                    continue;
                }

                byte[] chunkData = new byte[length - 1];
                try {
                    raf.readFully(chunkData);
                } catch (EOFException e) {
                    System.err.println("EOFException while reading chunk data at index " + i);
                    break;
                }

                InputStream is = new ByteArrayInputStream(chunkData);
                if (compressionType == 1) {
                    is = new GZIPInputStream(is);
                } else if (compressionType == 2) {
                    is = new InflaterInputStream(is);
                } else {
                    System.err.println("Unsupported compression type at index " + i + ": " + compressionType);
                    continue;
                }

                try (DataInputStream nbtIn = new DataInputStream(is)) {
                    NamedTag namedTag;
                    try (NBTInputStream nis = new NBTInputStream(nbtIn)) {
                        namedTag = nis.readTag(512); // Specify a maximum depth of 512
                    }
                    if (namedTag != null && namedTag.getTag() instanceof CompoundTag) {
                        CompoundTag chunkTag = (CompoundTag) namedTag.getTag();
                        parseChunk(chunkTag);
                        //return;
                    } else {
                        System.err.println("Invalid or corrupt chunk data at index " + i);
                    }
                } catch (EOFException e) {
                    System.err.println("EOFException while reading NBT data at index " + i);
                }
            }
        }
    }

    private void parseChunk(CompoundTag chunkTag) {
        ListTag<CompoundTag> sections = chunkTag.getListTag("sections").asCompoundTagList();

        // A map to store the highest block ID for each x, z coordinate
        Map<Integer, Integer> highestBlocks = new HashMap<>();
        Map<Integer, String> blockNames = new HashMap<>();
        Map<Integer, String> blockBiomes = new HashMap<>();

        // Iterate over each section in the chunk
        for (CompoundTag section : sections) {
            int yBase = section.getByte("Y") * 16;

            if (!section.containsKey("block_states") || !section.containsKey("biomes")) {
                continue;
            }

            CompoundTag blockStates = section.getCompoundTag("block_states");
            CompoundTag biomes = section.getCompoundTag("biomes");

            long[] blockStatesLong = blockStates.getLongArray("data");
            long[] biomesLong = biomes.getLongArray("data");

            if (blockStatesLong.length == 0 || blockStates.getListTag("palette") == null || biomesLong.length == 0 || biomes.getListTag("palette") == null) {
                continue;
            }

            ListTag<CompoundTag> blockPalette = blockStates.getListTag("palette").asCompoundTagList();
            ListTag<StringTag> biomePalette = biomes.getListTag("palette").asStringTagList();

            int bitsPerBlock = Math.max(4, (int) Math.ceil(Math.log(blockPalette.size()) / Math.log(2)));
            int blocksPerLong = 64 / bitsPerBlock;

            int bitsPerBiome = Math.max(1, (int) Math.ceil(Math.log(biomePalette.size()) / Math.log(2)));
            int biomesPerLong = 64 / bitsPerBiome;

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int height = 15; height >= 0; height--) {
                        int index = (height * 16 * 16) + (z * 16) + x;
                        int blockLongIndex = index / blocksPerLong;
                        int blockStartBit = (index % blocksPerLong) * bitsPerBlock;
                        int blockPaletteIndex = (int) ((blockStatesLong[blockLongIndex] >>> blockStartBit) & ((1 << bitsPerBlock) - 1));

                        String blockName = blockPalette.get(blockPaletteIndex).getString("Name");

                        if (!blockName.equals("minecraft:air")) {
                            int globalY = yBase + height;
                            int key = (x << 8) | z;

                            int biomeIndex = (height / 4) * 16 + (z / 4) * 4 + (x / 4);
                            int biomeLongIndex = biomeIndex / biomesPerLong;
                            int biomeStartBit = (biomeIndex % biomesPerLong) * bitsPerBiome;
                            int biomePaletteIndex = (int) ((biomesLong[biomeLongIndex] >>> biomeStartBit) & ((1 << bitsPerBiome) - 1));
                            String biomeName = biomePalette.get(biomePaletteIndex).getValue();//.getString("Name");

                            if (!highestBlocks.containsKey(key) || globalY > highestBlocks.get(key)) {
                                highestBlocks.put(key, globalY);
                                blockNames.put(key, blockName);
                                blockBiomes.put(key, biomeName);
                            }
                            break;
                        }
                    }
                }
            }
        }

        // Print or use the highestBlocks map as needed
        for (Map.Entry<Integer, Integer> entry : highestBlocks.entrySet()) {
            int x = (entry.getKey() >> 8) & 0xFF;
            int z = entry.getKey() & 0xFF;
            int y = entry.getValue();
            String blockName = blockNames.get(entry.getKey());
            String biomeName = blockBiomes.get(entry.getKey());
            System.out.println("Topmost block at (" + x + ", " + z + "): " + y + " (" + blockName + ") in biome: " + biomeName);
        }
        /*chunkTag.entrySet().forEach(entry -> {
            String key = entry.getKey();
            Plugin.debug(key + ": " + entry.getValue().toString());
        });

        CompoundTag level = chunkTag.getCompoundTag("Level");*/
        /*ListTag<CompoundTag> sections = chunkTag.getListTag("sections").asCompoundTagList();

        // A map to store the highest block ID for each x, z coordinate
        Map<Integer, Integer> highestBlocks = new HashMap<>();
        Map<Integer, String> blockNames = new HashMap<>();

        // Iterate over each section in the chunk
        for (CompoundTag section : sections) {
            //Plugin.debug(section.toString());
            int yBase = section.getByte("Y") * 16;
            CompoundTag blockStates = section.getCompoundTag("block_states");
            CompoundTag biomes = section.getCompoundTag("biomes");

            long[] blockStatesLong = blockStates.getLongArray("data");
            long[] biomesLong = biomes.getLongArray("data");

            if(blockStatesLong.length == 0) continue;
            if(blockStates.getListTag("palette") == null) continue;

            //Plugin.debug("Palette found");

            ListTag<CompoundTag> palette = blockStates.getListTag("palette").asCompoundTagList();
            //int paletteLength = palette != null ? palette.size() : 0;

            int bitsPerBlock = Math.max(4, (int) Math.ceil(Math.log(palette.size()) / Math.log(2)));
            int blocksPerLong = 64 / bitsPerBlock;

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int height = 15; height >= 0; height--) {
                        int index = (height * 16 * 16) + (z * 16) + x;
                        int longIndex = index / blocksPerLong;
                        int startBit = (index % blocksPerLong) * bitsPerBlock;
                        int paletteOffset = (int) ((blockStatesLong[longIndex] >>> startBit) & ((1 << bitsPerBlock) - 1));

                        String blockName = palette.get(paletteOffset).getString("Name");

                        
                        
                        
                        if (!blockName.equals("minecraft:air")) {
                            int globalY = yBase + height;
                            int key = (x << 8) | z;
                            if (!highestBlocks.containsKey(key) || globalY > highestBlocks.get(key)) {
                                highestBlocks.put(key, globalY);
                                blockNames.put(key, blockName);
                            }
                            break;
                        }
                    }
                }
            }

        }

        // Print or use the highestBlocks map as needed
        for (Map.Entry<Integer, Integer> entry : highestBlocks.entrySet()) {
            int x = (entry.getKey() >> 8) & 0xFF;
            int z = entry.getKey() & 0xFF;
            int y = entry.getValue();
            System.out.println("Topmost block at (" + x + ", " + z + "): " + y + " (" + blockNames.get(entry.getKey()) + ")");
        }*/
    //}

    private enum RenderTaskState {
        IDLE, LOADING_REGION, RENDERING, DONE
    }

    public static class BlockRenderTask extends RenderTask {
        public BlockRenderTask(Offset offset) {
            super(offset);
            this.regionX = (int) Math.floor(((double) offset.x / 512.0 ));
            this.regionZ = (int) Math.floor(((double) offset.z / 512.0 ));
        }

        public Offset getBlockOffset() {
            return offset;
        }
    }

    public static class ChunkRenderTask extends RenderTask {
        public ChunkRenderTask(Offset offset) {
            super(offset);
            this.regionX = (int) Math.floor(((double) offset.x / 32.0 ));
            this.regionZ = (int) Math.floor(((double) offset.z / 32.0 ));
        }

        public Offset getChunkOffset() {
            return offset;
        }
    }

    public static class RegionRenderTask extends RenderTask {
        public RegionRenderTask(Offset offset) {
            super(offset);
            this.regionX = offset.x;
            this.regionZ = offset.z;
        }

        public Offset getRegionOffset() {
            return offset;
        }
    }

    public static class RenderTask {
        private RenderTaskState state;
        protected Offset offset;
        //private boolean chunk;

        protected int regionX;
        protected int regionZ;

        public RegionData region;

        private long loadingWatchdog;
        private long renderingWatchdog;
        private long tickCount = 0;

        //rendering variables
        public boolean rendering = false;
        public int renderingX=0;
        public int renderingZ=0;
        public boolean unload = false;
        public ChunkSnapshot rchunk;

        public byte[] buffer;
        public IPacket result;

        public RenderTask(Offset offset/*, boolean chunk*/) {
            this.offset = offset;
            //this.chunk = chunk;
            this.state = RenderTaskState.IDLE;

            //this.regionX = (int) Math.floor(((double) offset.x / (chunk ? 32.0 : 512.0 )));
            //this.regionZ = (int) Math.floor(((double) offset.z / (chunk ? 32.0 : 512.0 )));
        }

        public boolean isChunkLoadEvent() {
            return offset.onlyIfAbsent;
        }

        /*public boolean isChunk() {
            return chunk;
        }*/

        public int getRegionX() {
            return regionX;
        }

        public int getRegionZ() {
            return regionZ;
        }

        /*public Offset getChunkOrBlock() {
            return offset;
        }*/

        @Override
        public String toString() {
            return "RenderTask[state="+state.name()+"; x="+offset.x+"; z="+offset.z+"; regionX="+regionX+"; regionZ="+regionZ+" ticks="+tickCount+"; loading="+loadingWatchdog+"ms; rendering="+renderingWatchdog+"ms]";
        }
    }

    public class RegionInfo extends Offset {
        public long timestamp;

        public RegionInfo(int x, int z, long timestamp) {
            this.x = x;
            this.z = z;
            this.timestamp = timestamp;
        }
    }
}
