package at.livekit.storage;

import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.apache.commons.lang.NotImplementedException;
import org.bukkit.Bukkit;
import org.json.JSONArray;
import org.json.JSONObject;

import at.livekit.api.map.POI;
import at.livekit.api.map.PersonalPin;
import at.livekit.api.pm.PrivateMessage;
import at.livekit.authentication.Pin;
import at.livekit.authentication.Session;
import at.livekit.plugin.Plugin;
import at.livekit.utils.HeadLibraryV2.HeadInfo;

public class JSONStorage extends StorageThreadMarshallAdapter
{
    private File workingDir;
    private Map<Class<?>, List<?>> _cache = new HashMap<>();

    public JSONStorage() {

    }

    @Override
    public void initialize() throws Exception {
        workingDir = new File(Plugin.getInstance().getDataFolder(), "/data");
        if(!workingDir.exists()) {
            workingDir.mkdir();
        }

        registerClassForStorage(Session.class);
        registerClassForStorage(HeadInfo.class);
        registerClassForStorage(Pin.class);
        registerClassForStorage(POI.class);
        registerClassForStorage(PersonalPin.class);
        registerClassForStorage(PrivateMessage.class);
    }

    private <T> void registerClassForStorage(Class<T> clazz) throws Exception {
        String name = clazz.getSimpleName().toLowerCase();

        DatabaseTable table = clazz.getDeclaredAnnotation(DatabaseTable.class);
        if(table != null && table.tableName() != null && !table.tableName().equals("")) name = table.tableName();

        List<T> objects = new ArrayList<>();
        Gson gson = new Gson();

        File file = new File(workingDir, name+".json");
        if(file.exists()) {
            JSONArray array = new JSONArray(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            for(int i = 0; i < array.length(); i++){
                objects.add(gson.fromJson(array.getJSONObject(i).toString(), clazz));
            }
        }

        _cache.put(clazz, objects);
    }

    private void save() {
        for(Entry<Class<?>,List<?>> entry : _cache.entrySet()) {
            try{
                Class<?> clazz = entry.getKey();

                String name = clazz.getSimpleName().toLowerCase();

                DatabaseTable table = clazz.getDeclaredAnnotation(DatabaseTable.class);
                if(table != null && table.tableName() != null && !table.tableName().equals("")) name = table.tableName();

                Gson gson = new Gson();

                JSONArray array = new JSONArray();
                for(Object c : entry.getValue()) {
                    array.put(new JSONObject(gson.toJson(c)));
                }

                PrintWriter writer = new PrintWriter(new File(workingDir, name+".json"));
                writer.print(array.toString());
                writer.flush();
                writer.close();

            }catch(Exception ex){ex.printStackTrace();}
        }
    }

    @Override
    public void dispose() {
        save();
    }

    private <T> List<T> getCache(Class<T> clazz) throws Exception {
        if(_cache.containsKey(clazz)) {
            return (List<T>) _cache.get(clazz);
        }
        throw new Exception("Storage class "+clazz.getSimpleName()+" not found!");
    }

    private <T> Field getIDField(Class<T> clazz) {
        for(Field field : clazz.getFields()) {
            DatabaseField f = field.getAnnotation(DatabaseField.class);
            if(f != null) {
                if(f.id()) {
                    return field;
                }
            }
        }
        return null;
    }

    private <T> Field getField(Class<T> clazz, String name) throws Exception {
        for(Field f : clazz.getDeclaredFields()) {
            if(f.getName().equalsIgnoreCase(name)) {
                f.setAccessible(true);
                return f;
            }
        }
        
        return null;
    }

    @Override
    public <T> T loadSingle(Class<T> clazz, String id) throws Exception {
        super.loadSingle(clazz, id);

        List<T> cache = getCache(clazz);
        Field field = getIDField(clazz);
        if(field == null) throw new Exception("Storage class "+clazz.getSimpleName()+" has no ID field!");
        if(field.getType() != id.getClass()) throw new Exception("Key DataType and Value DataType don't match ("+clazz.getSimpleName()+", "+field.getName()+", "+id+", "+field.getType().getSimpleName()+", "+id.getClass().getSimpleName()+")");

       

        for(T t : cache) {
            Object idObj = field.get(t);
            if(idObj != null && idObj.equals(id)) {
                return t;
            }
        }
        return null;
    }

    @Override
    public <T> T loadSingle(Class<T> clazz, String key, Object value) throws Exception {
        super.loadSingle(clazz, key, value);

        List<T> cache = getCache(clazz);
        Field field = getField(clazz, key);
        if(field == null) throw new Exception("Storage class "+clazz.getSimpleName()+" has no "+key+" field!");
        if(field.getType() != value.getClass()) throw new Exception("Key DataType and Value DataType don't match ("+clazz.getSimpleName()+", "+key+", "+value+", "+key.getClass().getSimpleName()+", "+value.getClass().getSimpleName()+")");

        

        synchronized(cache) {
            return cache.stream().filter(new Predicate<T>(){
                @Override
                public boolean test(T t) {
                    try{
                        return value.equals(field.get(t));
                    }catch(Exception ex){ex.printStackTrace();}
                    return false;
                }        
            }).findFirst().orElse(null);
        }
    }

    /*@Override
    public <T> List<T> load(Class<T> clazz, String id) throws Exception {
        List<T> cache = getCache(clazz);
        Field field = getIDField(clazz);
        if(field == null) throw new Exception("Storage class "+clazz.getSimpleName()+" has no ID field!");

        synchronized(cache) {
            return cache.stream().filter(new Predicate<T>(){
                @Override
                public boolean test(T t) {
                    try{
                        return id.equals(field.get(t));
                    }catch(Exception ex){ex.printStackTrace();}
                    return false;
                }        
            }).collect(Collectors.toList());
        }
    }*/

    @Override
    public <T> List<T> load(Class<T> clazz, String key, Object value) throws Exception {
        super.load(clazz, key, value);

        List<T> cache = getCache(clazz);
        Field field = getField(clazz, key);
        if(field == null) throw new Exception("Storage class "+clazz.getSimpleName()+" has no "+key+" field!");
        if(field.getType() != value.getClass()) throw new Exception("Key DataType and Value DataType don't match ("+clazz.getSimpleName()+", "+key+", "+value+", "+key.getClass().getSimpleName()+", "+value.getClass().getSimpleName()+")");

        synchronized(cache) {
            return cache.stream().filter(new Predicate<T>(){
                @Override
                public boolean test(T t) {
                    try{
                        return value.equals(field.get(t));
                    }catch(Exception ex){ex.printStackTrace();}
                    return false;
                }        
            }).collect(Collectors.toList());
        }
    }

    @Override
    public <T> List<T> loadAll(Class<T> clazz) throws Exception {
        super.loadAll(clazz);

        List<T> cache = getCache(clazz);
        synchronized(cache) {
            return cache.stream().filter(e->true).collect(Collectors.toList());
        }
    }

    @Override
    public <T> void create(T entry) throws Exception {
        super.create(entry);

        List<T> cache = getCache((Class<T>)entry.getClass());
        synchronized(cache) {
            cache.add(entry);
        }
    }

    @Override
    public <T> void update(T entry) throws Exception {
        super.update(entry);
        /*List<T> cache = getCache((Class<T>)entry.getClass());

        synchronized(cache) {
            cache.add(entry);
        }*/
        //throw new NotImplementedException();
    }

    @Override
    public <T> void delete(T entry) throws Exception {
        super.delete(entry);

        List<T> cache = getCache((Class<T>)entry.getClass());
        synchronized(cache) {
            cache.remove(entry);
        }
    }

    @Override
    public <T> void createOrUpdate(T entry) throws Exception {
        super.createOrUpdate(entry);

        List<T> cache = getCache((Class<T>)entry.getClass());
        synchronized(cache) {
            if(!cache.contains(entry)) cache.add(entry);
            else update(entry);
        }
    }
    
    @Override
    public void migrateTo(IStorageAdapterGeneric adapter) throws Exception {
        for(Entry<Class<?>, List<?>> entry : _cache.entrySet()) {
            Plugin.log("Migrating "+entry.getKey().getSimpleName()+" with "+entry.getValue().size()+" entries");
            for(Object o : entry.getValue()) {
                adapter.create(o);
            }
        }
    }

    @Override
    public boolean isEmpty() {
        for(List<?> list : _cache.values()) {
            if(list.size() != 0) {
                return false;
            }
        }
        return true;
    }
}
