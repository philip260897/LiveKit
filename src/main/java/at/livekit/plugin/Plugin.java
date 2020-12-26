package at.livekit.plugin;

import java.io.File;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.JSONObject;

import at.livekit.livekit.LiveKit;
import at.livekit.livekit.PlayerAuth;
import at.livekit.main.LiveEntity;
import at.livekit.utils.HeadLibrary;
import at.livekit.utils.HeadLibraryEvent;
import net.md_5.bungee.api.ChatColor;

public class Plugin extends JavaPlugin implements CommandExecutor {
	//private static int pluginPort = 8123;
	protected static Logger logger;

	public static Plugin instance;
	public static String workingDirectory;

	@Override
	public void onEnable() {
		instance = this;
		logger = getLogger();

		File folder = new File(System.getProperty("user.dir") + "/plugins/LiveKit/");
		if (!folder.exists()) {
			logger.info("Creating LiveKit directory");
			folder.mkdir();
		}
		workingDirectory = folder.getAbsolutePath();

		logger.info("Materials: " + Material.values().length);
		logger.info("Biomes: " + Biome.values().length);

		try{
			LiveKit.start();
		}catch(Exception ex){ex.printStackTrace();}

		/*for(String world : worlds) {
			LiveMap livemap = new LiveMap(world);
			livemaps.put(world, livemap);
			livemap.start();
		}*/

		/*server = new TCPServer(8123);
		server.setServerListener(new ServerListener() {
			@Override
			public void onConnect(RemoteClient client) {
				client.sendPacket(new WelcomePacket(1,1,LiveMap.TICK_RATE,worlds));
			}
			@Override
			public void onDisconnect(RemoteClient client) {

			}

			@Override
			public void onLiveMapSettings(RemoteClient client, String world) {
				if(livemaps.containsKey(world)) {
					livemaps.get(world).fullUpdate(client);
				}
			}
		});
		server.open();*/

		HeadLibrary.setHeadLibraryListener(new HeadLibraryEvent(){
			@Override
			public void onHeadResolved(String uuid, String base64) {
				LiveEntity entity = (LiveEntity)LiveKit.getLiveMap(LiveKit.getLiveMapWorld()).getSyncable(uuid);
				if(entity != null) {
					entity.updateHead(base64);
				}
				
			}
		});


		this.getServer().getPluginManager().registerEvents(new PlayerListener(), this);
		this.getServer().getPluginManager().registerEvents(new WorldListener(), this);
    }
    
    @Override
    public void onDisable() {
		LiveKit.dispose();
			
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
						player.sendMessage(PlayerAuth.get(player.getUniqueId().toString()).generateClaimPin());
					}
				}
			}
		}
		return true;
	}
}
