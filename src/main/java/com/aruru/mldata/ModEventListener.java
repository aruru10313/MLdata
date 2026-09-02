package com.aruru.mldata;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ModEventListener {
    private final MySQLManager mySQLManager;
    private final ConcurrentHashMap<UUID, Long> lastMovementTimes = new ConcurrentHashMap<>();
    private long lastStatsTime = 0;

    public ModEventListener(MySQLManager mySQLManager) {
        this.mySQLManager = mySQLManager;
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        long now = System.currentTimeMillis();
        if (now - lastStatsTime >= 10000 && event.getServer() != null) { // Every 10 seconds
            lastStatsTime = now;
            MinecraftServer server = event.getServer();
            double mspt = server.getAverageTickTimeNanos() / 1000000.0;
            double tps = Math.min(20.0, 1000.0 / mspt);
            long ramMax = Runtime.getRuntime().maxMemory();
            long ramUsed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

            Map<String, Object> data = new HashMap<>();
            data.put("tps", tps);
            data.put("mspt", mspt);
            data.put("ram_used", ramUsed);
            data.put("ram_max", ramMax);
            data.put("timestamp", now);
            if (mySQLManager != null) mySQLManager.logStats(data);
        }
    }


    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        long now = System.currentTimeMillis();
        UUID uuid = player.getUUID();
        long lastTime = lastMovementTimes.getOrDefault(uuid, 0L);

        if (now - lastTime >= 2000) {
            lastMovementTimes.put(uuid, now);
            Map<String, Object> data = new HashMap<>();
            data.put("player_name", player.getName().getString());
            data.put("player_uuid", uuid.toString());
            data.put("world", player.level().dimension().location().toString());
            data.put("x", player.getX());
            data.put("y", player.getY());
            data.put("z", player.getZ());
            data.put("yaw", player.getYRot());
            data.put("pitch", player.getXRot());
            data.put("timestamp", now);
            if (mySQLManager != null) mySQLManager.logMovement(data);
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide() || event.getPlayer() == null) return;
        Map<String, Object> data = new HashMap<>();
        data.put("action", "BREAK");
        data.put("player_name", event.getPlayer().getName().getString());
        data.put("block_type", event.getState().getBlock().getDescriptionId());
        data.put("world", event.getLevel() instanceof Level lvl ? lvl.dimension().location().toString() : "unknown");
        data.put("x", event.getPos().getX());
        data.put("y", event.getPos().getY());
        data.put("z", event.getPos().getZ());
        data.put("timestamp", System.currentTimeMillis());
        if (mySQLManager != null) mySQLManager.logBlock(data);
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Player player)) return;
        Map<String, Object> data = new HashMap<>();
        data.put("action", "PLACE");
        data.put("player_name", player.getName().getString());
        data.put("block_type", event.getState().getBlock().getDescriptionId());
        data.put("world", event.getLevel() instanceof Level lvl ? lvl.dimension().location().toString() : "unknown");
        data.put("x", event.getPos().getX());
        data.put("y", event.getPos().getY());
        data.put("z", event.getPos().getZ());
        data.put("timestamp", System.currentTimeMillis());
        if (mySQLManager != null) mySQLManager.logBlock(data);
    }

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        String message = event.getMessage().getString();
        Map<String, Object> data = new HashMap<>();
        data.put("player_name", event.getPlayer().getName().getString());
        data.put("is_command", message.startsWith("/") ? 1 : 0);
        data.put("message", message);
        data.put("world", event.getPlayer().level().dimension().location().toString());
        data.put("timestamp", System.currentTimeMillis());
        if (mySQLManager != null) mySQLManager.logChat(data);
    }

    @SubscribeEvent
    public void onDamage(LivingDamageEvent.Post event) {
        if (event.getEntity().level().isClientSide()) return;
        Entity victim = event.getEntity();
        Entity attacker = event.getSource().getEntity();
        if (!(victim instanceof Player) && !(attacker instanceof Player)) return;

        Map<String, Object> data = new HashMap<>();
        data.put("attacker_name", attacker != null ? attacker.getName().getString() : "Environment");
        data.put("victim_name", victim.getName().getString());
        data.put("damage", event.getNewDamage());
        data.put("cause", event.getSource().getMsgId());
        data.put("world", victim.level().dimension().location().toString());
        data.put("x", victim.getX());
        data.put("y", victim.getY());
        data.put("z", victim.getZ());
        data.put("timestamp", System.currentTimeMillis());
        if (mySQLManager != null) mySQLManager.logDamage(data);
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof Player player)) return;

        Map<String, Object> data = new HashMap<>();
        data.put("player_name", player.getName().getString());
        data.put("cause", event.getSource().getMsgId());
        data.put("death_message", event.getSource().getLocalizedDeathMessage(player).getString());
        data.put("world", player.level().dimension().location().toString());
        data.put("x", player.getX());
        data.put("y", player.getY());
        data.put("z", player.getZ());
        data.put("timestamp", System.currentTimeMillis());
        if (mySQLManager != null) mySQLManager.logDeath(data);
    }

    @SubscribeEvent
    public void onInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        Map<String, Object> data = new HashMap<>();
        data.put("player_name", event.getEntity().getName().getString());
        data.put("action", "RIGHT_CLICK_BLOCK");
        data.put("target_name", event.getLevel().getBlockState(event.getPos()).getBlock().getDescriptionId());
        data.put("target_type", "BLOCK");
        data.put("world", event.getLevel().dimension().location().toString());
        data.put("x", event.getPos().getX());
        data.put("y", event.getPos().getY());
        data.put("z", event.getPos().getZ());
        data.put("timestamp", System.currentTimeMillis());
        if (mySQLManager != null) mySQLManager.logInteraction(data);
    }

    @SubscribeEvent
    public void onItemDrop(ItemTossEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        Map<String, Object> data = new HashMap<>();
        data.put("player_name", event.getPlayer().getName().getString());
        data.put("action", "DROP");
        data.put("item_type", event.getEntity().getItem().getDescriptionId());
        data.put("amount", event.getEntity().getItem().getCount());
        data.put("world", event.getPlayer().level().dimension().location().toString());
        data.put("x", event.getPlayer().getX());
        data.put("y", event.getPlayer().getY());
        data.put("z", event.getPlayer().getZ());
        data.put("timestamp", System.currentTimeMillis());
        if (mySQLManager != null) mySQLManager.logItem(data);
    }

    @SubscribeEvent
    public void onItemPickup(ItemEntityPickupEvent.Post event) {
        if (event.getPlayer().level().isClientSide()) return;
        Map<String, Object> data = new HashMap<>();
        data.put("player_name", event.getPlayer().getName().getString());
        data.put("action", "PICKUP");
        data.put("item_type", event.getItemEntity().getItem().getDescriptionId());
        data.put("amount", event.getItemEntity().getItem().getCount());
        data.put("world", event.getPlayer().level().dimension().location().toString());
        data.put("x", event.getPlayer().getX());
        data.put("y", event.getPlayer().getY());
        data.put("z", event.getPlayer().getZ());
        data.put("timestamp", System.currentTimeMillis());
        if (mySQLManager != null) mySQLManager.logItem(data);
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        Map<String, Object> data = new HashMap<>();
        data.put("player_name", event.getEntity().getName().getString());
        data.put("action", "JOIN");
        data.put("ip_address", event.getEntity().getServer().getPlayerList().getPlayer(event.getEntity().getUUID()).connection.getRemoteAddress().toString());
        data.put("timestamp", System.currentTimeMillis());
        if (mySQLManager != null) mySQLManager.logSession(data);
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        Map<String, Object> data = new HashMap<>();
        data.put("player_name", event.getEntity().getName().getString());
        data.put("action", "LEAVE");
        data.put("ip_address", "unknown");
        data.put("timestamp", System.currentTimeMillis());
        if (mySQLManager != null) mySQLManager.logSession(data);
    }
}
