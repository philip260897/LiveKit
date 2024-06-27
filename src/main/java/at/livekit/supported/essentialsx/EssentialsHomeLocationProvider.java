package at.livekit.supported.essentialsx;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;

import at.livekit.api.core.Color;
import at.livekit.api.core.IIdentity;
import at.livekit.api.core.LKLocation;
import at.livekit.api.core.Privacy;
import at.livekit.api.map.PersonalPin;
import at.livekit.api.map.PlayerLocationProvider;
import at.livekit.plugin.Config;
import at.livekit.plugin.Plugin;
import net.essentialsx.api.v2.events.HomeModifyEvent;

public class EssentialsHomeLocationProvider extends PlayerLocationProvider implements Listener {

    private Essentials essentials;

    public EssentialsHomeLocationProvider(Essentials essentials) {
        super(essentials, essentials.getName(), "livekit.essentials.homes");
        this.essentials = essentials;
    }

    @Override
    public List<PersonalPin> onResolvePlayerLocation(IIdentity identity, OfflinePlayer player) {
        List<PersonalPin> pins = new ArrayList<>();

        User user = essentials.getUser(player.getUniqueId());

        List<String> homes = user.getHomes();
        for(String home : homes) {
            pins.add(new PersonalPin(player, LKLocation.fromLocation(user.getHome(home)), home,  essentials.getName()+" home location", Color.fromChatColor(ChatColor.RED), Config.canEssentialsTeleportHomes(), Privacy.PRIVATE, UUID.nameUUIDFromBytes(home.getBytes())));
        }

        return pins;
    }

    @EventHandler
    public void onHomeModify(HomeModifyEvent event) {
        Plugin.getInstance().getLiveKit().notifyPlayerInfoChange(event.getUser().getBase());
    }
    
}