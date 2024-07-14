package at.livekit.map;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import org.bukkit.Material;
import org.bukkit.block.Biome;

import at.livekit.modules.LiveMapModule.Offset;
import at.livekit.modules.LiveMapModule.RegionData;
import at.livekit.plugin.Plugin;
import at.livekit.plugin.Texturepack;
import net.querz.nbt.io.NBTInputStream;
import net.querz.nbt.io.NamedTag;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.IntTag;
import net.querz.nbt.tag.ListTag;
import net.querz.nbt.tag.LongArrayTag;
import net.querz.nbt.tag.StringTag;

public class FastRegionData extends RegionData {

    private static Texturepack texturepack;
    private final String world;

    private Offset foundBlock;

    public FastRegionData(String world, int x, int z, byte[] data) throws Exception{
        super(x, z, data);
        this.world = world;
    }

    public Offset getFoundBlock() {
        return foundBlock;
    }

    public void fastrender() throws Exception{
        if(texturepack == null) {
            texturepack = Texturepack.getInstance();
        }

        File file = new File(Plugin.getInstance().getDataFolder(), "/../../"+world+"/region/r."+x+"."+z+".mca");
        if(!file.exists()) {
            file = new File(Plugin.getInstance().getDataFolder(), "/../../"+world+"/DIM-1/region/r."+x+"."+z+".mca");
            if(!file.exists()) {
                file = new File(Plugin.getInstance().getDataFolder(), "/../../"+world+"/DIM1/region/r."+x+"."+z+".mca");
                if(!file.exists()) {
                    throw new Exception("[Fastrender] Region file not found: "+file.getAbsolutePath());
                }
            }
        }

        byte[] data = Files.readAllBytes(file.toPath());
        byte[] header = data;

        ByteBuffer buffer = ByteBuffer.wrap(header);

        for (int i = 0; i < 1024; i++) {
            int offset = ((header[i * 4 + 0] & 0xFF) << 16) | ((header[i * 4 + 1] & 0xFF) << 8)  | (header[i * 4 + 2] & 0xFF);
            int sectorCount = header[i * 4 + 3] & 0xFF;

            if (offset == 0 || sectorCount == 0) {
                continue;
            }

            int length = buffer.getInt(offset * 4096);


            byte compressionType = buffer.get(offset * 4096 + 4);
            if (compressionType != 2 && compressionType != 1) {
                throw new Exception("Unsupported compression type at index " + i + ": " + compressionType);
            }

            InputStream is = new ByteArrayInputStream(data, offset * 4096 + 5, length - 1);
            if (compressionType == 1) {
                is = new GZIPInputStream(is);
            } else if (compressionType == 2) {
                is = new InflaterInputStream(is);
            } else {
                throw new Exception("Unsupported compression type at index " + i + ": " + compressionType);
            }

            try (DataInputStream nbtIn = new DataInputStream(is)) {
                NamedTag namedTag;
                try (NBTInputStream nis = new NBTInputStream(nbtIn)) {
                    namedTag = nis.readTag(512); 
                }
                
                if (namedTag != null && namedTag.getTag() instanceof CompoundTag) {
                    parseChunk((CompoundTag) namedTag.getTag());
                } else {
                    throw new Exception("Invalid or corrupt chunk data at index " + i);
                }
            }
        }
    }

    private Map<String, Material> blockCache = new HashMap<>();
    private Map<Integer, CompoundTag> sectionCache = new HashMap<>();
    private void parseChunk(CompoundTag chunkTag) {
        sectionCache.clear();

        //print all tag names in chunkTag, recursively


       // IntTag level = chunkTag.getIntTag("DataVersion");
       // System.out.println("DataVersion: " + level.toString());
        
        ListTag<CompoundTag> sections = chunkTag.getListTag("sections").asCompoundTagList();
        for(int i = 0; i < sections.size(); i++) {
            CompoundTag section = sections.get(i);
            sectionCache.put((int)section.getByte("Y"), section);
        }

        int chunkX = chunkTag.getInt("xPos");
        int chunkZ = chunkTag.getInt("zPos");
        int chunkY = chunkTag.getInt("yPos");


        CompoundTag heightMaps = chunkTag.getCompoundTag("Heightmaps");
        if(heightMaps == null) return;

        LongArrayTag motionBlocking = (LongArrayTag) heightMaps.get("WORLD_SURFACE");
        if(motionBlocking == null) return;

        long[] heightMapData = motionBlocking.getValue();
        int[] heights = unpackHeightMap(heightMapData, 9, 256);


        Block previous = null;
        for (int i = 0; i < heights.length; i++) {
            int x = i % 16;
            int z = i / 16;
            int y = (chunkY * 16) - 1 + heights[i];

            Block block = previous == null ? decodeBlock(x, y, z, sections) : decodeBlock(x, y, z, sections, previous.getSection(), previous.getBlockStates(), previous.getBlockPalette(), previous.getBlockStatesLong(), previous.getBiomes(), previous.getBiomePalette(), previous.getBiomesLong(), previous.getType());
            if(block == null || block.getType() == null) continue;

            int localX = (chunkX * 16 + x) % 512;
            int localZ = (chunkZ * 16 + z) % 512;

            if(localX < 0) localX += 512;
            if(localZ < 0) localZ += 512;

            int offset = 8+(localZ * 4) * 512 + (localX * 4);

            block = getBlockForRendering(block, sections);
            if(block == null || block.getType() == null) continue;

            byte biome = Renderer.isLeave(block.getType()) ? (byte) 0x04 : 0x00;

            int waterDepth = -1;
            int h = block.getY();
            if(block.getType() == Material.WATER) {
                biome |= 0x08;
                Block b = getBlockBelow(block, sections);
                if(b == null) continue;
                for(int j = 0; j < 100; j++) {
                    if (b.getType() != Material.WATER) {
                        block = b;
                        waterDepth = ++j;
                        break;
                    }
                    b = getBlockBelow(b, sections);
                    if(b == null) break;
                }
            }

            if(waterDepth > 0) {
                h = waterDepth;
            }

            int dataId = (int)texturepack.getTexture(block.getType());
            byte biomeId = (byte)texturepack.getBiome(block.getBiome());
                            
            data[offset + 0] = (byte) ((byte)(dataId >> 8) | (biomeId & 0xF0));
            data[offset + 1] = (byte) dataId;
            data[offset + 2] = (byte) h;
            data[offset + 3] = (byte) (biome | (biomeId << 4) | ((h & 0x100) != 0x00 ? ((byte)0x01) : ((byte)0x00)));

            if(foundBlock == null) {
                foundBlock = new Offset((chunkX * 16 + x), (chunkZ * 16 + z));
            }
        }
    }

    private Block getBlockForRendering(Block block, ListTag<CompoundTag> sections) {
        if(block.getType() == Material.BEDROCK && block.getY() != 0) {
            boolean air = false;
            while(block.getY() > 0 && air == false) {
               block = getBlockBelow(block, !air ? 3 : 1, sections);
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

    public Block getBlockBelow(Block block, int depth, ListTag<CompoundTag> sections) {
        return decodeBlock(block.getX(), block.getY() - depth, block.getZ(), sections, block.getSection(), block.getBlockStates(), block.getBlockPalette(), block.getBlockStatesLong(), block.getBiomes(), block.getBiomePalette(), block.getBiomesLong(), block.getType());
    }

    public Block getBlockBelow(Block block, ListTag<CompoundTag> sections) {
        return getBlockBelow(block, 1, sections);
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
            if(material != Material.WATER && material != Material.SNOW && material != Material.LAVA) {
                return false;
            }
        }
        return material.isBlock();
    }

    private Block decodeBlock(int x, int y, int z, ListTag<CompoundTag> sections) {
        return decodeBlock(x, y, z, sections, null, null, null, null, null, null, null, null);
    }
    
    private Block decodeBlock(int x, int y, int z, ListTag<CompoundTag> sections, CompoundTag section, CompoundTag blockStates, ListTag<CompoundTag> blockPalette, long[] blockStatesLong, CompoundTag biomes, ListTag<StringTag> biomePalette, long[] biomesLong, Material mat) {
        
        int ySection = Math.floorDiv(y, 16);
        int sectionHeight = Math.floorMod(y, 16);

        if(section == null || section.getByte("Y") != ySection) {
            section = sectionCache.get(ySection);
            if(section == null) return null;

            blockStates = section.getCompoundTag("block_states");
            if(blockStates == null) return null;

            blockPalette = blockStates.getListTag("palette").asCompoundTagList();
            blockStatesLong = blockStates.getLongArray("data");

            biomes = section.getCompoundTag("biomes");
            if(biomes == null) return null;

            biomePalette = biomes.getListTag("palette").asStringTagList();
            biomesLong = biomes.getLongArray("data");
        }

        int bitsPerBiome = Math.max(1, (int) Math.ceil(Math.log(biomePalette.size()) / Math.log(2)));
        int biomesPerLong = 64 / bitsPerBiome;

        int biomeIndex = (sectionHeight / 4) * 16 + (z / 4) * 4 + (x / 4);
        int biomeLongIndex = biomeIndex / biomesPerLong;
        int biomeStartBit = (biomeIndex % biomesPerLong) * bitsPerBiome;
        int biomePaletteIndex = biomesLong.length == 0 ? 0 : (int) ((biomesLong[biomeLongIndex] >>> biomeStartBit) & ((1 << bitsPerBiome) - 1));
        String biomeName = biomePalette.get(biomePaletteIndex).getValue();
            
        Biome biome = Biome.valueOf(biomeName.replace("minecraft:", "").toUpperCase());

        if(blockStatesLong == null || blockStatesLong.length == 0) {
            String blockName = blockPalette.get(0).getString("Name");
            mat = mat != null && mat.name().equals(blockName.replace("minecraft:", "").toUpperCase()) ? mat : getMaterial(blockName);

            Block block = new Block(x, y, z, mat, biome);
            block.setCache(section, blockStates, blockPalette, blockStatesLong, biomes, biomePalette, biomesLong);

            return block;
        }

        int bitsPerBlock = Math.max(4, (int) Math.ceil(Math.log(blockPalette.size()) / Math.log(2)));
        int blocksPerLong = 64 / bitsPerBlock;

        int index = (sectionHeight * 16 * 16) + (z * 16) + x;
        int blockLongIndex = index / blocksPerLong;
        int blockStartBit = (index % blocksPerLong) * bitsPerBlock;
        int blockPaletteIndex = (int) ((blockStatesLong[blockLongIndex] >>> blockStartBit) & ((1 << bitsPerBlock) - 1));

            
        String blockName = blockPalette.get(blockPaletteIndex).getString("Name");
        mat = mat != null && mat.name().equals(blockName.replace("minecraft:", "").toUpperCase()) ? mat : getMaterial(blockName);

        Block block = new Block(x, y, z, mat, biome);
        block.setCache(section, blockStates, blockPalette, blockStatesLong, biomes, biomePalette, biomesLong);

        return block;
    }

    private Material getMaterial(String name) {
        name = name.replace("minecraft:", "").toUpperCase();
        Material mat = blockCache.get(name);
        if(mat == null) {
            mat = Material.getMaterial(name);
            blockCache.put(name, mat);
        }
        return mat;
    }

    private static class Block {
        final private int x;
        final private int y;
        final private int z;
        final private Material material;
        final private Biome biome;

        private CompoundTag cachedSection;
        private CompoundTag cachedBlockStates;
        private ListTag<CompoundTag> cachedBlockPalette;
        private long[] cachedBlockStatesLong;

        private CompoundTag cachedBiomes;
        private ListTag<StringTag> cachedBiomePalette;
        private long[] cachedBiomesLong;

        public Block(int x, int y, int z, Material material, Biome biome) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.material = material;
            this.biome = biome;
        }

        public void setCache(CompoundTag section, CompoundTag blockStates, ListTag<CompoundTag> blockPalette, long[] blockStatesLong, CompoundTag biomes, ListTag<StringTag> biomePalette, long[] biomesLong) {
            this.cachedSection = section;
            this.cachedBlockStates = blockStates;
            this.cachedBlockPalette = blockPalette;
            this.cachedBlockStatesLong = blockStatesLong;
            this.cachedBiomes = biomes;
            this.cachedBiomePalette = biomePalette;
            this.cachedBiomesLong = biomesLong;
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

        public CompoundTag getSection() {
            return cachedSection;
        }

        public CompoundTag getBlockStates() {
            return cachedBlockStates;
        }

        public ListTag<CompoundTag> getBlockPalette() {
            return cachedBlockPalette;
        }

        public long[] getBlockStatesLong() {
            return cachedBlockStatesLong;
        }

        public CompoundTag getBiomes() {
            return cachedBiomes;
        }

        public ListTag<StringTag> getBiomePalette() {
            return cachedBiomePalette;
        }

        public long[] getBiomesLong() {
            return cachedBiomesLong;
        }
    }
}
