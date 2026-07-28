package com.aruru.mldata;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EventListener implements Listener {
    private final DataCollector dataCollector;
    private final long movementThrottleMs;
    
    // Memory map to throttle high-frequency movement events per player
    private final ConcurrentHashMap<UUID, Long> lastMovementTimes = new ConcurrentHashMap<>();
    
    public EventListener(DataCollector dataCollector, long movementThrottleMs) {
        this.dataCollector = dataCollector;
        this.movementThrottleMs = movementThrottleMs;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        long lastTime = lastMovementTimes.getOrDefault(player.getUniqueId(), 0L);
        
        // Throttle movement records
        if (now - lastTime >= movementThrottleMs) {
            lastMovementTimes.put(player.getUniqueId(), now);
            
            Map<String, Object> data = new HashMap<>();
            data.put("player_name", player.getName());
            data.put("player_uuid", player.getUniqueId().toString());
            data.put("world", player.getWorld().getName());
            data.put("x", player.getLocation().getX());
            data.put("y", player.getLocation().getY());
            data.put("z", player.getLocation().getZ());
            data.put("yaw", player.getLocation().getYaw());
            data.put("pitch", player.getLocation().getPitch());
            data.put("timestamp", now);
            
            dataCollector.logMovement(data);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        logBlockEvent("BREAK", event.getPlayer(), event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        logBlockEvent("PLACE", event.getPlayer(), event.getBlock());
    }
    
    private void logBlockEvent(String action, Player player, Block block) {
        Map<String, Object> data = new HashMap<>();
        data.put("action", action);
        data.put("player_name", player.getName());
        data.put("block_type", block.getType().name());
        data.put("world", block.getWorld().getName());
        data.put("x", block.getX());
        data.put("y", block.getY());
        data.put("z", block.getZ());
        data.put("timestamp", System.currentTimeMillis());
        
        dataCollector.logBlock(data);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        
        Map<String, Object> data = new HashMap<>();
        data.put("player_name", player.getName());
        data.put("death_message", event.getDeathMessage());
        
        if (player.getLastDamageCause() != null) {
            data.put("cause", player.getLastDamageCause().getCause().name());
        } else {
            data.put("cause", "UNKNOWN");
        }
        
        data.put("world", player.getWorld().getName());
        data.put("x", player.getLocation().getX());
        data.put("y", player.getLocation().getY());
        data.put("z", player.getLocation().getZ());
        data.put("timestamp", System.currentTimeMillis());
        
        dataCollector.logDeath(data);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRedstone(BlockRedstoneEvent event) {
        // Redstone loops can generate thousands of events per second.
        // We only log if the current actually changes state.
        if (event.getOldCurrent() != event.getNewCurrent()) {
            Map<String, Object> data = new HashMap<>();
            data.put("block_type", event.getBlock().getType().name());
            data.put("world", event.getBlock().getWorld().getName());
            data.put("x", event.getBlock().getX());
            data.put("y", event.getBlock().getY());
            data.put("z", event.getBlock().getZ());
            data.put("old_current", event.getOldCurrent());
            data.put("new_current", event.getNewCurrent());
            data.put("timestamp", System.currentTimeMillis());
            
            dataCollector.logRedstone(data);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity target = event.getRightClicked();
        
        Map<String, Object> data = new HashMap<>();
        data.put("player_name", player.getName());
        data.put("target_type", target.getType().name());
        data.put("target_name", target.getName());
        data.put("action", "INTERACT");
        data.put("world", player.getWorld().getName());
        data.put("x", player.getLocation().getX());
        data.put("y", player.getLocation().getY());
        data.put("z", player.getLocation().getZ());
        data.put("timestamp", System.currentTimeMillis());
        
        dataCollector.logInteraction(data);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        Entity victim = event.getEntity();
        
        if (!(damager instanceof Player) && !(victim instanceof Player)) return;
        
        Map<String, Object> data = new HashMap<>();
        data.put("attacker_name", damager.getName());
        data.put("victim_name", victim.getName());
        data.put("damage", event.getFinalDamage());
        data.put("cause", event.getCause().name());
        data.put("world", victim.getWorld().getName());
        data.put("x", victim.getLocation().getX());
        data.put("y", victim.getLocation().getY());
        data.put("z", victim.getLocation().getZ());
        data.put("timestamp", System.currentTimeMillis());
        
        dataCollector.logDamage(data);
    }
}
