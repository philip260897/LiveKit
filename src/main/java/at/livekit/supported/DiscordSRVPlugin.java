package at.livekit.supported;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.ListenerPriority;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.DiscordGuildMessageReceivedEvent;

import org.bukkit.Bukkit;

import at.livekit.plugin.Plugin;

public class DiscordSRVPlugin {
    
    private DiscordSRVEvent event;
    public void onEnable(DiscordSRVEvent event) {
        try{
            if(Bukkit.getPluginManager().getPlugin("DiscordSRV") != null) {
                DiscordSRV.api.subscribe(this);
                this.event = event;
            }
        }catch(Exception ex){ex.printStackTrace();}
    }

    public void onDisable() {
        try{
            if(event != null) {
                DiscordSRV.api.unsubscribe(this);
            }
        }catch(Exception ex){
            ex.printStackTrace();
        }
    }

    @Subscribe(priority = ListenerPriority.MONITOR)
    public void discordMessageReceived(DiscordGuildMessageReceivedEvent event) {
        long start = System.currentTimeMillis();
        Plugin.debug("Triggering discord event "+event.getMember().getId()+" "+DiscordSRV.getPlugin().getAccountLinkManager().getUuidFromCache(event.getAuthor().getId())+" "+(System.currentTimeMillis()-start));

        String uuid = DiscordSRV.getPlugin().getAccountLinkManager().getUuidFromCache(event.getAuthor().getId()).toString();

        if(this.event != null) this.event.onDiscordChat(uuid, event.getAuthor().getName(), event.getMessage().getContentDisplay());
    }

    public interface DiscordSRVEvent {
        public void onDiscordChat(String id, String name, String message);
    }
}
