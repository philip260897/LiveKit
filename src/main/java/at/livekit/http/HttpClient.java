package at.livekit.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import at.livekit.plugin.Plugin;
import github.scarsz.discordsrv.dependencies.json.JSONObject;

public class HttpClient {

    public HttpResponse get(String url) {
        return get(url, new HashMap<String, String>());
    }

    public HttpResponse get(String url, Map<String, String> headers) {
        return makeWebRequest(url, null, headers);
    }

    public HttpResponse post(String url, JSONObject body) {
        return post(url, body, new HashMap<String, String>());
    }

    public HttpResponse post(String url, JSONObject body, Map<String, String> headers) {
        return makeWebRequest(url, body.toString(), headers);
    }

    private HttpResponse makeWebRequest(String url, String body, Map<String, String> headers) {
        /*return new CompletableFuture<HttpResponse>() {
            @Override
            public HttpResponse call() throws Exception {*/
                HttpURLConnection connection = null;
                try {
                    URL uri = new URL(url);
                    connection = (HttpURLConnection) uri.openConnection();
    
                    for(Map.Entry<String, String> entry : headers.entrySet()) {
                        connection.setRequestProperty(entry.getKey(), entry.getValue());
                    }
                    connection.setRequestProperty("Referer", "bukkit");
                    if(body != null) {
                        connection.setRequestMethod("POST");
                        connection.setDoOutput(true);
                        connection.getOutputStream().write(body.getBytes());
                    }
    
                    String response = readBody(connection.getInputStream());
                    connection.disconnect();
                    return new HttpResponse(connection.getResponseCode(), response, url);
                } catch (ConnectException e) {
                    return new HttpResponse(0, e.getMessage(), url);
                } 
                catch (IOException e) {
                    if(Plugin.isDebug()) e.printStackTrace();
                    try {
                        String error = readBody(connection.getErrorStream());
                        return new HttpResponse(connection.getResponseCode(), error, url);
                    } catch (Exception ex) {e.printStackTrace();}
                }
    
                return new HttpResponse(0, "Something went wrong", url);
            //}
       // };
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

    public static class HttpResponse {
        private String url;
        private int status;
        private String body;

        public HttpResponse(int status, String body, String url) {
            this.status = status;
            this.body = body;
            this.url = url;
        }

        public String getUrl() {
            return url;
        }

        public int getStatus() {
            return status;
        }

        public String getBody() {
            return body;
        }
    }
}
