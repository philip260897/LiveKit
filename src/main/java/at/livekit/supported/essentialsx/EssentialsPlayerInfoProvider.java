package at.livekit.supported.essentialsx;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;

import at.livekit.api.map.InfoEntry;
import at.livekit.api.map.PersonalPin;
import at.livekit.api.map.PlayerInfoProvider;

public class EssentialsPlayerInfoProvider extends PlayerInfoProvider {

    private Essentials essentials;

    public EssentialsPlayerInfoProvider(Essentials essentials) {
        super(essentials, essentials.getName(), "livekit.essentials.info");
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

    }
    
}
