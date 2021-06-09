package at.livekit.plugin;

import java.io.FilterOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.google.common.util.concurrent.Futures;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import at.livekit.api.core.Color;
import at.livekit.api.core.ILiveKit;
import at.livekit.api.core.ILiveKitPlugin;
import at.livekit.api.core.LKLocation;
import at.livekit.api.core.Privacy;
import at.livekit.api.map.InfoEntry;
import at.livekit.api.map.POI;
import at.livekit.api.map.Waypoint;
import at.livekit.authentication.AuthenticationHandler;
import at.livekit.authentication.Pin;
import at.livekit.authentication.Session;
import at.livekit.livekit.Identity;
import at.livekit.livekit.LiveKit;
import at.livekit.map.RenderBounds;
import at.livekit.map.RenderJob;
import at.livekit.map.RenderScheduler;
import at.livekit.map.RenderWorld;
import at.livekit.map.RenderBounds.RenderShape;
import at.livekit.map.RenderJob.RenderJobMode;
import at.livekit.modules.BaseModule;
import at.livekit.modules.ConsoleModule;
import at.livekit.modules.PlayerModule;
import at.livekit.modules.LiveMapModule;
import at.livekit.modules.PlayerModule.LPlayer;
import at.livekit.provider.BasicPOIProvider;
import at.livekit.provider.BasicPlayerInfoProvider;
import at.livekit.provider.BasicPlayerPinProvider;
import at.livekit.provider.POISpawnProvider;
import at.livekit.storage.IStorageAdapter;
import at.livekit.storage.JSONStorage;
import at.livekit.storage.SQLStorage;
import at.livekit.utils.ConsoleListener;
import at.livekit.utils.FutureSyncCallback;
import at.livekit.utils.HeadLibraryEvent;
import at.livekit.utils.HeadLibraryV2;
import at.livekit.utils.Metrics;
import at.livekit.utils.Utils;

public class Plugin extends JavaPlugin implements CommandExecutor, ILiveKitPlugin {

	private static Logger logger;
	private static Plugin instance;

	private static String name;
	private static ChatColor color = ChatColor.GREEN;
	private static String prefix;
	private static String prefixError;

	private static IStorageAdapter storage;

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
			logger.severe("Error initializing Permissions, shutting down");
			getServer().getPluginManager().disablePlugin(this);
			return;
		}

		try{
			try{
				System.out.println("jdbc:sqlite:"+getDataFolder().getPath()+"/sample.db");
				storage = new SQLStorage("jdbc:sqlite:"+getDataFolder().getPath()+"/sample.db");
			}catch(Exception exception){exception.printStackTrace();}	

			//storage = new JSONStorage();
			storage.initialize();
		} catch(Exception exception) {
			exception.printStackTrace();
			logger.severe("Error initializing Storage, shutting down");
			getServer().getPluginManager().disablePlugin(this);
			return;
		}

		//System.out.println("System out printlning");

		/*List<String> worlds = Config.getLiveMapWorlds();
		for(String world : worlds) {
			if(Bukkit.getWorld(world) == null) {
				Plugin.severe(world + " does not exist! Shutting down");
				getServer().getPluginManager().disablePlugin(this);
				return;
			}
		}*/

		//logger.info("Materials: " + Material.values().length);
		//logger.info("Biomes: " + Biome.values().length);

		HeadLibraryV2.onEnable();
		HeadLibraryV2.setHeadLibraryListener(new HeadLibraryEvent(){
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

		try{
			Metrics metrics = new Metrics(this, 10516);
		}catch(Exception ex){Plugin.debug("bStats could not be initialized! "+ex.getMessage());}

		//this.getLiveKit().addLocationProvider(new LocationBedSpawnProvider());
		BasicPlayerInfoProvider playerInfoProvider = new BasicPlayerInfoProvider();
		this.getLiveKit().addPlayerInfoProvider(playerInfoProvider);
		Bukkit.getServer().getPluginManager().registerEvents(playerInfoProvider, Plugin.getInstance());

		//POI
		POISpawnProvider provider = new POISpawnProvider();
		this.getLiveKit().addPOIInfoProvider(provider);
		Bukkit.getServer().getPluginManager().registerEvents(provider, Plugin.getInstance());

		//POI
		//POI center = new POI(new Location(Bukkit.getWorld("world"), 0, 65, 0), "Origin", "The origin of world", Color.fromChatColor(ChatColor.DARK_PURPLE), false);
        //Plugin.getInstance().getLiveKit().addPointOfInterest(center);

		//Player Pin Provider
		//PlayerPinProvider playerPins = new PlayerPinProvider();
		this.getLiveKit().addPlayerInfoProvider(new BasicPlayerPinProvider());

		//registers console listener (console enable check is done inside)
		ConsoleListener.registerListener();
    }
    
    @Override
    public void onDisable() {
		//unregister before livekit gets disabled
		ConsoleListener.unregisterListener();

		LiveKit.getInstance().onDisable();

		storage.dispose();
			
		try{
			HeadLibraryV2.onDisable();
		}catch(Exception ex){ex.printStackTrace();}
	}


	static ChatColor pluginColor = ChatColor.BLUE;
	static String chatPrefix = pluginColor+"[livekit] "+ChatColor.WHITE;

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
		if(label.equalsIgnoreCase("livekit")) {
			boolean handled = false;
			if(!handled) handled = handleUserCommands(sender, command, label, args);
			if(!handled) handled = handleMapCommands(sender, command, label, args);
			if(!handled) handled = handleAdminCommands(sender, command, label, args);
			if(!handled) handled = handlePlayerPinCommands(sender, command, label, args);


			if(args.length == 1) {
				/*if(args[0].equalsIgnoreCase("test")) {
					logger.warning("ACHTUNG WARNUNG "+ChatColor.AQUA+"lele");
					Object o = null;
					System.out.println("Command triggered with args");
					o.toString();
				}*/
				/*if(args[0].equalsIgnoreCase("reload")) {
					if(!checkPerm(sender, "livekit.commands.admin")) return true;

					getServer().getPluginManager().disablePlugin(this);
					getServer().getPluginManager().enablePlugin(this);
					sender.sendMessage(prefix+"reload complete!");

					handled = true;
				}*/
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

					JSONArray array = new JSONArray();
					for(int i = 0; i < EntityType.values().length; i++) {
						array.put(EntityType.values()[i].name());
					}

					try{
						File file = new File( System.getProperty("user.dir") + "/plugins/LiveKit/entities.json" );
						if(!file.exists()) file.createNewFile();

						PrintWriter writer = new PrintWriter(file);
						writer.write(array.toString());
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
			}

			if(!handled) {
				sender.sendMessage(prefixError+"Unknown command. Try /livekit help");
			}
		}
		return true;
	}

	private boolean handleUserCommands(CommandSender sender, Command command, String label, String[] args) {
		if(args.length == 0) {
			sender.sendMessage(prefix+"LiveKit is supported!");
			sender.sendMessage("iOS App: "+ChatColor.AQUA+"https://bit.ly/livekitios"+ChatColor.RESET);
			sender.sendMessage("Android App: "+ChatColor.AQUA+"https://bit.ly/livekitandroid"+ChatColor.RESET);
			sender.sendMessage("Port: "+Config.getServerPort());
			sender.sendMessage("Password needed: "+friendlyBool((Config.getPassword()!=null)));
			sender.sendMessage("Supports anonymous: "+friendlyBool((Config.allowAnonymous())));
			sender.sendMessage("Has access: "+friendlyBool(checkPerm(sender, "livekit.commands.basic", false) && (sender instanceof Player)));
			if(checkPerm(sender, "livekit.commands.basic", false) && (sender instanceof Player)) sender.sendMessage("Use "+ChatColor.GREEN+"/livekit claim"+ChatColor.RESET+" to generate an access pin");
			sender.sendMessage("Use "+ChatColor.GREEN+"/livekit help"+ChatColor.RESET+" for more info");
			return true;
		}
		if(args.length == 1) {
			if(args[0].equalsIgnoreCase("headrefresh")) {
				if(sender instanceof Player) {
					Player player = (Player)sender;
					if(!checkPerm(sender, "livekit.commands.basic")) return true;

					Long timestamp = HeadLibraryV2.refreshCooldown.get(player.getUniqueId());
					if(timestamp == null || (System.currentTimeMillis() > timestamp+5*60*1000) || checkPerm(sender, "livekit.commands.admin", false)) {
						HeadLibraryV2.get(player.getName(), true, true);
						HeadLibraryV2.refreshCooldown.put(player.getUniqueId(), System.currentTimeMillis());
						sender.sendMessage(prefix+"Your head is going to be refreshed!");
					} else {
						sender.sendMessage(prefixError+"You can refresh your head every 5 minutes!");
					}
				} else {
					sender.sendMessage(prefixError+"Only players can refresh their head!");
				}
				return true;
			}
			if(args[0].equalsIgnoreCase("claim")) {
				if(sender instanceof Player) 
				{
					Player player = (Player) sender;
					if(!checkPerm(player, "livekit.commands.basic")) return true;

					AuthenticationHandler.generatePin(player, new FutureSyncCallback<Pin>(){
						@Override
						public void onSyncResult(Pin result) {
							player.sendMessage(prefix+"Pin: "+result.getPin()+" (valid for 2 mins)");
						}
					}, Utils.errorHandler(sender));
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
					
					AuthenticationHandler.getSessionList(player, new FutureSyncCallback<List<Session>>(){
						@Override
						public void onSyncResult(List<Session> result) {
							if(result != null) {
								player.sendMessage("Active Session Tokens: "+result.size()+" [/livekit clearsessions to clear]");
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
					}, Utils.errorHandler(sender));
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

					AuthenticationHandler.clearSessionList(player, new FutureSyncCallback<Void>(){
						@Override
						public void onSyncResult(Void result) {
							AuthenticationHandler.getSessionList(player, new FutureSyncCallback<List<Session>>(){
								@Override
								public void onSyncResult(List<Session> result) {
									player.sendMessage(prefix+"Active Session Tokens: "+result.size());
								}
								
							}, Utils.errorHandler(sender));
						}
					}, Utils.errorHandler(sender));
				}
				else
				{
					sender.sendMessage(prefixError+"Only players can clear their sessions!");
				}
				return true;
			}
			if(args[0].equalsIgnoreCase("?") || args[0].equalsIgnoreCase("help")) {
				sender.sendMessage(prefix+"Help Page");
				sender.sendMessage(ChatColor.GREEN+"/livekit "+ChatColor.RESET+" - LiveKit basic info");
				if(checkPerm(sender, "livekit.commands.basic", false)) {
					sender.sendMessage(ChatColor.GREEN+"/livekit claim"+ChatColor.RESET+" - Generate a claim pin, used to identify yourself in the App");
					sender.sendMessage(ChatColor.GREEN+"/livekit info"+ChatColor.RESET+" - Info about App sessions identified as you");
					sender.sendMessage(ChatColor.GREEN+"/livekit clearsessions"+ChatColor.RESET+" - Clear all active sessions. App clients need to re-claim");
					sender.sendMessage(ChatColor.GREEN+"/livekit headrefresh"+ChatColor.RESET+" - Refresh your Head (only neccessary if you changed your skin)");
				} else {
					sender.sendMessage("You don't have the needed permission "+ChatColor.GREEN+"livekit.commands.basic"+ChatColor.RESET+" to access LiveKit");
					if(Config.allowAnonymous())sender.sendMessage("However, anonymous joining is enabled!");
				}
				if(checkPerm(sender, "livekit.poi.personalpins", false)) {
					sender.sendMessage(ChatColor.GREEN+"/livekit setpin <name>"+ChatColor.RESET+" - Set a personal pin at your current location");
					sender.sendMessage(ChatColor.GREEN+"/livekit pins"+ChatColor.RESET+" - List of all your set pins");
					sender.sendMessage(ChatColor.GREEN+"/livekit removepin <id>"+ChatColor.RESET+" - Remove a pin. Obtain the <id> from /livekit pins");
				}
				if(checkPerm(sender, "livekit.commands.admin", false)) {
					sender.sendMessage(prefixError+"Admin Commands:"+ChatColor.RESET);
					//sender.sendMessage(ChatColor.GREEN+"/livekit reload"+ChatColor.RESET+" - Reload LiveKit plugin");
					sender.sendMessage(ChatColor.GREEN+"/livekit map"+ChatColor.RESET+" - Display info about live map");
					sender.sendMessage(ChatColor.GREEN+"/livekit map cpu <time in %>"+ChatColor.RESET+" - Speed up rendering performance at the cost of server lag. Use with care. Default: 40%");
					sender.sendMessage(ChatColor.GREEN+"/livekit <world>"+ChatColor.RESET+" - Show general info and rendering status of <world>");
					sender.sendMessage(ChatColor.GREEN+"/livekit <world> render full [-m|-f]"+ChatColor.RESET+" - Start full render on <world> -m: missing tiles only, -f forces already rendered tiles to render");
					sender.sendMessage(ChatColor.GREEN+"/livekit <world> render <radius>"+ChatColor.RESET+" - Renders a rectangular radius around the players position. (Worlds must match)");
					sender.sendMessage(ChatColor.GREEN+"/livekit <world> render stop"+ChatColor.RESET+" - Stop current rendering job");
					sender.sendMessage(ChatColor.GREEN+"/livekit <world> bounds"+ChatColor.RESET+" - Displays bounds of <world>");
					sender.sendMessage(ChatColor.GREEN+"/livekit <world> bounds <radius> [-r|-c]"+ChatColor.RESET+" - Creates rectangular (-r) or circular (-c) bounds with <radius> [in blocks]");
					sender.sendMessage(ChatColor.GREEN+"/livekit <world> bounds <left> <top> <right> <bottom>"+ChatColor.RESET+" - Set bounds for <world> in blocks");
					sender.sendMessage(ChatColor.GREEN+"/livekit headrefresh <player>"+ChatColor.RESET+" - Refresh a players Head (only neccessary if you changed your skin)");
				}
				return true;
			}
		}
		return false;
	}

	private boolean handleMapCommands(CommandSender sender, Command command, String label, String[] args) {
		if(args.length >= 1) {
			List<String> worlds = Config.getLiveMapWorlds();

			if(label.equalsIgnoreCase("livekit") && args[0].equalsIgnoreCase("map") || worlds.contains(args[0])) {
				if(!checkPerm(sender, "livekit.commands.admin")) return true;

				if(args[0].equalsIgnoreCase("map")) {
					if(args.length == 1) {
						sender.sendMessage(prefix+"Live Map info");
						sender.sendMessage("Worlds: ");
						for(String s : worlds) sender.sendMessage(" - "+s);
						sender.sendMessage("CPU-Time: "+RenderScheduler.getCPUTime()+"ms / "+(int)(((float)RenderScheduler.getCPUTime()/50f)*100f)+"%");
						return true;
					}
					if(args.length == 3) {
						if(args[1].equalsIgnoreCase("cpu")) {
							float percent = 20;
							try{
								percent = Float.parseFloat(args[2]);
							}catch(Exception ex){sender.sendMessage(prefixError+args[2]+" is not a valid number! 0-100%"); return true;}
	
							if(percent < 5) percent = 5;
							if(percent > 100) percent = 100;
	
							RenderScheduler.setCPUTime((int)(percent*50f/100f));
							sender.sendMessage(prefix+"CPU-Time set to "+RenderScheduler.getCPUTime()+"ms / "+(int)(((float)RenderScheduler.getCPUTime()/50f)*100f)+"%");
	
							if(percent >= 80) {
								sender.sendMessage(prefix+"WARNING: Setting cpu time above 80% might cause severe lag!");
							}
	
							return true;
						}
					}
				}

				
				if(worlds.contains(args[0])) {
					LiveMapModule map = (LiveMapModule)LiveKit.getInstance().getModuleManager().getModule("LiveMapModule:"+args[0]);
					if(map == null || !map.isEnabled()) { sender.sendMessage(prefixError+" LiveMapModule not enabled."); return true;}

					RenderWorld world = map.getRenderWorld();


					if(args.length == 1) {
						RenderJob job = world.getRenderJob();
						RenderBounds bounds = world.getRenderBounds();

						sender.sendMessage(prefix+"Info of "+map.getWorldName());
						sender.sendMessage(world.getWorldInfoString());
						sender.sendMessage("Render bounds [in Blocks]: ");
						sender.sendMessage("  shape: "+bounds.getShape().name());
						if(bounds.getShape() == RenderShape.CIRCLE) {
							sender.sendMessage("  radius: "+bounds.getRadius());
						} else {
							sender.sendMessage("  left("+ChatColor.GREEN+"-x"+ChatColor.RESET+"): "+bounds.getLeft());
							sender.sendMessage("  top("+ChatColor.GREEN+"-z"+ChatColor.RESET+"): "+bounds.getTop());
							sender.sendMessage("  right("+ChatColor.GREEN+"x"+ChatColor.RESET+"): "+bounds.getRight());
							sender.sendMessage("  bottom("+ChatColor.GREEN+"z"+ChatColor.RESET+"): "+bounds.getBottom());
						}
						sender.sendMessage("Is Rendering: "+friendlyBool(job != null));
						if(job != null) {
							sender.sendMessage("  progress: "+ChatColor.GREEN+job.progressPercent()+"%"+ChatColor.RESET);
							sender.sendMessage("  chunks: "+job.currentCount()+"/"+job.maxCount());
						}

						return true;
					}
					if(args.length >= 3 && args[1].equalsIgnoreCase("render")) {
						String mode = args[2];
						boolean forced = true;
						if(args.length == 4 && args[3].equalsIgnoreCase("-m")) forced = false;

						if(mode.equalsIgnoreCase("full")) {
							RenderJob job = RenderJob.fromBounds(world.getRenderBounds(), forced ? RenderJobMode.FORCED : RenderJobMode.MISSING);
							try{
								map.startRenderJob(job);
								sender.sendMessage(prefix+"Full render has been started for "+map.getWorldName()+" (mode: "+(forced ? RenderJobMode.FORCED : RenderJobMode.MISSING).name()+")");
							}catch(Exception ex) {
								sender.sendMessage(prefixError+ex.getMessage());
							}
							return true;
						}
						if(mode.equalsIgnoreCase("stop")) {
							map.stopRenderJob();
							sender.sendMessage(prefix+"Render job of "+map.getWorldName()+" has been stopped!");
							return true;
						}
						try{
							int radius = Integer.parseInt(mode);
							if(sender instanceof Player) {
								Player player = (Player) sender;
								if(player.getWorld().getName().equalsIgnoreCase(map.getWorldName())) {
									RenderBounds bounds = new RenderBounds(radius, player.getLocation().getBlockX(), player.getLocation().getBlockZ());
									if(bounds.valid() && radius <= 1024) {
										try{
											RenderJob job = RenderJob.fromBounds(bounds, forced ? RenderJobMode.FORCED : RenderJobMode.MISSING);
											map.startRenderJob(job);
											sender.sendMessage(prefix+"Rendering chunks of specified radius for "+map.getWorldName()+" (mode: "+(forced ? RenderJobMode.FORCED : RenderJobMode.MISSING).name()+")");
										
											sender.sendMessage(radius+" "+player.getLocation().getBlockX() + " " + player.getLocation().getBlockZ());
											sender.sendMessage(bounds.toString());
											sender.sendMessage(job.toString());
										}catch(Exception ex){
											sender.sendMessage(prefixError+ex.getMessage());
										}
									} else {
										sender.sendMessage(prefixError+"A max radius of 1024 is supportd! Do a full render instead?");
									}
								} else {
									sender.sendMessage(prefixError+"Input world missmatch from the current world your in!");
								}
								return true;
							} else {
								sender.sendMessage(prefixError+"A radius can only be specified as a Player in the current world!");
								return true;
							}
						}catch(NumberFormatException ex){}
					}

					if(args.length >= 2 && args[1].equalsIgnoreCase("bounds")) {
						
						if(args.length == 2) {
							sender.sendMessage(prefix+"Bounds for "+world.getWorldName());
							sender.sendMessage(world.getRenderBounds().toString());
							return true;
						}
						if(args.length == 3 ||  args.length == 4) {
							boolean circle = false;
							if(args.length == 4 && args[3].equalsIgnoreCase("-c")) circle = true;

							try{
								int radius = Math.abs(Integer.parseInt(args[2]));
								RenderBounds bounds = circle ? new RenderBounds(radius) : new RenderBounds(-radius, -radius, radius, radius);
								if(bounds.valid()) {
									map.setRenderBounds(bounds);
									sender.sendMessage(prefix+"New render bounds set for "+map.getWorldName());
									sender.sendMessage(bounds.toString());
								} else {
									sender.sendMessage(prefixError+"Invalid radius specified. Make sure ist greater than 0.");
								}
							}catch(NumberFormatException ex) {
								sender.sendMessage(prefixError+"Invalid radius specified!");
								
							}
							return true;
						} 
						if(args.length == 6) {
							try{
								int left = Integer.parseInt(args[2]);
								int top = Integer.parseInt(args[3]);
								int right = Integer.parseInt(args[4]);
								int bottom = Integer.parseInt(args[5]);
								RenderBounds bounds = new RenderBounds(left, top, right, bottom);
								if(bounds.valid()) {
									map.setRenderBounds(bounds);
									sender.sendMessage(prefix+"New render bounds set for "+map.getWorldName());
									sender.sendMessage(bounds.toString());
								} else {
									sender.sendMessage(prefixError+"Invalid render bounds specified! make sure [right - left > 0] and [bottom - top > 0]");
								}
							}catch(NumberFormatException ex){
								sender.sendMessage(prefixError+"Invalid left,right,top,bottom bounds specified!");
							}
							return true;
						}
					}
				}

				/*if(args.length == 2) {
					if(args[1].equals("fullrender")) {
						try{
							map.startRenderJob(RenderJob.fromBounds(map.getRenderWorld("world").getRenderBounds(), RenderJobMode.FORCED));
							sender.sendMessage(prefix+"Full render started");
						}catch(Exception ex){
							ex.printStackTrace();
							sender.sendMessage(prefixError+ex.getMessage());
						}
						return true;
					}
					if(args[1].equals("abortrender")) {
						try{
							map.stopRenderJob();
							sender.sendMessage(prefix+"Full render stopped");
						}catch(Exception ex){
							ex.printStackTrace();
							sender.sendMessage(prefixError+ex.getMessage());
						}
						return true;
					}
				}*/
			}
		}

		/*if(args.length >= 1) {
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
					builder.append("Chunks loaded: "+Bukkit.getWorld(map.getWorld()).getLoadedChunks().length);
					
					sender.sendMessage(prefix+builder.toString());

					return true;
				}
				if(args.length == 2) {
					if(args[1].equalsIgnoreCase("fullrender")) {

						if(options.getLimits().maxX - options.getLimits().minX > 200 || options.getLimits().maxZ - options.getLimits().minZ > 200) {
							sender.sendMessage(prefixError+"The current bounds are to big! Use /livekit map bounds <minX> <maxX> <minZ> <maxZ> to set fitting bounds");
							return true;
						}

						if(map.getRegionQueueSize() != 0) {
							sender.sendMessage(prefixError+"Fullrender can only be startet if Region Queue is empty!");
							return true;
						}

						try{
							map.fullRender();
							sender.sendMessage(prefix+"Fullrender started");
						}catch(Exception ex){sender.sendMessage(prefixError+ex.getMessage()); return true;}

						return true;
					}
					if(args[1].equalsIgnoreCase("abortrender")) {
						map.clearChunkQueue();
						map.clearRegionQueue();
						sender.sendMessage(prefix+"Rendering queues cleared!");

						return true;
					}
					if(args[1].equalsIgnoreCase("bounds")) {
						BoundingBox world = BoundingBox.fromWorld(Bukkit.getWorld(map.getWorld()).getName());
						sender.sendMessage(prefix+"Current: "+options.getLimits().toString());
						sender.sendMessage(prefix+"World bounds: "+world.toString());

						return true;
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

						return true;
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

						return true;
					}
					if(args[1].equalsIgnoreCase("bounds")) {
						if(args[2].equalsIgnoreCase("update")) {
							BoundingBox world = BoundingBox.fromWorld(Bukkit.getWorld(map.getWorld()).getName());
							if(world != null) {
								map.setBounds(world.minX, world.maxX, world.minZ, world.maxZ);
							}
							sender.sendMessage(prefix+"Updated map bounds to "+world.toString());

							return true;
						}
					}
				}
				if(args.length == 6) {
					if(args[1].equalsIgnoreCase("bounds")) {
						try{
							int minX = Integer.parseInt(args[2]);
							int maxX = Integer.parseInt(args[3]);
							int minZ = Integer.parseInt(args[4]);
							int maxZ = Integer.parseInt(args[5]);

							if(maxX - minX >= 1 && maxZ - minZ >= 1) {
								if(maxX - minX > 50 || maxZ - minZ > 50) {
									sender.sendMessage(prefixError+"The bounds you are trying to set are to big! Limited to 50x50. If you think it's not enough, contact me livekitapp@gmail.com");
									return true;
								}

								map.setBounds(minX, maxX, minZ, maxZ);
								sender.sendMessage(prefix+"New bounds have been set!");
								sender.sendMessage("Bounds: "+options.getLimits().toString());
							} else {
								sender.sendMessage(prefixError+"Invalid bounds set. Make sure max > min");
							}

						}catch(Exception ex) {
							sender.sendMessage(prefixError+"Invalid bound numbers specified!");
						}
						return true;
					}
				}
			}
		}*/
		return false;
	}

	private boolean handleAdminCommands(CommandSender sender, Command command, String label, String[] args) {
		
		if(args.length == 2) {
			if(args[0].equalsIgnoreCase("headrefresh")) {
				String playerName = args[1];

				Player player = Bukkit.getPlayer(playerName);
				if(player != null) {
					if(!checkPerm(sender, "livekit.commands.admin")) return true;

					HeadLibraryV2.get(player.getName(), true, true);
					sender.sendMessage(prefix+player.getName()+"'s head is going to be refreshed!");
				} else {
					sender.sendMessage(prefixError+"Player "+args[1]+" could not be found!");
				}
				
				return true;
			}
		}

		if(args.length == 1) {
			if(args[0].equalsIgnoreCase("permreload")) {
				if(!checkPerm(sender, "livekit.commands.admin")) return true;

				LiveKit.getInstance().commandReloadPermissions();
				sender.sendMessage(prefix+"Permissions will reload");

				return true;
			}

			/*if(args[0].equalsIgnoreCase("modules")) {
				if(!checkPerm(sender, "livekit.commands.admin")) return true;

				sender.sendMessage(prefix+"Modules:");
				for(BaseModule module : LiveKit.getInstance().getModules()) {
					sender.sendMessage(module.getType()+" Version: "+module.getVersion()+" Enabled: "+module.isEnabled());
				}

				return true;
			}*/
			
			/*if(args[0].equalsIgnoreCase("subs")) {
				if(!checkPerm(sender, "livekit.commands.admin")) return true;

				Map<String, List<String>> subscriptions = LiveKit.getInstance().getModuleManager().getSubscriptions();
				sender.sendMessage(prefix+"Registered Subscriptions: ");
				for(Entry<String, List<String>> entry : subscriptions.entrySet()) {
					sender.sendMessage(entry.getKey()+": ");
					for(String s : entry.getValue()) 
						sender.sendMessage(" - "+s);
				}

				return true;
			}


			World mWorld = Bukkit.getWorld(args[0]);
            WorldType mType = mWorld.getWorldType();
			sender.sendMessage(mType.getName());


			if(sender instanceof Player) {
				Player player = (Player)sender;
				sender.sendMessage(mWorld.getHighestBlockAt(player.getLocation()).getType().name());
			}*/
		}
		if(args.length == 3) {
			/*if(args[0].equalsIgnoreCase("modules")) {
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

				return true;
			}*/
			/*if(args[0].equalsIgnoreCase("load")) {
				if(!checkPerm(sender, "livekit.commands.admin")) return true;
				
				if(sender instanceof Player) {
					Player player = (Player)sender;
					World world = player.getWorld();

					int x = Integer.parseInt(args[1]);
					int z = Integer.parseInt(args[2]);

					long start = System.currentTimeMillis();
					for(int cx = x*32; cx < (x+1)*32; cx++) {
						for(int cz = z*32; cz < (z+1)*32; cz++) {
							//world.getChunkAt(cx, cz);
							world.loadChunk(cx, cz);
						}
					}
					player.sendMessage(prefix+"Chunks loaded!" + (System.currentTimeMillis()-start)+"ms");
					return true;
				}
			}*/
		}
		return false;
	}

	private boolean handlePlayerPinCommands(CommandSender sender, Command command, String label, String[] args) {
		if(!(sender instanceof Player)) {
            return false;
        }

        Player player = (Player)sender;

        if(args.length == 1) {
            if(args[0].equalsIgnoreCase("pins")) {
				if(!checkPerm(sender, "livekit.poi.personalpins")) return true;

                BasicPlayerPinProvider.listPlayerPinsAsync(player, new FutureSyncCallback<List<Waypoint>>(){
                    @Override
                    public void onSyncResult(List<Waypoint> result) {
                        if(result.size() != 0) {
							player.sendMessage(Plugin.getPrefix()+"Your pins:");
							for(int i = 0; i < result.size(); i++) {
								player.sendMessage(ChatColor.GREEN+"["+ChatColor.RESET+(i+1)+ChatColor.GREEN+"] "+ChatColor.RESET+result.get(i).getName() + " - " + ((int)result.get(i).getLocation().distance(LKLocation.fromLocation(player.getLocation())))+"m");
							}
						} else {
							player.sendMessage(prefix+"You have not set any pins yet! Start with "+ChatColor.AQUA+"/livekit setpin <name>");
						}
                    }
                }, Utils.errorHandler(sender));

                return true;
            }
        }

        if(args.length >= 2) {
            if(args[0].equalsIgnoreCase("setpin")) {
				if(!checkPerm(sender, "livekit.poi.personalpins")) return true;

                String name = args[1];
                for(int i = 2; i < args.length; i++) name+=" "+args[i];
				
				final String finalName = name;
				BasicPlayerPinProvider.listPlayerPinsAsync(player, new FutureSyncCallback<List<Waypoint>>(){
                    @Override
                    public void onSyncResult(List<Waypoint> result) {
                        if(result.size() < Config.getPersonalPinLimit()) {
							
							final Waypoint waypoint = new Waypoint(LKLocation.fromLocation(player.getLocation()), finalName, "Custom set pin", BasicPlayerPinProvider.PLAYER_PIN_COLOR, false, Privacy.PRIVATE);
							BasicPlayerPinProvider.setPlayerPinAsync(player, waypoint, new FutureSyncCallback<Void>(){
								@Override
								public void onSyncResult(Void result) {
									player.sendMessage(Plugin.getPrefix()+"Pin "+ChatColor.AQUA+waypoint.getName()+ChatColor.RESET+" has been set!");
									
									getLiveKit().notifyPlayerInfoChange(player);
								}
							}, Utils.errorHandler(sender));

						} else {
							player.sendMessage(prefixError+"You've reached your personal pin limit of "+Config.getPersonalPinLimit()+"! Remove a pin to set a new one.");
						}
                    }
                }, Utils.errorHandler(sender));
            
                return true;
            }
            if(args[0].equalsIgnoreCase("removepin")) {
				if(!checkPerm(sender, "livekit.poi.personalpins")) return true;

                try{
                    int id = Integer.parseInt(args[1]) - 1;

                    BasicPlayerPinProvider.listPlayerPinsAsync(player, new FutureSyncCallback<List<Waypoint>>(){
                        @Override
                        public void onSyncResult(List<Waypoint> result) {
                            if(id >= result.size()) player.sendMessage(Plugin.getPrefixError()+"Wrong Pin ID! '/livekit pins' to list available pins");
                            Waypoint toRemove = result.get(id);

                            BasicPlayerPinProvider.removePlayerPinAsync(player, toRemove, new FutureSyncCallback<Void>(){
                                @Override
                                public void onSyncResult(Void result) {
                                    player.sendMessage(Plugin.getPrefix()+"Pin "+ChatColor.AQUA+toRemove.getName()+ChatColor.RESET+" has been removed!");
									
									getLiveKit().notifyPlayerInfoChange(player);
                                }
                            }, Utils.errorHandler(sender));
                        }
                    }, Utils.errorHandler(sender));

                }catch(Exception ex){/*ex.printStackTrace();*/ player.sendMessage(Plugin.getPrefixError()+"Wrong Pin ID!");}

                return true;
            }
        }

        return false;
	}

	/*private boolean handlePOICommands(CommandSender sender, Command command, String label, String[] args) {
		if(!(sender instanceof Player)) {
            return false;
        }

        Player player = (Player)sender;

        /*if(args.length == 1) {
            if(args[0].equalsIgnoreCase("pois")) {
				if(!checkPerm(sender, "livekit.module.admin")) return true;

                BasicPOIProvider.listPOIAsync(new FutureSyncCallback<List<POI>>(){
                    @Override
                    public void onSyncResult(List<POI> result) {
                        player.sendMessage(Plugin.getPrefix()+"Your pins:");
                        for(int i = 0; i < result.size(); i++) {
                            player.sendMessage(ChatColor.GREEN+"["+ChatColor.RESET+(i+1)+ChatColor.GREEN+"] "+ChatColor.RESET+result.get(i).getName() + " - " + ((int)result.get(i).getLocation().distance(player.getLocation()))+"m");
                        }
                    }
                }, Utils.errorHandler(sender));

                return true;
            }
        }*/

        /*if(args.length >= 2) {
            if(args[0].equalsIgnoreCase("setpoi")) {
				if(!checkPerm(sender, "livekit.module.admin")) return true;

                String name = args[1];
                for(int i = 2; i < args.length; i++) name+=" "+args[i];

                final Waypoint waypoint = new Waypoint(player.getLocation(), name, "Custom set pin", Color.fromChatColor(ChatColor.AQUA), false, Privacy.PRIVATE);
                BasicPlayerPinProvider.setPlayerPinAsync(player, waypoint, new FutureSyncCallback<Void>(){
                    @Override
                    public void onSyncResult(Void result) {
                        player.sendMessage(Plugin.getPrefix()+"Pin "+ChatColor.AQUA+waypoint.getName()+ChatColor.RESET+" has been set!");
						
						getLiveKit().notifyPlayerInfoChange(player);
                    }
                }, Utils.errorHandler(sender));
            
                return true;
            }
            if(args[0].equalsIgnoreCase("removepin")) {
				if(!checkPerm(sender, "livekit.player.pins")) return true;

                try{
                    int id = Integer.parseInt(args[1]) - 1;

                    BasicPlayerPinProvider.listPlayerPinsAsync(player, new FutureSyncCallback<List<Waypoint>>(){
                        @Override
                        public void onSyncResult(List<Waypoint> result) {
                            if(id >= result.size()) player.sendMessage(Plugin.getPrefixError()+"Wrong Pin ID! '/livekit pins' to list available pins");
                            Waypoint toRemove = result.get(id);

                            BasicPlayerPinProvider.removePlayerPinAsync(player, toRemove, new FutureSyncCallback<Void>(){
                                @Override
                                public void onSyncResult(Void result) {
                                    player.sendMessage(Plugin.getPrefix()+"Pin "+ChatColor.AQUA+toRemove.getName()+ChatColor.RESET+" has been removed!");
									
									getLiveKit().notifyPlayerInfoChange(player);
                                }
                            }, Utils.errorHandler(sender));
                        }
                    }, Utils.errorHandler(sender));

                }catch(Exception ex){ex.printStackTrace(); player.sendMessage(Plugin.getPrefixError()+"Wrong Pin ID!");}

                return true;
            }
        }

        return false;
	}*/

	private boolean checkPerm(CommandSender sender, String permission) {
		return checkPerm(sender, permission, true);
	}

	private boolean checkPerm(CommandSender sender, String permission, boolean verbose) {
		if(sender.isOp()) return true;

		if(sender instanceof Player) {
			Player player = (Player)sender;
			boolean access = Permissions.has(player, permission);
			if(!access && verbose) player.sendMessage(prefixError+"You need "+permission+" permission to access this command!");
			return access;
		}
		return false;
	}

	private String friendlyBool(boolean bool) {
		if(bool) return ChatColor.GREEN+"Yes"+ChatColor.RESET;
		else return ChatColor.RED+"No"+ChatColor.RESET;
	}

	public static Plugin getInstance() {
		return instance;
	}

	public static IStorageAdapter getStorage() {
		return storage;
	}

	public static String getPrefixError() {
		return prefixError;
	}

	public static String getPrefix() {
		return prefix;
	}

	public static void log(String message) {
		logger.info(message);
	}

	public static void warning(String message) {
		logger.warning(message);
	}

	public static void severe(String message) {
		logger.severe(message);
	}

	public static void debug(String message) {
		//logger.warning(message);
	}

	@Override
	public ILiveKit getLiveKit() {
		return LiveKit.getInstance();
	}
}
