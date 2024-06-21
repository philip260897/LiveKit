package at.livekit.supported.essentialsx;

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

public class EssentialsHomeLocationProvider extends PlayerInfoProvider implements Listener {

    private Essentials essentials;

    public EssentialsHomeLocationProvider(Essentials essentials) {
        super(essentials, essentials.getName(), "livekit.essentials.homes");
        this.essentials = essentials;
    }

    @Override
    public void onResolvePlayerInfo(OfflinePlayer player, List<InfoEntry> entries) {}

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