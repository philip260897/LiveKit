package at.livekit.utils;

import java.io.File;
import java.nio.file.Files;
import org.json.JSONObject;
import at.livekit.map.RenderBounds;


public class Legacy 
{
    /**
     * Used to detect if map uses legacy data storage vom <= v0.0.4
     * @param workingDirectory
     * @return true if legacy storage detected
     */
    public static boolean hasLegacySettingsCache(File workingDirectory) {
        return new File(workingDirectory, "data.json").exists();
    }

    /**
     * Deletes Legacy data file (<= v0.0.4)
     * @param workingDirectory
     */
    public static void deleteLegacySettingsCache(File workingDirectory) {
        File file = new File(workingDirectory, "data.json");
        if(file.exists()) file.delete();
    }
    
    /**
     * Loads Legacy (<= 0.0.4) bounds in new format 
     * @param workingDirectory
     * @return RenderBounds either parsed or default on error.
     */
    public static RenderBounds getRenderBoundsFromLegacySettingsCache(File workingDirectory) {
        try{
            File file = new File(workingDirectory, "data.json");
            if(file.exists()) {
                JSONObject json = new JSONObject(new String(Files.readAllBytes(file.toPath())));
                int left = json.getInt("minX");
                int top = json.getInt("minZ");
                int right = json.getInt("maxX");
                int bottom = json.getInt("maxZ");

                if(right - left > 0 && right - left <= 40) {
                    if(bottom - top > 0 && bottom - top <= 40) {
                        RenderBounds bounds = new RenderBounds(left*512, top*512, right*512, bottom*512);
                        return bounds;
                    }
                }
            }
        }catch(Exception ex) {
            //ex.printStackTrace();
        }

        return RenderBounds.DEFAULT;
    }
}
