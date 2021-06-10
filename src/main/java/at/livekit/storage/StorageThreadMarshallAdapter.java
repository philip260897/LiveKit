package at.livekit.storage;

import java.util.List;

public abstract class StorageThreadMarshallAdapter implements IStorageAdapterGeneric{

    public static boolean DISABLE = false;

    @Override
    public <T> T loadSingle(Class<T> clazz, String id) throws Exception {
        checkThread("loadSingle", clazz, id);
        return null;
    }

    @Override
    public <T> T loadSingle(Class<T> clazz, String key, Object value) throws Exception {
        checkThread("loadSingle", clazz, key, value);
        return null;
    }

    @Override
    public <T> List<T> load(Class<T> clazz, String key, Object value) throws Exception {
        checkThread("load", clazz, key, value);
        return null;
    }

    @Override
    public <T> List<T> loadAll(Class<T> clazz) throws Exception {
        checkThread("loadAll", clazz);
        return null;
    }

    @Override
    public <T> void create(T entry) throws Exception {
        checkThread("create", entry);
    }

    @Override
    public <T> void update(T entry) throws Exception {
        checkThread("update", entry);
        
    }

    @Override
    public <T> void delete(T entry) throws Exception {
        checkThread("delete", entry);
        
    }

    @Override
    public <T> void createOrUpdate(T entry) throws Exception {
        checkThread("createOrUpdate", entry);
    }

    protected void checkThread(String name, Object...values) throws Exception {
        if(StorageThreadMarshallAdapter.DISABLE) return;
        
        Thread current = Thread.currentThread();

        if(current.getName().equalsIgnoreCase("Server thread")) {
            String s = "";
            for(Object o : values) s+=o.toString()+", ";

            throw new Exception("Illegal Access! Storage API called from main thread! "+name+"("+s+")");
        }
    }
    
}
