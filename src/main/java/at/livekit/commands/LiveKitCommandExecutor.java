package at.livekit.commands;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.json.JSONArray;

import at.livekit.api.core.LKLocation;
import at.livekit.api.core.Privacy;
import at.livekit.api.map.PersonalPin;
import at.livekit.authentication.AuthenticationHandler;
import at.livekit.authentication.Pin;
import at.livekit.authentication.Session;
import at.livekit.commands.CommandHandler.CommandHandlerPermissionCallback;
import at.livekit.commands.CommandHandler.MatchResult;
import at.livekit.livekit.Identity;
import at.livekit.livekit.LiveKit;
import at.livekit.map.RenderBounds;
import at.livekit.map.RenderJob;
import at.livekit.map.RenderScheduler;
import at.livekit.map.RenderWorld;
import at.livekit.map.RenderBounds.RenderShape;
import at.livekit.map.RenderJob.RenderJobMode;
import at.livekit.modules.BaseModule;
import at.livekit.modules.LiveMapModule;
import at.livekit.plugin.Config;
import at.livekit.plugin.Permissions;
import at.livekit.plugin.Plugin;
import at.livekit.plugin.Texturepack;
import at.livekit.provider.BasicPlayerPinProvider;
import at.livekit.utils.FutureSyncCallback;
import at.livekit.utils.HeadLibraryV2;
import at.livekit.utils.Utils;

public class LiveKitCommandExecutor implements CommandExecutor, TabCompleter {

    private LKCommand LIVEKIT = new LKCommand("livekit", null, true, this::cmdLiveKit, "LiveKit basic info");
    private LKCommand LIVEKIT_HELP = new LKCommand("livekit help", null, true, this::cmdLiveKitHelp);
    private LKCommand LIVEKIT_HELP2 = new LKCommand("livekit ?", null, true, this::cmdLiveKitHelp);
    private LKCommand LIVEKIT_CLAIM = new LKCommand("livekit claim", "livekit.commands.basic", false, this::cmdLiveKitClaim, "Generate a claim pin, used to identify yourself in the App");
    private LKCommand LIVEKIT_INFO = new LKCommand("livekit info", "livekit.commands.basic", false, this::cmdLiveKitInfo, "Info about App sessions identified as you");
    private LKCommand LIVEKIT_CLEARSESSIONS = new LKCommand("livekit clearsessions", "livekit.commands.basic", false, this::cmdLiveKitClearsessions, "Clear all active sessions. App clients need to re-claim");
    private LKCommand PIN_LIST = new LKCommand("livekit pins", "livekit.poi.personalpins", false, this::cmdPinList, "Set a personal pin at your current location");
    private LKCommand PIN_SET = new LKCommand("livekit setpin {message(name)}", "livekit.poi.personalpins", false, this::cmdPinSet, "List of all your set pins");
    private LKCommand PIN_REMOVE = new LKCommand("livekit removepin {num(id)}", "livekit.poi.personalpins", false, this::cmdPinRemove, "Remove a pin. Obtain the <id> from /livekit pins");
    private LKCommand HEADREFRESH = new LKCommand("livekit headrefresh", "livekit.commands.basic", false, this::cmdHeadrefresh, "Refresh your Head (only neccessary if you changed your skin)");

    private LKCommand MAP_INFO = new LKCommand("livekit map", "livekit.commands.admin", true, this::cmdMapInfo, "Display info about live map"); 
    private LKCommand MAP_CPU_SET = new LKCommand("livekit map cpu {num(%)}", "livekit.commands.admin", true, this::cmdMapSetCPU, "Speed up rendering performance at the cost of server lag. Use with care. Default: 40%"); 

    private LKCommand WORLD_INFO = new LKCommand("livekit {world}", "livekit.commands.admin", true, this::cmdWorldInfo, "Show general info and rendering status of <world>");
    private LKCommand WORLD_RENDER_FULL = new LKCommand("livekit {world} render full", "livekit.commands.admin", true, this::cmdWorldRender, " Start full render on <world>");
    private LKCommand WORLD_RENDER_FULL_MISSING = new LKCommand("livekit {world} render full -m", "livekit.commands.admin", true, this::cmdWorldRender, "Start full render on <world> and only render missing tiles");
    private LKCommand WORLD_RENDER_FULL_STOP = new LKCommand("livekit {world} render stop", "livekit.commands.admin", true, this::cmdWorldRenderStop, "Stop current rendering job");
    private LKCommand WORLD_RENDER_RADIUS = new LKCommand("livekit {world} render {radius}", "livekit.commands.admin", false, this::cmdWorldRenderRadius, "Renders a rectangular radius around the players position. (Worlds must match)");
    private LKCommand WORLD_RENDER_RADIUS_MISSING = new LKCommand("livekit {world} render {radius} -m", "livekit.commands.admin", false, this::cmdWorldRenderRadius, "Renders missing tiles in a rectangular radius around the players position. (Worlds must match)");

    private LKCommand WORLD_BOUNDS_INFO = new LKCommand("livekit {world} bounds", "livekit.commands.admin", true, this::cmdWorldBoundsInfo, "Displays bounds of <world>");
    private LKCommand WORLD_BOUNDS_RADIUS = new LKCommand("livekit {world} bounds {radius}", "livekit.commands.admin", true, this::cmdWorldBounds, "Creates rectangular Bound with radius <radius>");
    private LKCommand WORLD_BOUNDS_RADIUS_CIRCULAR = new LKCommand("livekit {world} bounds {radius} -c", "livekit.commands.admin", true, this::cmdWorldBounds, "Creates circular Bound with radius <radius>");
    private LKCommand WORLD_BOUNDS_LTRB = new LKCommand("livekit {world} bounds {radius(left)} {radius(top)} {radius(right)} {radius(bottom)}", "livekit.commands.admin", true, this::cmdWorldBoundsLTRB, "Set bounds for <world> in blocks");
    
    private LKCommand HEADREFRESH_OTHER = new LKCommand("livekit headrefresh {player}", "livekit.commands.admin", true, this::cmdHeadrefreshOther, "Refresh a players Head (only neccessary if player changed skin)");

    private LKCommand ADMIN_PERMRELOAD = new LKCommand("livekit permreload", "livekit.commands.admin", true, this::cmdPermreload, Plugin.isDebug());
    private LKCommand ADMIN_MODULES = new LKCommand("livekit modules", "livekit.commands.admin", true, this::cmdModules, Plugin.isDebug());
    private LKCommand ADMIN_MODULES_ENABLE = new LKCommand("livekit modules {module} enable", "livekit.commands.admin", true, this::cmdModulesToggle, Plugin.isDebug());
    private LKCommand ADMIN_MODULES_DISABLE = new LKCommand("livekit modules {module} disable", "livekit.commands.admin", true, this::cmdModulesToggle, Plugin.isDebug());
    private LKCommand ADMIN_TEXTUREPACK = new LKCommand("livekit tp", "livekit.commands.admin", true, this::cmdTexturepack, Plugin.isDebug());
    private LKCommand ADMIN_TEST = new LKCommand("livekit test", "livekit.commands.admin", false, this::cmdTest, Plugin.isDebug());

    private String prefix;
    private String prefixError;

    public LiveKitCommandExecutor() {
        this.prefix = Plugin.getPrefix();
        this.prefixError = Plugin.getPrefixError();

        CommandHandler.setPermissionCallback(new CommandHandlerPermissionCallback(){
            @Override
            public boolean hasPermission(CommandSender sender, String permission, boolean verbose) {
                return checkPerm(sender, permission, verbose);
            }
            @Override
            public boolean hasConsoleAccess(CommandSender sender, boolean consoleAccess, boolean verbose) {
                if(consoleAccess == false && sender instanceof ConsoleCommandSender) {
                    if(verbose) sender.sendMessage(Plugin.getPrefixError()+"Command can't be executed from console");
                    return false;
                }
                return true;
            }
            @Override
            public void unknownCommand(CommandSender sender, boolean verbose) {
                if(verbose) sender.sendMessage(Plugin.getPrefixError()+"Unknown command. Try /livekit help");
            }
            @Override
            public String formatHelpEntry(String command, String description) {
                return ChatColor.GREEN+command+ChatColor.RESET + " - " + description;
            }
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return CommandHandler.getAutoComplete(sender, alias, args);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MatchResult result = CommandHandler.match(sender, label, args);

        return true;
    }

    /**
     * LiveKit all commands overview
     * @param sender
     * @param cmd
     */
    private void cmdLiveKitHelp(CommandSender sender, LKCommand cmd) {
        sender.sendMessage(prefix+"Help");
        sender.sendMessage(CommandHandler.getHelp(sender));
    }

    /**
     * Displays LiveKit status (app store links, access )
     * @param sender
     * @param cmd
     */
    private void cmdLiveKit(CommandSender sender, LKCommand cmd) {
        sender.sendMessage(prefix+"LiveKit is supported!");
        sender.sendMessage("iOS App: "+ChatColor.AQUA+"https://bit.ly/livekitios"+ChatColor.RESET);
        sender.sendMessage("Android App: "+ChatColor.AQUA+"https://bit.ly/livekitandroid"+ChatColor.RESET);
        sender.sendMessage("Port: "+Config.getServerPort());
        sender.sendMessage("Password needed: "+friendlyBool((Config.getPassword()!=null)));
        sender.sendMessage("Supports anonymous: "+friendlyBool((Config.allowAnonymous())));
        sender.sendMessage("Has access: "+friendlyBool(checkPerm(sender, "livekit.commands.basic", false) && (sender instanceof Player)));
        if(checkPerm(sender, "livekit.commands.basic", false) && (sender instanceof Player)) sender.sendMessage("Use "+ChatColor.GREEN+"/livekit claim"+ChatColor.RESET+" to generate an access pin");
        sender.sendMessage("Use "+ChatColor.GREEN+"/livekit help"+ChatColor.RESET+" for more info");
    }

    /**
     * Generates claim pin for livekit app access
     * @param sender
     * @param cmd
     */
    private void cmdLiveKitClaim(CommandSender sender, LKCommand cmd) {
        Player player = (Player) sender;

        AuthenticationHandler.generatePin(player, new FutureSyncCallback<Pin>(){
            @Override
            public void onSyncResult(Pin result) {
                player.sendMessage(prefix+"Pin: "+result.getPin()+" (valid for 2 mins)");
            }
        }, Utils.errorHandler(sender));
    }

    /**
     * Displays session info for player
     * @param sender
     * @param cmd
     */
    private void cmdLiveKitInfo(CommandSender sender, LKCommand cmd) {
        Player player = (Player) sender;
        List<Identity> identities = LiveKit.getInstance().getConnectedClients(player.getUniqueId().toString());
        
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

    /**
     * Clears all active app sessions
     * @param sender
     * @param cmd
     */
    private void cmdLiveKitClearsessions(CommandSender sender, LKCommand cmd) {
        Player player = (Player) sender;

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

    /**
     * Request player skin refresh
     * @param sender
     * @param cmd
     */
    private void cmdHeadrefresh(CommandSender sender, LKCommand cmd) {
        Player player = (Player)sender;

        Long timestamp = HeadLibraryV2.refreshCooldown.get(player.getUniqueId());
        if(timestamp == null || (System.currentTimeMillis() > timestamp+5*60*1000) || checkPerm(sender, "livekit.commands.admin", false)) {
            HeadLibraryV2.get(player.getName(), true, true);
            HeadLibraryV2.refreshCooldown.put(player.getUniqueId(), System.currentTimeMillis());
            sender.sendMessage(prefix+"Your head is going to be refreshed!");
        } else {
            sender.sendMessage(prefixError+"You can refresh your head every 5 minutes!");
        }
    }

    /**
     * List Player pins
     * @param sender
     * @param cmd
     */
    private void cmdPinList(CommandSender sender, LKCommand cmd) {
        Player player = (Player)sender;

        BasicPlayerPinProvider.listPlayerPinsAsync(player, new FutureSyncCallback<List<PersonalPin>>(){
            @Override
            public void onSyncResult(List<PersonalPin> result) {
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
    }

    /**
     * Set player pin
     * @param sender
     * @param cmd
     */
    private void cmdPinSet(CommandSender sender, LKCommand cmd) {
        Player player = (Player)sender;

        final String finalName = cmd.get("message");
        BasicPlayerPinProvider.listPlayerPinsAsync(player, new FutureSyncCallback<List<PersonalPin>>(){
            @Override
            public void onSyncResult(List<PersonalPin> result) {
                if(result.size() < Config.getPersonalPinLimit()) {
                    
                    final PersonalPin waypoint = PersonalPin.create(player, LKLocation.fromLocation(player.getLocation()), finalName, "Custom set pin", BasicPlayerPinProvider.PLAYER_PIN_COLOR, false, Privacy.PRIVATE);
                    BasicPlayerPinProvider.setPlayerPinAsync(player, waypoint, new FutureSyncCallback<Void>(){
                        @Override
                        public void onSyncResult(Void result) {
                            player.sendMessage(Plugin.getPrefix()+"Pin "+ChatColor.AQUA+waypoint.getName()+ChatColor.RESET+" has been set!");
                            
                            Plugin.getInstance().getLiveKit().notifyPlayerInfoChange(player);
                        }
                    }, Utils.errorHandler(sender));

                } else {
                    player.sendMessage(prefixError+"You've reached your personal pin limit of "+Config.getPersonalPinLimit()+"! Remove a pin to set a new one.");
                }
            }
        }, Utils.errorHandler(sender));
    }

    /**
     * Remove player pin
     * @param sender
     * @param cmd
     */
    private void cmdPinRemove(CommandSender sender, LKCommand cmd) {
        int id = cmd.get("num");
        id--;

        Player player = (Player)sender;
        final int index = id;

        BasicPlayerPinProvider.listPlayerPinsAsync(player, new FutureSyncCallback<List<PersonalPin>>(){
            @Override
            public void onSyncResult(List<PersonalPin> result) {
                if(index >= result.size()) player.sendMessage(Plugin.getPrefixError()+"Wrong Pin ID! '/livekit pins' to list available pins");
                PersonalPin toRemove = result.get(index);

                BasicPlayerPinProvider.removePlayerPinAsync(player, toRemove, new FutureSyncCallback<Void>(){
                    @Override
                    public void onSyncResult(Void result) {
                        player.sendMessage(Plugin.getPrefix()+"Pin "+ChatColor.AQUA+toRemove.getName()+ChatColor.RESET+" has been removed!");
                        
                        Plugin.getInstance().getLiveKit().notifyPlayerInfoChange(player);
                    }
                }, Utils.errorHandler(sender));
            }
        }, Utils.errorHandler(sender));
    }

    /**
     * Displays livemap infos (enabled worlds, cpu time)
     * @param sender
     * @param cmd
     */
    private void cmdMapInfo(CommandSender sender, LKCommand cmd) {
        Set<String> worlds = Config.getLiveMapWorlds().keySet();
        sender.sendMessage(prefix+"Live Map info");
        sender.sendMessage("Worlds: ");
        for(String s : worlds) sender.sendMessage(" - "+s);
        sender.sendMessage("CPU-Time: "+RenderScheduler.getCPUTime()+"ms / "+(int)(((float)RenderScheduler.getCPUTime()/50f)*100f)+"%");
    }

    /**
     * Sets the cpu rendering time
     * @param sender
     * @param cmd
     */
    private void cmdMapSetCPU(CommandSender sender, LKCommand cmd) {
        int percent = cmd.get("num");

        if(percent < 5) percent = 5;
        if(percent > 100) percent = 100;

        RenderScheduler.setCPUTime((int)(percent*50f/100f));
        sender.sendMessage(prefix+"CPU-Time set to "+RenderScheduler.getCPUTime()+"ms / "+(int)(((float)RenderScheduler.getCPUTime()/50f)*100f)+"%");

        if(percent >= 80) {
            sender.sendMessage(prefix+"WARNING: Setting cpu time above 80% might cause severe lag!");
        }
    }

    /**
     * Prints Current world map infos
     * @param sender
     * @param cmd
     */
    private void cmdWorldInfo(CommandSender sender, LKCommand cmd) {
        World w = cmd.get("world");

        LiveMapModule map = (LiveMapModule)LiveKit.getInstance().getModuleManager().getModule("LiveMapModule:"+w.getName());
        if(map == null || !map.isEnabled()) { sender.sendMessage(Plugin.getPrefixError()+" LiveMapModule not enabled."); return; }
        RenderWorld world = map.getRenderWorld();

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
    }

    /**
     * Starts rendering of world
     * @param sender
     * @param cmd
     */
    private void cmdWorldRender(CommandSender sender, LKCommand cmd) {
        World w = cmd.get("world");
        boolean renderMissing = false; //(cmd == WORLD_RENDER_FULL_MISSING);

        LiveMapModule map = (LiveMapModule)LiveKit.getInstance().getModuleManager().getModule("LiveMapModule:"+w.getName());
        if(map == null || !map.isEnabled()) { sender.sendMessage(Plugin.getPrefixError()+" LiveMapModule not enabled."); return; }
        RenderWorld world = map.getRenderWorld();

        RenderJob job = RenderJob.fromBounds(world.getRenderBounds(), !renderMissing ? RenderJobMode.FORCED : RenderJobMode.MISSING);
        try{
            map.startRenderJob(job);
            sender.sendMessage(prefix+"Full render has been started for "+map.getWorldName()+" (mode: "+(!renderMissing ? RenderJobMode.FORCED : RenderJobMode.MISSING).name()+")");
        }catch(Exception ex) {
            sender.sendMessage(prefixError+ex.getMessage());
        }
    }

    /**
     * Render world around player radius
     * @param sender
     * @param cmd
     */
    private void cmdWorldRenderRadius(CommandSender sender, LKCommand cmd) {
        World w = cmd.get("world");
        int radius = cmd.get("radius");
        boolean forced = true; //(cmd != WORLD_RENDER_RADIUS_MISSING);

        LiveMapModule map = (LiveMapModule)LiveKit.getInstance().getModuleManager().getModule("LiveMapModule:"+w.getName());
        if(map == null || !map.isEnabled()) { sender.sendMessage(Plugin.getPrefixError()+" LiveMapModule not enabled."); return; }

        Player player = (Player)sender;
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
    }

    /**
     * Stops rendering of world
     * @param sender
     * @param cmd
     */
    private void cmdWorldRenderStop(CommandSender sender, LKCommand cmd) {
        World w = cmd.get("world");

        LiveMapModule map = (LiveMapModule)LiveKit.getInstance().getModuleManager().getModule("LiveMapModule:"+w.getName());
        if(map == null || !map.isEnabled()) { sender.sendMessage(Plugin.getPrefixError()+" LiveMapModule not enabled."); return; }

        map.stopRenderJob();
        sender.sendMessage(prefix+"Render job of "+map.getWorldName()+" has been stopped!");
    }

    /**
     * Displays render bounds of given world
     * @param sender
     * @param cmd
     */
    private void cmdWorldBoundsInfo(CommandSender sender, LKCommand cmd) {
        World w = cmd.get("world");

        LiveMapModule map = (LiveMapModule)LiveKit.getInstance().getModuleManager().getModule("LiveMapModule:"+w.getName());
        if(map == null || !map.isEnabled()) { sender.sendMessage(Plugin.getPrefixError()+" LiveMapModule not enabled."); return; }
        RenderWorld world = map.getRenderWorld();

        sender.sendMessage(prefix+"Bounds for "+world.getWorldName());
        sender.sendMessage(world.getRenderBounds().toString());
    }

    /**
     * Sets render bounds radius and optional circle
     * @param sender
     * @param cmd
     */
    private void cmdWorldBounds(CommandSender sender, LKCommand cmd) {
        World w = cmd.get("world");
        int radius = cmd.get("radius");
        boolean circular = (cmd == WORLD_BOUNDS_RADIUS_CIRCULAR);

        LiveMapModule map = (LiveMapModule)LiveKit.getInstance().getModuleManager().getModule("LiveMapModule:"+w.getName());
        if(map == null || !map.isEnabled()) { sender.sendMessage(Plugin.getPrefixError()+" LiveMapModule not enabled."); return; }

        RenderBounds bounds = circular ? new RenderBounds(radius) : new RenderBounds(-radius, -radius, radius, radius);
        if(bounds.valid()) {
            map.setRenderBounds(bounds);
            sender.sendMessage(prefix+"New render bounds set for "+map.getWorldName());
            sender.sendMessage(bounds.toString());
        } else {
            sender.sendMessage(prefixError+"Invalid radius specified. Make sure ist greater than 0.");
        }
    }

    /**
     * Sets render bounds left top right bottom
     * @param sender
     * @param cmd
     */
    private void cmdWorldBoundsLTRB(CommandSender sender, LKCommand cmd) {
        World w = cmd.get("world");
        int left = cmd.get("radius:1");
        int top = cmd.get("radius:2");
        int right = cmd.get("radius:3");
        int bottom = cmd.get("radius:4");

        LiveMapModule map = (LiveMapModule)LiveKit.getInstance().getModuleManager().getModule("LiveMapModule:"+w.getName());
        if(map == null || !map.isEnabled()) { sender.sendMessage(Plugin.getPrefixError()+" LiveMapModule not enabled."); return; }

        RenderBounds bounds = new RenderBounds(left, top, right, bottom);
        if(bounds.valid()) {
            map.setRenderBounds(bounds);
            sender.sendMessage(prefix+"New render bounds set for "+map.getWorldName());
            sender.sendMessage(bounds.toString());
        } else {
            sender.sendMessage(prefixError+"Invalid render bounds specified! make sure [right - left > 0] and [bottom - top > 0]");
        }
    }

    /**
     * Refreshes other players head
     * @param sender
     * @param cmd
     */
    private void cmdHeadrefreshOther(CommandSender sender, LKCommand cmd) {
        Player player = cmd.get("player");
        if(player != null) {

            HeadLibraryV2.get(player.getName(), true, true);
            sender.sendMessage(prefix+player.getName()+"'s head is going to be refreshed!");
        } else {
            sender.sendMessage(prefixError+"Player could not be found!");
        }
    }

    /**
     * Reload LiveKit permissions for all connected clients
     * @param sender
     * @param cmd
     */
    private void cmdPermreload(CommandSender sender, LKCommand cmd) {
        LiveKit.getInstance().commandReloadPermissions();
        sender.sendMessage(prefix+"Permissions will reload");
    }

    /**
     * List all registered livekit modules
     * @param sender
     * @param cmd
     */
    private void cmdModules(CommandSender sender, LKCommand cmd) {
        sender.sendMessage(prefix+"Modules:");
        for(BaseModule module : LiveKit.getInstance().getModules()) {
            sender.sendMessage(module.getType()+" Version: "+module.getVersion()+" Enabled: "+module.isEnabled());
        }
    }

    /**
     * Enable/Disable livekit modules
     * @param sender
     * @param cmd
     */
    private void cmdModulesToggle(CommandSender sender, LKCommand cmd) {
        BaseModule module = cmd.get("module");
        boolean enable = (cmd == ADMIN_MODULES_ENABLE);
        
        if(module != null) {
            if(enable) {
                LiveKit.getInstance().enableModule(module.getType());
            } else {
                LiveKit.getInstance().disableModule(module.getType());
            }
            LiveKit.getInstance().notifyQueue("SettingsModule");
        }
    }

    private void cmdTexturepack(CommandSender sender, LKCommand cmd) {
        try{
            Texturepack.generateTexturePack();
            Texturepack.generateBiomes();

            JSONArray array = new JSONArray();
            for(int i = 0; i < EntityType.values().length; i++) {
                array.put(EntityType.values()[i].name());
            }

            File file = new File( System.getProperty("user.dir") + "/plugins/LiveKit/entities.json" );
            if(!file.exists()) file.createNewFile();

            PrintWriter writer = new PrintWriter(file);
            writer.write(array.toString());
            writer.flush();
            writer.close();

        }catch(Exception ex){ex.printStackTrace();}
    }

    private void cmdTest(CommandSender sender, LKCommand cmd) {
        try {
            if(sender instanceof Player) {
                Player player = (Player)sender;
                Location location = player.getLocation();
                player.sendMessage("Block Type: "+location.getBlock().getType().name() + " " + Texturepack.getInstance().getTexture(location.getBlock().getType()));
                Location below = location.clone().add(0, -1, 0);
                player.sendMessage("Below Block Type: "+below.getBlock().getType().name()+ " " + Texturepack.getInstance().getTexture(location.getBlock().getType()));
            }
        }catch(Exception ex){ex.printStackTrace();}
    }
    
	private boolean checkPerm(CommandSender sender, String permission, boolean verbose) {
		if(sender.isOp()) return true;

		if(sender instanceof Player) {
			Player player = (Player)sender;
			boolean access = Permissions.has(player, permission);
			if(!access && verbose) player.sendMessage(Plugin.getPrefixError()+"You need "+permission+" permission to access this command!");
			return access;
		}
		return false;
	}

	private String friendlyBool(boolean bool) {
		if(bool) return ChatColor.GREEN+"Yes"+ChatColor.RESET;
		else return ChatColor.RED+"No"+ChatColor.RESET;
	}
}
