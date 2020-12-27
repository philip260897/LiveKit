package at.livekit.plugin;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Chunk;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.SpawnChangeEvent;
import org.bukkit.event.world.StructureGrowEvent;

import at.livekit.livekit.LiveKit;
import at.livekit.main.LiveMap;

import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;

public class WorldListener implements Listener 
{
    //BLOCK EVENTS

    /*@EventHandler
    public void onBlockPlaceEvent(BlockPlaceEvent event) {
        LiveMap livemap = LiveKit.getLiveMap(event.getBlock().getWorld().getName());
        if(livemap == null) return;

        if(event.getBlock().getY() == event.getBlock().getWorld().getHighestBlockAt(event.getBlock().getX(), event.getBlock().getZ()).getY()) {
            livemap.queue(event.getBlock());
        }
    }

    @EventHandler
    public void onBlockBreakEvent(BlockBreakEvent event) {
        LiveMap livemap = LiveKit.getLiveMap(event.getBlock().getWorld().getName());
        if(livemap == null) return;

        if(event.getBlock().getY() == event.getBlock().getWorld().getHighestBlockAt(event.getBlock().getX(), event.getBlock().getZ()).getY()) {
            livemap.queue(event.getBlock());
        }        
    }

    @EventHandler
    public void onBlockFormEvent(BlockFormEvent event) {
        LiveMap livemap = LiveKit.getLiveMap(event.getBlock().getWorld().getName());
        if(livemap == null) return;

        if(event.getBlock().getY() == event.getBlock().getWorld().getHighestBlockAt(event.getBlock().getX(), event.getBlock().getZ()).getY()) {
            livemap.queue(event.getBlock());
        }     
    }

    @EventHandler
    public void onBlockGrowEvent(BlockGrowEvent event) {
        LiveMap livemap = LiveKit.getLiveMap(event.getBlock().getWorld().getName());
        if(livemap == null) return;

        if(event.getBlock().getY() == event.getBlock().getWorld().getHighestBlockAt(event.getBlock().getX(), event.getBlock().getZ()).getY()) {
            livemap.queue(event.getBlock());
        }     
    }

    @EventHandler
    public void onBlockSpreadEvent(BlockSpreadEvent event) {
        LiveMap livemap = LiveKit.getLiveMap(event.getBlock().getWorld().getName());
        if(livemap == null) return;

        if(event.getBlock().getY() == event.getBlock().getWorld().getHighestBlockAt(event.getBlock().getX(), event.getBlock().getZ()).getY()) {
            livemap.queue(event.getBlock());
        }    
    }

    @EventHandler
    public void onBlockExplodeEvent(BlockExplodeEvent event) {
        LiveMap livemap = LiveKit.getLiveMap(event.getBlock().getWorld().getName());
        if(livemap == null) return;

        if(event.getBlock().getY() == event.getBlock().getWorld().getHighestBlockAt(event.getBlock().getX(), event.getBlock().getZ()).getY()) {
            livemap.queue(event.getBlock());
        }    
    }

    @EventHandler
    public void onBlockFadeEvent(BlockFadeEvent event) {
        LiveMap livemap = LiveKit.getLiveMap(event.getBlock().getWorld().getName());
        if(livemap == null) return;
    
        if(event.getBlock().getY() == event.getBlock().getWorld().getHighestBlockAt(event.getBlock().getX(), event.getBlock().getZ()).getY()) {
            livemap.queue(event.getBlock());
        }    
    }


    // WORLD EVENTS

    @EventHandler
    private void onChunkPopulateEvent(ChunkPopulateEvent event) {
        LiveMap livemap = LiveKit.getLiveMap(event.getWorld().getName());
        if(livemap == null) return;

        livemap.queue(event.getChunk());
    }

    @EventHandler
    private void onChunkLoadEvent(ChunkLoadEvent event) {
        LiveMap livemap = LiveKit.getLiveMap(event.getWorld().getName());
        if(livemap == null) return;

        if(!livemap.loadedChunkExists(event.getChunk())) {
            livemap.queue(event.getChunk());
        }
    }

    @EventHandler
    private void onSpawnChangeEvent(SpawnChangeEvent event) {
        //todo: handle spawn syncable
    }

    @EventHandler
    private void onStructureGrowEvent(StructureGrowEvent event) {
        LiveMap livemap = LiveKit.getLiveMap(event.getWorld().getName());
        if(livemap == null) return;

        List<Chunk> chunks = new ArrayList<Chunk>();
        for(BlockState bd : event.getBlocks()) {
            if(!chunks.contains(bd.getChunk())) {
                chunks.add(bd.getChunk());
                System.out.println("Something has grown: Queing chunk "+bd.getChunk().getX() + " " + bd.getChunk().getZ());
                livemap.queue(bd.getChunk());
            }
        }
    }*/
}