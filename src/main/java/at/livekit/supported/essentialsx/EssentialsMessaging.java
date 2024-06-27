package at.livekit.supported.essentialsx;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;

import at.livekit.api.pm.MessageGroup;
import at.livekit.api.pm.MessagingAdapter;
import at.livekit.api.pm.PrivateMessage;
import net.ess3.api.events.PrivateMessageSentEvent;

public class EssentialsMessaging extends MessagingAdapter implements Listener {
    
    final private Essentials essentials;
    final List<PrivateMessage> blacklist = new ArrayList<PrivateMessage>();

    public EssentialsMessaging(Essentials essentials) {
        this.essentials = essentials;
    }

    @EventHandler
    public void onPrivateMessageSent(PrivateMessageSentEvent event) {
        PrivateMessage message;
        if((message = findBlacklistedMessage(event.getSender().getUUID(), event.getRecipient().getUUID(), event.getMessage())) == null) {
            sendPrivateMessage(event.getSender().getUUID(), event.getRecipient().getUUID(), event.getMessage());
        } else {
            blacklist.remove(message);
        }
    }

    @Override
    public void onGroupMessageReceived(OfflinePlayer sender, MessageGroup receiver, String message) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'onGroupMessageReceived'");
    }

    @Override
    public void onPrivateMessageReceived(OfflinePlayer sender, OfflinePlayer receiver, String message) {
        User senderUser = essentials.getUser(sender.getUniqueId());
        User receiverUser = essentials.getUser(receiver.getUniqueId());

        if(senderUser == null || receiverUser == null) {
            return;
        }

        blacklist.add(PrivateMessage.create(sender.getUniqueId(), receiver.getUniqueId(), message));
        senderUser.sendMessage(receiverUser, message);
    }

    PrivateMessage findBlacklistedMessage(UUID sender, UUID receiver, String message) {
        for(PrivateMessage pm : blacklist) {
            if(pm.getSender().equals(sender) && pm.getReceiver().equals(receiver) && pm.getMessage().equals(message)) {
                return pm;
            }
        }
        return null;
    }
}
