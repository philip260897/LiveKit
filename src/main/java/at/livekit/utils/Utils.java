package at.livekit.utils;

import java.security.SecureRandom;

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
}
