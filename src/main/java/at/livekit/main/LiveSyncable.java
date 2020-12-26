package at.livekit.main;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.json.JSONObject;

public class LiveSyncable 
{
    private String uuid;
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
        if(_changes.size() == 0) return null;

        JSONObject json = new JSONObject();
        json.put("uuid", uuid);
        json.put("type", this.getClass().getSimpleName());
        
        synchronized(_changes) {
            for(Field field : this.getClass().getDeclaredFields()) {
                if(_changes.contains(field.getName())) {
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
                try{
                    json.put(field.getName(), field.get(this));
                }catch(Exception ex){ex.printStackTrace();}
            }
        }

        return json;
    }
}
