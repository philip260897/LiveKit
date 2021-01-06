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

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.Connection.Response;

import at.livekit.plugin.Plugin;

public class HeadLibrary implements Runnable
{
    private static final String SKIN_URL = "https://sessionserver.mojang.com/session/minecraft/profile";
    public static final String DEFAULT_HEAD = "iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAM0lEQVR4XmNgYGD4TwBjCKBjDAF0jCGAin0+af3XucL1H0TDBNHEsOhCxRgC6BhDAAUDAADgR3U7WfxJAAAAAElFTkSuQmCC";
    private static Map<String,String> playerHeads = new HashMap<String,String>(); 
    private static List<String> queue = new ArrayList<String>();
    private static Thread thread = null;

    private static HeadLibraryEvent listener;

    public static void load() {
        File heads = new File(Plugin.getInstance().getDataFolder(), "heads");
        if(!heads.exists()) heads.mkdir();
        for(File file : heads.listFiles()) {
            try{
                UUID uuid = UUID.fromString(file.getName().replace(".txt", ""));
                synchronized(playerHeads) {
                    playerHeads.put(uuid.toString(), new String(Files.readAllBytes(Paths.get(file.getAbsolutePath()))));
                }
            }catch(Exception ex){ex.printStackTrace();}
        }
    }

    private static void save(String uuid, String head) {
        try{
            File headFile = new File(Plugin.getInstance().getDataFolder(), "heads/"+uuid+".txt");
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

    public static void resolveAsync(String uuid) {
        synchronized(queue) {
            queue.add(uuid);
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

    public static boolean has(String uuid) {
        synchronized(playerHeads) {
            return playerHeads.containsKey(uuid);
        }
    }

    public static String get(String uuid) {
        synchronized(playerHeads) {
            if(playerHeads.containsKey(uuid)) {
                return playerHeads.get(uuid);
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

    private static void resolveHead(String uuid) throws Exception{
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
                        playerHeads.put(uuid, head);
                        save(uuid, head);
                    }
                }
            }catch(Exception ex){/*ex.printStackTrace(); /*playerHeads.put(uuid, DEFAULT_HEAD);*/Plugin.debug("HeadLibrary error "+ex.getMessage());}
    }

    @Override
    public void run() {
        

        while(true) {
            while(!queue.isEmpty()) {
                String uuid;
                synchronized(queue) {
                    uuid = queue.remove(0);
                }

                try{
                    Plugin.log("resolving head for "+uuid);
                    resolveHead(uuid);

                    if(listener != null) listener.onHeadResolved(uuid, playerHeads.get(uuid));
                }catch(Exception ex) {
                    ex.printStackTrace();
                    synchronized(queue) {
                        queue.add(0, uuid);
                    }
                    try{
                        Thread.sleep(30*1000);
                    }catch(Exception exx){}
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
