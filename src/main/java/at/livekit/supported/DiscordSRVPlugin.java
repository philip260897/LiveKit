package at.livekit.supported;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.ListenerPriority;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.DiscordGuildMessageReceivedEvent;

import java.util.UUID;
import java.util.concurrent.Callable;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import at.livekit.api.chat.ChatMessage;
import at.livekit.plugin.Plugin;

public class DiscordSRVPlugin {
    
    private boolean listening = false;
    public void onEnable() {
        try{
            if(Bukkit.getPluginManager().getPlugin("DiscordSRV") != null) {
                DiscordSRV.api.subscribe(this);
                listening = true;
            }
        }catch(Exception ex){ex.printStackTrace();}
    }

    public void onDisable() {
        try{
            if(listening) {
                DiscordSRV.api.unsubscribe(this);
            }
        }catch(Exception ex){
            ex.printStackTrace();
        }
    }

    @Subscribe(priority = ListenerPriority.MONITOR)
    public void discordMessageReceived(DiscordGuildMessageReceivedEvent event) 
    {
        final UUID uuid = DiscordSRV.getPlugin().getAccountLinkManager().getUuidFromCache(event.getAuthor().getId());
        Bukkit.getScheduler().callSyncMethod(Plugin.getInstance(), new Callable<Void>() {

            @Override
            public Void call() throws Exception {
                OfflinePlayer player = (uuid != null) ? Bukkit.getOfflinePlayer(uuid) : null;

                ChatMessage message = (player != null) ? new ChatMessage(player, event.getMessage().getContentDisplay()) : new ChatMessage(event.getAuthor().getName(), event.getMessage().getContentDisplay());
                message.setPrefix("Discord");
                Plugin.getInstance().getLiveKit().sendChatMessage(message);
                return null;
            }
        });
    }
}
