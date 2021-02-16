package at.livekit.map;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.block.Block;

import at.livekit.modules.LiveMapModule.Offset;
import at.livekit.modules.LiveMapModule.RegionData;
import at.livekit.packets.IPacket;
import at.livekit.plugin.Plugin;

public class RenderWorld 
{   
    private static int TIMEOUT_LOADING = 40 * 1000;

    private String world;
    private File workingDirectory;

    //private BoundingBox _boundingBox = new BoundingBox();
    private List<Offset> _blockQueue = new ArrayList<Offset>();
    private List<Offset> _chunkQueue = new ArrayList<Offset>();

    private List<Offset> _regions = new ArrayList<Offset>(); 
    private List<RegionData> _loadedRegions = new ArrayList<RegionData>();

    private RenderTask _task = null;
    
    private RenderBounds _bounds;
    private RenderJob _job;

    public RenderWorld(String world) {
        this.world = world;
        this.workingDirectory = new File(Plugin.getInstance().getDataFolder(), "map/"+world);
        if(!this.workingDirectory.exists()) this.workingDirectory.mkdirs();

        _bounds = new RenderBounds(-512*20, -512*20, 511*20, 511*20);
        //TODO: only load regions where render bounds

        synchronized(_regions) {
            for(File file : workingDirectory.listFiles()) {
                if(file.getAbsolutePath().endsWith(".region")) {
                    int x = Integer.parseInt(file.getName().split("_")[0]);
                    int z = Integer.parseInt(file.getName().split("_")[1].replace(".region", ""));
                    _regions.add(new Offset(x, z));
                }
            }
            Plugin.debug(_regions.size()+" regions detected for world "+world);
        }
    }

    public void startJob(RenderJob job) throws Exception {
        if(_job != null) throw new Exception("Renderjob still active");
        _job = job;
    }

    public void stopJob() {
        _job = null;
    }

    public List<Offset> getRegions() {
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
        if(!_bounds.chunkInBounds(chunk.getX(), chunk.getZ())) return;
        if(_task != null && chunkLoadEvent) {
            if(_task.offset.x == chunk.getX() && _task.offset.z == chunk.getZ()) return;
        }

        synchronized(_chunkQueue) {
            _chunkQueue.add(new Offset(chunk.getX(), chunk.getZ(), chunkLoadEvent));
        }
    }

    public IPacket update(long frameStart, long cpuTime, boolean tick) {
        IPacket result = null;
        
        if(_task == null) {
            synchronized(_blockQueue) {
                if(_blockQueue.size() != 0) {
                    Offset block = _blockQueue.remove(0);
                    _task = new RenderTask(block, false);
                    if(!tick) _task.tickCount++;
                }
            }
            if(_task == null) {
                synchronized(_chunkQueue) {
                    if(_chunkQueue.size() != 0) {
                        Offset chunk = _chunkQueue.remove(0);
                        _task = new RenderTask(chunk, true);
                        if(!tick) _task.tickCount++;
                    }
                }
            }
        }

        if(_task == null) {
            if(_job != null) {
                Offset next = _job.next();
                if(next == null) _job = null;
                else { _task = new RenderTask(next, true); if(!tick) _task.tickCount++; }
            }
        }

        if(_task != null) {
            if(tick) _task.tickCount++;

            if(_task.state == RenderTaskState.IDLE) {
                _task.state = RenderTaskState.LOADING_REGION;
                _task.loadingWatchdog = System.currentTimeMillis();
                if((_task.region = getLoadedRegion(_task.regionX, _task.regionZ)) != null) {
                    _task.loadingWatchdog = System.currentTimeMillis() - _task.loadingWatchdog;
                    _task.state = RenderTaskState.RENDERING;
                } else {
                    loadRegionAsync(_task.regionX, _task.regionZ);
                }
            }
            if(_task.state == RenderTaskState.LOADING_REGION) {
                _task.region = getLoadedRegion(_task.regionX, _task.regionZ);
                if(_task.region != null) {
                    _task.loadingWatchdog = System.currentTimeMillis() - _task.loadingWatchdog;
                    _task.state = RenderTaskState.RENDERING;
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
                    long start = System.currentTimeMillis();

                    boolean done = Renderer.render(world, _task, cpuTime, frameStart);

                    System.out.println((System.currentTimeMillis()-start)+" rendereding  ms");

                    if(done == true) {
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
                Plugin.debug(_task.toString());
                //TODO: doing this for testing purpose...
                //if(_task.region != null) unloadRegionAsync(_task.region);
                _task = null;
            }
        }



        return result;
    }

    public void checkUnload() {
        RegionData _unloadTarget = null;
        synchronized(_loadedRegions) {
            for(RegionData region : _loadedRegions) {
                if(System.currentTimeMillis() - region.timestamp > 5*1000) {
                    _unloadTarget = region;
                    break;
                }
            }
        }
        if(_unloadTarget != null) unloadRegionAsync(_unloadTarget);
    }

    private boolean regionExists(int x, int z) {
        synchronized(_regions) {
            return _regions.stream().filter(r->r.x == x && r.z == z).findFirst().orElse(null) != null;
        }
    }

    private RegionData getLoadedRegion(int x, int z) {
        synchronized(_loadedRegions) {
            return _loadedRegions.stream().filter(r->r.getX() == x && r.getZ() == z && !r.isDead()).findFirst().orElse(null);
        }
    }

    private RegionData createRegion(int x, int z) {
        byte[] regionBuffer = new byte[8 + 512 * 512 * 4];
        Arrays.fill(regionBuffer, (byte)0xFF);

        RegionData region = new RegionData(x, z, regionBuffer);
        synchronized(_loadedRegions) {
            _loadedRegions.add(region);
        }
        synchronized(_regions) {
            _regions.add(new Offset(x, z));
        }
        return region;
    }

    private void loadRegionAsync(int x, int z) {
        System.out.println("LOADING REGION SCHED");
        Bukkit.getScheduler().runTaskAsynchronously(Plugin.getInstance(), new Runnable(){
			@Override
			public void run() {
				boolean createNew = true;
                if(regionExists(x, z)) {
                    File file = new File(workingDirectory, x+"_"+z+".region");
                    try{
                        if(file.exists()) {
                            byte[] data = Files.readAllBytes(file.toPath());
                            RegionData region = new RegionData(x, z, data);
                            region.timestamp = 0;
                            for(int i = 0; i < 8; i++) {
                                region.timestamp <<= 8;
                                region.timestamp |= data[i];
                            }
                            createNew = false;
                            synchronized(_loadedRegions) {
                                _loadedRegions.add(region);
                            }
                        }
                    }catch(Exception ex){ex.printStackTrace();}
                }
                if(createNew) createRegion(x, z);

                System.out.println("LOADING REGION LOADED "+x+" "+z);
			}
        });
    }

    private void unloadRegionAsync(RegionData region) {
        Bukkit.getScheduler().runTaskAsynchronously(Plugin.getInstance(), new Runnable(){
            @Override
            public void run() {
                if(_task != null && _task.region == region) return;
                region.setDead(true);

                System.out.println("Unloading region "+region.getX()+" "+region.getZ());
                saveRegion(region);

                synchronized(_loadedRegions) {
                    _loadedRegions.remove(region);
                }
            }
        });
    }

    private void saveRegion(RegionData region) {
        region.save(workingDirectory);
    }

    public void shutdown() {
        
        //TODO: save region queue
        //TODO: save chunk queue
        //TODO: save block queue

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

    public String getWorldInfoString() {
        int queueSize = 0;
        int regionSize = 0;

        String result = "World info of "+world;
        synchronized(_regions) {
            result += "\nRegions: "+_regions.size();
            queueSize += _regions.size();
        }
        synchronized(_loadedRegions) {
            result += "\nLoaded: "+_loadedRegions.size();
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
        if(_task != null) {
            result += "\nCurrent Task: "+_task.toString();
        }
        result +="\n"+_bounds.toString();
        if(_job != null) {
            result += "\n" + _job.toString();
        }

        long ramQueues = queueSize * (4 + 4 + 1) + regionSize * (512*512*4+8+4+4+8);
        result += "\nRAM Estimate: "+(ramQueues/1024)+"kB";

        return result;
    }

    private enum RenderTaskState {
        IDLE, LOADING_REGION, RENDERING, DONE
    }

    public static class RenderTask {
        private RenderTaskState state;
        private Offset offset;
        private boolean chunk;

        private int regionX;
        private int regionZ;

        public RegionData region;

        private long loadingWatchdog;
        private long renderingWatchdog;
        private long tickCount = 0;

        //rendering variables
        public boolean rendering = false;
        public int renderingX=0;
        public int renderingZ=0;

        public byte[] buffer;
        public IPacket result;

        public RenderTask(Offset offset, boolean chunk) {
            this.offset = offset;
            this.chunk = chunk;
            this.state = RenderTaskState.IDLE;

            this.regionX = (int) Math.floor(((double) offset.x / (chunk ? 32.0 : 512.0 )));
            this.regionZ = (int) Math.floor(((double) offset.z / (chunk ? 32.0 : 512.0 )));
        }

        public boolean isChunk() {
            return chunk;
        }

        public int getRegionX() {
            return regionX;
        }

        public int getRegionZ() {
            return regionZ;
        }

        public Offset getChunkOrBlock() {
            return offset;
        }

        @Override
        public String toString() {
            return "RenderTask[state="+state.name()+"; chunk="+chunk+"; x="+offset.x+"; z="+offset.z+"; regionX="+regionX+"; regionZ="+regionZ+" ticks="+tickCount+"; loading="+loadingWatchdog+"ms; rendering="+renderingWatchdog+"ms]";
        }
    }
}
