package at.livekit.utils;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.concurrent.Callable;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.scheduler.BukkitTask;

import at.livekit.plugin.Plugin;



public class Utils 
{
    private static Integer timezoneOffset = null;
    public static int getTimezoneOffset() {
        if(timezoneOffset == null) {
            TimeZone tz = TimeZone.getDefault();  
            Calendar cal = GregorianCalendar.getInstance(tz);
            timezoneOffset = tz.getOffset(cal.getTimeInMillis());
        }
        return ((timezoneOffset.intValue()/1000)/60)*1000*60;
    }

    public static long getRoundedDayTimestamp() {
        return ((((System.currentTimeMillis()/1000)/60)/60)/24) * 1000 * 60 * 60 * 24 + Utils.getTimezoneOffset();
    }

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

    public static void performance()
    {
        com.sun.management.OperatingSystemMXBean operatingSystemMXBean = (com.sun.management.OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean();
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        int availableProcessors = operatingSystemMXBean.getAvailableProcessors();
        long prevUpTime = runtimeMXBean.getUptime();
        long prevProcessCpuTime = operatingSystemMXBean.getProcessCpuTime();
        
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        
        
        System.out.println("Load: "+(operatingSystemMXBean.getProcessCpuLoad()*100)+"%");
        System.out.println("Processors: "+availableProcessors);
        //System.out.println("Java CPU: " + cpuUsage);
        System.out.println("Memory: "+(ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed()/1024/1024));
    }

    public static long getMaxMemory() {
        return Runtime.getRuntime().maxMemory()/1024/1024;
    }

    public static long getMemoryUsage() {
        return (ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed()/1024/1024);
    }

    public static float getCPUUsage() {
        return (float) ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getProcessCpuLoad()*100;
    }
}
