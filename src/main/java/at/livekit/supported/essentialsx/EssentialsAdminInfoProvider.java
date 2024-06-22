package at.livekit.supported.essentialsx;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;

import at.livekit.api.core.IIdentity;
import at.livekit.api.map.InfoEntry;
import at.livekit.api.map.PlayerInfoProvider;

public class EssentialsAdminInfoProvider extends PlayerInfoProvider{

    private Essentials essentials;

    public EssentialsAdminInfoProvider(Essentials essentials) {
        super(essentials, essentials.getName(), "livekit.essentials.admin");
        this.essentials = essentials;
    }

    @Override
    public List<InfoEntry> onResolvePlayerInfo(IIdentity identity, OfflinePlayer player) {
        List<InfoEntry> entries = new ArrayList<>();

        User user = essentials.getUser(player.getUniqueId());
        String no = ChatColor.RED+"No";
        String yes = ChatColor.GREEN+"Yes";


        entries.add(new InfoEntry("God", user.isGodModeEnabled() ? yes : no));
        entries.add(new InfoEntry("Muted", user.isMuted() ? yes : no));
        entries.add(new InfoEntry("Jailed", user.isJailed() ? yes : no));
        entries.add(new InfoEntry("Vanished", user.isVanished() ? yes : no));

        return entries;
    }

    
}
