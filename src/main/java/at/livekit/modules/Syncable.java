package at.livekit.modules;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

public class Syncable 
{
    protected String uuid;
    protected String type;
    protected boolean _needsFullUpdate = true;
    protected List<String> _changes = new ArrayList<String>(); 
    
    public Syncable(String uuid) {
        this.uuid = uuid;
        this.type = this.getClass().getSimpleName();
    }

    public String getUUID() {
        return uuid;
    }

    public void markDirty(String fieldName) {
        synchronized(_changes) {
            if(!_changes.contains(fieldName)) {
                this._changes.add(fieldName);
            }
        }
    }

    public void markDirty(String ...fieldNames) {
        synchronized(_changes) {
            for(String fieldName : fieldNames) {
                if(!_changes.contains(fieldName)) {
                    this._changes.add(fieldName);
                }
            }
        }
    }

    public void forceDirty() {
        _needsFullUpdate = true;
    }

    public boolean hasChanges() {
        synchronized(_changes) {
            if(_changes.size() > 0 || _needsFullUpdate) return true;
            for(Field field : this.getClass().getDeclaredFields()) {
                try{
                    if(Collection.class.isAssignableFrom(field.getType())) {
                        Collection c = (Collection) field.get(this);
                        for(Object o : c) {
                            if(o instanceof Syncable && ((Syncable)o).hasChanges()) {
                                return true;
                            }
                        }
                    }
                }catch(Exception ex){}
            }
        }

        return false;
    }   

    public void clearChanges() {
        synchronized(_changes) {
            _changes.clear();
            for(Field field : this.getClass().getDeclaredFields()) {
                try{
                    if(Collection.class.isAssignableFrom(field.getType())) {
                        Collection c = (Collection) field.get(this);
                        for(Object o : c) {
                            if(o instanceof Syncable) {
                                ((Syncable)o).clearChanges();
                            }
                        }
                    }
                }catch(Exception ex){}
            }
        }
    }

    public JSONObject serialize(boolean full) {
        JSONObject json = new JSONObject();
        json.put("full", (full || _needsFullUpdate));
        json.put("uuid", uuid);
        json.put("type", type);

        synchronized(_changes) {
            for(Field field : this.getClass().getDeclaredFields()) {
                if(field.getName().startsWith("_")) continue;

                try{

                    if(Collection.class.isAssignableFrom(field.getType())) {
                        JSONArray array = new JSONArray();
                        Collection c = (Collection) field.get(this);
                        for(Object o : c) {
                            if(o instanceof Syncable) {
                                JSONObject j = ((Syncable)o).serialize(full);
                                if(j != null) array.put(j);
                            }
                        }
                        json.put(field.getName(), array);
                    } else if(full || _needsFullUpdate || _changes.contains(field.getName())) {
                        json.put(field.getName(), field.get(this));
                    }

                }catch(Exception ex){ex.printStackTrace();}
            }
            _needsFullUpdate = false;
        }
        

        return json;
    }
}
