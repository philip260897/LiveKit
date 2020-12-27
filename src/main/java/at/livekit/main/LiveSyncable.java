package at.livekit.main;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONObject;

public class LiveSyncable 
{
    private String uuid;

    private boolean _forceDirty = false;
    private List<String> _changes = new ArrayList<String>();

    public LiveSyncable() {
        this(UUID.randomUUID().toString());
    }

    public LiveSyncable(String uuid) {
        this.uuid = uuid;
    }

    public String getUUID() {
        return this.uuid;
    }

    public boolean hasChanges() {
        return this._changes.size() > 0;
    }

    public void markDirty() {
        this._forceDirty = true;
    }

    protected void markDirty(String fieldName)    {
        synchronized(_changes) {
            if(!_changes.contains(fieldName)){
                    _changes.add(fieldName);
            }
        }
    }

    protected void markDirty(String... fieldNames) {
        synchronized(_changes) {
            for(String fieldName : fieldNames) {
                if(!_changes.contains(fieldName)){
                    _changes.add(fieldName);
                }
            }
        }
    }

    public JSONObject serializeChanges() {
        return serializeChanges(true);
    }

    public void clearChanges() {
        _forceDirty = false;
        _changes.clear();

        for(Field field : this.getClass().getDeclaredFields()) {
            if(Collection.class.isAssignableFrom(field.getType())) {
                try{
                    Collection c = (Collection) field.get(this);
                    for(Object o : c) {
                        if(o instanceof LiveSyncable) {
                            LiveSyncable syncable = (LiveSyncable)o;
                            syncable.clearChanges();
                        }
                    }
                }catch(Exception ex){ex.printStackTrace();}
            } 
        }
    }

    public JSONObject serializeChanges(boolean clear) {
        if(_changes.size() == 0 && !_forceDirty) return null;
        if(clear) _forceDirty = false;

        JSONObject json = new JSONObject();
        json.put("uuid", uuid);
        json.put("type", this.getClass().getSimpleName());
        
        synchronized(_changes) {
            for(Field field : this.getClass().getDeclaredFields()) {
                if(Collection.class.isAssignableFrom(field.getType())) {
                    try{
                        JSONArray array = new JSONArray();
                        Collection c = (Collection) field.get(this);
                        for(Object o : c) {
                            if(o instanceof LiveSyncable) {
                                LiveSyncable syncable = (LiveSyncable)o;
                                if(syncable.hasChanges()) {
                                    array.put(syncable.serializeChanges(clear));
                                }
                            }
                        }
                        json.put(field.getName(), array);

                    }catch(Exception ex){ex.printStackTrace();}
                } else if(_changes.contains(field.getName())) {
                    try{
                        json.put(field.getName(), field.get(this));
                    }catch(Exception ex){ex.printStackTrace();}
                }
            }
            _changes.clear();
        }

        return json;
    }

    public JSONObject serialize() {
        JSONObject json = new JSONObject();
        json.put("uuid", uuid);
        json.put("type", this.getClass().getSimpleName());

        for(Field field : this.getClass().getDeclaredFields()) {
            if(!field.getName().startsWith("_")) {
                if(Collection.class.isAssignableFrom(field.getType())) {
                    try{
                        JSONArray array = new JSONArray();
                        Collection c = (Collection) field.get(this);
                        for(Object o : c) {
                            if(o instanceof LiveSyncable) {
                                array.put(((LiveSyncable)o).serialize());
                            }
                        }
                        json.put(field.getName(), array);

                    }catch(Exception ex){ex.printStackTrace();}
                } else {
                    try{
                        json.put(field.getName(), field.get(this));
                    }catch(Exception ex){ex.printStackTrace();}
                }
            }
        }

        return json;
    }
}
