package at.livekit.livekit;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.Callable;

import com.google.gson.Gson;

import org.bukkit.Bukkit;
import org.jsoup.Jsoup;
import org.jsoup.Connection.Method;
import org.jsoup.Connection.Response;
import org.json.*;

import at.livekit.plugin.Config;
import at.livekit.plugin.Plugin;

public class LiveCloudAPI {
    
    protected static String domain = "https://api.livekitapp.com/";

    protected static SessionResponse updateSession() 
    {
        try{
            SessionRequest request = Bukkit.getScheduler().callSyncMethod(Plugin.getInstance(), new Callable<SessionRequest>(){
                @Override
                public SessionRequest call() throws Exception {
                    SessionRequest request = new SessionRequest();
                    request.slots = Bukkit.getMaxPlayers();
                    request.name = Config.getServerName();
                    request.serverPort = Bukkit.getPort();
                    request.livekitPort = Config.getServerPort();
                    //TODO: request.host = Config.getHostName();
                    //TODO: request.optInSearch = Config.getOptInSearch();
                    request.optInSearch = false;
                    request.host = null;
                    return request;
                }
            }).get();

            request.livekitVersion = LiveKit.getInstance().getModuleManager().getSettings().liveKitVersion;

            File file = new File(Plugin.getInstance().getDataFolder(), "identity");
            if(file.exists())
            {
                JSONObject root = new JSONObject(new String(Files.readAllBytes(file.toPath())));
                
                request.serverKey = root.getString("serverKey");
                request.token = root.getString("token");
            }
        
            Response response = post(domain + "session/start", (new Gson()).toJson(request));
            if(response.statusCode() == 200) {
                SessionResponse session = (new Gson().fromJson(response.body(), SessionResponse.class));

                JSONObject root = new JSONObject();
                root.put("serverKey", session.getServerKey());
                root.put("token", session.getSessionToken());
                
                if(!file.exists()) file.createNewFile();
                Files.write(file.toPath(), root.toString().getBytes());

                return session;
            }
        }catch(Exception ex){if(Plugin.isDebug()) ex.printStackTrace();}

        return null;
    }

    private static Response post(String url, String json) throws Exception {
        return Jsoup.connect(url)
            .method(Method.POST)
            .requestBody(json)
            .ignoreContentType(true)
            .ignoreHttpErrors(true)
            .header("content-type", "application/json")
            .execute();
    }

    public static class SessionRequest
    {
        private String serverKey;
        private String token;
        private String name;
        private int slots;
        private String host;
        private Integer livekitPort;
        private Integer serverPort;
        private Integer livekitVersion;
        private boolean optInSearch;

        protected SessionRequest(){}

        public SessionRequest(String serverKey, String name, String token, int slots){
            this.serverKey = serverKey;
            this.name = name;
            this.slots = slots;
            this.token = token;
        }

        public String getServerKey() {
            return serverKey;
        }

        public void setServerKey(String serverKey) {
            this.serverKey = serverKey;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getSlots() {
            return slots;
        }

        public void setSlots(Integer slots) {
            this.slots = slots;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public Integer getLivekitPort() {
            return livekitPort;
        }

        public void setLivekitPort(Integer livekitPort) {
            this.livekitPort = livekitPort;
        }

        public Integer getServerPort() {
            return serverPort;
        }

        public void setServerPort(Integer serverPort) {
            this.serverPort = serverPort;
        }

        public Integer getLivekitVersion() {
            return livekitVersion;
        }

        public void setLivekitVersion(Integer livekitVersion) {
            this.livekitVersion = livekitVersion;
        }

        public boolean isOptInSearch() {
            return optInSearch;
        }

        public void setOptInSearch(boolean optInSearch) {
            this.optInSearch = optInSearch;
        }
    }

    public static class SessionResponse
    {
        private String serverKey;
        private String sessionToken;

        protected SessionResponse(){}

        public SessionResponse(String serverKey, String sessionToken) {
            this.serverKey = serverKey;
            this.sessionToken = sessionToken;
        }

        public String getServerKey() {
            return serverKey;
        }

        public String getSessionToken() {
            return sessionToken;
        }
    }
}
