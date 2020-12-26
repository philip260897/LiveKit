package at.livekit.plugin;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import at.livekit.livekit.LiveKit;
import at.livekit.main.LiveEntity;
import at.livekit.main.LiveMap;
import at.livekit.utils.HeadLibrary;

import org.bukkit.event.player.PlayerMoveEvent;


import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;


public class PlayerListener implements Listener
{
	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) 
	{
		Player player = event.getPlayer();

		LiveMap livemap = LiveKit.getLiveMap(player.getWorld().getName());
		if(livemap != null) {
			LiveEntity entity = new LiveEntity(player.getUniqueId().toString(), player.getDisplayName(), null);
			entity.updateLocation(player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ());
			entity.updateHealth(player.getHealthScale());
			entity.updateExhaustion(player.getExhaustion());

			if(!HeadLibrary.has(player.getUniqueId().toString())) { 
				HeadLibrary.resolveAsync(player.getUniqueId().toString());
			} 
			entity.updateHead(HeadLibrary.get(player.getUniqueId().toString()));

			livemap.registerSyncable(entity);
		}
	}
	
	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) 
	{
		LiveMap livemap = LiveKit.getLiveMap(event.getPlayer().getWorld().getName());
		if(livemap != null) {
			livemap.removeSyncable(event.getPlayer().getUniqueId().toString());
		}
	}

	@EventHandler
	public void onPlayerMove(PlayerMoveEvent event) {
		Player player = event.getPlayer();

		LiveMap livemap = LiveKit.getLiveMap(player.getWorld().getName());
		if(livemap != null) {
			LiveEntity entity = (LiveEntity) livemap.getSyncable(player.getUniqueId().toString());
			if(entity != null) {
				entity.updateLocation(player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ());
			}
		}
	}

}
