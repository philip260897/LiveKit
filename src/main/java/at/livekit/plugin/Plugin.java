package at.livekit.plugin;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;


import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.craftbukkit.v1_20_R3.CraftServer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.SimplePluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scheduler.BukkitWorker;


import at.livekit.api.core.ILiveKit;
import at.livekit.api.core.ILiveKitPlugin;
import at.livekit.commands.CommandHandler;
import at.livekit.commands.LiveKitCommandExecutor;
import at.livekit.livekit.LiveKit;
import at.livekit.modules.PlayerModule;
import at.livekit.modules.PlayerModule.LPlayer;
import at.livekit.provider.BasicPlayerInfoProvider;
import at.livekit.provider.BasicPlayerPinProvider;
import at.livekit.provider.POISpawnProvider;
import at.livekit.storage.IStorageAdapterGeneric;
import at.livekit.storage.StorageManager;
import at.livekit.supported.DiscordSRVPlugin;
import at.livekit.timings.TimedRegisteredListener;
import at.livekit.utils.ConsoleListener;
import at.livekit.utils.HeadLibraryEvent;
import at.livekit.utils.HeadLibraryV2;
import at.livekit.utils.Metrics;

public class Plugin extends JavaPlugin implements ILiveKitPlugin, Listener {

	private static boolean DEBUG = true;

	private static Logger logger;
	private static Plugin instance;

	private static String name;
	private static ChatColor color = ChatColor.GREEN;
	private static String prefix;
	private static String prefixError;

	private static IStorageAdapterGeneric storage;

	private static DiscordSRVPlugin discordPlugin;
	//private static Stat stat;

	@Override
	public void onEnable() {

		instance = this;
		logger = getLogger();

		//create config folder if doesn't exist
		if(!getDataFolder().exists()) {
			getDataFolder().mkdirs();
		}

		//stat = new Stat();

		try{
			Texturepack.getInstance();
		}catch(Exception ex){ 
			ex.printStackTrace(); 
			logger.severe("Error initializing Textures, shutting down");
			getServer().getPluginManager().disablePlugin(this);
			return;
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
			storage = StorageManager.initialize();
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

		Bukkit.getServer().getPluginManager().registerEvents(this, Plugin.getInstance());

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

		CommandHandler.initialize();

		LiveKitCommandExecutor base = new LiveKitCommandExecutor();
		PluginCommand cmd = getCommand("livekit");
		cmd.setExecutor(base);
		cmd.setTabCompleter(base);

		if(Config.isDiscordEnabled()) {
			discordPlugin = new DiscordSRVPlugin();
        	discordPlugin.onEnable();
		}

		/*TimedRegisteredListener.registerListeners();

		try {
			Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
			commandMapField.setAccessible(true);
			SimpleCommandMap commandMap = (SimpleCommandMap) commandMapField.get(Bukkit.getServer());


			Field knownCommandsField = getField(commandMap.getClass(), "knownCommands");
            knownCommandsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);

            for (Map.Entry<String, Command> entry : knownCommands.entrySet()) {
                Command command = entry.getValue();
				if(command instanceof PluginCommand) {
					System.out.println(entry.getKey() + " -> " + command);

                    PluginCommand pluginCommand = (PluginCommand) command;

                    CommandExecutor originalExecutor = pluginCommand.getExecutor();
					if(!(originalExecutor instanceof TimedCommandExecutor)) {
						System.out.println(originalExecutor.getClass().getName());
						if (originalExecutor != null) {
							TimedCommandExecutor timedExecutor = new TimedCommandExecutor(originalExecutor, pluginCommand.getPlugin(), pluginCommand.getName());
							pluginCommand.setExecutor(timedExecutor);
						}
					}
				}

            }
		}catch(Exception ex){ex.printStackTrace();}*/


		/*Runnable

		Class[] classes = scheduler.getClass().getDeclaredClasses();
		for(Class clazz : classes) {
			System.out.println(clazz.getName());
		}
		for(Field field : scheduler.getClass().getSuperclass().getDeclaredFields()) {
			System.out.println(field.getName());
		}*/

		


		/*int serverPort = Bukkit.getPort();
		int lkPort = Config.getServerPort();
		Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
			@Override
			public void run() {
				stat.onEnabled(serverPort, lkPort);
			}
		});*/

/*try {
            Field field = BukkitScheduler.class.getDeclaredField("taskMap");
            field.setAccessible(true);
            Map<Integer, BukkitTask> taskMap = (Map<Integer, BukkitTask>) field.get(Bukkit.getScheduler());Bukkit

            BukkitScheduler wrappedScheduler = Bukkit.getScheduler();// {
                /*@Override
                public int scheduleSyncRepeatingTask(Plugin plugin, Runnable task, long delay, long period) {
                    Runnable wrappedTask = () -> {
                        long start = System.nanoTime();
                        task.run();
                        long end = System.nanoTime();
                        timingsManager.addTiming(plugin, end - start);
                    };
                    return super.scheduleSyncRepeatingTask(plugin, wrappedTask, delay, period);
                }

                @Override
                public BukkitTask runTaskTimer(Plugin plugin, BukkitRunnable task, long delay, long period) {
                    BukkitRunnable wrappedTask = new BukkitRunnable() {
                        @Override
                        public void run() {
                            long start = System.nanoTime();
                            task.run();
                            long end = System.nanoTime();
                            timingsManager.addTiming(plugin, end - start);
                        }
                    };
                    return super.runTaskTimer(plugin, wrappedTask, delay, period);
                }*/

                // Override other schedule methods similarly...
           // };

       /*   // Replace the Bukkit scheduler instance with the wrapped one
            field.set(Bukkit.getScheduler(), wrappedScheduler);
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
        }*/
    }
    
	/*private static Field getField(Class clazz, String fieldName)
	throws NoSuchFieldException {
try {
  return clazz.getDeclaredField(fieldName);
} catch (NoSuchFieldException e) {
  Class superClass = clazz.getSuperclass();
  if (superClass == null) {
	throw e;
  } else {
	return getField(superClass, fieldName);
  }
}
}*/

	@EventHandler
	public void onServerLoadEvent(ServerLoadEvent event){
		LiveKit.getInstance().onServerLoad();
	}

	/*@EventHandler
	public void onServerLoadEvent(ServerLoadEvent event) {
		//try enable economy after everything has loaded
		if(Economy.getInstance().isAvailable() == false) {
			if(Bukkit.getPluginManager().getPlugin("Vault") == null) {
				Plugin.debug("Vault not found! No default Economy available!");
				return;
			}

			RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> rsp = Bukkit.getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
			if(rsp == null) {
				Plugin.debug("Economy rsp == null!");
				return;
			}
			
			net.milkbowl.vault.economy.Economy econ = rsp.getProvider();
			if(econ == null) {
				Plugin.debug("Econ provider null!");
				return;
			}

			Plugin.log("Found Vault! Using economy ["+econ.getName()+"]");
			LiveKit.getInstance().setEconomyAdapter(new VaultEconomyAdapter(econ));
		}
	}*/

    @Override
    public void onDisable() {
		//unregister before livekit gets disabled
		ConsoleListener.unregisterListener();

		try{
			if(discordPlugin != null) {
				discordPlugin.onDisable();
			}
		}catch(Exception ex){ex.printStackTrace();}

		try{
			LiveKit.getInstance().onDisable();
		}catch(Exception ex){ex.printStackTrace();}

		try{
			if(storage != null) storage.dispose();
		}catch(Exception ex){ex.printStackTrace();}
			
		try{
			HeadLibraryV2.onDisable();
		}catch(Exception ex){ex.printStackTrace();}
	}

	public static Plugin getInstance() {
		return instance;
	}

	public static IStorageAdapterGeneric getStorage() {
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
		if(DEBUG) logger.warning(message);
	}

	public static boolean isDebug() {
		return DEBUG;
	}

	/*public static Stat getStat() {
		return stat;
	}*/

	@Override
	public ILiveKit getLiveKit() {
		return LiveKit.getInstance();
	}
}
