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

    private static int CHAT_LOG_SIZE = 50;

    private List<ChatMessage> _updates = new ArrayList<ChatMessage>();
    private List<ChatMessage> _backlog = new ArrayList<ChatMessage>(CHAT_LOG_SIZE);

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
        System.out.println(message.toString());
        synchronized (_updates) {
            _updates.add(message);
        }

        notifyChange();
    }

    @Override
    public IPacket onJoinAsync(Identity identity) {
        JSONObject json = new JSONObject();
        JSONArray messages = new JSONArray();

        synchronized(_backlog) {
            for(ChatMessage message : _backlog) {
                messages.put(message.toJson());
            }
        }

        json.put("messages", messages);

        return new ModuleUpdatePacket(this, json, true);
    }

    @Override
    public Map<Identity,IPacket> onUpdateAsync(List<Identity> identities) {
        Map<Identity, IPacket> responses = new HashMap<Identity,IPacket>();

        JSONObject json = new JSONObject();
        JSONArray messages = new JSONArray();
    
        synchronized(_updates) {
            for(ChatMessage message : _updates) {
                messages.put(message.toJson());
            }

            while(_updates.size() > 0) {
                _backlog.add(_updates.remove(0));
            }
            while(_backlog.size() > CHAT_LOG_SIZE) {
                _backlog.remove(0);
            }
        }
        json.put("messages", messages);
        IPacket response =  new ModuleUpdatePacket(this, json, false);

        for(Identity identity : identities) responses.put(identity, response);

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
            return "ChatMessage[sender="+sender+"; format="+format+"; message="+message+"; json="+toJson().toString()+"]";
        }
    }
}
