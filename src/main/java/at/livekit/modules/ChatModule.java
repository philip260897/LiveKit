package at.livekit.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.json.JSONArray;
import org.json.JSONObject;

import at.livekit.livekit.Identity;
import at.livekit.packets.IPacket;
import at.livekit.plugin.Plugin;

public class ChatModule extends BaseModule implements Listener {

    private List<ChatMessage> _chat = new ArrayList<ChatMessage>();

    public ChatModule(ModuleListener listener) {
        super(1, "Chat", "livekit.module.chat", UpdateRate.NEVER, listener);
    }

    public void update() {
        super.update();
    }

    @Override
    public void onEnable(Map<String, ActionMethod> signature) {
        Bukkit.getServer().getPluginManager().registerEvents(this, Plugin.getInstance());

        super.onEnable(signature);
    }

    @Override
    public void onDisable(Map<String, ActionMethod> signature) {

        super.onDisable(signature);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAsyncPlayerChatEvent(AsyncPlayerChatEvent event) {
        if (!isEnabled())
            return;

        ChatMessage message = new ChatMessage(event.getPlayer().getUniqueId().toString(), event.getFormat(), event.getMessage());

        synchronized (_chat) {
            _chat.add(message);
        }

        notifyChange();
    }

    @Override
    public IPacket onJoinAsync(Identity identity) {
        JSONObject json = new JSONObject();
        JSONArray messages = new JSONArray();

        synchronized(_chat) {
            for(ChatMessage message : _chat) {
                messages.put(message.toJson());
            }
            _chat.clear();
        }

        json.put("messages", messages);

        return new ModuleUpdatePacket(this, json, true);
    }

    @Override
    public Map<Identity,IPacket> onUpdateAsync(List<Identity> identities) {
        Map<Identity, IPacket> responses = new HashMap<Identity,IPacket>();
        
        if(identities.size() > 0) {
            ModuleUpdatePacket response = (ModuleUpdatePacket) onJoinAsync(identities.get(0));
            response.full = false;
            for(Identity identity : identities) responses.put(identity, response);
        }



        return responses;
    }


    public static class ChatMessage implements Serializable {
        private String sender;
        private String format;
        private String message;
        private Long timestamp;

        public ChatMessage(String sender, String format, String message) {
            this.sender = sender;
            this.format = format;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }

        @Override
        public JSONObject toJson() {
            JSONObject object = new JSONObject();
            object.put("sender", sender);
            object.put("format", format);
            object.put("message", message);
            object.put("timestamp", timestamp);
            return object;
        }

        @Override
        public String toString() {
            return "ChatMessage[sender="+sender+"; format="+format+"; message="+message+"]";
        }
    }
}
