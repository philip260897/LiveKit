package at.livekit.supported.essentialsx;

import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;

import at.livekit.api.map.InfoEntry;
import at.livekit.api.map.PersonalPin;
import at.livekit.api.map.PlayerInfoProvider;

public class EssentialsAdminInfoProvider extends PlayerInfoProvider{

    private Essentials essentials;

    public EssentialsAdminInfoProvider(Essentials essentials) {
        super(essentials, essentials.getName(), "livekit.essentials.admin");
        this.essentials = essentials;
    }

    @Override
    public void onResolvePlayerInfo(OfflinePlayer player, List<InfoEntry> entries) {
        User user = essentials.getUser(player.getUniqueId());
        String no = ChatColor.RED+"No";
        String yes = ChatColor.GREEN+"Yes";


        entries.add(new InfoEntry("God", user.isGodModeEnabled() ? yes : no));
        entries.add(new InfoEntry("Muted", user.isMuted() ? yes : no));
        entries.add(new InfoEntry("Jailed", user.isJailed() ? yes : no));
        entries.add(new InfoEntry("Vanished", user.isVanished() ? yes : no));
    }

    @Override
    public void onResolvePlayerLocation(OfflinePlayer arg0, List<PersonalPin> arg1) {}
    
}
