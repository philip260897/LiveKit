package at.livekit.supported.essentialsx;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;

import at.livekit.api.core.IIdentity;
import at.livekit.api.core.Privacy;
import at.livekit.api.map.InfoEntry;
import at.livekit.api.map.PlayerInfoProvider;

public class EssentialsPlayerInfoProvider extends PlayerInfoProvider {

    private Essentials essentials;

    public EssentialsPlayerInfoProvider(Essentials essentials) {
        super(essentials, essentials.getName(), "livekit.essentials.info");
        this.essentials = essentials;
    }

    @Override
    public List<InfoEntry> onResolvePlayerInfo(IIdentity identity, OfflinePlayer player) {
        List<InfoEntry> entries = new ArrayList<>();

        User user = essentials.getUser(player.getUniqueId());
        String no = ChatColor.RED+"No";

        if(player.isOnline()) { 
            entries.add(new InfoEntry("AFK", user.isAfk() == false ? no : new SimpleDateFormat("dd MMM. HH:mm").format(new Date(user.getAfkSince())), Privacy.PUBLIC));
            if(user.getAfkMessage() != null) {
                entries.add(new InfoEntry("AFK Message", user.getAfkMessage(), Privacy.PUBLIC));
            }
        }

        return entries;
    }
    
}
