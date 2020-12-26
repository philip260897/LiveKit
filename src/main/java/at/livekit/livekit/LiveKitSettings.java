package at.livekit.livekit;

import org.json.JSONObject;

public class LiveKitSettings 
{
    public int liveKitVersion = 1;
    public int liveKitPort = 8123;

    public int liveMapVersion = 1;
    public int liveMapTickRate = 8;
    
    public boolean needsIdentity = true;
    public String[] liveMaps = new String[]{"world"};
    public String[] capabilities = new String[]{"admin"};

    public JSONObject json() {
        JSONObject json = new JSONObject();
        json.put("liveKitVersion", liveKitVersion);
        json.put("liveMapVersion", liveMapVersion);
        json.put("liveMapTickRate", liveMapTickRate);
        json.put("liveKitPort", liveKitPort);
        json.put("needsIdentity", needsIdentity);
        json.put("liveMaps", liveMaps);
        json.put("capabilities", capabilities);
        return json;
    }
}
