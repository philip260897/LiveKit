package at.livekit.main;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.json.JSONObject;

import at.livekit.livekit.LiveKitClient;
import at.livekit.packets.BoundingBoxPacket;
import at.livekit.packets.TimeWeatherPacket;
import at.livekit.plugin.Plugin;
import at.livekit.server.BlockPacket;
import at.livekit.server.ChunkPacket;
import at.livekit.server.IPacket;
import at.livekit.server.MultiPacket;
import at.livekit.server.RegionPacket;
import at.livekit.server.SyncablePacket;
import at.livekit.server.SyncableRemovedPacket;
import at.livekit.server.TCPServer;
import at.livekit.server.TCPServer.RemoteClient;
import at.livekit.utils.HeadLibrary;

public class LiveMap implements Runnable {
    public static int TICK_RATE = 8;

    private String world;

    private List<Block> _queueBlocks = new ArrayList<Block>();
    
    private List<Chunk> _queueChunks = new ArrayList<Chunk>();
    private List<Chunk> _queueLoaded = new ArrayList<Chunk>();

    private BoundingBox boundingBox = new BoundingBox();
    private HashMap<String, byte[]> regions = new HashMap<String, byte[]>();
    private HashMap<String, LiveSyncable> syncables = new HashMap<String, LiveSyncable>();

    private Thread thread;
    private boolean abort = false;
    private TCPServer server;

    public LiveMap(String world, TCPServer server) {
        this.world = world;
        this.server = server;
    }

    public void registerSyncable(LiveSyncable syncable) {
        if(!this.syncables.containsKey(syncable.getUUID())) {
            synchronized(syncables) {
                this.syncables.put(syncable.getUUID(), syncable);
            }
        }
    }

    public void removeSyncable(String uuid) {
        synchronized(syncables) {
            if(this.syncables.containsKey(uuid)) {
                this.syncables.remove(uuid);
                this.server.broadcastForWorld(world, new SyncableRemovedPacket(uuid));
            }
        }
    }

    public LiveSyncable getSyncable(String uuid) {
        if(this.syncables.containsKey(uuid)) {
            return this.syncables.get(uuid);
        }
        return null;
    }

    public void start() {
        thread = new Thread(this);
        thread.start();
    }

    public void close(){
        if (thread != null && thread.isAlive()) {
            //thread.stop();
            abort = true;
            thread.interrupt();
        }
    }

    public boolean loadedChunkExists(Chunk chunk) {
        int regionX = (int) Math.floor(((double) chunk.getX() / 32.0));
        int regionZ = (int) Math.floor(((double) chunk.getZ() / 32.0));
        String key = regionX + "_" + regionZ;

        if(!regions.containsKey(key)) return false;

        int localX = chunk.getX()*16 % 512;
        if (localX < 0)
            localX += 512;

        int localZ = chunk.getZ()*16 % 512;
        if (localZ < 0)
            localZ += 512;

        for(int i = 0; i < 4; i++) {
           if( regions.get(key)[(localZ * 4 * 512) + (localX * 4) + i] != (byte)0xFF) return true;
        }
        return false;
    }

    public void queueLoaded(Chunk chunk) {
        synchronized(_queueLoaded) {
            _queueLoaded.add(chunk);
        }
    }

    public void queue(Chunk chunk) {
        synchronized(_queueChunks) {
            _queueChunks.add(chunk);
        }
    }

    public void queue(Block block) {
        synchronized(_queueBlocks) {
            _queueBlocks.add(block);
        }
    }

    public HashMap<String, byte[]> getRegions() {
        return regions;
    }

    private IPacket update(Block block) throws Exception {
        int regionX = (int) Math.floor(((double) block.getX() / 512.0));
        int regionZ = (int) Math.floor(((double) block.getZ() / 512.0));
        String key = regionX + "_" + regionZ;

        byte[] regionData;
        synchronized (regions) {
            if (!regions.containsKey(key)) {
                regions.put(key, new byte[512 * 512 * 4]);
                Arrays.fill(regions.get(key), (byte) 0xFF);
                boundingBox.update(regionX, regionZ);
            }
            regionData = regions.get(key);
        }

        if (regionData != null) {
            int localX = block.getX() % 512;
            if (localX < 0)
                localX += 512;

            int localZ = block.getZ() % 512;
            if (localZ < 0)
                localZ += 512;

            byte[] blockData = getBlockData(block);
            for (int i = 0; i < blockData.length; i++) {
                regionData[(localZ * 4) * 512 + (localX * 4) + i] = blockData[i];
            }

            return new BlockPacket(block.getX(), block.getZ(), blockData);
        } else {
            throw new Exception("Region null error!!!");
        }
    }

    private IPacket update(Chunk chunk) throws Exception {
        System.out.println("[LiveKit] Updating chunk " + chunk.getX() + " " + chunk.getZ());
        int regionX = (int) Math.floor(((double) chunk.getX() / 32.0));
        int regionZ = (int) Math.floor(((double) chunk.getZ() / 32.0));
        String key = regionX + "_" + regionZ;

        byte[] regionData;
        synchronized (regions) {
            if (!regions.containsKey(key)) {
                regions.put(key, new byte[512 * 512 * 4]);
                Arrays.fill(regions.get(key), (byte) 0xFF);
                boundingBox.update(regionX, regionZ);
            }
            regionData = regions.get(key);
        }

        if (regionData != null) {
            byte[] chunkData = new byte[16*16*4];
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    Block block = chunk.getWorld().getHighestBlockAt(chunk.getX() * 16 + x, chunk.getZ() * 16 + z);
                    int localX = block.getX() % 512;
                    if (localX < 0)
                        localX += 512;

                    int localZ = block.getZ() % 512;
                    if (localZ < 0)
                        localZ += 512;

                    byte[] blockData = getBlockData(block);
                    for (int i = 0; i < blockData.length; i++) {
                        regionData[(localZ * 4) * 512 + (localX * 4) + i] = blockData[i];
                        chunkData[z * 4 * 16 + x*4 + i] = blockData[i];
                    }
                }
            }

            return new ChunkPacket(chunk.getX(), chunk.getZ(), chunkData);
        } else {
            throw new Exception("Region null error!!!");
        }
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

    public void fullUpdate(LiveKitClient client) {
        client.sendPacket(new BoundingBoxPacket(boundingBox));
        synchronized(regions) {
            for(Entry<String, byte[]> entry : regions.entrySet()) {
                IPacket packet = RegionPacket.fromMapEntry(entry.getKey(), entry.getValue());
                client.sendPacket(packet);
            }
        }
        synchronized(syncables) {
            for(Entry<String, LiveSyncable> e : syncables.entrySet()) {
                client.sendPacket(new SyncablePacket(e.getValue().serialize()));
            }
        }
    }

    Future<int[]> futureWorld = null;
    long futureWorldUpdate = 0;

    @Override
    public void run() {
        try{
            load();
        }catch(Exception ex){ex.printStackTrace();}

        if(regions.size() == 0) {
            World world = Bukkit.getWorld(this.world);
            Chunk[] chunks = world.getLoadedChunks(); 

            synchronized(_queueChunks) {
                for(Chunk c : chunks) {
                    _queueChunks.add(c);
                }
            }
        }

        //handle online players on reload
        for(Player player : Plugin.instance.getServer().getOnlinePlayers()) {
            LiveEntity entity = new LiveEntity(player.getUniqueId().toString(), player.getDisplayName(), null);
            entity.updateLocation(player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ());
            entity.updateHealth(player.getHealthScale());
            entity.updateExhaustion(player.getExhaustion());
            if(!HeadLibrary.has(player.getUniqueId().toString())) { 
                HeadLibrary.resolveAsync(player.getUniqueId().toString());
            } 
            entity.updateHead(HeadLibrary.get(player.getUniqueId().toString()));
            registerSyncable(entity);
        }

        int interval = 1000 / TICK_RATE;
        
        while(!abort) {
            Long start = System.currentTimeMillis();

            //handle world weather time polling
            if(futureWorld == null) {
                futureWorld = getWorldTimeWeather();
                this.registerSyncable(new LiveWeather(world));
            }
            if(futureWorld.isDone() && (futureWorldUpdate + 1000) < System.currentTimeMillis()) {
                try{
                    int[] result = futureWorld.get();
                    LiveWeather weather = (LiveWeather) getSyncable(world+"-time-weather-syncable");
                    if(weather != null) {
                        weather.update(result);
                    }
                    //System.out.println(result[0] + " " + result[1] + " " + result[2]);
                }catch(Exception ex){ex.printStackTrace();}
                futureWorld = getWorldTimeWeather();
                futureWorldUpdate = System.currentTimeMillis();
            }

            List<IPacket> packets = new ArrayList<IPacket>();
            synchronized(syncables) {
                for(Entry<String, LiveSyncable> e : syncables.entrySet()) {
                    if(e.getValue().hasChanges()) {
                        packets.add(new SyncablePacket(e.getValue().serializeChanges()));
                    }
                }
            }

            while(_queueBlocks.size() > 0 && !abort && (System.currentTimeMillis() - start) < interval){
                Block b;
                synchronized(_queueBlocks) {
                    b = _queueBlocks.remove(0);
                }
                try{
                    packets.add(update(b));
                }catch(Exception ex){ex.printStackTrace();}
            }

            while(_queueChunks.size() > 0 && !abort && (System.currentTimeMillis() - start) < interval) {
                Chunk c;
                synchronized(_queueChunks) {
                   c = _queueChunks.remove(0);
                }
                try{
                    packets.add(update(c));
                }catch(Exception ex){ex.printStackTrace();}
            }


            

            if(packets.size() > 0) this.server.broadcastForWorld(world, new MultiPacket(packets));

            try{
                long sleepTime = interval -  (System.currentTimeMillis() - start);
                if(sleepTime > 0) {
                    Thread.sleep(sleepTime);
                } 
            }catch(InterruptedException ex){}
        }

        

        try {
            System.out.println("Saving map state...");
            save();
        }catch(Exception ex){ex.printStackTrace();}
    }

    private void load() throws Exception{
        File file = new File(System.getProperty("user.dir") + "/plugins/LiveKit/"+world+".json");
        if(file.exists()) {
            JSONObject json = new JSONObject(new String(Files.readAllBytes(Paths.get(System.getProperty("user.dir") + "/plugins/LiveKit/"+world+".json"))));
            for(String key : json.keySet()) {
                regions.put(key, Base64.getDecoder().decode(json.getString(key)));
                boundingBox.update(Integer.parseInt(key.split("_")[0]), Integer.parseInt(key.split("_")[1]));
            }
            System.out.println("Loaded LiveKit '"+world+"' from file. "+regions.size()+" regions!");
        }
    }

    private void save() throws FileNotFoundException, IOException {
        File file = new File(System.getProperty("user.dir") + "/plugins/LiveKit/"+world+".json");
        if(!file.exists()) file.createNewFile();

        JSONObject json = new JSONObject();
        synchronized(regions) {
            for(Entry<String, byte[]> entry : regions.entrySet()) {
            json.put(entry.getKey(), Base64.getEncoder().encodeToString(entry.getValue()));
            }
        }

        PrintWriter writer = new PrintWriter(file);
        writer.write(json.toString());
        writer.flush();
        writer.close();
    }

    private Future<int[]> getWorldTimeWeather()  {
        return Bukkit.getScheduler().callSyncMethod(Plugin.instance, new Callable<int[]>(){
            @Override
            public int[] call() throws Exception {
                World world = Bukkit.getWorld(LiveMap.this.world);
                return new int[]{world.hasStorm() ? 1 : world.isThundering() ? 2:0, world.getWeatherDuration(), (int)(world.getTime())};
            }
        });
    }

    public static class BoundingBox {
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
    }
}