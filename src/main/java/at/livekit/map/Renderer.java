package at.livekit.map;

import java.util.Arrays;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import at.livekit.map.RenderWorld.RenderTask;
import at.livekit.packets.BlockPacket;
import at.livekit.packets.ChunkPacket;
import at.livekit.plugin.Plugin;

public class Renderer 
{
    private static byte[] DEFAULT_BLOCK = new byte[]{(byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff};
    //private static RenderTask _currentTask;
    //private static byte[] _chunkData;
    public static boolean render(String world, RenderTask task, long cpuTime, long frameStart, RenderBounds bounds) throws Exception {
        long start = System.currentTimeMillis();
        
        if (task != null) {
            if(task.region == null || task.region.isDead()) throw new Exception("RenderTask region is dead!");

            boolean isChunk = task.isChunk();
            
            if(isChunk && task.getChunkOrBlock().onlyIfAbsent) {
                if(task.region.loadedChunkExists(task.getChunkOrBlock())) {
                    return true;
                }
            }

            if(task.rendering == false) {
                task.rendering = true;
                task.renderingX = 0;
                task.renderingZ = 0;
                task.buffer = new byte[ task.isChunk() ? 16*16*4 : 4];
                //reset in region data buffer if has previous data => out of bounds dont gets overwritten
                Arrays.fill(task.buffer, (byte)0xFF);
            }
            
            if(task.isChunk()) {

                if(task.renderingX == 0 && task.renderingZ == 0) task.unload = !Bukkit.getWorld(world).isChunkLoaded(task.getChunkOrBlock().x, task.getChunkOrBlock().z);
                Chunk c = Bukkit.getWorld(world).getChunkAt(task.getChunkOrBlock().x, task.getChunkOrBlock().z);
                
                int blockX = 0;
                int blockZ = 0;

                for (int z = task.renderingZ; z < 16; z++) {
                    for (int x = task.renderingX; x < 16; x++) {
                        Block block = null;
                        blockX = c.getX() * 16 + x;
                        blockZ = c.getZ() * 16 + z;
                        //get block data only if in bounds
                        if(bounds.blockInBounds(blockX, blockZ)) { 
                            block = c.getWorld().getHighestBlockAt(blockX, blockZ);
                        }
                        
                        int localX = blockX % 512;
                        if (localX < 0)
                            localX += 512;

                        int localZ = blockZ % 512;
                        if (localZ < 0)
                            localZ += 512;

                        byte[] blockData = (block != null ? getBlockData(block) : DEFAULT_BLOCK);
                        for (int i = 0; i < blockData.length; i++) {
                            task.region.data[8 + (localZ * 4) * 512 + (localX * 4) + i] =  blockData[i];
                            task.buffer[z * 4 * 16 + x*4 + i] = blockData[i];
                        }

                        if(System.currentTimeMillis() - frameStart > cpuTime) {
                            task.renderingZ = z;
                            task.renderingX = x+1;

                            if(! (z == 15 && x == 15)) return false;
                        }
                    }
                    task.renderingX = 0;
                }
                if(task.unload) Bukkit.getWorld(world).unloadChunk(task.getChunkOrBlock().x, task.getChunkOrBlock().z, false);
            } else {
                Block block = Bukkit.getWorld(world).getHighestBlockAt(task.getChunkOrBlock().x, task.getChunkOrBlock().z);

                int localX = block.getX() % 512;
                if (localX < 0)
                    localX += 512;
        
                int localZ = block.getZ() % 512;
                if (localZ < 0)
                    localZ += 512;
        
                task.buffer = getBlockData(block);
                for (int i = 0; i < task.buffer.length; i++) {
                    task.region.data[8+(localZ * 4) * 512 + (localX * 4) + i] = task.buffer[i];
                }
            }

            task.region.invalidate();
            task.rendering = false;
            long timestamp = task.region.timestamp;

            if(isChunk) task.result = new ChunkPacket(task.getChunkOrBlock().x, task.getChunkOrBlock().z, task.buffer, timestamp);
            else task.result = new BlockPacket(task.getChunkOrBlock().x, task.getChunkOrBlock().z, task.buffer, timestamp);

            return true;
        } else {
            throw new Exception("Region null error!!!");
        }
    }

    private static byte[] getBlockData(Block block) {
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

    private static boolean isLeave(Block block) {
        Material mat = block.getType();
        return /* mat == Material.LEGACY_LEAVES || mat == Material.LEGACY_LEAVES_2 || */
        mat == Material.ACACIA_LEAVES || mat == Material.BIRCH_LEAVES || mat == Material.OAK_LEAVES
                || mat == Material.DARK_OAK_LEAVES || mat == Material.SPRUCE_LEAVES || mat == Material.JUNGLE_LEAVES;
    }

    private static int getMaterialId(Material material) {
        for (int i = 0; i < Material.values().length; i++) {
            if (material == Material.values()[i]) {
                return i;
            }
        }
        return 0;
    }
}
