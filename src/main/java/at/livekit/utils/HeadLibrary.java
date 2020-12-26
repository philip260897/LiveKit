package at.livekit.utils;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.Connection.Response;

public class HeadLibrary implements Runnable
{
    private static final String SKIN_URL = "https://sessionserver.mojang.com/session/minecraft/profile";
    public static final String DEFAULT_HEAD = "iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAM0lEQVR4XmNgYGD4TwBjCKBjDAF0jCGAin0+af3XucL1H0TDBNHEsOhCxRgC6BhDAAUDAADgR3U7WfxJAAAAAElFTkSuQmCC";
    private static Map<String,String> playerHeads = new HashMap<String,String>();    
    
    private static List<String> queue = new ArrayList<String>();
    private static Thread thread = null;

    private static HeadLibraryEvent listener;

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
            thread.notifyAll();
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

                    }
                }
            }catch(Exception ex){ex.printStackTrace(); playerHeads.put(uuid, DEFAULT_HEAD);}
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
                    System.out.println("[MineKit] resolving head for "+uuid);
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
