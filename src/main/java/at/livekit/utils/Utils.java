package at.livekit.utils;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.scheduler.BukkitTask;

import at.livekit.modules.LiveMapModule.Offset;
import at.livekit.plugin.Plugin;



public class Utils 
{
    public static FutureSyncCallback<Exception> errorHandler(CommandSender sender) {
        return new FutureSyncCallback<Exception>(){
            @Override
            public void onSyncResult(Exception result) {
                sender.sendMessage(Plugin.getPrefixError()+"Something went wrong!");
            }
        };
    }

    public static String generateRandom(int length) {
        SecureRandom r = new SecureRandom();
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ123456789abcdefghijklmnopqrstuvwxyz";
        //String alphabet = "1234567890";
        String pin = "";
        for (int i = 0; i < length; i++) {
            pin += alphabet.charAt(r.nextInt(alphabet.length()));
        }
        return pin;
    } 

    public static String generateRandomNumbers(int length) {
        SecureRandom r = new SecureRandom();
        //String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ123456789abcdefghijklmnopqrstuvwxyz";
        String alphabet = "1234567890";
        String pin = "";
        for (int i = 0; i < length; i++) {
            pin += alphabet.charAt(r.nextInt(alphabet.length()));
        }
        return pin;
    }  
    
    public static boolean isBed(Material mat) {
        return (mat == Material.YELLOW_BED || mat == Material.BLACK_BED || 
        mat == Material.BLUE_BED || mat == Material.BROWN_BED || 
        mat == Material.CYAN_BED || mat == Material.GRAY_BED || 
        mat == Material.GREEN_BED || mat == Material.LIME_BED ||
        mat == Material.ORANGE_BED ||mat == Material.PINK_BED );
    }

    public static byte[] encodeTimestamp(long lng) {
       return new byte[] {
            (byte) (lng >> 56),
            (byte) (lng >> 48),
            (byte) (lng >> 40),
            (byte) (lng >> 32),
            (byte) (lng >> 24),
            (byte) (lng >> 16),
            (byte) (lng >> 8),
            (byte) lng };
    }

    public static long decodeTimestamp(byte[] b) {
        return ((long) b[0] << 56)
       | ((long) b[1] & 0xff) << 48
       | ((long) b[2] & 0xff) << 40
       | ((long) b[3] & 0xff) << 32
       | ((long) b[4] & 0xff) << 24
       | ((long) b[5] & 0xff) << 16
       | ((long) b[6] & 0xff) << 8
       | ((long) b[7] & 0xff);
    }

    public static void tryWriteFile(File file, String data) {
        try{
            writeFile(file, data);
        }catch(Exception ex){
            ex.printStackTrace();
        }
    }

    public static void writeFile(File file, String data) throws Exception {
        writeFile(file, data.getBytes());
    }

    public static void writeFile(File file, byte[] data) throws Exception {
        if(!file.exists()) file.createNewFile();
        Files.write(file.toPath(), data);
    }

    public static boolean isVanished(Player player) {
        for (MetadataValue meta : player.getMetadata("vanished")) {
            if (meta.asBoolean()) return true;
        }
        return false;
}
    /*public static long timestampFromBytes(byte[] data) {
        long timestamp = 0;
        for(int i = 0; i < 8; i++) {
            timestamp <<= 8;
            timestamp |= data[i];
        }
        return timestamp;
    }*/

    public static <T> BukkitTask executeAsyncForSyncResult(Callable<T> task, FutureSyncCallback<T> onResult, FutureSyncCallback<Exception> onError) {
        return Bukkit.getScheduler().runTaskAsynchronously(Plugin.getInstance(), new Runnable(){
            @Override
            public void run() {
                try{
                    final T result = task.call();

                    if(onResult != null) {
                        Bukkit.getScheduler().callSyncMethod(Plugin.getInstance(), new Callable<Void>(){
                            @Override
                            public Void call() throws Exception {
                                onResult.onSyncResult(result);
                                return null;
                            }
                        });
                    }
                }catch(Exception ex) {
                    ex.printStackTrace();
                    if(onError != null) {
                        Bukkit.getScheduler().callSyncMethod(Plugin.getInstance(), new Callable<Void>(){
                            @Override
                            public Void call() throws Exception {
                                onError.onSyncResult(ex);
                                return null;
                            }
                        });
                    }
                }
            }
        });
    }

    public static long getMaxMemory() {
        return Runtime.getRuntime().maxMemory()/1024/1024;
    }

    public static long getMemoryUsage() {
        return (ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed()/1024/1024);
    }

    public static float getCPUUsage() {
        try{
            return (float) ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getProcessCpuLoad()*100;
        }catch(Exception ex){
            return 0f;
        }
    }

    public static Field getField(Class clazz, String fieldName) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            Class superClass = clazz.getSuperclass();
            if (superClass == null) {
                throw e;
            } else {
                return Utils.getField(superClass, fieldName);
            }
        }
    }

    public static File getRegionFolder(String world) {
        File regionFolder = new File(Plugin.getInstance().getDataFolder(), "/../../"+world+"/region");
        if(!regionFolder.exists()) {
            regionFolder = new File(Plugin.getInstance().getDataFolder(), "/../../"+world+"/DIM-1/region");
            if(!regionFolder.exists()) {
                regionFolder = new File(Plugin.getInstance().getDataFolder(), "/../../"+world+"/DIM1/region");
                if(!regionFolder.exists()) {
                    return null;
                }
            }
        }
        return regionFolder;
    }

    public static boolean hasRegion(String world, int x, int z) {
        File regionFolder = getRegionFolder(world);
        if(regionFolder == null) return false;

        File regionFile = new File(regionFolder, "r."+x+"."+z+".mca");
        return regionFile.exists();
    }

    public static List<Offset> getWorldRegions(String world) {
        List<Offset> regions = new ArrayList<>();
        File regionFolder = getRegionFolder(world);
        if(regionFolder == null) return regions;

        for(File file : regionFolder.listFiles()) {
            if(file.getName().endsWith(".mca")) {
                String[] parts = file.getName().split("\\.");
                int x = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);
                regions.add(new Offset(x, z));
            }
        }

        return regions;
    }
}
