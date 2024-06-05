package at.livekit.livekit;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
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

    private String echoIp = null;
    private int echoPort = 0;
    private String myIp = null;

    private LiveProxy() {

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
        getServerEchoIp();
        Plugin.debug("Echo IP: "+echoIp+" Echo Port: "+echoPort);
        if(echoIp == null || echoPort == 0) {
            return false;
        }

        String ip = getMyIp();
        Plugin.debug("My IP: "+ip);
        if(ip == null) {
            return false;
        }

        File lockFile = new File(Plugin.getInstance().getDataFolder(), "identity.lock");
        for(int i = 0; i < 8; i++) {
            if(lockFile.exists()) {
                try {
                    Thread.sleep(2000);
                    Plugin.log("Identity lock file exists, waiting...");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                break;
            }
        }
        try{ lockFile.createNewFile(); }catch(Exception ex){ex.printStackTrace();}

        sessionFile = new File(Plugin.getInstance().getDataFolder(), "identity");
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

        int serverPort = Plugin.getInstance().getServer().getPort();
        int liveKitPort = Config.getServerPort();

        Map<String, String> headers = new HashMap<String, String>();
        if(serverUuid != null && serverToken != null) {
            headers.put("Authorization", serverUuid + ":" + serverToken);
        }

        String response = get(HOST+"/server/enable?port=" + serverPort + "&lkport=" + liveKitPort + "&ip="+ip + (Config.getProxyHostname() != null ? "&alias="+Config.getProxyHostname() : "") , headers);
        if(response != null) {
            JSONObject json = new JSONObject(response);
            serverUuid = json.getString("uuid");
            serverToken = json.getString("token");
            persistIdentity();
            proxy = json.getBoolean("proxy");
            proxyConnections = json.getInt("proxy_connections");
            proxyIp = json.getString("proxy_ip");
            proxyPort = json.getInt("proxy_port");

            lockFile.delete();
            return true;
        }

        lockFile.delete();
        return false;
    }

    public void getServerEchoIp() {
        String response = get(HOST+"/server/ip", new HashMap<String, String>());
        if(response != null) {
            JSONObject json = new JSONObject(response);
            echoIp = json.getString("ip");
            echoPort = json.getInt("port");
        }
    }


    public String getMyIp() {
        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(echoIp, echoPort));
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            myIp = reader.readLine();
            reader.close();
            socket.close();
            return myIp;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
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

    public String getMyResolvedIp() {
        return myIp;
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
