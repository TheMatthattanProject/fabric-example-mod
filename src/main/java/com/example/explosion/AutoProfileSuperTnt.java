package com.example.explosion;

import com.example.ExampleMod;
import com.example.mixin.TntEntityAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.TntEntity;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.GameMode;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class AutoProfileSuperTnt {
    private static final String ENABLED_PROPERTY = "modid.autoProfileSuperTnt";
    private static final String POWER_PROPERTY = "modid.autoProfileSuperTnt.power";
    private static final String START_DELAY_TICKS_PROPERTY = "modid.autoProfileSuperTnt.delayTicks";
    private static final String FUSE_TICKS_PROPERTY = "modid.autoProfileSuperTnt.fuseTicks";
    private static final String STOP_SERVER_PROPERTY = "modid.autoProfileSuperTnt.stopServer";
    private static final String MAX_TICKS_PROPERTY = "modid.autoProfileSuperTnt.maxTicks";
    private static final String STOP_DELAY_TICKS_PROPERTY = "modid.autoProfileSuperTnt.stopDelayTicks";

    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "false"));
    private static final float POWER = parseFloatProperty(POWER_PROPERTY, 128.0F);
    private static final int START_DELAY_TICKS = parseIntProperty(START_DELAY_TICKS_PROPERTY, 40);
    private static final int FUSE_TICKS = parseIntProperty(FUSE_TICKS_PROPERTY, 1);
    private static final boolean STOP_SERVER = Boolean.parseBoolean(System.getProperty(STOP_SERVER_PROPERTY, "true"));
    private static final int MAX_TICKS = parseIntProperty(MAX_TICKS_PROPERTY, 20 * 60 * 5);
    private static final int STOP_DELAY_TICKS = parseIntProperty(STOP_DELAY_TICKS_PROPERTY, 20 * 60);

    private static final Map<MinecraftServer, State> STATE_BY_SERVER = new IdentityHashMap<>();
    private static boolean registered = false;

    private AutoProfileSuperTnt() {
    }

    static void init() {
        if (registered) {
            return;
        }
        registered = true;

        if (!ENABLED) {
            return;
        }

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            STATE_BY_SERVER.put(server, new State());
            ExampleMod.LOGGER.info(
                    "[AutoProfileSuperTnt] Enabled: power={} delayTicks={} fuseTicks={} stopServer={} maxTicks={} stopDelayTicks={}",
                    POWER,
                    START_DELAY_TICKS,
                    FUSE_TICKS,
                    STOP_SERVER,
                    MAX_TICKS,
                    STOP_DELAY_TICKS
            );

            ServerWorld world = server.getOverworld();
            if (world != null) {
                ExampleMod.LOGGER.info(
                        "[AutoProfileSuperTnt] Loaded world: seed={} path={}",
                        world.getSeed(),
                        server.getSavePath(WorldSavePath.ROOT).toAbsolutePath()
                );
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(STATE_BY_SERVER::remove);
        ServerTickEvents.END_SERVER_TICK.register(AutoProfileSuperTnt::tick);
    }

    private static void tick(MinecraftServer server) {
        State state = STATE_BY_SERVER.get(server);
        if (state == null) {
            return;
        }

        state.ticks++;

        handlePlayerJoins(server, state);

        if (!state.explosionTriggered && state.ticks >= START_DELAY_TICKS) {
            if (triggerExplosionAtSpawn(server, state)) {
                state.explosionTriggered = true;
                ExampleMod.LOGGER.info("[AutoProfileSuperTnt] Super TNT spawned at {}", formatPos(state.tntPos));
            } else if (state.ticks >= START_DELAY_TICKS + 200) {
                ExampleMod.LOGGER.warn("[AutoProfileSuperTnt] Failed to spawn Super TNT at spawn; stopping server");
                server.stop(false);
            }
        }

        if (state.explosionTriggered) {
            state.postExplosionTicks++;
        }

        if (state.explosionTriggered && ExplosionCarver.hasPendingTasks(server)) {
            state.sawCarverTasks = true;
        }

        if (STOP_SERVER && state.stopAtTick < 0) {
            if (state.explosionTriggered && state.sawCarverTasks && !ExplosionCarver.hasPendingTasks(server)) {
                scheduleStop(server, state, "carving finished");
            } else if (state.ticks >= MAX_TICKS) {
                scheduleStop(server, state, "maxTicks reached (" + MAX_TICKS + ")");
            }
        }

        if (STOP_SERVER && state.stopAtTick >= 0 && state.ticks >= state.stopAtTick) {
            ExampleMod.LOGGER.info("[AutoProfileSuperTnt] Stopping server ({})", state.stopReason);
            server.stop(false);
        }
    }

    private static void handlePlayerJoins(MinecraftServer server, State state) {
        PlayerManager playerManager = server.getPlayerManager();
        for (ServerPlayerEntity player : playerManager.getPlayerList()) {
            UUID uuid = player.getUuid();
            if (!state.seenPlayers.add(uuid)) {
                continue;
            }

            state.playerEverJoined = true;

            player.changeGameMode(GameMode.CREATIVE);

            PlayerConfigEntry entry = new PlayerConfigEntry(player.getGameProfile());
            if (!playerManager.isOperator(entry)) {
                playerManager.addToOperators(entry);
            }
            playerManager.sendCommandTree(player);

            ExampleMod.LOGGER.info("[AutoProfileSuperTnt] Player joined: {} (set creative + op)", player.getName().getString());

            if (STOP_SERVER && state.stopAtTick >= 0) {
                int newStopAtTick = Math.max(state.stopAtTick, state.ticks + STOP_DELAY_TICKS);
                if (newStopAtTick != state.stopAtTick) {
                    state.stopAtTick = newStopAtTick;
                    ExampleMod.LOGGER.info("[AutoProfileSuperTnt] Stop delayed by {} ticks due to player join", STOP_DELAY_TICKS);
                }
            }
        }
    }

    private static void scheduleStop(MinecraftServer server, State state, String reason) {
        int delay = (state.playerEverJoined || server.getPlayerManager().getCurrentPlayerCount() > 0) ? STOP_DELAY_TICKS : 1;
        state.stopAtTick = state.ticks + delay;
        state.stopReason = reason;
        if (delay > 1) {
            ExampleMod.LOGGER.info("[AutoProfileSuperTnt] {} — scheduling stop in {} ticks", reason, delay);
        }
    }

    private static boolean triggerExplosionAtSpawn(MinecraftServer server, State state) {
        ServerWorld world = server.getOverworld();
        if (world == null) {
            return false;
        }

        BlockPos spawn = world.getSpawnPoint().getPos();
        world.getChunk(spawn.getX() >> 4, spawn.getZ() >> 4, ChunkStatus.FULL, true);

        BlockPos surface = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, spawn);
        BlockPos tntPos = surface.up();

        if (!world.isInBuildLimit(tntPos)) {
            return false;
        }

        TntEntity tntEntity = new TntEntity(
                world,
                tntPos.getX() + 0.5D,
                tntPos.getY(),
                tntPos.getZ() + 0.5D,
                null
        );
        ((TntEntityAccessor) tntEntity).modid$setExplosionPower(POWER);
        tntEntity.setFuse(FUSE_TICKS);
        world.spawnEntity(tntEntity);

        state.tntPos = tntPos;
        return true;
    }

    private static String formatPos(BlockPos pos) {
        if (pos == null) {
            return "unknown";
        }
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static int parseIntProperty(String key, int defaultValue) {
        String raw = System.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            ExampleMod.LOGGER.warn("[AutoProfileSuperTnt] Invalid {}='{}' (expected int); defaulting to {}", key, raw, defaultValue);
            return defaultValue;
        }
    }

    private static float parseFloatProperty(String key, float defaultValue) {
        String raw = System.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException ignored) {
            ExampleMod.LOGGER.warn("[AutoProfileSuperTnt] Invalid {}='{}' (expected float); defaulting to {}", key, raw, defaultValue);
            return defaultValue;
        }
    }

    private static final class State {
        int ticks;
        boolean explosionTriggered;
        int postExplosionTicks;
        boolean sawCarverTasks;
        int stopAtTick = -1;
        String stopReason = "auto stop";
        boolean playerEverJoined;
        BlockPos tntPos;

        final Set<UUID> seenPlayers = new HashSet<>();
    }
}
