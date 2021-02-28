package at.livekit.utils;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;
import at.livekit.map.RenderBounds;
import at.livekit.plugin.Plugin;
import at.livekit.utils.HeadLibraryV2.HeadInfo;


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

    /**
     * Used to detect if server has legacy head data <= v0.0.5
     * @return true if legacy heads detected
     */
    public static boolean hasLegacyHeads() {
        File file = new File(Plugin.getInstance().getDataFolder(), "heads/");
        return file.exists();
    }

    /**
     * Gets Legacy Head data and converts to new format <= v0.0.5
     * @return Head data in new format
     */
    public static List<HeadInfo> getLegacyHeads() {
        List<HeadInfo> infos = new ArrayList<>();

        for(File file : new File(Plugin.getInstance().getDataFolder(), "heads/").listFiles()) {
            try{
                if(file.getName().endsWith(".txt")) {
                    String name = file.getName().replace(".txt", "");
                    String head = new String(Files.readAllBytes(file.toPath()));
                    if(head.length() != 0) {
                        HeadInfo info = new HeadInfo(name, head);
                        infos.add(info);
                    }
                }
            }catch(Exception ex){
                ex.printStackTrace();
            }
        }

        return infos;
    }

    /**
     * Deletes legacy head storage <= v0.0.5
     * @return true if legacy storage detected
     */
    public static void deleteLegacyHeads() {
        File file = new File(Plugin.getInstance().getDataFolder(), "heads/");
        for(File h : file.listFiles()) h.delete();
        file.delete();
    }
}
