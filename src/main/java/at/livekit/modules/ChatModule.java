package at.livekit.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.json.JSONArray;
import org.json.JSONObject;

import at.livekit.api.chat.ChatMessage;
import at.livekit.livekit.Identity;
import at.livekit.packets.ActionPacket;
import at.livekit.packets.IPacket;
import at.livekit.packets.StatusPacket;
import at.livekit.plugin.Plugin;

public class ChatModule extends BaseModule implements Listener {

    private static int CHAT_LOG_SIZE = 50;
    //private DiscordSRVPlugin discrodPlugin;

    private List<ChatMessage> _updates = new ArrayList<ChatMessage>();
    private List<ChatMessage> _backlog = new ArrayList<ChatMessage>(CHAT_LOG_SIZE);

    public ChatModule(ModuleListener listener) {
        super(1, "Chat", "livekit.module.chat", UpdateRate.NEVER, listener);
    }

    public void update() {
        super.update();
    }

    public void sendChatMessage(ChatMessage message) {
        if(!isEnabled()) return;

        synchronized(_updates) {
            _updates.add(message);
        }

        notifyChange();
    }

    @Override
    public void onEnable(Map<String, ActionMethod> signature) {
        Bukkit.getServer().getPluginManager().registerEvents(this, Plugin.getInstance());


        super.onEnable(signature);
    }

    @Override
    public void onDisable(Map<String, ActionMethod> signature) {
        HandlerList.unregisterAll(this);


        _backlog.clear();
        _updates.clear();
        super.onDisable(signature);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAsyncPlayerChatEvent(AsyncPlayerChatEvent event) {
        if (!isEnabled())
            return;

        ChatMessage message = new ChatMessage(event.getPlayer(), event.getMessage());
        message.setPrefix(null);

        synchronized (_updates) {
            _updates.add(message);
        }

        notifyChange();
    }
    
    /*@Override
    public void onDiscordChat(String id, String name, String message) {
        if(!isEnabled())
            return;

        Plugin.debug("DiscordSRV: "+id+" "+name+" "+message);

        ChatMessage m = new ChatMessage(name, message, ChatColor.DARK_PURPLE+"DiscordSRV");
        synchronized(_updates) {
            _updates.add(m);
        }

        notifyChange();
    }*/

    @Action(name = "Message")
    public IPacket sendMessage(Identity identity, ActionPacket packet) {
        JSONObject data = packet.getData();

        if(!identity.hasPermission("livekit.chat.write") || !identity.hasPermission("livekit.chat.write_offline")) return new StatusPacket(0, "Permission denied!");
        if(identity.isAnonymous()) return new StatusPacket(0, "Permission denied!");

        OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(identity.getUuid()));
        if(op == null) return new StatusPacket(0, "Player not found");

        if(!op.isOnline() && !identity.hasPermission("livekit.chat.write_offline")) return new StatusPacket(0, "Can't chat while offline");

        if(op.isOnline()) {
            Player player = op.getPlayer();
            if(data.has("message") && !data.isNull("message")) player.chat(data.getString("message"));
        } else {
            if(data.has("message") && !data.isNull("message")/* && data.has("displayName") && !data.isNull("displayName")*/) {
                String message = data.getString("message");
                //String displayName = data.getString("displayName");
                for(Player player : Bukkit.getOnlinePlayers()) {
                    player.sendMessage(ChatColor.GREEN+"["+op.getName()+"] "+ChatColor.RESET+message);
                }

                ChatMessage m = new ChatMessage(op, message);
                m.setPrefix("LiveKit");
                sendChatMessage(m);
            }
        }

        return new StatusPacket(1);
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
        json.put("write", identity.hasPermission("livekit.chat.write"));
        json.put("offline", identity.hasPermission("livekit.chat.write_offline"));

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


    /*public static class ChatMessage implements Serializable {
        private String sender;
        private String altName;
        private String source;
        private String format;
        private String message;
        private Long timestamp;

        public ChatMessage(String sender, String format, String message) {
            this(sender, format, message, null, null);
        }

        public ChatMessage(String sender, String format, String message, String altName, String source) {
            this.sender = sender;
            this.format = format;
            this.message = message;
            this.altName = altName;
            this.source = source;
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
    }*/



}
