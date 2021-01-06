package at.livekit.plugin;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
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

public class Plugin extends JavaPlugin implements CommandExecutor {

	private static Logger logger;
	private static Plugin instance;

	private static String name;
	private static ChatColor color = ChatColor.GREEN;
	private static String prefix;
	private static String prefixError;

	@Override
	public void onEnable() {
		instance = this;
		logger = getLogger();

		//create config folder if doesn't exist
		if(!getDataFolder().exists()) {
			getDataFolder().mkdirs();
		}

		name = this.getDescription().getName();
		prefix = color+"["+ChatColor.WHITE+name+color+"] "+ChatColor.WHITE;
		prefixError = ChatColor.RED+"["+ChatColor.WHITE+name+ChatColor.RED+"] "+ChatColor.WHITE;

		//initialize config
		Config.initialize();
		//initialize permission -> disable plugin if permission initialization failed
		if(!Permissions.initialize()) {
			getServer().getPluginManager().disablePlugin(this);
			return;
		}

		if(Bukkit.getWorld(Config.getModuleString("LiveMapModule", "world")) == null) {
			Plugin.severe(Config.getModuleString("LiveMapModule", "world")+" does not exist! Shutting down");
			getServer().getPluginManager().disablePlugin(this);
			return;
		}

		//logger.info("Materials: " + Material.values().length);
		//logger.info("Biomes: " + Biome.values().length);

		HeadLibrary.load();
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

		try{
			LiveKit.getInstance().onEnable();
		}catch(Exception ex){ex.printStackTrace();}
    }
    
    @Override
    public void onDisable() {
		LiveKit.getInstance().onDisable();
			
		try{
			HeadLibrary.dispose();
		}catch(Exception ex){ex.printStackTrace();}
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
						if(!checkPerm(player, "livekit.basics.claim")) return true;

						player.sendMessage(prefix+"Pin: "+PlayerAuth.get(player.getUniqueId().toString()).generateClaimPin()+" (valid for 2 mins)");
					}
					else 
					{
						sender.sendMessage(prefixError+"Only players can be claimed!");
					}
				}
				if(args[0].equalsIgnoreCase("permreload")) {
					if(sender instanceof Player) {
						Player player = (Player) sender;
						if(!checkPerm(player, "livekit.admin.permreload")) return true;
					}

					if(sender instanceof Player || sender.isOp()) {
						LiveKit.getInstance().commandReloadPermissions();
						sender.sendMessage(prefix+"Permissions will reload");
					}
				}
				if(args[0].equalsIgnoreCase("identity")) {
					if(sender instanceof Player) {
						Player player = (Player) sender;
						if(!checkPerm(player, "livekit.basics.claim")) return true;

						Identity identity = LiveKit.getInstance().getIdentity(player.getUniqueId().toString());
						player.sendMessage(prefix+"Identity");
						if(identity != null) {
							player.sendMessage("Name: "+identity.getName());
							player.sendMessage("Permissions: ");
							for(String perm : identity.getPermissions()) {
								player.sendMessage(perm);
							}
						} else {
							player.sendMessage("No clients connected");
						}
						PlayerAuth auth = PlayerAuth.get(player.getUniqueId().toString());
						if(auth != null) {
							player.sendMessage("Active Sessions: "+auth.getSessionKeys().length);
						}
					}
					else
					{
						sender.sendMessage(prefixError+"Only players can have an identity!");
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

	private boolean checkPerm(Player player, String permission) {
		boolean access = Permissions.has(player, permission);
		if(!access) player.sendMessage(prefixError+"You need "+permission+" permission to access this command!");
		return access;
	}

	public static Plugin getInstance() {
		return instance;
	}

	public static void log(String message) {
		logger.info(message);
	}

	public static void severe(String message) {
		logger.severe(message);
	}

	public static void debug(String message) {
		logger.warning(message);
	}
}
