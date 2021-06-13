package at.livekit.plugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Material;
import org.json.JSONObject;



public class Texturepack {
    
    private Map<String, Integer> _textures = new HashMap<String, Integer>();

    public Texturepack() throws Exception{
        int nextId = 0;

        String json = "";
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("texturepack.json");
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));

        String line = null;
        while((line = reader.readLine()) != null) {
            json += line;
        }
        reader.close();

        JSONObject root = new JSONObject(json);
        for(String key : root.keySet()) {
            Integer intKey = Integer.parseInt(key.split(":")[0]);
            _textures.put(key.split(":")[1], intKey);

            if(intKey.intValue() >= nextId) nextId = intKey.intValue()+1;
        }

        int additionalTextures = 0;
        for(Material mat : Material.values()) {
            if(!_textures.containsKey(mat.toString())) {
                _textures.put(mat.toString(), nextId++);
                Plugin.debug("Patching textures "+(nextId-1)+":"+mat.toString());
                additionalTextures++;
            }
        }
        Plugin.debug("Patched "+additionalTextures+" textures");
    }

    public int getTexture(Material material) {
        return _textures.containsKey(material.toString()) ? _textures.get(material.toString()) : 0;
    }

    protected static void generateTexturePack() {

    }

}
