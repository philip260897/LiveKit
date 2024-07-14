package at.livekit.livekit;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.json.JSONObject;

import at.livekit.http.HttpClient;
import at.livekit.http.HttpClient.HttpResponse;
import at.livekit.plugin.Config;
import at.livekit.plugin.Plugin;

public class LiveCloud {
    
    private static final String HOST = "https://proxy.livekitapp.com/livekit/api/v1";
    private static LiveCloud instance = null;
    public static LiveCloud getInstance() {
        if(instance == null) {
            instance = new LiveCloud();
        }
        return instance;
    }

    private HttpClient httpClient = new HttpClient();
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private ServerIdentity identity;

    private String serverIp;
    private ProxyInfo proxyInfo;

    private LiveCloud(){}

    protected CompletableFuture<Boolean> initialize(boolean enabled) {
        return async(()->{
            if(!enabled) return false;

            Plugin.debug("Initializing LiveCloud");
            File identityFile = new File(Plugin.getInstance().getDataFolder(), "identity.json");
            if(identityFile.exists()) {
                JSONObject json = new JSONObject(new String(Files.readAllBytes(identityFile.toPath())));
                identity = ServerIdentity.fromJson(json);
            }

            LCIpResponse ipResponse = apiResolveServerIp();
            if(ipResponse.getStatus() != 200) return error(ipResponse.response);
            
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(ipResponse.getIp(), ipResponse.getPort()));
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            serverIp = reader.readLine();
            reader.close();
            socket.close();

            LCServerAuthResponse serverAuth = apiEnableServer(identity, serverIp, Config.getProxyHostname(), Bukkit.getServer().getPort(), Config.getServerPort());
            if(serverAuth.getStatus() != 200) return error(serverAuth.response);
            if(identity == null || !identity.getUuid().equals(serverAuth.getUuid()) || !identity.getToken().equals(serverAuth.getToken())) {
                identity = new ServerIdentity(serverAuth.getUuid(), serverAuth.getToken());
                JSONObject json = identity.toJson();
                Files.write(identityFile.toPath(), json.toString().getBytes());
            }
            
            if(serverAuth.isProxy()) {
                proxyInfo = new ProxyInfo(serverAuth.getProxyIp(), serverAuth.getProxyPort(), serverAuth.getProxyConnectionCount(), serverAuth.getHostname());
            }

            return true;
        });
    }

    protected CompletableFuture<Boolean> liveKitClientConnected() {
        return async(()->{
            if(isInitialized() == false) return false;
            HttpResponse response = apiLiveKitClientConnected(identity);
            return response.getStatus() == 200;
        });
    }

    public boolean isInitialized() {
        return identity != null;
    }

    public boolean isProxyEnabled() {
        return proxyInfo != null;
    }

    public ProxyInfo getProxyInfo() {
        return proxyInfo;
    }

    public ServerIdentity getIdentity() {
        return identity;
    }

    public String getServerIp() {
        return serverIp;
    }

    private <T> CompletableFuture<T> async(Callable<T> supplier) {
        return CompletableFuture.supplyAsync(()->{
            try {
                return supplier.call();
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }, executor);
    }

    protected void dispose() {
        try {
            executor.shutdown();
            executor.awaitTermination(1000*10, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private boolean error(HttpResponse response) {
        Plugin.debug("HTTP " + response.getStatus() + " " + response.getUrl() + " " + response.getBody());
        return false;
    }

    private LCServerAuthResponse apiEnableServer(ServerIdentity identity, String ip, String alias, int serverPort, int liveKitPort) {
        HttpResponse response = httpClient.get(HOST + "/server/enable?ip=" + ip + "&port=" + serverPort + "&lkport=" + liveKitPort+ (alias != null ? "&alias="+alias : ""), identity != null ? identity.toHeaders() : new HashMap<>());
        return new LCServerAuthResponse(response);
    }

    private HttpResponse apiLiveKitClientConnected(ServerIdentity identity) {
        assert identity != null;
        return httpClient.get(HOST + "/server/connected", identity.toHeaders());
    }

    private LCIpResponse apiResolveServerIp() {
        HttpResponse response = httpClient.get(HOST + "/server/ip");
        return new LCIpResponse(response);
    }


    public static class ServerIdentity {
        final String uuid;
        final String token;

        public ServerIdentity(String uuid, String token) {
            this.uuid = uuid;
            this.token = token;
        }

        public static ServerIdentity fromJson(JSONObject json) {
            return new ServerIdentity(json.getString("uuid"), json.getString("token"));
        }

        public String getUuid() {
            return uuid;
        }

        public String getToken() {
            return token;
        }

        public JSONObject toJson() {
            return new JSONObject().put("uuid", uuid).put("token", token);
        }

        public HashMap<String, String> toHeaders() {
            return new HashMap<String, String>() {{
                put("Authorization", uuid + ":" + token);
            }};
        }
    }

    public static class ProxyInfo {
        private String proxyIp;
        private int proxyPort;
        private int proxyConnectionCount;
        private String hostname;

        public ProxyInfo(String proxyIp, int proxyPort, int proxyConnectionCount, String hostname) {
            this.proxyIp = proxyIp;
            this.proxyPort = proxyPort;
            this.proxyConnectionCount = proxyConnectionCount;
            this.hostname = hostname;
        }

        public String getProxyIp() {
            return proxyIp;
        }

        public int getProxyPort() {
            return proxyPort;
        }

        public int getProxyConnectionCount() {
            return proxyConnectionCount;
        }

        public String getHostname() {
            return hostname;
        }
    }

    public static class LCResponse {
        final HttpResponse response;

        public LCResponse(HttpResponse response) {
            this.response = response;
            if(response.getStatus() >= 200 && response.getStatus() < 300) {
                parse(new JSONObject(response.getBody()));
            }
        }

        public int getStatus() {
            return response.getStatus();
        }

        public String getMessage() {
            return response.getBody();
        }

        protected void parse(JSONObject json) {

        }
    }

    public static class LCServerAuthResponse extends LCResponse {
        private String uuid;
        private String token;
        private boolean proxy;
        private int proxyConnectionCount;
        private String proxyIp;
        private int proxyPort;
        private String hostname;

        public LCServerAuthResponse(HttpResponse response) {
            super(response);
        }

        public String getUuid() {
            return uuid;
        }

        public String getToken() {
            return token;
        }

        public boolean isProxy() {
            return proxy;
        }

        public int getProxyConnectionCount() {
            return proxyConnectionCount;
        }

        public String getProxyIp() {
            return proxyIp;
        }

        public int getProxyPort() {
            return proxyPort;
        }

        public String getHostname() {
            return hostname;
        }

        @Override
        protected void parse(JSONObject json) {
            uuid = json.getString("uuid");
            token = json.getString("token");
            proxy = json.getBoolean("proxy");
            proxyConnectionCount = json.getInt("proxy_connections");
            proxyIp = json.getString("proxy_ip");
            proxyPort = json.getInt("proxy_port");
            hostname = json.has("hostname") && !json.isNull("hostname") ? json.getString("hostname") : null;
        }
    }

    public static class LCIpResponse extends LCResponse {
        private String ip;
        private int port;

        public LCIpResponse(HttpResponse response) {
            super(response);
        }

        public String getIp() {
            return ip;
        }

        public int getPort() {
            return port;
        }

        @Override
        protected void parse(JSONObject json) {
            ip = json.getString("ip");
            port = json.getInt("port");
        }
    }
}
