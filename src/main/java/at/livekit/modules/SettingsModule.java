package at.livekit.modules;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import at.livekit.livekit.Identity;
import at.livekit.plugin.Plugin;
import at.livekit.server.IPacket;

public class SettingsModule extends BaseModule 
{
    public int liveKitVersion = 1;
    public int liveMapVersion = 1;
    public int liveKitPort = 8123;
    public String serverName = "Minecraft Server";

    public int liveMapTickRate = 4;
    public boolean needsIdentity = true;
    //public String liveMap = null;
    //public String[] liveMaps = new String[]{"world"};
    //public String[] capabilities = new String[]{"admin"};
    public Map<String, JSONObject> modules = new HashMap<String, JSONObject>();

    public SettingsModule(ModuleListener listener) {
        super(1, "LiveKit Settings", "livekit.admin.settings", UpdateRate.NEVER, listener);
    }

    
    @Override
    public void onEnable() {
        super.onEnable();
        File settingsFile = new File(Plugin.workingDirectory+"/settings.json");
        if(!settingsFile.exists()) return;

        try{
            JSONObject json = new JSONObject(new String(Files.readAllBytes(Paths.get(Plugin.workingDirectory+"/settings.json"))));
            liveKitPort = json.getInt("liveKitPort");
            liveMapTickRate = json.getInt("liveMapTickRate");
            needsIdentity = json.getBoolean("needsIdentity");
           // liveMap = json.getString("liveMap");
            serverName = json.getString("serverName");

            if(liveMapTickRate > 20) liveMapTickRate = 20;
            if(liveMapTickRate < 1) liveMapTickRate = 1;

            JSONArray mods = json.getJSONArray("modules");
            for(int i = 0; i < mods.length(); i++) {
                modules.put(mods.getJSONObject(i).getString("moduleType"), mods.getJSONObject(i));
            }
        }catch(Exception ex){ex.printStackTrace();}
    }

    public void registerModule(String type, JSONObject moduleInfo) {
        if(!modules.containsKey(type)) {
            System.out.println("registering "+type);
            moduleInfo.remove("version");
            modules.put(type, moduleInfo);
        }
    }

    @Override
    public void onDisable() {
        File settingsFile = new File(Plugin.workingDirectory+"/settings.json");
        if(!settingsFile.exists()) {
            try{
                settingsFile.createNewFile();
            }catch(Exception ex){ex.printStackTrace();}
        }

        JSONObject json = new JSONObject();
        json.put("liveKitPort", liveKitPort);
        json.put("liveMapTickRate", liveMapTickRate);
        //json.put("liveMap", liveMap);
        json.put("needsIdentity", needsIdentity);
        json.put("modules", modules.values());
        json.put("serverName", serverName);

        try{
            PrintWriter writer = new PrintWriter(settingsFile);
            writer.write(json.toString());
            writer.flush();
            writer.close();
        }catch(Exception ex){ex.printStackTrace();}
        super.onDisable();
    }

    @Override
    public IPacket onJoinAsync(Identity identity) {
        JSONObject json = new JSONObject();
        json.put("liveKitTickRate", liveMapTickRate);
        json.put("needsIdentity", needsIdentity);
        json.put("modules", modules);
        return new ModuleUpdatePacket(this, json, true);
    }

    @Override
    public Map<Identity, IPacket> onUpdateAsync(List<Identity> identities) {
        Map<Identity, IPacket> responses = new HashMap<Identity,IPacket>();
        for(Identity identity : identities) {
            responses.put(identity, this.onJoinAsync(identity));
        }
        return responses;
    }

    @Override
    public IPacket onChangeAsync(Identity identity, IPacket packet) {
        return null;
    }

    /*@Override
    public JSONObject toJson(String uuid) {
        JSONObject json = super.toJson(uuid);
        json.put("liveKitVersion", liveKitVersion);
        json.put("liveMapVersion", liveMapVersion);
        json.put("liveMapTickRate", liveMapTickRate);
        json.put("liveKitPort", liveKitPort);
        json.put("needsIdentity", needsIdentity);
        json.put("liveMap", liveMap);
        json.put("modules", modules);
        return json;
    }*/
}
