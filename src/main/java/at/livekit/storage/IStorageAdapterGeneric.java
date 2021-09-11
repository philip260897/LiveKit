package at.livekit.storage;

import java.util.List;

public interface IStorageAdapterGeneric 
{
    public void initialize() throws Exception;

    public void dispose();
    
    public <T> T loadSingle(Class<T> clazz, String id) throws Exception;

    public <T> T loadSingle(Class<T> clazz, String key, Object value) throws Exception;

    public <T> T loadSingle(Class<T> clazz, String[] keys, Object[] values) throws Exception;
    //public <T> List<T> load(Class<T> clazz, String id) throws Exception;

    public <T> List<T> load(Class<T> clazz, String key, Object value) throws Exception;

    public <T> List<T> loadAll(Class<T> clazz) throws Exception;

    public <T> void create(T entry) throws Exception;

    public <T> void update(T entry) throws Exception;

    public <T> void delete(T entry) throws Exception;

    public <T> void createOrUpdate(T entry) throws Exception;

    public boolean isEmpty();

    public void migrateTo(IStorageAdapterGeneric adapter) throws Exception;
}
