package at.livekit.nbt;

import java.io.File;

import org.bukkit.Material;
import org.bukkit.block.Biome;

import at.livekit.plugin.Texturepack;
import net.querz.mca.Chunk;
import net.querz.mca.MCAFile;
import net.querz.mca.MCAUtil;
import net.querz.nbt.tag.CompoundTag;

public class NBTRenderer {
    private static Texturepack _texturepack;

    private static void initialize() throws Exception {
        if(_texturepack == null) {
            _texturepack = Texturepack.getInstance();
        }
    }
    private static byte[] DEFAULT_BLOCK = new byte[]{(byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff};

    public static byte[] renderRegion(File file, byte[] buffer) throws Exception
    {
        initialize();

        System.out.println(file.getName());
        String[] nameSplit = file.getName().split("\\.");
        int regionX = Integer.parseInt(nameSplit[1]);
        int regionZ = Integer.parseInt(nameSplit[2]);
        System.out.println(file.getName() + " " + regionX+" - "+regionZ);
        

        MCAFile mcaFile = MCAUtil.read(file);


        for(int cz = 0; cz < 32; cz++) {
            for(int cx = 0; cx < 32; cx++) {

                int chunkX = cx + (regionX * 32);
                int chunkZ = cz + (regionZ * 32);

                Chunk chunk = mcaFile.getChunk(chunkX, chunkZ);
                if(chunk == null) continue;

                long[] heightmap = chunk.getHeightMaps().getLongArray("MOTION_BLOCKING");

                for(int bz = 0; bz < 16; bz++) {
                    for(int bx = 0; bx < 16; bx++) {

                        int x = bx + (chunkX * 16);
                        int y = getHighestBlockY(heightmap, bx, bz);
                        int z = bz + (chunkZ * 16);

                        try{
                            NBTBlock block = NBTBlock.getBlock(mcaFile, x, y-1, z);

                            byte[] blockData = (block != null ? getBlockData(block/*, c.getBiome(x, z), c.getHighestBlockYAt(x, z)*/) : DEFAULT_BLOCK);

                            int localX = x % 512;
                            if (localX < 0)
                                localX += 512;
    
                            int localZ = z % 512;
                            if (localZ < 0)
                                localZ += 512;

                            for (int i = 0; i < blockData.length; i++) {
                                buffer[8 + (localZ * 4) * 512 + (localX * 4) + i] =  blockData[i];
                            }

                        }catch(Exception ex){ex.printStackTrace();}

                    }
                }
            }
        }

        return buffer;
    }

    public static LKBlock getBlockForRendering(LKBlock block, int y) {
        if(block.getType() == Material.BEDROCK && block.getY() != 0) {
            boolean air = false;
            while(block.getY() > 0 && air == false) {
               block = block.getBlockBelow(!air ? 3 : 1);
               if(block.getType() == Material.AIR) air = true;
               if(block.getType() == Material.VOID_AIR) air = true;
               if(block.getType() == Material.CAVE_AIR) air = true;
            }
        }

        while((block.getType() == Material.AIR || block.getType() == Material.VOID_AIR || block.getType() == Material.CAVE_AIR || block.getType() == Material.GRASS ) && block.getY() > 0) {
            block = block.getBlockBelow(1);
        }

        LKBlock up = block.getBlockBelow(-1);
        if(up != null && (up.getType() == Material.SNOW || up.getType() == Material.SNOW_BLOCK)) {
            block = up;
        }

        return block;
    }

    private static byte[] getBlockData(LKBlock block) {
        Integer height = null;
        
        byte biome = 0x00;
        if(block.getType() == Material.WATER) {
            biome |= 0x08;
            LKBlock b = block.getBlockBelow(1);
            for (int i = 0; i < 100; i++) {
                if (b.getType() != Material.WATER) {
                    block = b;
                    height = ++i;
                    break;
                }
                b = b.getBlockBelow(1);
            }
        }

       /* if (isLeave(block)) {
            biome |= 0x04;
        }*/    

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

    private static int getHighestBlockY(long[] heightMap, int x, int z) {
        int offset = z * 16 + x;
        long l = heightMap[offset / 7];
        long mask = 0x1FFL << (9 * (offset%7));

        long isolated = (l & mask);


        return (int) (isolated >> (9 * (offset%7)));
    }
}

interface LKBlock {

    public LKBlock getBlockBelow(int count);

    public Material getType();

    public Biome getBiome();

    public int getX();

    public int getZ();

    public int getY();
}

class NBTBlock implements LKBlock {

    private MCAFile file;

    private Material type;
    private Biome biome;
    private int x, y, z;

    public NBTBlock(MCAFile mca, int x, int y, int z, Material material, Biome biome) {
        this.file = mca;
        this.x = x;
        this.y = y;
        this.z = z;
        this.type = material;
        this.biome = biome;
    }

    @Override
    public LKBlock getBlockBelow(int count) {
        return NBTBlock.getBlock(file, x, y-count, z);
    }

    @Override
    public Material getType() {
        return type;
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getZ() {
        return z;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public Biome getBiome() {
        return biome;
    }

    public static NBTBlock getBlock(MCAFile mca, int x, int y, int z) {
        try{
            CompoundTag blockState = mca.getBlockStateAt(x, y, z);
            Material material = Material.valueOf(blockState.getStringTag("Name").getValue().replace("minecraft:", "").toUpperCase());
            return new NBTBlock(mca, x, y, z, material, Biome.PLAINS);
        }catch(Exception ex){ex.printStackTrace();}

        return null;
    }
}
