package at.livekit.map;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import at.livekit.map.RenderWorld.RenderTask;
import at.livekit.packets.BlockPacket;
import at.livekit.packets.ChunkPacket;
import at.livekit.plugin.Texturepack;

public class Renderer 
{
    private static Texturepack _texturepack;

    private static void initialize(ClassLoader loader) throws Exception {
        if(_texturepack == null) {
            _texturepack = Texturepack.getInstance();
        }
    }

    private static byte[] DEFAULT_BLOCK = new byte[]{(byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff};
    public static boolean render(String world, RenderTask task, long cpuTime, long frameStart, RenderBounds bounds) throws Exception {
        long start = System.currentTimeMillis();
        initialize(task.getClass().getClassLoader());

        if (task != null) {
            if(task.region == null || task.region.isDead()) throw new Exception("RenderTask region is dead!");

            World bWorld = Bukkit.getWorld(world);

            boolean isChunk = task.isChunk();
            
            if(isChunk && task.rendering == false && task.getChunkOrBlock().onlyIfAbsent) {
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

                if(task.isChunk()) {
                    task.unload = !bWorld.isChunkLoaded(task.getChunkOrBlock().x, task.getChunkOrBlock().z);
                    
                    
                    task.rchunk = bWorld.getChunkAt(task.getChunkOrBlock().x, task.getChunkOrBlock().z);
                    if(/*task.unload &&*/ !task.isChunkLoadEvent()) task.rchunk.load();
                }
            }

            long setup = System.currentTimeMillis();
            long chunk = System.currentTimeMillis();

            if(task.isChunk()) {

                //if(task.renderingX == 0 && task.renderingZ == 0) task.unload = !Bukkit.getWorld(world).isChunkLoaded(task.getChunkOrBlock().x, task.getChunkOrBlock().z);
                //if(task.unload) Bukkit.getWorld(world).loadChunk(task.getChunkOrBlock().x, task.getChunkOrBlock().z);

                //Chunk c = Bukkit.getWorld(world).getChunkAt(task.getChunkOrBlock().x, task.getChunkOrBlock().z);
                Chunk c = task.rchunk;
               
                
                chunk = System.currentTimeMillis();

                int blockX = 0;
                int blockZ = 0;

                for (int z = task.renderingZ; z < 16; z++) {
                    for (int x = task.renderingX; x < 16; x++) {
                        long bstart = System.nanoTime();
                        long getblock = 0;

                        Block block = null;
                        blockX = c.getX() * 16 + x;
                        blockZ = c.getZ() * 16 + z;
                        //get block data only if in bounds
                        if(bounds.blockInBounds(blockX, blockZ)) { 
                            //task.rchunk = bWorld.getChunkAt(task.getChunkOrBlock().x, task.getChunkOrBlock().z);
                            block = bWorld.getHighestBlockAt(blockX, blockZ);
                            block = getBlockForRendering(block);

                            getblock = System.currentTimeMillis();
                            //if(getblock-bstart != 0) System.out.println("["+block.getX()+", "+block.getY()+", "+block.getZ()+"] Took "+(getblock-start)+"ms world "+(wstart-bstart)+"ms");
                        }
                        

                        
                        
                        int localX = blockX % 512;
                        if (localX < 0)
                            localX += 512;

                        int localZ = blockZ % 512;
                        if (localZ < 0)
                            localZ += 512;

                        byte[] blockData = (block != null ? getBlockData(block) : DEFAULT_BLOCK);
                        long render = System.nanoTime();

                        for (int i = 0; i < blockData.length; i++) {
                            task.region.data[8 + (localZ * 4) * 512 + (localX * 4) + i] =  blockData[i];
                            task.buffer[z * 4 * 16 + x*4 + i] = blockData[i];
                        }

                        long write = System.nanoTime();

                        //System.out.println(((System.nanoTime()-bstart)/1000)+"us write: "+((write - render)/1000)+"us render: "+((render - getblock)/1000)+" block: "+((getblock - bstart)/1000)+" block update "+block.getType()+" y: "+block.getY()+" "+Thread.currentThread().getName());
                         

                        if(System.currentTimeMillis() - frameStart > cpuTime) {
                            task.renderingZ = z;
                            task.renderingX = x+1;

                            if(! (z == 15 && x == 15)) { 
                                long end = System.currentTimeMillis();
                                //System.out.println("Abort rendering: "+(end - chunk)+"ms chunk: "+(chunk - setup)+"ms setup: "+(setup - start)+"ms total: "+(end - start)+"ms");
                                return false;
                            }
                        }
                               
                        
                    }
                    task.renderingX = 0;
                }
                if(task.unload) { Bukkit.getWorld(world).unloadChunk(task.getChunkOrBlock().x, task.getChunkOrBlock().z, false);  }
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

            long rdone = System.currentTimeMillis();

            task.region.invalidate();
            task.rendering = false;
            long timestamp = task.region.timestamp;

            if(isChunk) task.result = new ChunkPacket(task.getChunkOrBlock().x, task.getChunkOrBlock().z, task.buffer, timestamp);
            else task.result = new BlockPacket(task.getChunkOrBlock().x, task.getChunkOrBlock().z, task.buffer, timestamp);

            long end = System.currentTimeMillis();
            //System.out.println("DONE cleanup: "+(end-rdone)+"ms rendering: "+(rdone - chunk)+"ms chunk: "+(chunk - setup)+"ms setup: "+(setup - start)+"ms total: "+(end - start)+"ms");

            return true;
        } else {
            throw new Exception("Region null error!!!");
        }
    }

    public static Block getBlockForRendering(Block block) {
        if(block.getType() == Material.BEDROCK && block.getY() != 0) {
            boolean air = false;
            while(block.getY() > 0 && air == false) {
               block = block.getRelative(BlockFace.DOWN,!air ? 3 : 1);
               if(block.getType() == Material.AIR) air = true;
               if(block.getType() == Material.VOID_AIR) air = true;
               if(block.getType() == Material.CAVE_AIR) air = true;
            }
        }

        while((block.getType() == Material.AIR || block.getType() == Material.VOID_AIR || block.getType() == Material.CAVE_AIR ) && block.getY() > 0) {
            block = block.getRelative(BlockFace.DOWN);
        }

        Block up = block.getRelative(BlockFace.UP);
        if(up != null && (up.getType() == Material.SNOW || up.getType() == Material.SNOW_BLOCK)) {
            block = up;
        }

        return block;
    }

    private static byte[] getBlockData(Block block) {
        Integer height = null;
        
        byte biome = 0x00;
        if(block.getType() == Material.WATER) {
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
        int dataId = _texturepack.getTexture(block.getType());
        byte biomeId = (byte) _texturepack.getBiome(block.getBiome());
        //byte biomeId = 0x01;

        data[1] = (byte) dataId;
        data[0] = (byte) ((byte)(dataId >> 8) | (biomeId & 0xF0));

        data[2] = height != null ? (byte) height.intValue() : (byte) block.getY();
        data[3] = (byte)(biome | (biomeId << 4));
        return data;
    }

    private static boolean isLeave(Block block) {
        Material mat = block.getType();
        return /* mat == Material.LEGACY_LEAVES || mat == Material.LEGACY_LEAVES_2 || */
        mat == Material.ACACIA_LEAVES || mat == Material.BIRCH_LEAVES || mat == Material.OAK_LEAVES
                || mat == Material.DARK_OAK_LEAVES || mat == Material.SPRUCE_LEAVES || mat == Material.JUNGLE_LEAVES;
    }
}
