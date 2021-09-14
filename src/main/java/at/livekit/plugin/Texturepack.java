package at.livekit.plugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import javax.swing.text.html.parser.Entity;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.entity.EntityType;
import org.json.JSONObject;



public class Texturepack {

    public static Texturepack instance;
    public static Texturepack getInstance() throws Exception {
        if(instance == null) {
            instance = new Texturepack();
        }
        return instance;
    }
    
    private Map<String, Integer> _textures = new HashMap<String, Integer>();
    private Map<String, Integer> _biomes = new HashMap<String, Integer>();
    private Map<String, Integer> _entities = new HashMap<String, Integer>();

    public Texturepack() throws Exception{
        int nextId = 0;

        //load Textures
        String json = "";
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("texturepack.json");
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));

        String line = null;
        while((line = reader.readLine()) != null) {
            json += line;
        }
        reader.close();
        in.close();

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

        //load Biomes
        nextId = 1;
        json = "";

        in = this.getClass().getClassLoader().getResourceAsStream("biomes.json");
        reader = new BufferedReader(new InputStreamReader(in));

        line = null;
        while((line = reader.readLine()) != null) {
            json += line;
        }
        reader.close();
        in.close();

        root = new JSONObject(json);
        for(String key : root.keySet()) {
            Integer intKey = Integer.parseInt(key.split(":")[0]);
            _biomes.put(key.split(":")[1], intKey);

            if(intKey.intValue() >= nextId) nextId = intKey.intValue()+1;
        }

        additionalTextures = 0;
        for(Biome mat : Biome.values()) {
            if(!_biomes.containsKey(mat.toString())) {
                _biomes.put(mat.toString(), nextId++);
                Plugin.debug("Patching biome "+(nextId-1)+":"+mat.toString());
                additionalTextures++;
            }
        }
        Plugin.debug("Patched "+additionalTextures+" biomes");


        //load entities
        nextId = 0;
        json = "";

        in = this.getClass().getClassLoader().getResourceAsStream("entities.json");
        reader = new BufferedReader(new InputStreamReader(in));

        line = null;
        while((line = reader.readLine()) != null) {
            json += line;
        }
        reader.close();
        in.close();

        root = new JSONObject(json);
        for(String key : root.keySet()) {
            Integer intKey = Integer.parseInt(key.split(":")[0]);
            _entities.put(key.split(":")[1], intKey);

            if(intKey.intValue() >= nextId) nextId = intKey.intValue()+1;
        }

        additionalTextures = 0;
        for(EntityType mat : EntityType.values()) {
            if(!_entities.containsKey(mat.toString())) {
                _entities.put(mat.toString(), nextId++);
                Plugin.debug("Patching entity "+(nextId-1)+":"+mat.toString());
                additionalTextures++;
            }
        }
        Plugin.debug("Patched "+additionalTextures+" entities");
    }

    public int getTexture(Material material) {
        Integer id = _textures.get(material.toString());
        if(id == null) return 0;
        return id.intValue();
    }

    public int getBiome(Biome biome) {
        Integer id = _biomes.get(biome.toString());
        if(id == null) return 0;
        return id.intValue();
    }

    public int getEntity(EntityType type) {
        Integer id = _entities.get(type.toString());
        if(id == null) return 0;
        return id.intValue();
    }

    public static void generateTexturePack() {
        try {
            Texturepack texturepack = Texturepack.getInstance();
            JSONObject root = new JSONObject();
            for(Material mat : Material.values()) {
                root.put(texturepack.getTexture(mat)+":"+mat.toString(), "#00000000");
            }
            root.put("372:GRASS_PATH", "#00000000");
            File file = new File( System.getProperty("user.dir") + "/plugins/LiveKit/textures.json" );
            if(!file.exists()) file.createNewFile();

            PrintWriter writer = new PrintWriter(file);
            writer.write(root.toString());
            writer.flush();
            writer.close();
        }catch(Exception ex){
            ex.printStackTrace();
        }
    }

    public static void generateBiomes() {
        try {
            Texturepack texturepack = Texturepack.getInstance();
            JSONObject root = new JSONObject();
            for(Biome biome : Biome.values()) {
                root.put(texturepack.getBiome(biome)+":"+biome.toString(), "#00000000");
            }

            File file = new File( System.getProperty("user.dir") + "/plugins/LiveKit/biomes.json" );
            if(!file.exists()) file.createNewFile();

            PrintWriter writer = new PrintWriter(file);
            writer.write(root.toString());
            writer.flush();
            writer.close();
        }catch(Exception ex){
            ex.printStackTrace();
        }
    }

    public static void generateEntities() {
        try {
            Texturepack texturepack = Texturepack.getInstance();
            JSONObject root = new JSONObject();
            for(EntityType entity : EntityType.values()) {
                System.out.println(texturepack.getEntity(entity)+":"+entity.toString());
                root.put(texturepack.getEntity(entity)+":"+entity.toString(), "#00000000");
            }

            File file = new File( System.getProperty("user.dir") + "/plugins/LiveKit/entities.json" );
            if(!file.exists()) file.createNewFile();

            PrintWriter writer = new PrintWriter(file);
            writer.write(root.toString());
            writer.flush();
            writer.close();
        }catch(Exception ex){
            ex.printStackTrace();
        }
    }
}
