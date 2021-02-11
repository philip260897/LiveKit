package at.livekit.modules;


import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

import at.livekit.livekit.Identity;
import at.livekit.plugin.Config;
import at.livekit.packets.IPacket;

public class SettingsModule extends BaseModule 
{
    public int liveKitVersion = 2;
    public int liveMapVersion = 1;

    public int liveKitPort;
    public String serverName;
    public int liveMapTickRate;
    public boolean needsIdentity;
    public boolean needsPassword;
    public Map<String, JSONObject> modules = new HashMap<String, JSONObject>();

    public SettingsModule(ModuleListener listener) {
        super(1, "LiveKit Settings", "livekit.admin.settings", UpdateRate.NEVER, listener);
    }

    
    @Override
    public void onEnable(Map<String,ActionMethod> signature) {
        super.onEnable(signature);
        liveKitPort = Config.getServerPort();
        serverName = Config.getServerName();
        liveMapTickRate = Config.getTickRate();
        needsIdentity = !Config.allowAnonymous();
        needsPassword = Config.getPassword() != null;
    }

    public void registerModule(String type, JSONObject moduleInfo) {
        if(!modules.containsKey(type)) {
            moduleInfo.remove("version");
            modules.put(type, moduleInfo);
        }
    }

    @Override
    public void onDisable(Map<String,ActionMethod> signature) {
        super.onDisable(signature);
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
}
