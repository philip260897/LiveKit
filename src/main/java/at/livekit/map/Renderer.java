package at.livekit.map;

import java.util.Arrays;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;

import at.livekit.map.RenderWorld.BlockRenderTask;
import at.livekit.map.RenderWorld.ChunkRenderTask;
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
        //long start = System.currentTimeMillis();
        initialize(task.getClass().getClassLoader());



        if (task != null) {
            if(task.region == null || task.region.isDead()) throw new Exception("RenderTask region is dead! Region("+(task.region!=null)+")");

            

            boolean isChunk = (task instanceof ChunkRenderTask);
            
            if(isChunk && task.rendering == false && ((ChunkRenderTask) task).getChunkOffset().onlyIfAbsent) {
                if(task.region.loadedChunkExists(((ChunkRenderTask) task).getChunkOffset())) {
                    return true;
                }
            }

            World bWorld = Bukkit.getWorld(world);

            if(task.rendering == false) {
                task.rendering = true;
                task.renderingX = 0;
                task.renderingZ = 0;
                task.buffer = new byte[ isChunk ? 16*16*4 : 4];
                //reset in region data buffer if has previous data => out of bounds dont gets overwritten
                Arrays.fill(task.buffer, (byte)0xFF);

                if(isChunk) {
                    ChunkRenderTask ctask = (ChunkRenderTask) task;
                    
                    //long chunkLoad = System.currentTimeMillis();

                    task.unload = !bWorld.isChunkLoaded(ctask.getChunkOffset().x, ctask.getChunkOffset().z);

                    Chunk c = bWorld.getChunkAt(ctask.getChunkOffset().x, ctask.getChunkOffset().z);
                    if(task.unload && !task.isChunkLoadEvent()) c.load();

                    //long snap = System.currentTimeMillis();
                    //task.rchunk = c.getChunkSnapshot(true, true, false);

                    //task.rchunk = bWorld.getChunkAt(task.getChunkOrBlock().x, task.getChunkOrBlock().z).getChunkSnapshot(true, true, false);

                    //System.out.println("Snapshot Load: "+(System.currentTimeMillis()-snap)+"ms Chunk Load: "+(snap - chunkLoad)+"ms");
                    //if(/*task.unload &&*/ !task.isChunkLoadEvent()) task.rchunk.load();
                }
            }

            //long setup = System.currentTimeMillis();
            //long chunk = System.currentTimeMillis();

            if(task instanceof ChunkRenderTask) {
                ChunkRenderTask ctask = (ChunkRenderTask) task;
                //Plugin.debug("Rendering chunk "+task.getChunkOrBlock().x+" "+task.getChunkOrBlock().z+" "+task.renderingX+" "+task.renderingZ);
                //if(task.renderingX == 0 && task.renderingZ == 0) task.unload = !Bukkit.getWorld(world).isChunkLoaded(task.getChunkOrBlock().x, task.getChunkOrBlock().z);
                //if(task.unload) Bukkit.getWorld(world).loadChunk(task.getChunkOrBlock().x, task.getChunkOrBlock().z);

                //Chunk c = Bukkit.getWorld(world).getChunkAt(task.getChunkOrBlock().x, task.getChunkOrBlock().z);
                //ChunkSnapshot c = task.rchunk;
                
                
               //chunk = System.currentTimeMillis();
                Chunk c = bWorld.getChunkAt(ctask.getChunkOffset().x, ctask.getChunkOffset().z);

                int blockX = 0;
                int blockZ = 0;

                for (int z = task.renderingZ; z < 16; z++) {
                    for (int x = task.renderingX; x < 16; x++) {
                        //long bstart = System.currentTimeMillis();
                        //long getblock = 0;


                        Block block = null;
                        blockX = c.getX() * 16 + x;
                        blockZ = c.getZ() * 16 + z;
                        
                        //get block data only if in bounds
                        if(bounds.blockInBounds(blockX, blockZ)) {
                            //long hstart = System.currentTimeMillis(); 
                            //task.rchunk = bWorld.getChunkAt(task.getChunkOrBlock().x, task.getChunkOrBlock().z);
                            block = bWorld.getHighestBlockAt(blockX, blockZ);
                            //int y = c.getHighestBlockYAt(x, z)-1;
                           // getblock = System.currentTimeMillis();

                            

                            //if(y < 0) y = 0;
                            //if(y > 255) y = 255;
                            //block = c.getBlockData(x, y, z); 
                            block = getBlockForRendering(block);

                            
                            //if(getblock-bstart != 0) System.out.println("["+blockX+", y, "+blockZ+"] Took "+(getblock-bstart));
                        }
                        

                        
                        
                        int localX = blockX % 512;
                        if (localX < 0)
                            localX += 512;

                        int localZ = blockZ % 512;
                        if (localZ < 0)
                            localZ += 512;

                        byte[] blockData = (block != null ? getBlockData(block/*, c.getBiome(x, z), c.getHighestBlockYAt(x, z)*/) : DEFAULT_BLOCK);
                        //long render = System.nanoTime();

                        for (int i = 0; i < blockData.length; i++) {
                            task.region.data[8 + (localZ * 4) * 512 + (localX * 4) + i] =  blockData[i];
                            task.buffer[z * 4 * 16 + x*4 + i] = blockData[i];
                        }

                        //long write = System.nanoTime();

                        //System.out.println(((System.nanoTime()-bstart)/1000)+"us write: "+((write - render)/1000)+"us render: "+((render - getblock)/1000)+" block: "+((getblock - bstart)/1000)+" block update "+block.getType()+" y: "+block.getY()+" "+Thread.currentThread().getName());
                         

                        if(System.currentTimeMillis() - frameStart > cpuTime) {
                            task.renderingZ = z;
                            task.renderingX = x+1;

                            if(! (z == 15 && x == 15)) { 
                                //long end = System.currentTimeMillis();
                                //System.out.println("Abort rendering: "+(end - chunk)+"ms chunk: "+(chunk - setup)+"ms setup: "+(setup - start)+"ms total: "+(end - start)+"ms");
                                return false;
                            }
                        }
                               
                        
                    }
                    task.renderingX = 0;
                }
                if(task.unload) { bWorld.unloadChunk(ctask.getChunkOffset().x, ctask.getChunkOffset().z, false);  }
            } else if(task instanceof BlockRenderTask) {
                BlockRenderTask btask = (BlockRenderTask) task;
                Block block = bWorld.getHighestBlockAt(btask.getBlockOffset().x, btask.getBlockOffset().z);

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

            //long rdone = System.currentTimeMillis();

            task.region.invalidate();
            task.rendering = false;
            long timestamp = task.region.timestamp;

            if(task instanceof ChunkRenderTask) task.result = new ChunkPacket(((ChunkRenderTask)task).getChunkOffset().x, ((ChunkRenderTask)task).getChunkOffset().z, task.buffer, timestamp);
            if(task instanceof BlockRenderTask) task.result = new BlockPacket(((BlockRenderTask)task).getBlockOffset().x, ((BlockRenderTask)task).getBlockOffset().z, task.buffer, timestamp);

            //long end = System.currentTimeMillis();
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

        if (isLeave(block.getType())) {
            biome |= 0x04;
        }    

        byte[] data = new byte[4];

        int dataId = 0;
        byte biomeId = 2;
        int h = 64;

        try{
            dataId = _texturepack.getTexture(block.getType());
            h = height != null ? height.intValue() : block.getY();
            biomeId = (byte) _texturepack.getBiome(block.getBiome());
        }catch(Exception ex){/*ex.printStackTrace();*/}

        data[1] = (byte) dataId;
        data[0] = (byte) ((byte)(dataId >> 8) | (biomeId & 0xF0));

        data[2] = (byte) h;
        data[3] = (byte)(biome | (biomeId << 4) | ((h & 0x100) != 0x00 ? ((byte)0x01) : ((byte)0x00)));
        return data;
    }

    private static byte[] getBlockData(BlockData block, Biome b, int y) {
        Integer height = null;
        
        byte biome = 0x00;
        /*if(block.getType() == Material.WATER) {
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
        }   */     

        byte[] data = new byte[4];
        int dataId = _texturepack.getTexture(block.getMaterial());
        byte biomeId = (byte) _texturepack.getBiome(b);
        //byte biomeId = 0x01;

        data[1] = (byte) dataId;
        data[0] = (byte) ((byte)(dataId >> 8) | (biomeId & 0xF0));

        data[2] = height != null ? (byte) height.intValue() : (byte) /*block.getY()*/y;
        data[3] = (byte)(biome | (biomeId << 4));
        return data;
    }

    public static boolean isLeave(Material mat) {
        return /* mat == Material.LEGACY_LEAVES || mat == Material.LEGACY_LEAVES_2 || */
        mat == Material.ACACIA_LEAVES || mat == Material.BIRCH_LEAVES || mat == Material.OAK_LEAVES
                || mat == Material.DARK_OAK_LEAVES || mat == Material.SPRUCE_LEAVES || mat == Material.JUNGLE_LEAVES;
    }

}
