package at.livekit.utils;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import com.j256.ormlite.field.DatabaseField;

import org.bukkit.OfflinePlayer;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.Connection.Response;

import at.livekit.plugin.Plugin;

public class HeadLibraryV2 implements Runnable
{
    public static String DEFAULT = "iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAA1ElEQVR4XmPQV+D9ry3H+19fnv+/hgz3fxUJjv9qUlxgMS1Z3v8MIAYMG6sI/nfSEvpvryX6X0eOD6wBrGBbZ87/vX1F/49Na/i/t7sIyC75v6Ys6r+JqtB/hlW1af+3tOT+B9FrG3L/zymK+b+7s+j/nEwfMGYASYJAkG3n/63V6WBJEBsEQJoY5iS7/d9cnfR/e1PR/ywHAzDe15Hzf1GW+/+GYJP/DBPinP9Pi3f4X+5kioL74+z+NwZb/mfId9X5n+usBeaAcJWf8f/mUGswBrEBqAeFqNKFJtgAAAAASUVORK5CYII=";
    private static final String SKIN_URL = "https://sessionserver.mojang.com/session/minecraft/profile";
    
    public static HashMap<UUID, Long> refreshCooldown = new HashMap<UUID, Long>();

    //key is player name -> offline server compatibility
    private static HashMap<String, HeadInfo> _cache = new HashMap<String, HeadInfo>();
    private static List<String> _queue = new ArrayList<String>();

    private static Thread thread;
    private static HeadLibraryEvent listener;

    public static void setHeadLibraryListener(HeadLibraryEvent listener) {
        HeadLibraryV2.listener = listener;
    }

    public static void onEnable() {
        abort = false;
        thread = new Thread(new HeadLibraryV2());
        thread.start();
    }   
    
    public static void onDisable() {
        try{
           // save();

            abort = true;
            if(thread != null) {
                thread.interrupt();
                synchronized(_queue) {
                    _queue.notifyAll();
                }
            }
        }catch(Exception ex){
            ex.printStackTrace();
        }
    }

    public static String get(OfflinePlayer player) {
        return get(player.getName(), player.isOnline());
    }

    public static String get(String playerName, boolean online) {
        return get(playerName, online, false);
    }

    public static String get(String playerName, boolean online, boolean forceResolve) {
        String returnValue = DEFAULT;
        boolean resolve = true;
        
        synchronized(_cache) {
            if(_cache.containsKey(playerName)) {
                HeadInfo info = _cache.get(playerName);
                if(info.head != null) {
                    returnValue = info.head;
                    if(!info.needsUpdate() || !online) {
                        resolve = false;
                    }
                }
                if(info.failed == true) resolve = false;
            }
        }
        if(resolve || forceResolve) {
            synchronized(_queue) {
                if(!_queue.contains(playerName)) {
                    _queue.add(playerName);
                    _queue.notifyAll();
                }
            }
        }
        return returnValue;
    }
    

    public static class HeadInfo{
        //private String uuid;
        @DatabaseField(id = true)
        private String name;
        @DatabaseField
        private String head = null;
        @DatabaseField
        private boolean first = true;
        @DatabaseField
        private boolean failed = false;
        @DatabaseField
        private long timestamp = 0;

        HeadInfo(){}

        public HeadInfo(String name) {
            this.name = name;
        }

        public HeadInfo(String name, String head) {
            this.name = name;
            this.head = head;
            this.first = true;
            this.failed = head == null;
        }

        public String getName() {
            return name;
        }

        public String getHead() {
            return head;
        }

        public boolean needsUpdate() {
            return (System.currentTimeMillis() - timestamp > 24*60*60*1000);
        }

        public static HeadInfo fromJson(JSONObject json) {
            HeadInfo info = new HeadInfo(null);
            info.name = json.getString("name");
            if(json.has("head")) info.head = json.getString("head");
            info.first = json.getBoolean("first");
            info.failed = json.getBoolean("failed");
            if(json.has("timestamp")) info.timestamp = json.getLong("timestamp");
            return info;
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("name", name);
            if(head != null) json.put("head", head);
            json.put("first", first);
            json.put("failed", failed);
            json.put("timestamp", timestamp);
            return json;
        }
    }

    private static int rateLimit = 0;
    private static boolean abort = false;
    @Override
    public void run() {
        try{
            List<HeadInfo> heads = Plugin.getStorage().loadPlayerHeads();
            synchronized(_cache) {
                for(HeadInfo head : heads) {
                    _cache.put(head.getName(), head);
                }
            }
        }catch(Exception ex){
            ex.printStackTrace();
        }

        while(!abort) {

            String name = null;
            synchronized(_queue) {
                if(_queue.size() > 0) {
                    name = _queue.remove(0);
                }
            }

            if(name != null) {
                HeadInfo info = null;
                synchronized(_cache) {
                    if(_cache.containsKey(name)) {
                        info = _cache.get(name);
                    }
                }

                if(info == null) {
                    try{
                        info = Plugin.getStorage().loadPlayerHead(name);
                    }catch(Exception ex){ex.printStackTrace();}
                }

                if(info == null || (info.head == null && info.failed == false) || (info.head != null && info.needsUpdate())) {
                    Plugin.debug("[HeadResolver] Resolving "+name+ " update="+(info != null ? info.needsUpdate():false));
                    if(info == null) {
                        info = new HeadInfo(name);
                        /*synchronized(_cache) {
                            _cache.put(name, info);
                        }*/
                    }

                    String head = resolveHeadByName(name);
                    if(head != null) {
                        info.head = head;
                        info.failed = false;
                        info.timestamp = System.currentTimeMillis();

                        synchronized(_cache) {
                            if(_cache.containsKey(name)) {
                                _cache.remove(name);
                            }
                            _cache.put(name, info);
                        }
                        try{
                            Plugin.getStorage().savePlayerHead(name, info);
                        }catch(Exception ex){ex.printStackTrace();}

                        if(HeadLibraryV2.listener != null) HeadLibraryV2.listener.onHeadResolved(info.name, info.head);
                    } else {
                        info.failed = (rateLimit == 0);
                        
                        synchronized(_cache) {
                            if(_cache.containsKey(name)) {
                                _cache.remove(name);
                            }
                            _cache.put(name, info);
                        }
                        try{
                            Plugin.getStorage().savePlayerHead(name, info);
                        }catch(Exception ex){ex.printStackTrace();}

                        if(rateLimit != 0) {
                            synchronized(_queue) {
                                _queue.add(name);
                            }
                            try{
                                Thread.sleep(rateLimit);
                            }catch(InterruptedException ex){}
                        }
                    }



                    //HeadLibraryV2.save();
                } else if(info != null && info.head != null && info.failed == false) {
                    if(HeadLibraryV2.listener != null) HeadLibraryV2.listener.onHeadResolved(info.name, info.head);
                }
            } else {
                try{
                    synchronized(_queue) {
                        _queue.wait();
                    }
                }catch(InterruptedException ex){}
            }

            rateLimit = 0;
        }
    }

    private static String resolveHeadByName(String name) {
        try{
            Response response = Jsoup.connect("https://api.mojang.com/users/profiles/minecraft/"+name)
                                .ignoreContentType(true)
                                .ignoreHttpErrors(true)
                                .execute();
            
            if(response.statusCode() >= 200 && response.statusCode() < 300) {
                try{
                    JSONObject json = new JSONObject(response.body());
                    return resolveHead(name, json.getString("id"));
                }catch(Exception ex){Plugin.debug(ex.getMessage());}
            }
            if(response.statusCode() == 429) {
                rateLimit = 1000*60*10;
            }
        }catch(Exception ex){Plugin.debug(ex.getMessage());}
        return null;
    }

    private static String resolveHead(String name, String uuid) throws Exception{
        Response response  = Jsoup.connect(SKIN_URL+"/"+uuid.replaceAll("-", ""))
                                .ignoreContentType(true)
                                .ignoreHttpErrors(true)
                                .execute();
        
        if(response.statusCode() == 429 ) {
            rateLimit = 1000*60*10;
            return null;
        }
        if(response.statusCode() >= 200 && response.statusCode() < 300) {

            try{
                JSONObject root = new JSONObject(response.body());
                JSONArray props = root.getJSONArray("properties");
                for(int i = 0; i < props.length(); i++) {
                    JSONObject textures = props.getJSONObject(i);
                    if(textures.getString("name").equals("textures")) {
                        String texture = textures.getString("value");
                        JSONObject skinObject = new JSONObject(new String(Base64.getDecoder().decode(texture)));
                        JSONObject t = skinObject.getJSONObject("textures");
                        JSONObject skin = t.getJSONObject("SKIN");
                        String url = skin.getString("url");
                        //System.out.println("SKIN URL: "+url);

                        BufferedImage image = ImageIO.read(new URL(url));

                        BufferedImage face = image.getSubimage(8, 8, 8, 8);
                        BufferedImage hatFace = image.getSubimage(40, 8, 8, 8);

                        Graphics g = face.getGraphics();
                        g.drawImage(hatFace, 0, 0, null);
                        g.dispose();

                        final ByteArrayOutputStream os = new ByteArrayOutputStream();

                        ImageIO.write(face, "png", os);
                        return Base64.getEncoder().encodeToString(os.toByteArray());
                    }
                }
            }catch(Exception ex){/*ex.printStackTrace(); /*playerHeads.put(uuid, DEFAULT_HEAD);*/Plugin.debug("HeadLibrary error "+ex.getMessage());}

        }
        return null;
    }

    /*private static void save() {
        try{
            File file = new File(Plugin.getInstance().getDataFolder(), "heads.json");
            if(!file.exists()) file.createNewFile();

            JSONArray array = new JSONArray();
            synchronized(_cache) {
                for(Entry<String, HeadInfo> entry : _cache.entrySet()) {
                    array.put(entry.getValue().toJson());
                }
            }
        
            PrintWriter writer = new PrintWriter(file);
            writer.write(array.toString());
            writer.flush();
            writer.close();
        }catch(Exception ex){ex.printStackTrace();}
    }*/
}
