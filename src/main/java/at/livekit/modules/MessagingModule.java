package at.livekit.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.json.JSONArray;
import org.json.JSONObject;

import at.livekit.api.pm.IMessagingInterface;
import at.livekit.api.pm.MessageGroup;
import at.livekit.api.pm.MessagingAdapter;
import at.livekit.api.pm.PrivateMessage;
import at.livekit.livekit.Identity;
import at.livekit.livekit.LiveKit;
import at.livekit.packets.ActionPacket;
import at.livekit.packets.IPacket;
import at.livekit.packets.StatusPacket;
import at.livekit.plugin.Plugin;

public class MessagingModule extends BaseModule implements IMessagingInterface {

    private ConcurrentLinkedQueue<PrivateMessage> messageQueue = new ConcurrentLinkedQueue<PrivateMessage>();
    private Map<String, List<PrivateMessage>> messageUpdates = new HashMap<String, List<PrivateMessage>>();
    private MessagingAdapter messagingAdapter;

    public MessagingModule(ModuleListener listener) {
        super(1, "MessagingModule", "livekit.module.messaging", UpdateRate.ONCE_PERSEC, listener);
    }

    public void setMessagingAdapter(MessagingAdapter messagingAdapter) {
        this.messagingAdapter = messagingAdapter;
        messagingAdapter.registerMessagingService(this);
    }

    @Override
    public void update() {
        if(messageQueue.isEmpty()) return;

        Bukkit.getScheduler().runTaskAsynchronously(Plugin.getInstance(), new Runnable() {
            @Override
            public void run() {
                while(!messageQueue.isEmpty()) {
                    try {
                        PrivateMessage pm = messageQueue.poll();
                        if(pm != null) {
                            Plugin.getStorage().create(pm);
                        }

                        List<PrivateMessage> existing = getPrivateMessages(pm.getReceiver(), pm.getSender());
                        existing.sort((a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));

                        while(existing.size() > 50) {
                            Plugin.getStorage().delete(existing.get(0));
                            existing.remove(0);
                        }

                        if(pm.getChannel().equals("default")) {
                            if(LiveKit.getInstance().getConnectedClients(pm.getReceiver().toString()).size() > 0) {
                                synchronized(messageUpdates) {
                                    List<PrivateMessage> messages = messageUpdates.containsKey(pm.getReceiver().toString()) ? messageUpdates.get(pm.getReceiver().toString()) : new ArrayList<>();
                                    messages.add(pm);
                                    messageUpdates.put(pm.getReceiver().toString(), messages);
                                }
                            }

                            if(!pm.getSender().equals(pm.getReceiver()) && LiveKit.getInstance().getConnectedClients(pm.getSender().toString()).size() > 0) {
                                synchronized(messageUpdates) {
                                    List<PrivateMessage> messages = messageUpdates.containsKey(pm.getSender().toString()) ? messageUpdates.get(pm.getSender().toString()) : new ArrayList<>();
                                    messages.add(pm);
                                    messageUpdates.put(pm.getSender().toString(), messages);
                                }
                            }
                        } else if(pm.getChannel().equals("group")) {
                            MessageGroup group = Plugin.getStorage().loadSingle(MessageGroup.class, pm.getReceiver().toString());
                            if(group != null) {
                                for(UUID member : group.getMembers()) {
                                    if(member.equals(pm.getSender())) continue;
                                    if(LiveKit.getInstance().getConnectedClients(pm.getReceiver().toString()).size() > 0) {
                                        synchronized(messageUpdates) {
                                            List<PrivateMessage> messages = messageUpdates.containsKey(member.toString()) ? messageUpdates.get(member.toString()) : new ArrayList<>();
                                            messages.add(pm);
                                            messageUpdates.put(member.toString(), messages);
                                        }
                                    }
                                }
                            }
                        }

                    } catch(Exception e) {
                        e.printStackTrace();
                    }
                }
                notifyChange();
            }
        
        });
    }

    @Override
    public void onDisconnectAsync(Identity identity) {
        synchronized(messageUpdates) {
            messageUpdates.remove(identity.getUuid());
        }
    }

    @Override
    public void onGroupMessageReceived(OfflinePlayer sender, MessageGroup receiver, String message) {
        if(messagingAdapter != null) {
            messagingAdapter.onGroupMessageReceived(sender, receiver, message);
        }
    }

    @Override
    public void onPrivateMessageReceived(OfflinePlayer arg0, OfflinePlayer arg1, String arg2) {
        if(messagingAdapter != null) {
            messagingAdapter.onPrivateMessageReceived(arg0, arg1, arg2);
        }
    }

    @Override
    public void sendPrivateMessage(UUID sender, UUID receiver, String message) {
        PrivateMessage pm = PrivateMessage.create(sender, receiver, message);
        messageQueue.add(pm);
    }

    @Override
    public void sendPrivateMessage(UUID sender, MessageGroup receiver, String message) {
        PrivateMessage pm = PrivateMessage.create(sender, receiver, message);
        messageQueue.add(pm);
    }
    

    @Override
    public IPacket onJoinAsync(Identity identity) {
        JSONObject json = new JSONObject();
        JSONArray messages = new JSONArray();
        List<PrivateMessage> pms = getLastPrivateMessageForEachChat(UUID.fromString(identity.getUuid()));
        for(PrivateMessage pm : pms) {
            messages.put(pm.toJson());
        }
        json.put("messages", messages);
        json.put("uuid", identity.getUuid());
        return new ModuleUpdatePacket(this, json, true);
    }

    @Override
    public Map<Identity, IPacket> onUpdateAsync(List<Identity> identities) {
        Map<Identity, IPacket> responses = new HashMap<Identity, IPacket>();
        for(Identity identity : identities) {

            JSONObject json = new JSONObject();
            JSONArray messages = new JSONArray();
            synchronized(messageUpdates) {
                if(messageUpdates.containsKey(identity.getUuid())) {
                    for(PrivateMessage pm : messageUpdates.get(identity.getUuid())) {
                        messages.put(pm.toJson());
                    }
                    messageUpdates.remove(identity.getUuid());
                }
            }
            json.put("messages", messages);
            responses.put(identity, new ModuleUpdatePacket(this, json, false));
        }
        return responses;
    }

    @Action(name = "GetPrivateMessages", sync = false)
    public IPacket actionGetPrivateMessages(Identity identity, ActionPacket packet) {
        JSONObject json = packet.getData();
        UUID sender = UUID.fromString(identity.getUuid());
        UUID receiver = UUID.fromString(json.getString("receiver"));
        long timestamp = json.getLong("timestamp");

        List<PrivateMessage> messages = getPrivateMessages(sender, receiver).stream().filter((c) -> c.getTimestamp() > timestamp).collect(Collectors.toList());

        JSONArray array = new JSONArray();
        for(PrivateMessage pm : messages) {
            array.put(pm.toJson());
        }

        JSONObject response = new JSONObject();
        response.put("messages", array);
        return new StatusPacket(1, response);
    }

    @Action(name = "ListChats", sync = false) 
    public IPacket listChatsAction(Identity identity, ActionPacket packet) {
        List<PrivateMessage> messages = getLastPrivateMessageForEachChat(UUID.fromString(identity.getUuid()));

        JSONArray array = new JSONArray();
        for(PrivateMessage pm : messages) {
            JSONObject json = new JSONObject();
            json.put("sender", pm.getSender().toString());
            json.put("receiver", pm.getReceiver().toString());
            json.put("message", pm.getMessage());
            json.put("timestamp", pm.getTimestamp());
            array.put(json);
        }

        JSONObject response = new JSONObject();
        response.put("chats", array);

        return new StatusPacket(1, response);
    }

    @Action(name = "SendPrivateMessage", sync = true)
    public IPacket sendPrivateMessage(Identity identity, ActionPacket packet) {
        JSONObject json = packet.getData();
        UUID sender = UUID.fromString(identity.getUuid());
        UUID receiver = UUID.fromString(json.getString("receiver"));

        String message = json.getString("message");
        String channel = json.getString("channel");

        if(channel.equals("default")) {

            
            PrivateMessage pm = PrivateMessage.create(sender, receiver, message);
            messageQueue.add(pm);



            OfflinePlayer senderPlayer = Bukkit.getOfflinePlayer(sender);
            OfflinePlayer receiverPlayer = Bukkit.getOfflinePlayer(receiver);
                    
            onPrivateMessageReceived(senderPlayer, receiverPlayer, message);
            //return null;

        }

        return new StatusPacket(1);
    }

    private List<PrivateMessage> getPrivateMessages(UUID sender, UUID receiver) {
        List<PrivateMessage> messages = new ArrayList<PrivateMessage>();
        try {
            messages.addAll(Plugin.getStorage().load(PrivateMessage.class, "sender", sender).stream().filter((c) -> c.getReceiver().equals(receiver)).collect(Collectors.toList()));
            if(!sender.equals(receiver)) {
                messages.addAll(Plugin.getStorage().load(PrivateMessage.class, "receiver", sender).stream().filter((c) -> c.getSender().equals(receiver)).collect(Collectors.toList()));
            }
        } catch(Exception e) { e.printStackTrace(); }
        return messages;
    }

    private List<PrivateMessage> getLastPrivateMessageForEachChat(UUID sender) {
        List<PrivateMessage> messages = new ArrayList<PrivateMessage>();
        try {
            Set<UUID> uuids = new HashSet<UUID>();
            uuids.addAll(Plugin.getStorage().load(PrivateMessage.class, "receiver", sender).stream().map((c) -> c.getSender()).collect(Collectors.toSet()));
            uuids.addAll(Plugin.getStorage().load(PrivateMessage.class, "sender", sender).stream().map((c) -> c.getReceiver()).collect(Collectors.toSet()));

            for(UUID uuid : uuids) {
                List<PrivateMessage> chatMessages = getPrivateMessages(sender, uuid);
                chatMessages.sort((a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));
                if(chatMessages.size() > 0) {
                    messages.add(chatMessages.get(chatMessages.size() - 1));
                }
            }
        } catch(Exception e) { e.printStackTrace(); }
        return messages;
    }
}
