package com.example.explosion;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.explosion.ExplosionImpl;

import java.util.function.BiPredicate;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

public final class ExplosionCarver {
    public static final float HIGH_POWER_THRESHOLD = 48.0F;

    private static final int MAX_TOTAL_NODE_EXPANSIONS_PER_TICK = 40_000;
    private static final int MAX_TOTAL_BLOCK_BREAKS_PER_TICK = 20_000;
    // Drops are the biggest spike: limit how many *blocks* per tick are allowed to generate item drops.
    private static final int MAX_TOTAL_DROP_BLOCKS_PER_TICK = 64;

    private static final int MAX_NODE_EXPANSIONS_PER_TASK_PER_TICK = 20_000;
    private static final int MAX_BLOCK_BREAKS_PER_TASK_PER_TICK = 1_000;
    private static final int MAX_DROP_BLOCKS_PER_TASK_PER_TICK = 32;

    private static final Map<MinecraftServer, Deque<ExplosionCarverTask>> TASKS_BY_SERVER = new Object2ObjectOpenHashMap<>();
    private static boolean registered = false;

    private ExplosionCarver() {
    }

    public static void init() {
        if (registered) {
            return;
        }
        registered = true;

        ServerTickEvents.END_SERVER_TICK.register(ExplosionCarver::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            TASKS_BY_SERVER.remove(server);
            ExplosionCarverProfiling.onServerStopping();
        });

        AutoProfileSuperTnt.init();
    }

    public static void schedule(ServerWorld world, ExplosionImpl explosion, long seed) {
        MinecraftServer server = world.getServer();
        if (server == null) {
            return;
        }

        ExplosionCarverTask task = new ExplosionCarverTask(explosion, seed);
        ExplosionCarverProfiling.onTaskScheduled(world, explosion, seed, task);

        TASKS_BY_SERVER
                .computeIfAbsent(server, ignored -> new ArrayDeque<>())
                .addLast(task);
    }

    public static void schedule(
            ServerWorld world,
            ExplosionImpl explosion,
            long seed,
            BiPredicate<BlockPos, BlockState> canAffectBlock,
            boolean dropsEnabled
    ) {
        schedule(world, explosion, seed, canAffectBlock, dropsEnabled, 1.0F);
    }

    public static void schedule(
            ServerWorld world,
            ExplosionImpl explosion,
            long seed,
            BiPredicate<BlockPos, BlockState> canAffectBlock,
            boolean dropsEnabled,
            float initialEnergyMultiplier
    ) {
        MinecraftServer server = world.getServer();
        if (server == null) {
            return;
        }

        ExplosionCarverTask task = new ExplosionCarverTask(explosion, seed, canAffectBlock, dropsEnabled, initialEnergyMultiplier);
        ExplosionCarverProfiling.onTaskScheduled(world, explosion, seed, task);

        TASKS_BY_SERVER
                .computeIfAbsent(server, ignored -> new ArrayDeque<>())
                .addLast(task);
    }

    private static void tick(MinecraftServer server) {
        Deque<ExplosionCarverTask> tasks = TASKS_BY_SERVER.get(server);
        if (tasks == null || tasks.isEmpty()) {
            return;
        }

        int remainingNodeExpansions = MAX_TOTAL_NODE_EXPANSIONS_PER_TICK;
        int remainingBlockBreaks = MAX_TOTAL_BLOCK_BREAKS_PER_TICK;
        int remainingDropBlocks = MAX_TOTAL_DROP_BLOCKS_PER_TICK;

        int tasksToProcess = tasks.size();
        for (int i = 0; i < tasksToProcess; i++) {
            if (remainingNodeExpansions <= 0 && remainingBlockBreaks <= 0) {
                break;
            }

            ExplosionCarverTask task = tasks.pollFirst();
            if (task == null) {
                break;
            }

            long startNanos = System.nanoTime();
            ExplosionCarverTask.Progress progress = task.tick(
                    Math.min(remainingNodeExpansions, MAX_NODE_EXPANSIONS_PER_TASK_PER_TICK),
                    Math.min(remainingBlockBreaks, MAX_BLOCK_BREAKS_PER_TASK_PER_TICK),
                    Math.min(remainingDropBlocks, MAX_DROP_BLOCKS_PER_TASK_PER_TICK)
            );
            long elapsedNanos = System.nanoTime() - startNanos;
            ExplosionCarverProfiling.onTaskTick(task, progress, elapsedNanos);
            remainingNodeExpansions -= progress.nodesExpanded();
            remainingBlockBreaks -= progress.blocksBroken();
            remainingDropBlocks -= progress.dropBlocks();

            if (progress.done()) {
                ExplosionCarverProfiling.onTaskFinished(task);
            } else {
                tasks.addLast(task);
            }
        }

        if (tasks.isEmpty()) {
            TASKS_BY_SERVER.remove(server);
        }
    }

    static boolean hasPendingTasks(MinecraftServer server) {
        Deque<ExplosionCarverTask> tasks = TASKS_BY_SERVER.get(server);
        return tasks != null && !tasks.isEmpty();
    }
}
