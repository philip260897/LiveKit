package at.livekit.plugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;


public class Stat {
    private static String HOST = "https://stat.livekitapp.com/livekit/api/v1";
    public UUID session;

    Stat() {
		File sessionFile = new File(Plugin.getInstance().getDataFolder(), "session");
		if(!sessionFile.exists()) {
			try{
				sessionFile.createNewFile();
				PrintWriter	writer = new PrintWriter(sessionFile);
				writer.println(UUID.randomUUID().toString());
				writer.close();
			}catch(Exception ex){if(Plugin.isDebug()) ex.printStackTrace();}
		}

		session = UUID.randomUUID();
		try{
			List<String> lines = Files.readAllLines(sessionFile.toPath());
			if(lines.size() > 0) session = UUID.fromString(lines.get(0));
		}catch(Exception ex){if(Plugin.isDebug()) ex.printStackTrace();}
    }

    public void onEnabled(int port, int lkPort) {
        get2(HOST+"/server/enable/"+session+"?port="+port+"&lkport="+lkPort);
    }

    public void onLkConnected() {
        get2(HOST+"/server/lkconnect/"+session);
    }

    private void get2(String url2) {
        try {
            URL url = new URL(url2);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            StringBuilder response = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
        

            connection.disconnect();
        } catch (IOException e) {
            if(Plugin.isDebug()) e.printStackTrace();
        }
    }

}
