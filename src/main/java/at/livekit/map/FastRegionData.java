package at.livekit.map;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockFace;

import at.livekit.modules.LiveMapModule.RegionData;
import at.livekit.plugin.Plugin;
import at.livekit.plugin.Texturepack;
import net.minecraft.world.level.block.Block;
import net.querz.nbt.io.NBTInputStream;
import net.querz.nbt.io.NamedTag;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.ListTag;
import net.querz.nbt.tag.LongArrayTag;
import net.querz.nbt.tag.StringTag;

public class FastRegionData extends RegionData {

    private static Texturepack texturepack;

    public FastRegionData(String world, int x, int z, byte[] data) throws Exception{
        super(x, z, data);
        initialize(world);
    }

    private void initialize(String world) throws Exception{
        if(texturepack == null) {
            texturepack = Texturepack.getInstance();
        }

        File file = new File(Plugin.getInstance().getDataFolder(), "/../../"+world+"/region/r."+x+"."+z+".mca");
        if(!file.exists()) {
            file = new File(Plugin.getInstance().getDataFolder(), "/../../"+world+"/DIM-1/region/r."+x+"."+z+".mca");
            if(!file.exists()) {
                file = new File(Plugin.getInstance().getDataFolder(), "/../../"+world+"/DIM1/region/r."+x+"."+z+".mca");
                if(!file.exists()) {
                    Plugin.debug("Region file not found: "+file.getAbsolutePath());
                    return;
                }
            }
        }

        byte[] data = Files.readAllBytes(file.toPath());
        byte[] header = data;

        ByteBuffer buffer = ByteBuffer.wrap(header);

        //try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
        //    byte[] header = new byte[8192];
        //    raf.readFully(header);

            for (int i = 0; i < 1024; i++) {
                int offset = ((header[i * 4 + 0] & 0xFF) << 16) | ((header[i * 4 + 1] & 0xFF) << 8)  | (header[i * 4 + 2] & 0xFF);
                int sectorCount = header[i * 4 + 3] & 0xFF;
                int timestamp = ((header[i * 4 + 4096] & 0xFF) << 24) | ((header[i * 4 + 4096 + 1] & 0xFF) << 16) | ((header[i * 4  + 4096 + 2] & 0xFF) << 8)  | (header[i * 4 + 4096 + 3] & 0xFF);

                if (offset == 0 || sectorCount == 0) {
                    continue;
                }

                //raf.seek(offset * 4096);
                //int length = raf.readInt();
                
                int length = buffer.getInt(offset * 4096);
                //int length = buffer.get(i * 4 * 4096 + 0) << 24 | buffer.get(i * 4 * 4096 + 1) << 16 | buffer.get(i * 4 * 4096 + 2) << 8 | buffer.get(i * 4 * 4096 + 3);
                //Plugin.debug("Chunk "+i+" length: "+length);

                //byte compressionType = raf.readByte();
                byte compressionType = buffer.get(offset * 4096 + 4);
                if (compressionType != 2 && compressionType != 1) {
                    System.err.println("Unsupported compression type at index " + i + ": " + compressionType);
                    return;
                }

                byte[] chunkData = new byte[length - 1];
                //raf.readFully(chunkData);


                //InputStream is = new ByteArrayInputStream(chunkData);
                InputStream is = new ByteArrayInputStream(data, offset * 4096 + 5, length - 1);
                if (compressionType == 1) {
                    is = new GZIPInputStream(is);
                } else if (compressionType == 2) {
                    is = new InflaterInputStream(is);
                } else {
                    System.err.println("Unsupported compression type at index " + i + ": " + compressionType);
                    return;
                }

                try (DataInputStream nbtIn = new DataInputStream(is)) {
                    NamedTag namedTag;
                    try (NBTInputStream nis = new NBTInputStream(nbtIn)) {
                        namedTag = nis.readTag(512); // Specify a maximum depth of 512
                    }
                    if (namedTag != null && namedTag.getTag() instanceof CompoundTag) {
                        CompoundTag chunkTag = (CompoundTag) namedTag.getTag();
                        //long start = System.currentTimeMillis();
                        parseChunk(chunkTag);
                        //long end = System.currentTimeMillis();
                        //Plugin.debug("Chunk parsed in "+(end-start)+"ms");


                    } else {
                        System.err.println("Invalid or corrupt chunk data at index " + i);
                        return;
                    }
                } catch (EOFException e) {
                    e.printStackTrace();
                    return;
                }


            }

            Plugin.debug("millis: "+millis);
            millis = 0;
       /*  }catch (Exception e) {
            e.printStackTrace();
        }*/
    }

    private void parseChunk(CompoundTag chunkTag) {
        ListTag<CompoundTag> sections = chunkTag.getListTag("sections").asCompoundTagList();
        sections.sort((a, b) -> b.getByte("Y") - a.getByte("Y"));
        int maxSection = sections.get(0).getByte("Y");

        int chunkX = chunkTag.getInt("xPos");
        int chunkZ = chunkTag.getInt("zPos");

        boolean log = chunkX == 14 && chunkZ == 22;

        int counter = 0;

        Map<String, Material> materials = new HashMap<>();

        CompoundTag heightMaps = chunkTag.getCompoundTag("Heightmaps");
        if(heightMaps == null) return;

        LongArrayTag motionBlocking = (LongArrayTag) heightMaps.get("WORLD_SURFACE");
        if(motionBlocking == null) return;

        long[] heightMapData = motionBlocking.getValue();
        int[] heights = unpackHeightMap(heightMapData, 9, 256);

        int chunkYPos = chunkTag.getInt("yPos");

        //Plugin.debug("Chunk: "+chunkX+" "+chunkYPos+" "+chunkZ);



        for (int i = 0; i < heights.length; i++) {
            int x = i % 16;
            int z = i / 16;
            int y = (chunkYPos * 16) - 1 + heights[i];

            Block block = decodeBlock(x, y, z, sections);
            if(block == null || block.getType() == null) continue;

            //int ySection = Math.floorDiv(y, 16);
            

            //int listIndex = sections.size() - 1 - (ySection - (int)sections.get(sections.size() - 1).getByte("Y"));


            /*CompoundTag section = findSection(sections, ySection);
            if(section == null) {
               // Plugin.debug("Section not found: "+ySection + " "+chunkYPos);
                continue;
            }*/


            //System.out.println("Height at (" + x + ", " + z + "): " + y + " Section: " + ySection + " Height: " + height + " section actual: "+section.getByte("Y")+" maxSection: "+maxSection);

        /* }

        // Iterate over each section in the chunk
        for (CompoundTag section : sections){*/
            /*CompoundTag blockStates = section.getCompoundTag("block_states");
            if(blockStates == null) { Plugin.debug("No block states"); continue; }

            long[] blockStatesLong = blockStates.getLongArray("data");
            if(blockStatesLong == null || blockStatesLong.length == 0) { Plugin.debug("block states null or empty"); continue; }
            
            CompoundTag biomes = section.getCompoundTag("biomes");
            if(biomes == null) { Plugin.debug("No biomes"); continue; }

            long[] biomesLong = biomes.getLongArray("data");
            if(biomesLong == null) biomesLong = new long[0];

            ListTag<CompoundTag> blockPalette = blockStates.getListTag("palette").asCompoundTagList();
            ListTag<StringTag> biomePalette = biomes.getListTag("palette").asStringTagList();

            int bitsPerBlock = Math.max(4, (int) Math.ceil(Math.log(blockPalette.size()) / Math.log(2)));
            int blocksPerLong = 64 / bitsPerBlock;

            int bitsPerBiome = Math.max(1, (int) Math.ceil(Math.log(biomePalette.size()) / Math.log(2)));
            int biomesPerLong = 64 / bitsPerBiome;

            int yBase = section.getByte("Y") * 16;*/

            /*for (int height = 15; height >= 0; height--) {
                counter = 0;
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {*/
                        int localX = (chunkX * 16 + x) % 512;
                        int localZ = (chunkZ * 16 + z) % 512;

                        if(localX < 0) localX += 512;
                        if(localZ < 0) localZ += 512;

                        int offset = 8+(localZ * 4) * 512 + (localX * 4);
                        /*boolean isEmpty = (data[offset] == (byte)0xFF && data[offset+1] == (byte)0xFF && data[offset+2] == (byte)0xFF && data[offset+3] == (byte)0xFF);
                        if(!isEmpty) {
                            counter++;
                            { Plugin.debug("not empty"); continue; }
                        }*/
                        
  
                        //int existing = (byte)data[offset + 2] & 0xFF;
                        //if(!((y > existing && (data[offset+3] & 0x08) == 0x00) || ((data[offset] & data[offset+1] & data[offset+2] & data[offset+3]) == (byte) 0xFF))) continue;
                    
                        /*int height = Math.floorMod(y, 16);
                        int index = (height * 16 * 16) + (z * 16) + x;
                        int blockLongIndex = index / blocksPerLong;
                        int blockStartBit = (index % blocksPerLong) * bitsPerBlock;
                        int blockPaletteIndex = (int) ((blockStatesLong[blockLongIndex] >>> blockStartBit) & ((1 << bitsPerBlock) - 1));

                        String blockName = blockPalette.get(blockPaletteIndex).getString("Name");
                        Block block = new Block(x, y, z, Material.getMaterial(blockName.replace("minecraft:", "").toUpperCase()));*/

                        block = getBlockForRendering(block, sections);
                        if(block == null || block.getType() == null) continue;

                        //height = Math.floorMod(block.getY(), 16);
                        
                        //Plugin.debug(x+" "+y+" "+z+" "+blockName + " " + (chunkX * 16 + x) + " " + (chunkZ * 16 + z));
                        //if (!blockName.contains("air") && !blockName.contains("void")) {
                            /*Material mat = materials.get(blockName);
                            if(mat == null) {
                                mat = Material.getMaterial(blockName.replace("minecraft:", "").toUpperCase());
                                materials.put(blockName, mat);
                            }*/

                            //Material mat = Material.getMaterial(blockName.replace("minecraft:", "").toUpperCase());
                            //if(mat == null || !isBlock(mat)) continue;

                            /*int biomeIndex = (height / 4) * 16 + (z / 4) * 4 + (x / 4);
                            int biomeLongIndex = biomeIndex / biomesPerLong;
                            int biomeStartBit = (biomeIndex % biomesPerLong) * bitsPerBiome;
                            int biomePaletteIndex = biomesLong.length == 0 ? 0 : (int) ((biomesLong[biomeLongIndex] >>> biomeStartBit) & ((1 << bitsPerBiome) - 1));
                            String biomeName = biomePalette.get(biomePaletteIndex).getValue();*/

                            int dataId = (int)texturepack.getTexture(block.getType());
                            byte biomeId = (byte)texturepack.getBiome(block.getBiome());
                            byte biome = Renderer.isLeave(block.getType()) ? (byte) 0x04 : 0x00;

                            int waterDepth = -1;
                            int h = block.getY();
                            if(block.getType() == Material.WATER) {
                                biome |= 0x08;
                                //Block d = getWaterDepth(sections, section.getByte("Y"), block.getX(), block.getZ(), block.getY(), 100, log);

                                /*Block b = getBlockBelow(block, sections);
                                if(b == null) continue;

                                for(int j = 0; j < 100; j++) {
                                    if (b.getType() != Material.WATER) {
                                        block = b;
                                        waterDepth = ++j;
                                        break;
                                    }
                                    b = getBlockBelow(block, sections);
                                    if(b == null) break;
                                }*/

                                /*if(d != null && (y - d.getY()) > 0) {
                                    waterDepth = (y - d.getY());
                                    dataId = (int)texturepack.getTexture(d.getType().name().toUpperCase());
                                }*/
                            }

                            if(waterDepth > 0) {
                                h = waterDepth;
                            }

                            //Plugin.debug("blockName: "+blockName+" "+mat.name()+" "+globalY+" "+height+" "+section.getByte("Y") + " isBlock: "+isBlock(mat));

                            data[offset + 0] = (byte) ((byte)(dataId >> 8) | (biomeId & 0xF0));
                            data[offset + 1] = (byte) dataId;
                            data[offset + 2] = (byte) h;
                            data[offset + 3] = (byte) (biome | (biomeId << 4) | ((h & 0x100) != 0x00 ? ((byte)0x01) : ((byte)0x00)));
                            counter++;

                            //break;
                        //}
                    }
               /*/ }
                if(counter == 16*16) {
                    //Plugin.debug("Skipping after "+(16-height)+" iterations");
                    return;
                }
            }*/
                

        
    }

    private CompoundTag findSection(ListTag<CompoundTag> sections, int ySection) {
        for(CompoundTag section : sections) {
            if(section.getByte("Y") == ySection) {
                return section;
            }
        }

        return null;
    }


    
    /*private Block getWaterDepth(ListTag<CompoundTag> sections, int ySection , int x, int z, int initialHeight, int limit, boolean log) {
        int height = 0;

        for(CompoundTag section : sections) {
            if(section.getByte("Y") != ySection) continue;

            CompoundTag blockStates = section.getCompoundTag("block_states");
            if(blockStates == null) continue;

            long[] blockStatesLong = blockStates.getLongArray("data");
            if(blockStatesLong == null || blockStatesLong.length == 0) continue;

            int y = section.getByte("Y") * 16;
            ListTag<CompoundTag> blockPalette = blockStates.getListTag("palette").asCompoundTagList();

            int bitsPerBlock = Math.max(4, (int) Math.ceil(Math.log(blockPalette.size()) / Math.log(2)));
            int blocksPerLong = 64 / bitsPerBlock;


            for(int heightIndex = 15; heightIndex >= 0; heightIndex--) {
                int index = (heightIndex * 16 * 16) + (z * 16) + x;
                int blockLongIndex = index / blocksPerLong;
                int blockStartBit = (index % blocksPerLong) * bitsPerBlock;
                int blockPaletteIndex = (int) ((blockStatesLong[blockLongIndex] >>> blockStartBit) & ((1 << bitsPerBlock) - 1));

                String blockName = blockPalette.get(blockPaletteIndex).getString("Name");
                Material mat = Material.getMaterial(blockName.replace("minecraft:", "").toUpperCase());

                if( x == 8 && z == 12) {
                    if(log) Plugin.debug("Block: "+blockName+" "+mat.name()+" "+y+" "+heightIndex+" "+section.getByte("Y") + " isBlock: "+isBlock(mat));
                }

                if(blockName.contains("water") || !isBlock(mat)) {
                    height = y + heightIndex;
                } else {
                    return new Block(x, (y + heightIndex), z, mat);
                }

                if(initialHeight - height > limit) {
                    return null;
                }
            }

            break;
        }

        return getWaterDepth(sections, ySection-1, x, z, initialHeight, limit, log);
    }*/

    private Block getBlockForRendering(Block block, ListTag<CompoundTag> sections) {
        if(block.getType() == Material.BEDROCK && block.getY() != 0) {
            boolean air = false;
            while(block.getY() > 0 && air == false) {
               block = getBlockBelow(block, sections);
               if(block == null) return null;

               if(block.getType() == Material.AIR) air = true;
               if(block.getType() == Material.VOID_AIR) air = true;
               if(block.getType() == Material.CAVE_AIR) air = true;
            }
        }

        while((block.getType() == Material.AIR || block.getType() == Material.VOID_AIR || block.getType() == Material.CAVE_AIR || !isBlock(block.getType())) && block.getY() > 0) {
            block = getBlockBelow(block, sections);
            if(block == null) return null;
        }

        return block;
    }

    public Block getBlockBelow(Block block, ListTag<CompoundTag> sections) {
        return decodeBlock(block.getX(), block.getY() - 1, block.getZ(), sections);
    }

    public static int[] unpackHeightMap(long[] data, int bitsPerValue, int valueCount) {
        int[] unpacked = new int[valueCount];
        int valuesPerLong = 64 / bitsPerValue; // How many values can fit in one long
        int mask = (1 << bitsPerValue) - 1; // Mask to extract the bitsPerValue bits

        for (int i = 0; i < valueCount; i++) {
            int longIndex = i / valuesPerLong;
            int bitIndex = (i % valuesPerLong) * bitsPerValue;
            unpacked[i] = (int) ((data[longIndex] >> bitIndex) & mask);
        }

        return unpacked;
    }

    private boolean isBlock(Material material) {
        if(material.isBlock() == true && material.isSolid() == false) {
            if(material != Material.WATER && material != Material.SNOW) {
                return false;
            }
        }
        return material.isBlock();
    }

    int millis = 0;
    private Block decodeBlock(int x, int y, int z, ListTag<CompoundTag> sections) {
        int start = (int)System.currentTimeMillis();
        int ySection = Math.floorDiv(y, 16);
        int sectionHeight = Math.floorMod(y, 16);

        for(CompoundTag section : sections) {
            if(section.getByte("Y") != ySection) continue;

            CompoundTag blockStates = section.getCompoundTag("block_states");
            if(blockStates == null) continue;
            ListTag<CompoundTag> blockPalette = blockStates.getListTag("palette").asCompoundTagList();

            CompoundTag biomes = section.getCompoundTag("biomes");
            if(biomes == null) continue; 

            long[] biomesLong = biomes.getLongArray("data");
            if(biomesLong == null) biomesLong = new long[0];
            ListTag<StringTag> biomePalette = biomes.getListTag("palette").asStringTagList();
            int bitsPerBiome = Math.max(1, (int) Math.ceil(Math.log(biomePalette.size()) / Math.log(2)));
            int biomesPerLong = 64 / bitsPerBiome;

            int biomeIndex = (sectionHeight / 4) * 16 + (z / 4) * 4 + (x / 4);
            int biomeLongIndex = biomeIndex / biomesPerLong;
            int biomeStartBit = (biomeIndex % biomesPerLong) * bitsPerBiome;
            int biomePaletteIndex = biomesLong.length == 0 ? 0 : (int) ((biomesLong[biomeLongIndex] >>> biomeStartBit) & ((1 << bitsPerBiome) - 1));
            String biomeName = biomePalette.get(biomePaletteIndex).getValue();
            
            Biome biome = Biome.valueOf(biomeName.replace("minecraft:", "").toUpperCase());
            //Biome biome = Biome.BADLANDS;

            long[] blockStatesLong = blockStates.getLongArray("data");
            if(blockStatesLong == null || blockStatesLong.length == 0) {
                String blockName = blockPalette.get(0).getString("Name");
                Material mat = Material.getMaterial(blockName.replace("minecraft:", "").toUpperCase());

                millis += (int)System.currentTimeMillis() - start;
                return new Block(x, y, z, mat, biome);
            }

            int bitsPerBlock = Math.max(4, (int) Math.ceil(Math.log(blockPalette.size()) / Math.log(2)));
            int blocksPerLong = 64 / bitsPerBlock;

            int index = (sectionHeight * 16 * 16) + (z * 16) + x;
            int blockLongIndex = index / blocksPerLong;
            int blockStartBit = (index % blocksPerLong) * bitsPerBlock;
            int blockPaletteIndex = (int) ((blockStatesLong[blockLongIndex] >>> blockStartBit) & ((1 << bitsPerBlock) - 1));

            String blockName = blockPalette.get(blockPaletteIndex).getString("Name");
            Material mat = Material.getMaterial(blockName.replace("minecraft:", "").toUpperCase());

            millis += (int)System.currentTimeMillis() - start;
            return new Block(x, y, z, mat, biome);
        }

        millis += (int)System.currentTimeMillis() - start;
        return null;
    }

    /*private static class DepthWrapper {
        public int depth;
        public Material mat;

        public DepthWrapper(int depth, Material mat) {
            this.depth = depth;
            this.mat = mat;
        }
    }*/

    private static class Block {
        final private int x;
        final private int y;
        final private int z;
        final private Material material;
        final private Biome biome;

        public Block(int x, int y, int z, Material material, Biome biome) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.material = material;
            this.biome = biome;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getZ() {
            return z;
        }

        public Material getType() {
            return material;
        }

        public Biome getBiome() {
            return biome;
        }
    }
}
