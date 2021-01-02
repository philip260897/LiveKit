package at.livekit.plugin;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.JSONObject;

import at.livekit.livekit.Identity;
import at.livekit.livekit.LiveKit;
import at.livekit.livekit.PlayerAuth;
import at.livekit.modules.BaseModule;
import at.livekit.modules.PlayerModule;
import at.livekit.modules.PlayerModule.LPlayer;
import at.livekit.modules.PlayerModule.PlayerAsset;
import at.livekit.modules.PlayerModule.ValueAsset;
import at.livekit.utils.HeadLibrary;
import at.livekit.utils.HeadLibraryEvent;
import net.md_5.bungee.api.ChatColor;
import net.milkbowl.vault.permission.Permission;

public class Plugin extends JavaPlugin implements CommandExecutor {
	//private static int pluginPort = 8123;
	protected static Logger logger;

	public static Plugin instance;
	public static String workingDirectory;

	public static Permission perms;
	public static List<String> permissions = new ArrayList<String>();

	@Override
	public void onEnable() {
		instance = this;
		logger = getLogger();

		if(!setupPermissions()) {
			logger.severe("[LiveKit] Vault not found! Disabling!");
			getServer().getPluginManager().disablePlugin(this);
			return;
		}

		permissions.add("livekit.basics.claim");
		permissions.add("livekit.basics.map");
		permissions.add("livekit.basics.players");
		permissions.add("livekit.basics.weathertime");
		//permissions.add("livekit.players.other");
		permissions.add("livekit.admin.settings");

		File folder = new File(System.getProperty("user.dir") + "/plugins/LiveKit/");
		if (!folder.exists()) {
			logger.info("Creating LiveKit directory");
			folder.mkdir();
		}
		workingDirectory = folder.getAbsolutePath();

		logger.info("Materials: " + Material.values().length);
		logger.info("Biomes: " + Biome.values().length);

		try{
			LiveKit.getInstance().onEnable();
		}catch(Exception ex){ex.printStackTrace();}



		HeadLibrary.setHeadLibraryListener(new HeadLibraryEvent(){
			@Override
			public void onHeadResolved(String uuid, String base64) {
				PlayerModule module = (PlayerModule)LiveKit.getInstance().getModuleManager().getModule("PlayerModule");
				LPlayer player = module.getPlayer(uuid);
				if(player != null) {
					player.updateHead(base64);
				}
			}
		});
    }
    
    @Override
    public void onDisable() {
		LiveKit.getInstance().onDisable();
			
		try{
			HeadLibrary.dispose();
		}catch(Exception ex){ex.printStackTrace();}
	}

	private boolean setupPermissions() {
        RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
        perms = rsp.getProvider();
        return perms != null;
    }

	static ChatColor pluginColor = ChatColor.BLUE;
	static String chatPrefix = pluginColor+"[livekit] "+ChatColor.WHITE;

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
		if(label.equalsIgnoreCase("livekit")) {
			if(args.length == 1) {
				if(args[0].equalsIgnoreCase("tp")) {
					JSONObject object = new JSONObject();
					
					for(int i = 0; i < Material.values().length; i++) {
						object.put(i+":"+Material.values()[i].name(), "#00000000");
					}
					try{
						File file = new File( System.getProperty("user.dir") + "/plugins/LiveKit/textures.json" );
						if(!file.exists()) file.createNewFile();

						PrintWriter writer = new PrintWriter(file);
						writer.write(object.toString());
						writer.flush();
						writer.close();
					}catch(Exception ex){ex.printStackTrace();}
				}
				if(args[0].equalsIgnoreCase("claim")) {
					if(sender instanceof Player) {
						Player player = (Player) sender;
						if(perms.playerHas(player, "livekit.basics.weathertime")) {
							player.sendMessage(PlayerAuth.get(player.getUniqueId().toString()).generateClaimPin());
						} else {
							player.sendMessage("Permission denied!");
						}
					}
				}
				if(args[0].equalsIgnoreCase("permreload")) {
					if(sender instanceof Player) {
						Player player = (Player) sender;
						if(perms.playerHas(player, "livekit.admin.permreload")) {
							
							LiveKit.getInstance().reloadPermissions();
							player.sendMessage("Permissions reloaded");

						} else {
							player.sendMessage("Permission denied!");
						}
					}
				}
				if(args[0].equalsIgnoreCase("identity")) {
					if(sender instanceof Player) {
						Player player = (Player) sender;
						if(perms.playerHas(player, "livekit.basics.identity")) {
							Identity identity = LiveKit.getInstance().getIdentity(player.getUniqueId().toString());
							player.sendMessage("Name: "+identity.getName());
							player.sendMessage("Permissions: ");
							for(String perm : identity.getPermissions()) {
								player.sendMessage(perm);
							}

						} else {
							player.sendMessage("Permission denied!");
						}
					}
				}
				if(args[0].equalsIgnoreCase("modules")) {
					for(BaseModule module : LiveKit.getInstance().getModules()) {
						sender.sendMessage("["+module.getType()+"] Version: "+module.getVersion()+" Enabled: "+module.isEnabled());
					}
				}
				if(args[0].equalsIgnoreCase("test")) {
					if(sender instanceof Player) {
						LPlayer player = ((PlayerModule)LiveKit.getInstance().getModuleManager().getModule("PlayerModule")).getPlayer(((Player)sender).getUniqueId().toString());
						PlayerAsset asset = new ValueAsset("test", "Test", "test");
						player.getAssetGroup("Locations").addPlayerAsset(asset);
						LiveKit.getInstance().notifyQueue("PlayerModule");
					}
				}
				if(args[0].equalsIgnoreCase("test2")) {
					if(sender instanceof Player) {
						LPlayer player = ((PlayerModule)LiveKit.getInstance().getModuleManager().getModule("PlayerModule")).getPlayer(((Player)sender).getUniqueId().toString());
						player.getAssetGroup("Locations").removePlayerAsset(player.getAsset("test"));
						LiveKit.getInstance().notifyQueue("PlayerModule");
					}
				}
			}
			if(args.length == 3) {
				if(args[0].equalsIgnoreCase("modules")) {
					String module = args[1];
					String action = args[2];

					BaseModule m = LiveKit.getInstance().getModuleManager().getModule(module);
					if(m != null) {
						if(action.equals("enable")) {
							LiveKit.getInstance().getModuleManager().enableModule(m.getType());
						} else {
							LiveKit.getInstance().getModuleManager().disableModule(m.getType());
						}
						LiveKit.getInstance().notifyQueue("SettingsModule");
					}
				}
			}
		}
		return true;
	}
}
