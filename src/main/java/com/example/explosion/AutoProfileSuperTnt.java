package com.example.explosion;

import com.example.ExampleMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.GameMode;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class AutoProfileSuperTnt {
    private static final String ENABLED_PROPERTY = "modid.autoProfileSuperTnt";
    private static final String POWER_PROPERTY = "modid.autoProfileSuperTnt.power";
    private static final String START_DELAY_TICKS_PROPERTY = "modid.autoProfileSuperTnt.delayTicks";
    private static final String STOP_SERVER_PROPERTY = "modid.autoProfileSuperTnt.stopServer";
    private static final String MAX_TICKS_PROPERTY = "modid.autoProfileSuperTnt.maxTicks";
    private static final String STOP_DELAY_TICKS_PROPERTY = "modid.autoProfileSuperTnt.stopDelayTicks";
    private static final String DELETE_WORLD_ON_STOP_PROPERTY = "modid.autoProfileSuperTnt.deleteWorldOnStop";

    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "false"));
    private static final float POWER = parseFloatProperty(POWER_PROPERTY, 128.0F);
    private static final int START_DELAY_TICKS = parseIntProperty(START_DELAY_TICKS_PROPERTY, 40);
    private static final boolean STOP_SERVER = Boolean.parseBoolean(System.getProperty(STOP_SERVER_PROPERTY, "true"));
    private static final int MAX_TICKS = parseIntProperty(MAX_TICKS_PROPERTY, 20 * 60 * 5);
    private static final int STOP_DELAY_TICKS = parseIntProperty(STOP_DELAY_TICKS_PROPERTY, 20 * 60);
    private static final boolean DELETE_WORLD_ON_STOP = Boolean.parseBoolean(System.getProperty(DELETE_WORLD_ON_STOP_PROPERTY, "true"));

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
            State state = new State();
            state.worldRootPath = normalizePath(server.getSavePath(WorldSavePath.ROOT));
            STATE_BY_SERVER.put(server, state);
            ExampleMod.LOGGER.info(
                    "[AutoProfileSuperTnt] Enabled: power={} delayTicks={} stopServer={} maxTicks={} stopDelayTicks={}",
                    POWER,
                    START_DELAY_TICKS,
                    STOP_SERVER,
                    MAX_TICKS,
                    STOP_DELAY_TICKS
            );

            logWorldDirectoryTimes(state.worldRootPath);

            ServerWorld world = server.getOverworld();
            if (world != null) {
                ExampleMod.LOGGER.info(
                        "[AutoProfileSuperTnt] Loaded world: seed={} path={}",
                        world.getSeed(),
                        server.getSavePath(WorldSavePath.ROOT).toAbsolutePath()
                );
            }
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            State state = STATE_BY_SERVER.remove(server);
            if (state != null) {
                deleteWorldIfEnabled(state);
            }
        });
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
                ExampleMod.LOGGER.info("[AutoProfileSuperTnt] Super TNT explosion triggered at {}", formatPos(state.tntPos));
            } else if (state.ticks >= START_DELAY_TICKS + 200) {
                ExampleMod.LOGGER.warn("[AutoProfileSuperTnt] Failed to trigger Super TNT explosion at spawn; stopping server");
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

    private static void deleteWorldIfEnabled(State state) {
        if (!DELETE_WORLD_ON_STOP) {
            return;
        }

        Path worldRootPath = state.worldRootPath;
        if (worldRootPath == null) {
            return;
        }

        Path gameDir = normalizePath(FabricLoader.getInstance().getGameDir());
        if (!worldRootPath.startsWith(gameDir) || worldRootPath.equals(gameDir)) {
            ExampleMod.LOGGER.warn("[AutoProfileSuperTnt] Refusing to delete world outside run dir: world={} runDir={}", worldRootPath, gameDir);
            return;
        }

        try {
            deleteDirectoryRecursively(worldRootPath);
            ExampleMod.LOGGER.info("[AutoProfileSuperTnt] Deleted world directory: {}", worldRootPath);
        } catch (Exception e) {
            ExampleMod.LOGGER.warn("[AutoProfileSuperTnt] Failed to delete world directory: {}", worldRootPath, e);
        }
    }

    private static void deleteDirectoryRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }

        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static Path normalizePath(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private static void logWorldDirectoryTimes(Path worldRootPath) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(worldRootPath, BasicFileAttributes.class);
            ExampleMod.LOGGER.info(
                    "[AutoProfileSuperTnt] World dir times: created={} modified={}",
                    attrs.creationTime(),
                    attrs.lastModifiedTime()
            );
        } catch (Exception e) {
            ExampleMod.LOGGER.debug("[AutoProfileSuperTnt] Failed to read world dir attributes: {}", worldRootPath, e);
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

        int maxPropagationBlocks = MathHelper.ceil(POWER * 0.78F) + 4;
        int chunkRadius = MathHelper.ceil(maxPropagationBlocks / 16.0F);
        forceLoadChunksAround(world, tntPos, chunkRadius);
        preloadChunksAround(world, tntPos, chunkRadius);

        world.createExplosion(
                null,
                tntPos.getX() + 0.5D,
                tntPos.getY(),
                tntPos.getZ() + 0.5D,
                POWER,
                false,
                World.ExplosionSourceType.TNT
        );

        state.tntPos = tntPos;
        return true;
    }

    private static void forceLoadChunksAround(ServerWorld world, BlockPos center, int chunkRadius) {
        int chunkX = center.getX() >> 4;
        int chunkZ = center.getZ() >> 4;
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                world.setChunkForced(chunkX + dx, chunkZ + dz, true);
            }
        }
    }

    private static void preloadChunksAround(ServerWorld world, BlockPos center, int chunkRadius) {
        int chunkX = center.getX() >> 4;
        int chunkZ = center.getZ() >> 4;
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                world.getChunk(chunkX + dx, chunkZ + dz, ChunkStatus.FULL, true);
            }
        }
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

        Path worldRootPath;
    }
}
