package at.livekit.utils;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.bukkit.Bukkit;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.Connection.Response;

import at.livekit.plugin.Plugin;

public class HeadLibrary implements Runnable
{
    private static final String SKIN_URL = "https://sessionserver.mojang.com/session/minecraft/profile";
    public static final String DEFAULT_HEAD = "iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAA1ElEQVR4XmPQV+D9ry3H+19fnv+/hgz3fxUJjv9qUlxgMS1Z3v8MIAYMG6sI/nfSEvpvryX6X0eOD6wBrGBbZ87/vX1F/49Na/i/t7sIyC75v6Ys6r+JqtB/hlW1af+3tOT+B9FrG3L/zymK+b+7s+j/nEwfMGYASYJAkG3n/63V6WBJEBsEQJoY5iS7/d9cnfR/e1PR/ywHAzDe15Hzf1GW+/+GYJP/DBPinP9Pi3f4X+5kioL74+z+NwZb/mfId9X5n+usBeaAcJWf8f/mUGswBrEBqAeFqNKFJtgAAAAASUVORK5CYII=";
    private static Map<String,String> playerHeads = new HashMap<String,String>(); 
    private static List<String> queue = new ArrayList<String>();
    private static Thread thread = null;

    private static HeadLibraryEvent listener;

    public static void load() {
        File heads = new File(Plugin.getInstance().getDataFolder(), "heads");
        if(!heads.exists()) heads.mkdir();
        for(File file : heads.listFiles()) {
            try{
                String name = file.getName().replace(".txt", "");
                synchronized(playerHeads) {
                    playerHeads.put(name, new String(Files.readAllBytes(Paths.get(file.getAbsolutePath()))));
                }
            }catch(Exception ex){ex.printStackTrace();}
        }
    }

    private static void save(String name, String head) {
        try{
            File headFile = new File(Plugin.getInstance().getDataFolder(), "heads/"+name+".txt");
            if(!headFile.exists()) {
                headFile.createNewFile();
            }
            PrintWriter writer = new PrintWriter(headFile);
            writer.write(head);
            writer.flush();
            writer.close();
        }catch(Exception ex){ex.printStackTrace();}
    }

    public static void setHeadLibraryListener(HeadLibraryEvent listener) {
        HeadLibrary.listener = listener;
    }

    public static void resolveAsync(String name) {
        synchronized(queue) {
            queue.add(name);
        }
        if(thread == null) {
            thread = new Thread(new HeadLibrary());
            thread.start();
        } else {
            synchronized(thread) {
                thread.notifyAll();
            }
        }
    }

    public static boolean has(String name) {
        synchronized(playerHeads) {
            return playerHeads.containsKey(name);
        }
    }

    public static String get(String name) {
        synchronized(playerHeads) {
            if(playerHeads.containsKey(name)) {
                return playerHeads.get(name);
            }
        }
        return DEFAULT_HEAD;
    }

    public static void dispose() {
        if(thread != null) {
            thread.stop();
            thread = null;
        }

        //todo: save heads
    }

    private static void resolveHeadByName(String name) throws Exception {
        Response response = Jsoup.connect("https://api.mojang.com/users/profiles/minecraft/"+name)
                            .ignoreContentType(true)
                            .execute();
        
        JSONObject json = new JSONObject(response.body());
        resolveHead(name, json.getString("id"));
    }

    private static void resolveHead(String name, String uuid) throws Exception{
        Response response  = Jsoup.connect(SKIN_URL+"/"+uuid.replaceAll("-", ""))
                                .ignoreContentType(true)
                                .execute();

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
                        image = image.getSubimage(8, 8, 8, 8);

                        final ByteArrayOutputStream os = new ByteArrayOutputStream();

                        ImageIO.write(image, "png", os);
                        String head = Base64.getEncoder().encodeToString(os.toByteArray());
                        playerHeads.put(name, head);
                        save(name, head);
                    }
                }
            }catch(Exception ex){/*ex.printStackTrace(); /*playerHeads.put(uuid, DEFAULT_HEAD);*/Plugin.debug("HeadLibrary error "+ex.getMessage());}
    }

    @Override
    public void run() {
        

        while(true) {
            while(!queue.isEmpty()) {
                String name;
                synchronized(queue) {
                    name = queue.remove(0);
                }

                try{
                    resolveHeadByName(name);
                    Plugin.log("resolving head for "+name);
                    

                    if(listener != null) listener.onHeadResolved(name, playerHeads.get(name));
                }catch(Exception ex) {
                    ex.printStackTrace();
                    /*synchronized(queue) {
                        queue.add(0, name);
                    }
                    try{
                        Thread.sleep(60*1000);
                    }catch(Exception exx){}*/
                }
            }

            synchronized(thread) {
                try{
                    thread.wait();
                }catch(Exception ex){}
            }
        }
    }
}
