package at.livekit.utils;

import java.security.SecureRandom;

import org.bukkit.Material;



public class Utils 
{
    public static String generateRandom(int length) {
        SecureRandom r = new SecureRandom();
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ123456789abcdefghijklmnopqrstuvwxyz";
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
}
