package at.livekit.plugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.EnumMap;
import java.util.EnumSet;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.json.JSONArray;
import org.json.JSONObject;



public class Texturepack {

    public static Texturepack instance;
    public static Texturepack getInstance() throws Exception {
        if(instance == null) {
            instance = new Texturepack();
        }
        return instance;
    }
    
    private EnumMap<Material, Integer> _textures =  new EnumMap<Material, Integer>(Material.class);
    private EnumMap<Biome, Integer> _biomes = new EnumMap<Biome, Integer>(Biome.class);
    private EnumMap<EntityType, Integer> _entities = new EnumMap<EntityType, Integer>(EntityType.class);
    private EnumMap<DamageCause, Integer> _damage = new EnumMap<DamageCause, Integer>(DamageCause.class);

    private EnumSet<Material> _tools = EnumSet.noneOf(Material.class);
    private EnumSet<Material> _weapons = EnumSet.noneOf(Material.class);

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
            //GRASS_PATH is no longer a valid material! was replaced by DIRT_PATH
            if(key.split(":")[1].equals("GRASS_PATH")) continue;

            Integer intKey = Integer.parseInt(key.split(":")[0]);
            Material mat = Material.getMaterial(key.split(":")[1]);
            if(mat != null) {
                _textures.put(Material.getMaterial(key.split(":")[1]), intKey);
            } else {
                Plugin.log("Material "+key.split(":")[1]+" not found!");
            } 

            if(intKey.intValue() >= nextId) nextId = intKey.intValue()+1;
        }

        int additionalTextures = 0;
        for(Material mat : Material.values()) {
            if(!_textures.containsKey(mat)) {
                _textures.put(mat, nextId++);
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
            _biomes.put(Biome.valueOf(key.split(":")[1]), intKey);

            if(intKey.intValue() >= nextId) nextId = intKey.intValue()+1;
        }

        additionalTextures = 0;
        for(Biome mat : Biome.values()) {
            if(!_biomes.containsKey(mat)) {
                _biomes.put(mat, nextId++);
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
            _entities.put(EntityType.valueOf(key.split(":")[1]), intKey);

            if(intKey.intValue() >= nextId) nextId = intKey.intValue()+1;
        }

        additionalTextures = 0;
        for(EntityType mat : EntityType.values()) {
            if(!_entities.containsKey(mat)) {
                _entities.put(mat, nextId++);
                Plugin.debug("Patching entity "+(nextId-1)+":"+mat.toString());
                additionalTextures++;
            }
        }
        Plugin.debug("Patched "+additionalTextures+" entities");

        //load entities
        nextId = 1;
        json = "";
        
        in = this.getClass().getClassLoader().getResourceAsStream("damage.json");
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
            _damage.put(DamageCause.valueOf(key.split(":")[1]), intKey);
        
            if(intKey.intValue() >= nextId) nextId = intKey.intValue()+1;
        }
        
        additionalTextures = 0;
        for(DamageCause mat : DamageCause.values()) {
            if(!_damage.containsKey(mat)) {
                _damage.put(mat, nextId++);
                Plugin.debug("Patching entity "+(nextId-1)+":"+mat.toString());
                additionalTextures++;
            }
        }
        Plugin.debug("Patched "+additionalTextures+" damage causes");

        //load resources
        json = "";

        in = this.getClass().getClassLoader().getResourceAsStream("resources.json");
        reader = new BufferedReader(new InputStreamReader(in));

        line = null;
        while((line = reader.readLine()) != null) {
            json += line;
        }
        reader.close();
        in.close();

        root = new JSONObject(json);
        JSONArray toolsArray = root.getJSONArray("tools");
        for(int i = 0; i < toolsArray.length(); i++) {
            String tool = toolsArray.getString(i);
            Material material = Material.getMaterial(tool);

            if(material == null) Plugin.debug("MATERIAL NOT FOUND!!! "+tool);
            else _tools.add(material);
        }

        JSONArray weaponsArrays = root.getJSONArray("weapons");
        for(int i = 0; i < weaponsArrays.length(); i++) {
            String tool = weaponsArrays.getString(i);
            Material material = Material.getMaterial(tool);

            if(material == null) Plugin.debug("MATERIAL NOT FOUND!!! "+tool);
            else _weapons.add(material);
        }

        for(Material tool : _tools) {
            Plugin.debug("Tool: "+tool.name());
        }
        for(Material tool : _weapons) {
            Plugin.debug("Weapons: "+tool.name());
        }
    }

    public boolean isTool(Material material) {
        return _tools.contains(material);
    }

    public boolean isWeapon(Material material) {
        return _weapons.contains(material);
    }

    public int getTexture(Material material) {
        Integer id = _textures.get(material);
        if(id == null) return 0;
        return id.intValue();
    }

    public int getBiome(Biome biome) {
        Integer id = _biomes.get(biome);
        if(id == null) return 0;
        return id.intValue();
    }

    public int getEntity(EntityType type) {
        Integer id = _entities.get(type);
        if(id == null) return 0;
        return id.intValue();
    }

    public int getDamage(DamageCause cause) {
        Integer id = _damage.get(cause);
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

    public static void generateDamage() {
        try {
            Texturepack texturepack = Texturepack.getInstance();
            JSONObject root = new JSONObject();
            for(DamageCause entity : DamageCause.values()) {
                System.out.println(texturepack.getDamage(entity)+":"+entity.toString());
                root.put(texturepack.getDamage(entity)+":"+entity.toString(), "#00000000");
            }

            File file = new File( System.getProperty("user.dir") + "/plugins/LiveKit/damage.json" );
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
