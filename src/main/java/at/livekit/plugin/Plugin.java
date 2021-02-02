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
import at.livekit.modules.LiveMapModule.*;
import at.livekit.modules.LiveMapModule;
import at.livekit.modules.PlayerModule.LPlayer;
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
			public void onHeadResolved(String name, String base64) {
				PlayerModule module = (PlayerModule)LiveKit.getInstance().getModuleManager().getModule("PlayerModule");
				LPlayer player = module.getPlayerByName(name);
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
			handleUserCommands(sender, command, label, args);
			handleMapCommands(sender, command, label, args);
			handleAdminCommands(sender, command, label, args);

			//if(args.length == 1) {
				/*if(args[0].equalsIgnoreCase("tp")) {
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
				}*/
				/*if(args[0].equalsIgnoreCase("loptions")) {
					LiveMapModule module = ((LiveMapModule) LiveKit.getInstance().getModuleManager().getModule("LiveMapModule"));
					RenderingOptions options = module.getOptions();
					sender.sendMessage(prefix+"CPU-Time: "+options.getCpuTime()+"ms");
					sender.sendMessage(prefix+"Mode: "+options.getMode().name());
					sender.sendMessage(prefix+"Chunk Queue: "+module.getChunkQueueSize());
					sender.sendMessage(prefix+"Region Queue: "+module.getRegionQueueSize());
					if(options.getMode() == RenderingMode.BOUNDS) {
						 sender.sendMessage(prefix+"Bounds: "+options.getLimits().toString());
					}
				}*/
				/*if(args[0].equalsIgnoreCase("fullrender")) {
					LiveMapModule module = ((LiveMapModule) LiveKit.getInstance().getModuleManager().getModule("LiveMapModule"));
					try{
						module.fullRender();
					}catch(Exception ex){ex.printStackTrace();}
				}*/
			//}

		}
		return true;
	}

	private boolean handleUserCommands(CommandSender sender, Command command, String label, String[] args) {
		if(args.length == 1) {
			if(args[0].equalsIgnoreCase("claim")) {
				if(sender instanceof Player) 
				{
					Player player = (Player) sender;
					if(!checkPerm(player, "livekit.commands.basic")) return true;

					player.sendMessage(prefix+"Pin: "+PlayerAuth.get(player.getUniqueId().toString()).generateClaimPin()+" (valid for 2 mins)");
				}
				else 
				{
					sender.sendMessage(prefixError+"Only players can be claimed!");
				}
				return true;
			}
			if(args[0].equalsIgnoreCase("info")) 
			{
				if(sender instanceof Player) 
				{
					Player player = (Player) sender;
					if(!checkPerm(player, "livekit.commands.basic")) return true;

					List<Identity> identities = LiveKit.getInstance().getConnectedClients(player.getUniqueId().toString());
					player.sendMessage(prefix+"Info for "+player.getName());
					
					PlayerAuth auth = PlayerAuth.get(player.getUniqueId().toString());
					if(auth != null) {
						player.sendMessage("Active Session Tokens: "+auth.getSessionKeys().length+" [/livekit clearsessions to clear]");
					}
					
					if(identities != null && identities.size() > 0) {
						player.sendMessage("Connected clients: "+identities.size());
						Identity identity = identities.get(0);
						player.sendMessage("Permissions: ");
						for(String perm : identity.getPermissions()) {
							player.sendMessage(perm);
						}
					} else {
						player.sendMessage("No LiveKit client is connected");
					}
				}
				else
				{
					sender.sendMessage(prefixError+"Only players can have an identity!");
				}
				return true;
			}
			if(args[0].equalsIgnoreCase("clearsessions")) {
				if(sender instanceof Player) 
				{
					Player player = (Player) sender;
					if(!checkPerm(player, "livekit.commands.basic")) return true;

					PlayerAuth auth = PlayerAuth.get(player.getUniqueId().toString());
					if(auth != null) {
						auth.clearSessionKeys();
						player.sendMessage(prefix+"Active Session Tokens: "+auth.getSessionKeys().length);
					}
				}
				else
				{
					sender.sendMessage(prefixError+"Only players can clear their sessions!");
				}
			}
		}
		return false;
	}

	private boolean handleMapCommands(CommandSender sender, Command command, String label, String[] args) {
		if(args.length >= 1) {
			if(label.equalsIgnoreCase("livekit") && args[0].equalsIgnoreCase("map")) {
				if(!checkPerm(sender, "livekit.commands.admin")) return true;

				LiveMapModule map = (LiveMapModule)LiveKit.getInstance().getModuleManager().getModule("LiveMapModule");
				if(!map.isEnabled()) { sender.sendMessage(prefixError+" LiveMapModule not enabled."); return true;}

				RenderingOptions options = map.getOptions();

				if(args.length == 1) {
					StringBuilder builder = new StringBuilder();
					
					builder.append("LiveMap Status:\n");
					builder.append("world: "+map.getWorld()+"\n");
					builder.append("mode: "+options.getMode().name()+"\n");
					builder.append("limits: "+options.getLimits().toString()+"\n");
					builder.append("cpu-time: "+options.getCpuTime()+"ms / "+(int)(((float)options.getCpuTime()/50f)*100f)+"%"+"\n");
					builder.append("queued chunks: "+map.getChunkQueueSize()+"\n");
					builder.append("queued regions: "+map.getRegionQueueSize()+"\n");
					
					sender.sendMessage(prefix+builder.toString());
				}
				if(args.length == 2) {
					if(args[1].equalsIgnoreCase("fullrender")) {
						if(map.getRegionQueueSize() != 0) {
							sender.sendMessage(prefixError+"Fullrender can only be startet if Region Queue is empty!");
							return true;
						}

						try{
							map.fullRender();
							sender.sendMessage(prefix+"Fullrender started");
						}catch(Exception ex){sender.sendMessage(prefixError+ex.getMessage()); return true;}
					}
					if(args[1].equalsIgnoreCase("abortrender")) {
						map.clearChunkQueue();
						map.clearRegionQueue();
						sender.sendMessage(prefix+"Rendering queues cleared!");
					}
					if(args[1].equalsIgnoreCase("bounds")) {
						BoundingBox world = BoundingBox.fromWorld(Bukkit.getWorld(map.getWorld()).getName());
						sender.sendMessage(prefix+"Current: "+options.getLimits().toString());
						sender.sendMessage(prefix+"World bounds: "+world.toString());
					}
				}
				if(args.length == 3) {
					if(args[1].equalsIgnoreCase("mode")) {
						RenderingMode mode = null;
						if(args[2].equalsIgnoreCase("forced")) mode = RenderingMode.FORCED;
						if(args[2].equalsIgnoreCase("discover")) mode = RenderingMode.DISCOVER;
						if(mode == null) {
							sender.sendMessage(prefixError+"Mode "+args[2]+" is not a valid rendering mode! -> [FORCED | DISCOVER]");
							return true;
						}

						map.setRenderingMode(mode);
						sender.sendMessage(prefix+"Mode "+mode.name()+" set!"+(mode == RenderingMode.FORCED ? " Don't forget to check bounds and start full render!" : ""));
					}
					if(args[1].equalsIgnoreCase("cpu")) {
						float percent = 20;
						try{
						percent = Float.parseFloat(args[2]);
						}catch(Exception ex){sender.sendMessage(prefixError+args[2]+" is not a valid number! 0-100%"); return true;}

						if(percent < 5) percent = 5;
						if(percent > 100) percent = 100;

						map.setCPUTime( (int)(percent*50f/100f));
						sender.sendMessage(prefix+"CPU-Time set to "+options.getCpuTime()+"ms / "+(int)(((float)options.getCpuTime()/50f)*100f)+"%");

						if(percent >= 80) {
							sender.sendMessage(prefix+"WARNING: Setting cpu time above 80% might cause severe lag!");
						}
					}
					if(args[1].equalsIgnoreCase("bounds")) {
						if(args[2].equalsIgnoreCase("update")) {
							BoundingBox world = BoundingBox.fromWorld(Bukkit.getWorld(map.getWorld()).getName());
							if(world != null) {
								options.setLimits(world);
							}
							sender.sendMessage(prefix+"Updated map bounds to "+world.toString());
						}
					}
				}
			}
		}
		return true;
	}

	private boolean handleAdminCommands(CommandSender sender, Command command, String label, String[] args) {
		

		if(args.length == 1) {
			if(args[0].equalsIgnoreCase("permreload")) {
				if(!checkPerm(sender, "livekit.commands.admin")) return true;

				LiveKit.getInstance().commandReloadPermissions();
				sender.sendMessage(prefix+"Permissions will reload");
			}

			if(args[0].equalsIgnoreCase("modules")) {
				if(!checkPerm(sender, "livekit.commands.admin")) return true;

				sender.sendMessage(prefix+"Modules:");
				for(BaseModule module : LiveKit.getInstance().getModules()) {
					sender.sendMessage(module.getType()+" Version: "+module.getVersion()+" Enabled: "+module.isEnabled());
				}
			}
			return true;
		}
		/*if(args.length == 3) {
			if(args[0].equalsIgnoreCase("modules")) {
				if(!checkPerm(sender, "livekit.commands.admin")) return true;

				String module = args[1];
				String action = args[2];

				BaseModule m = LiveKit.getInstance().getModuleManager().getModule(module);
				if(m != null) {
					if(action.equals("enable")) {
						LiveKit.getInstance().enableModule(m.getType());
					} else {
						LiveKit.getInstance().disableModule(m.getType());
					}
					LiveKit.getInstance().notifyQueue("SettingsModule");
				}
			}
		}*/
		return true;
	}

	private boolean checkPerm(CommandSender sender, String permission) {
		if(sender.isOp()) return true;

		if(sender instanceof Player) {
			Player player = (Player)sender;
			boolean access = Permissions.has(player, permission);
			if(!access) player.sendMessage(prefixError+"You need "+permission+" permission to access this command!");
			return access;
		}
		return false;
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
		//logger.warning(message);
	}
}
