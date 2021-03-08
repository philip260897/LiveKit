package at.livekit.modules;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.json.JSONArray;
import org.json.JSONObject;
import at.livekit.livekit.Identity;
import at.livekit.packets.IPacket;
import at.livekit.packets.Packet;

public abstract class BaseModule 
{
    private int version;
    private UpdateRate tick;
    private String name;
    private String permission;

    private boolean active = false;
    private boolean enabled = false;

    private ModuleListener listener;
    private String subscription;

    public BaseModule(int version, String name, String permission, UpdateRate tick, ModuleListener listener) {
        this(version, name, permission, tick, listener, null);
    }

    public BaseModule(int version, String name, String permission, UpdateRate tick, ModuleListener listener, String subscription) {
        this.version = version;
        this.name = name;
        this.permission = permission;
        this.tick = tick;
        this.listener = listener;
        this.subscription = subscription;
    }

    public boolean isSubscribeable() {
        return subscription != null;
    }

    public String getSubscription() {
        return subscription;
    }

    public void onEnable(Map<String,ActionMethod> signature) {
        Method[] methods = this.getClass().getDeclaredMethods();
        String name = getType();

        synchronized(signature) {
            for(Method method : methods) {
                if(method.isAnnotationPresent(Action.class)) {
                    Action a = method.getAnnotation(Action.class);
                    signature.put(name+":"+a.name(), new ActionMethod(method, a.sync()));
                }
            }
        }
        
        enabled = true;
        if(listener != null) listener.onFullUpdate(getType());
    }

    public void onDisable(Map<String,ActionMethod> signature) {
        enabled = false;
        String name = getType();
        synchronized(signature) {
            Iterator<Entry<String,ActionMethod>> iterator = signature.entrySet().iterator();
            while(iterator.hasNext()) {
                if(iterator.next().getKey().startsWith(name+":")) {
                    iterator.remove();
                }
            }
        }
    }

    protected void notifyChange() {
        if(listener != null) listener.onDataChangeAvailable(this.getType());
    }

    protected void notifyFull() {
        if(listener != null) listener.onFullUpdate(this.getType());
    }
    
    private long lastUpdate = 0;
    public boolean canUpdate(int tickrate) {
        if(tick == UpdateRate.MAX) return true;
        if(tick == UpdateRate.HIGH && System.currentTimeMillis() - lastUpdate > tickrate*2) return true; 
        if(tick == UpdateRate.ONCE && lastUpdate == 0) return true;
        if(tick == UpdateRate.ONCE_PERSEC && System.currentTimeMillis()-lastUpdate > 1000) return true;
        if(tick == UpdateRate.TWICE_PERSEC && System.currentTimeMillis()-lastUpdate > 500) return true;
        return false;
    }

    public void update(){
        lastUpdate = System.currentTimeMillis();
    }

    public IPacket onJoinAsync(Identity identity){return null;}

    public Map<Identity, IPacket> onUpdateAsync(List<Identity> identities){return null;}

    public IPacket onChangeAsync(Identity identity, IPacket packet){
        return null;
    }

    /*public IPacket invokeActionSync(Identity identity, ActionPacket packet) {
        ActionPacket action = (ActionPacket) packet;
        Method[] methods = this.getClass().getDeclaredMethods();
        for(Method method : methods) {
            if(method.isAnnotationPresent(Action.class)) {
                Action a = method.getAnnotation(Action.class);
                if(a.name().equals(action.getActionName())) {
                    try{
                        return Bukkit.getScheduler().callSyncMethod(Plugin.getInstance(), new Callable<IPacket>(){

                            @Override
                            public IPacket call() throws Exception {
                                try{
                                    return (IPacket) method.invoke(BaseModule.this, identity, action);
                                }catch(Exception ex){ex.printStackTrace();}
                                return new StatusPacket(0, "An error occured!");
                            }
                            
                        }).get();
                    }catch(Exception ex){ex.printStackTrace(); return new StatusPacket(0, "An error occured!");}
                }
            }
        }
        return null;
    }

    public IPacket invokeAction(Identity identity, ActionPacket packet, boolean sync) throws Exception {
        ActionPacket action = (ActionPacket) packet;
        Method[] methods = this.getClass().getDeclaredMethods();
        for(Method method : methods) {
            if(method.isAnnotationPresent(Action.class)) {
                Action a = method.getAnnotation(Action.class);
                if(a.sync() != sync) throw new Exception("Async/Sync invocation mismatch!");
                if(a.name().equals(action.getActionName())) {
                    return (IPacket) method.invoke(BaseModule.this, identity, action);
                }
            }
        }
        return null;
    }*/

    public boolean hasAccess(Identity identity) {
        return identity.hasPermission(permission) && (isSubscribeable() ? identity.isSubscribed(this.getClass().getSimpleName(), subscription) : true);
    }

    /*public JSONObject toJson(String uuid) {
        return moduleInfo();
    }*/

    public String getType() {
        return this.getClass().getSimpleName() + (subscription != null ? ":"+subscription : "");
    }

    public String getPermission() {
        return this.permission;
    }

    public int getVersion() {
        return this.version;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public UpdateRate getTickRate() {
        return tick;
    }

    public JSONObject moduleInfo() {
        JSONObject json = new JSONObject();
        json.put("version", version);
        json.put("name", name);
        json.put("active", active);
        json.put("subscribeable", isSubscribeable());
        json.put("moduleType", this.getClass().getSimpleName());
        return json;
    }

    public static enum UpdateRate {
        NEVER, ONCE, ONCE_PERSEC, TWICE_PERSEC, HIGH, MAX
    }

    public static interface ModuleListener {
        void onDataChangeAvailable(String moduleType);

        void onFullUpdate(String moduleType);
    }

    public static class ModuleUpdatePacket extends ModulePacket 
    {
        public static int PACKET_ID = 15;
        private JSONObject data;
        public boolean full;

        public ModuleUpdatePacket(BaseModule module, JSONObject data, boolean full) {
            super(module.getType());
            this.data = data;
            this.full = full;
        }

        @Override
        public JSONObject toJson() {
            JSONObject json = super.toJson();
            json.put("data", data);
            json.put("full", full);
            json.put("packet_id", PACKET_ID);
            return json;
        }
    }

    public static class ModulesAvailablePacket extends Packet {

        public static int PACKET_ID = 16;
        private JSONArray modules;
        private JSONArray subscriptions;

        public ModulesAvailablePacket(JSONArray modules, JSONArray subscriptions) {
            this.modules = modules;
            this.subscriptions = subscriptions;
        }

        @Override
        public IPacket fromJson(String json) {return null;}

        @Override
        public JSONObject toJson() { 
            JSONObject json = new JSONObject();
            json.put("packet_id", PACKET_ID);
            json.put("modules", modules);
            json.put("subscriptions", subscriptions);
            return json;
        }
    }

    public static class ModulePacket extends Packet {

        private String moduleType;

        public ModulePacket(String type) {
            this.moduleType = type;
        }

        public String getModuleType() {
            return moduleType;
        }

        @Override
        public IPacket fromJson(String json) {
            JSONObject j = new JSONObject(json);
            this.moduleType = j.getString("moduleType");
            return this;
        }

        @Override
        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("moduleType", moduleType);
            return json;
        }

    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Action {
        public String name() default "";
        public boolean sync() default true;
    }

    public static class ActionMethod {
        private boolean sync;
        private Method method;

        public ActionMethod(Method method, boolean sync) {
            this.method = method;
            this.sync = sync;
        }

        public boolean sync() {
            return sync;
        }

        public IPacket invoke(Object instance, boolean sync, Object ...parameters) throws Exception {
            if(this.sync != sync) throw new Exception("Async/Sync invokation mismatch!");
            return (IPacket) method.invoke(instance, parameters);
        }
    }
}
