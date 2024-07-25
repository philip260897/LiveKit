package at.livekit.plugin;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import at.livekit.api.core.ILiveKit;
import at.livekit.api.core.ILiveKitPlugin;
import at.livekit.commands.CommandHandler;
import at.livekit.commands.LiveKitCommandExecutor;
import at.livekit.lib.LibraryLoader;
import at.livekit.livekit.LiveKit;
import at.livekit.modules.PlayerModule;
import at.livekit.modules.PlayerModule.LPlayer;
import at.livekit.provider.BasicPOILocationProvider;
import at.livekit.provider.BasicPlayerInfoProvider;
import at.livekit.provider.BasicPlayerLocationProvider;
import at.livekit.provider.BasicPlayerPinProvider;
import at.livekit.provider.POISpawnProvider;
import at.livekit.storage.IStorageAdapterGeneric;
import at.livekit.storage.StorageManager;
import at.livekit.supported.discordsrv.DiscordSRVPlugin;
import at.livekit.supported.essentialsx.EssentialsPlugin;
import at.livekit.utils.ConsoleListener;
import at.livekit.utils.HeadLibraryEvent;
import at.livekit.utils.HeadLibraryV2;
import at.livekit.utils.Metrics;

public class Plugin extends JavaPlugin implements ILiveKitPlugin, Listener {

	private static boolean DEBUG = false;

	private static Logger logger;
	private static Plugin instance;

	private static String name;
	private static ChatColor color = ChatColor.GREEN;
	private static String prefix;
	private static String prefixError;

	private static IStorageAdapterGeneric storage;

	private static DiscordSRVPlugin discordPlugin;
	private static EssentialsPlugin essentialsPlugin;
	//private static Stat stat;


	@Override
	public void onEnable() {

		instance = this;
		logger = getLogger();

		//create config folder if doesn't exist
		if(!getDataFolder().exists()) {
			getDataFolder().mkdirs();
		}

		LibraryLoader.loadLibraries(this);

		try{
			Class.forName("org.postgresql.Driver");
		}catch(Exception ex){ex.printStackTrace();}

		try{
			Texturepack.getInstance();
		}catch(Exception ex){ 
			ex.printStackTrace(); 
			logger.severe("Error initializing Textures, shutting down");
			getServer().getPluginManager().disablePlugin(this);
			return;
		}

		name = this.getDescription().getName();
		prefix = color+"["+ChatColor.RESET+name+color+"] "+ChatColor.RESET;
		prefixError = ChatColor.RED+"["+ChatColor.RESET+name+ChatColor.RED+"] "+ChatColor.RESET;

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

		BasicPlayerLocationProvider playerLocationProvider = new BasicPlayerLocationProvider();
		this.getLiveKit().addPlayerLocationProvider(playerLocationProvider);

		BasicPOILocationProvider poiLocationProvider = new BasicPOILocationProvider();
		this.getLiveKit().addPOILocationProvider(poiLocationProvider);

		//POI
		if(Config.showDefaultWorldSpawns()) {
			POISpawnProvider provider = new POISpawnProvider();
			this.getLiveKit().addPOILocationProvider(provider);
			Bukkit.getServer().getPluginManager().registerEvents(provider, Plugin.getInstance());
		}

		//Player Pin Provider
		//PlayerPinProvider playerPins = new PlayerPinProvider();
		this.getLiveKit().addPlayerLocationProvider(new BasicPlayerPinProvider());

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

		if(Config.isEssentialsEnabled()) {
			essentialsPlugin = new EssentialsPlugin();
			essentialsPlugin.onEnable();
		}
    }
    
	@EventHandler
    public void onServerListPing(ServerListPingEvent event) {}


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
