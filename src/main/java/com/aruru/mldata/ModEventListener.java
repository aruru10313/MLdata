package com.aruru.mldata;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ModEventListener {
    private final MySQLManager mySQLManager;
    private final ConcurrentHashMap<UUID, Long> lastMovementTimes = new ConcurrentHashMap<>();

    public ModEventListener(MySQLManager mySQLManager) {
        this.mySQLManager = mySQLManager;
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        net.minecraft.world.entity.player.Player player = event.getEntity();
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

            if (mySQLManager != null) {
                mySQLManager.logMovement(data);
            }
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getPlayer() == null) return;

        String worldName = event.getLevel() instanceof Level lvl ? lvl.dimension().location().toString() : "unknown";

        Map<String, Object> data = new HashMap<>();
        data.put("action", "BREAK");
        data.put("player_name", event.getPlayer().getName().getString());
        data.put("block_type", event.getState().getBlock().getDescriptionId());
        data.put("world", worldName);
        data.put("x", event.getPos().getX());
        data.put("y", event.getPos().getY());
        data.put("z", event.getPos().getZ());
        data.put("timestamp", System.currentTimeMillis());

        if (mySQLManager != null) {
            mySQLManager.logBlock(data);
        }
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof net.minecraft.world.entity.player.Player player)) return;

        String worldName = event.getLevel() instanceof Level lvl ? lvl.dimension().location().toString() : "unknown";

        Map<String, Object> data = new HashMap<>();
        data.put("action", "PLACE");
        data.put("player_name", player.getName().getString());
        data.put("block_type", event.getState().getBlock().getDescriptionId());
        data.put("world", worldName);
        data.put("x", event.getPos().getX());
        data.put("y", event.getPos().getY());
        data.put("z", event.getPos().getZ());
        data.put("timestamp", System.currentTimeMillis());

        if (mySQLManager != null) {
            mySQLManager.logBlock(data);
        }
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

        if (mySQLManager != null) {
            mySQLManager.logChat(data);
        }
    }
}
