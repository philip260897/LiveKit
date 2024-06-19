package at.livekit.supported.essentialsx;


import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;

import at.livekit.api.core.Color;
import at.livekit.api.core.LKLocation;
import at.livekit.api.core.Privacy;
import at.livekit.api.map.InfoEntry;
import at.livekit.api.map.PersonalPin;
import at.livekit.api.map.PlayerInfoProvider;
import at.livekit.plugin.Config;
import at.livekit.plugin.Plugin;
import net.essentialsx.api.v2.events.HomeModifyEvent;

public class EssentialsPlayerInfoProvider extends PlayerInfoProvider implements Listener {

    private Essentials essentials;

    public EssentialsPlayerInfoProvider(Essentials essentials) {
        super(essentials, essentials.getName(), "livekit.essentials");
        this.essentials = essentials;
    }

    @Override
    public void onResolvePlayerInfo(OfflinePlayer player, List<InfoEntry> entries) {
        User user = essentials.getUser(player.getUniqueId());
        String no = ChatColor.RED+"No";
        //String yes = ChatColor.GREEN+"Yes";

        if(player.isOnline()) { 
            entries.add(new InfoEntry("AFK", user.isAfk() == false ? no : new SimpleDateFormat("dd MMM. HH:mm").format(new Date(user.getAfkSince()))));
            if(user.getAfkMessage() != null) {
                entries.add(new InfoEntry("AFK Message", user.getAfkMessage()));
            }
        }

        
    }

    @Override
    public void onResolvePlayerLocation(OfflinePlayer player, List<PersonalPin> pins) {
        if(Config.canEssentialsPinHomes() == false) return;

        User user = essentials.getUser(player.getUniqueId());

        List<String> homes = user.getHomes();
        for(String home : homes) {
            pins.add(new PersonalPin(player, LKLocation.fromLocation(user.getHome(home)), home,  essentials.getName()+" home location", Color.fromChatColor(ChatColor.RED), Config.canEssentialsTeleportHomes(), Privacy.PRIVATE, UUID.nameUUIDFromBytes(home.getBytes())));
        }
    }

    @EventHandler
    public void onHomeModify(HomeModifyEvent event) {
        Plugin.getInstance().getLiveKit().notifyPlayerInfoChange(event.getUser().getBase());
    }
    
}
