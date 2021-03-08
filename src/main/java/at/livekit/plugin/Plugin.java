package at.livekit.plugin;

import java.util.List;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import at.livekit.livekit.Identity;
import at.livekit.livekit.LiveKit;
import at.livekit.livekit.PlayerAuth;
import at.livekit.map.RenderBounds;
import at.livekit.map.RenderJob;
import at.livekit.map.RenderWorld;
import at.livekit.map.RenderBounds.RenderShape;
import at.livekit.map.RenderJob.RenderJobMode;
import at.livekit.modules.BaseModule;
import at.livekit.modules.PlayerModule;
import at.livekit.modules.LiveMapModule;
import at.livekit.modules.PlayerModule.LPlayer;
import at.livekit.utils.HeadLibraryEvent;
import at.livekit.utils.HeadLibraryV2;
import at.livekit.utils.Metrics;

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
    }
    
    @Override
    public void onDisable() {
		LiveKit.getInstance().onDisable();
			
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



			if(!handled) {
				sender.sendMessage(prefixError+"Unknown command. Try /livekit help");
			}

			if(args.length == 1) {
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
				return true;
			}
			if(args[0].equalsIgnoreCase("?") || args[0].equalsIgnoreCase("help")) {
				sender.sendMessage(prefix+"Help Page");
				sender.sendMessage(ChatColor.GREEN+"/livekit "+ChatColor.RESET+" - LiveKit basic info");
				if(checkPerm(sender, "livekit.commands.basic", false)) {
					sender.sendMessage(ChatColor.GREEN+"/livekit claim"+ChatColor.RESET+" - Generate a claim pin, used to identify yourself in the App");
					sender.sendMessage(ChatColor.GREEN+"/livekit info"+ChatColor.RESET+" - Info about App sessions identified as you");
					sender.sendMessage(ChatColor.GREEN+"/livekit clearsessions"+ChatColor.RESET+" - Clear all active sessions. App clients need to re-claim");
				} else {
					sender.sendMessage("You don't have the needed permission "+ChatColor.GREEN+"livekit.commands.basic"+ChatColor.RESET+" to access LiveKit");
					if(Config.allowAnonymous())sender.sendMessage("However, anonymous joining is enabled!");
				}
				if(checkPerm(sender, "livekit.commands.admin", false)) {
					sender.sendMessage(prefixError+"Admin Commands:"+ChatColor.RESET);
					sender.sendMessage(ChatColor.GREEN+"/livekit map"+ChatColor.RESET+" - Display info about live map");
					sender.sendMessage(ChatColor.GREEN+"/livekit map cpu <time in %>"+ChatColor.RESET+" - Speed up rendering performance at the cost of server lag. Use with care. Default: 40%");
					sender.sendMessage(ChatColor.GREEN+"/livekit <world>"+ChatColor.RESET+" - Show general info and rendering status of <world>");
					sender.sendMessage(ChatColor.GREEN+"/livekit <world> render full [-m|-f]"+ChatColor.RESET+" - Start full render on <world> -m: missing tiles only, -f forces already rendered tiles to render");
					sender.sendMessage(ChatColor.GREEN+"/livekit <world> render <radius>"+ChatColor.RESET+" - Renders a rectangular radius around the players position. (Worlds must match)");
					sender.sendMessage(ChatColor.GREEN+"/livekit <world> render stop"+ChatColor.RESET+" - Stop current rendering job");
					sender.sendMessage(ChatColor.GREEN+"/livekit <world> bounds"+ChatColor.RESET+" - Displays bounds of <world>");
					sender.sendMessage(ChatColor.GREEN+"/livekit <world> bounds <radius> [-r|-c]"+ChatColor.RESET+" - Creates rectangular (-r) or circular (-c) bounds with <radius> [in blocks]");
					sender.sendMessage(ChatColor.GREEN+"/livekit <world> bounds <left> <top> <right> <bottom>"+ChatColor.RESET+" - Set bounds for <world> in blocks");
				}
				return true;
			}
		}
		return false;
	}

	private boolean handleMapCommands(CommandSender sender, Command command, String label, String[] args) {
		if(args.length >= 1) {
			if(label.equalsIgnoreCase("livekit") && args[0].equalsIgnoreCase("map") || args[0].equalsIgnoreCase(Config.getModuleString("LiveMapModule", "world"))) {
				if(!checkPerm(sender, "livekit.commands.admin")) return true;

				LiveMapModule map = (LiveMapModule)LiveKit.getInstance().getModuleManager().getModule("LiveMapModule");
				if(!map.isEnabled()) { sender.sendMessage(prefixError+" LiveMapModule not enabled."); return true;}

				if(args[0].equalsIgnoreCase("map")) {
					if(args.length == 1) {
						sender.sendMessage(prefix+"Live Map info");
						sender.sendMessage("World: "+Config.getModuleString("LiveMapModule", "world"));
						sender.sendMessage("CPU-Time: "+LiveMapModule.CPU_TIME+"ms / "+(int)(((float)LiveMapModule.CPU_TIME/50f)*100f)+"%");
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
	
							LiveMapModule.CPU_TIME = (int)(percent*50f/100f);
							sender.sendMessage(prefix+"CPU-Time set to "+LiveMapModule.CPU_TIME+"ms / "+(int)(((float)LiveMapModule.CPU_TIME/50f)*100f)+"%");
	
							if(percent >= 80) {
								sender.sendMessage(prefix+"WARNING: Setting cpu time above 80% might cause severe lag!");
							}
	
							return true;
						}
					}
				}

				if(args[0].equalsIgnoreCase(Config.getModuleString("LiveMapModule", "world"))) {
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
		

		if(args.length == 1) {
			if(args[0].equalsIgnoreCase("permreload")) {
				if(!checkPerm(sender, "livekit.commands.admin")) return true;

				LiveKit.getInstance().commandReloadPermissions();
				sender.sendMessage(prefix+"Permissions will reload");

				return true;
			}

			if(args[0].equalsIgnoreCase("modules")) {
				if(!checkPerm(sender, "livekit.commands.admin")) return true;

				sender.sendMessage(prefix+"Modules:");
				for(BaseModule module : LiveKit.getInstance().getModules()) {
					sender.sendMessage(module.getType()+" Version: "+module.getVersion()+" Enabled: "+module.isEnabled());
				}

				return true;
			}
			
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
		return false;
	}

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
