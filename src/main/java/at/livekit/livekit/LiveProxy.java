package at.livekit.livekit;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

import at.livekit.plugin.Config;
import at.livekit.plugin.Plugin;

public class LiveProxy {

    private static final String HOST = "https://proxy.livekitapp.com/livekit/api/v1";

    //singleton
    private static LiveProxy instance = null;
    public static LiveProxy getInstance() {
        if(instance == null) {
            instance = new LiveProxy();
        }
        return instance;
    }
    
    private File sessionFile = null;
    private String serverUuid = null;
    private String serverToken = null;
    private boolean proxy = false;
    private int proxyConnections = 3;
    private String proxyIp = null;
    private int proxyPort = 0;

    private LiveProxy() {
        sessionFile = new File(Plugin.getInstance().getDataFolder(), "server_identity");
        if(sessionFile.exists()) {
            try {
                List<String> lines = Files.readAllLines(sessionFile.toPath());
                if(lines.size() > 0) {
                    serverUuid = lines.get(0);
                    serverToken = lines.get(1);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public void persistIdentity() {
        if(serverUuid == null || serverToken == null) {
            return;
        }

        try {
            if(!sessionFile.exists()) {
                sessionFile.createNewFile();
            }

            Files.write(sessionFile.toPath(), (serverUuid + "\n" + serverToken).getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean enableServer() {
        int serverPort = Plugin.getInstance().getServer().getPort();
        int liveKitPort = Config.getServerPort();

        Map<String, String> headers = new HashMap<String, String>();
        if(serverUuid != null && serverToken != null) {
            headers.put("Authorization", serverUuid + ":" + serverToken);
        }

        String response = get(HOST+"/server/enable?port=" + serverPort + "&lkport=" + liveKitPort, headers);
        if(response != null) {
            JSONObject json = new JSONObject(response);
            serverUuid = json.getString("uuid");
            serverToken = json.getString("token");
            proxy = json.getBoolean("proxy");
            proxyConnections = json.getInt("proxy_connections");
            proxyIp = json.getString("proxy_ip");
            proxyPort = json.getInt("proxy_port");

            persistIdentity();

            return true;
        }

        return false;
    }


    private String get(String url, Map<String, String> headers) {
        HttpURLConnection connection = null;
        try {
            URL uri = new URL(url);
            connection = (HttpURLConnection) uri.openConnection();



            for(Map.Entry<String, String> entry : headers.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
            connection.setRequestProperty("Referer", "bukkit");

            String response = readBody(connection.getInputStream());

            connection.disconnect();

            return response;
        } catch (ConnectException e) {
            if(Plugin.isDebug()) e.printStackTrace();
            Plugin.debug("LiveKit Proxy Error: "+e.getMessage());
            Plugin.debug("URL: "+url);
        } 
        catch (IOException e) {
            if(Plugin.isDebug()) e.printStackTrace();
            Plugin.debug("URL: "+url);
            try {
                String error = readBody(connection.getErrorStream());
                Plugin.debug("LiveKit Proxy Error: " + error);
            } catch (Exception ex) {e.printStackTrace();}
        }

        return null;
    }

    public String getUuid() {
        return serverUuid;
    }

    public String getToken() {
        return serverToken;
    }

    public String getProxyIp() {
        return proxyIp;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public boolean canProxyConnections() {
        return proxy && serverUuid != null && serverToken != null;
    }

    public int getProxyConnectionCount() {
        return proxyConnections;
    }

    private String readBody(InputStream stream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        String line;
        StringBuilder response = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        return response.toString();
    }
}
