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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.json.JSONArray;
import org.json.JSONObject;

import at.livekit.livekit.Identity;
import at.livekit.plugin.Plugin;
import at.livekit.packets.BlockPacket;
import at.livekit.packets.ChunkPacket;
import at.livekit.packets.IPacket;

public class LiveMapModule extends BaseModule implements Listener
{
    //private List<Offset> _queueBlocks = new ArrayList<Offset>();
    private List<Offset> _queueChunks = new ArrayList<Offset>();

    private String world;
    private BoundingBox boundingBox;
    private HashMap<String, byte[]> _regions = new HashMap<String, byte[]>();
    //private HashMap<String, LiveSyncable> _syncables = new HashMap<String, LiveSyncable>();

    private List<IPacket> _updates = new ArrayList<IPacket>();
    

    public LiveMapModule(String world, ModuleListener listener) {
        super(1, "Live Map", "livekit.basics.map", UpdateRate.MAX, listener);
        this.world = world;
    }

    public void updateBlock(Block block) {
        int regionX = (int) Math.floor(((double) block.getX() / 512.0));
        int regionZ = (int) Math.floor(((double) block.getZ() / 512.0));
        createRegion(regionX, regionZ);
        String key = regionX + "_" + regionZ;

        byte[] regionData = _regions.get(key);
        if(regionData == null) return;

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

        synchronized(_updates) {
            _updates.add(new BlockPacket(block.getX(), block.getZ(), blockData));
        }
        notifyChange();
    }

    public void updateChunk(Chunk chunk, boolean isChunkLoadedEvent) {
        Offset offset = new Offset();
        offset.x = chunk.getX();
        offset.z = chunk.getZ();
        offset.onlyIfAbsent = isChunkLoadedEvent;

        synchronized(_queueChunks) {
            _queueChunks.add(offset);
        }
    }

    @Override
    public void onEnable() {
        try{
            boundingBox = new BoundingBox();
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

        super.onEnable();
    }
       
    @Override
    public void onDisable() {
        
        try{
            save();
        }catch(Exception ex){ex.printStackTrace();}
        _regions.clear();
        //_syncables.clear();
        _updates.clear();
        _queueChunks.clear();


        super.onDisable();
    }

    @Override
    public IPacket onJoinAsync(Identity identity) {
        JSONObject json = new JSONObject();
        json.put("world", world);
        JSONArray syncable = new JSONArray();
        JSONObject regions = new JSONObject();
        json.put("regions", regions);
        json.put("syncables", syncable);
        //json.put("boundingBox", )

        synchronized(_regions) {
            for(Entry<String,byte[]> entry : _regions.entrySet()) {
                regions.put(entry.getKey(), Base64.getEncoder().encodeToString(entry.getValue()));
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

    @Override
    public void update() {
        //only do chunk update, queue result packet
        try{
            Offset next = null;
            synchronized(_queueChunks) {
                if(_queueChunks.size() != 0) next = _queueChunks.remove(0);
            }
            if(next == null) return;

            IPacket result = update(next);
            if(result == null) update();

            if(result != null) {
                synchronized(_updates) {
                    _updates.add(result);
                }
                notifyChange();
            }
        }catch(Exception ex){ex.printStackTrace();}
    }

    private void createRegion(int regionX, int regionZ) {
        String key = regionX + "_" + regionZ;
        synchronized (_regions) {
            if (!_regions.containsKey(key)) {
                _regions.put(key, new byte[512 * 512 * 4]);
                Arrays.fill(_regions.get(key), (byte) 0xFF);
                boundingBox.update(regionX, regionZ);
            }
        }
    }

    private IPacket update(Offset chunk) throws Exception {
        if(chunk.onlyIfAbsent && loadedChunkExists(chunk)) return null;

        Plugin.debug("Updating chunk " + chunk.x + " " + chunk.z);
        int regionX = (int) Math.floor(((double) chunk.x / 32.0));
        int regionZ = (int) Math.floor(((double) chunk.z / 32.0));
        String key = regionX + "_" + regionZ;

        byte[] regionData;
        synchronized (_regions) {
            if (!_regions.containsKey(key)) {
                _regions.put(key, new byte[512 * 512 * 4]);
                Arrays.fill(_regions.get(key), (byte) 0xFF);
                boundingBox.update(regionX, regionZ);
            }
            regionData = _regions.get(key);
        }

        if (regionData != null) {
            byte[] chunkData = new byte[16*16*4];
            boolean unload = !Bukkit.getWorld(world).isChunkLoaded(chunk.x, chunk.z);
            Chunk c = Bukkit.getWorld(world).getChunkAt(chunk.x, chunk.z);
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    Block block = c.getWorld().getHighestBlockAt(c.getX() * 16 + x, c.getZ() * 16 + z);
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
            if(unload) Bukkit.getWorld(world).unloadChunk(chunk.x, chunk.z, false);

            return new ChunkPacket(c.getX(), c.getZ(), chunkData);
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

        for(int i = 0; i < 4; i++) {
           if( _regions.get(key)[(localZ * 4 * 512) + (localX * 4) + i] != (byte)0xFF) return true;
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

    private void load() throws Exception{
        File folder = getDir();
        if(!folder.exists()) folder.mkdirs();

        for(File file : folder.listFiles()) {
            if(file.isFile() && file.getName().endsWith(".region")) {
                try{
                    synchronized(_regions) {
                        int regionX = Integer.parseInt(file.getName().split("_")[0]);
                        int regionZ = Integer.parseInt(file.getName().split("_")[1].replace(".region", ""));
                        _regions.put(regionX+"_"+regionZ, Files.readAllBytes(file.toPath()));
                    }
                }catch(Exception ex){ex.printStackTrace();}
            }
        }
        Plugin.log("LiveMapModule loaded "+_regions.size()+" regions for "+world);
    }

    private void save() throws FileNotFoundException, IOException { 
        synchronized(_regions) {
            for(Entry<String, byte[]> entry : _regions.entrySet()) {
                File file = new File(getDir(), entry.getKey()+".region");
                if(!file.exists()) file.createNewFile();

                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(entry.getValue());
                }
            }
        }
    }

    private File getDir() {
        return new File(Plugin.getInstance().getDataFolder(), "map/"+world);
    }

    private static class Offset {
        public int x;
        public int z;
        public boolean onlyIfAbsent;
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


    //events Players

    @EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) 
	{
        /*if(!isEnabled()) return;
		Player player = event.getPlayer();
        if(!event.getPlayer().getWorld().getName().equals(world)) return;

		LiveEntity entity = new LiveEntity(player.getUniqueId().toString(), player.getDisplayName(), null);
		entity.updateLocation(player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ());
		entity.updateHealth(player.getHealthScale());
		entity.updateExhaustion(player.getExhaustion());*/

		/*if(!HeadLibrary.has(player.getUniqueId().toString())) { 
			HeadLibrary.resolveAsync(player.getUniqueId().toString());
		} 
		entity.updateHead(HeadLibrary.get(player.getUniqueId().toString()));
*/
       /* synchronized(_syncables) {
            _syncables.put(entity.getUUID(), entity);
        }
        notifyChange();*/
	}
	
	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) 
	{
        /*if(!isEnabled()) return;
        if(!event.getPlayer().getWorld().getName().equals(world)) return;

        synchronized(_syncables) {
            _syncables.remove(event.getPlayer().getUniqueId().toString());
        }
        notifyChange();*/
	}

	@EventHandler
	public void onPlayerMove(PlayerMoveEvent event) {
        /*if(!isEnabled()) return;
		Player player = event.getPlayer();
        if(!event.getPlayer().getWorld().getName().equals(world)) return;

        synchronized(_syncables) {
            LiveEntity entity = (LiveEntity) _syncables.get(player.getUniqueId().toString());
            if(entity != null) {
                entity.updateLocation(player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ());
            }
        }

        notifyChange();*/
	}

    //World events

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

        updateChunk(event.getChunk(), false);
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
}
