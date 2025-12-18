package com.example.explosion;

import com.example.ExampleMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.explosion.ExplosionImpl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

final class ExplosionCarverProfiling {
    private static final String ENABLED_PROPERTY = "modid.explosionProfiling";
    private static final String MIN_POWER_PROPERTY = "modid.explosionProfiling.minPower";
    private static final String JFR_ENABLED_PROPERTY = "modid.explosionProfiling.jfr";

    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "false"));
    private static final float MIN_POWER = parseMinPower();
    private static final boolean JFR_ENABLED = Boolean.parseBoolean(System.getProperty(JFR_ENABLED_PROPERTY, "false"));

    private static final AtomicLong NEXT_ID = new AtomicLong();
    private static final Map<ExplosionCarverTask, TaskProfile> PROFILES = new IdentityHashMap<>();

    private static final Object JFR_LOCK = new Object();
    private static volatile Object jfrRecording = null;
    private static volatile Path jfrRecordingPath = null;

    private ExplosionCarverProfiling() {
    }

    static void onTaskScheduled(ServerWorld world, ExplosionImpl explosion, long seed, ExplosionCarverTask task) {
        if (!ENABLED) {
            return;
        }

        float power = explosion.getPower();
        if (power < MIN_POWER) {
            return;
        }

        TaskProfile profile = new TaskProfile(
                NEXT_ID.incrementAndGet(),
                world.getRegistryKey().getValue().toString(),
                BlockPos.ofFloored(explosion.getPosition()),
                power,
                task.getBoundingRadius(),
                seed,
                System.nanoTime()
        );
        PROFILES.put(task, profile);

        if (JFR_ENABLED) {
            startJfrIfNeeded();
        }
    }

    static void onTaskTick(ExplosionCarverTask task, ExplosionCarverTask.Progress progress, long elapsedNanos) {
        if (!ENABLED) {
            return;
        }

        TaskProfile profile = PROFILES.get(task);
        if (profile == null) {
            return;
        }

        profile.tickCalls++;
        profile.totalCarverNanos += elapsedNanos;
        profile.maxCarverTickNanos = Math.max(profile.maxCarverTickNanos, elapsedNanos);
        profile.nodesExpanded += progress.nodesExpanded();
        profile.blocksBroken += progress.blocksBroken();
        profile.dropBlocks += progress.dropBlocks();
    }

    static void onTaskFinished(ExplosionCarverTask task) {
        if (!ENABLED) {
            return;
        }

        TaskProfile profile = PROFILES.remove(task);
        if (profile == null) {
            return;
        }

        profile.finishedAtNanos = System.nanoTime();
        logSummary(profile);

        if (JFR_ENABLED && PROFILES.isEmpty()) {
            stopAndDumpJfrIfNeeded();
        }
    }

    static void onServerStopping() {
        if (!ENABLED) {
            return;
        }

        PROFILES.clear();
        if (JFR_ENABLED) {
            stopAndDumpJfrIfNeeded();
        }
    }

    private static void logSummary(TaskProfile profile) {
        long wallNanos = profile.finishedAtNanos - profile.scheduledAtNanos;
        double wallMs = wallNanos / 1_000_000.0;
        double carverMs = profile.totalCarverNanos / 1_000_000.0;
        double avgCarverMsPerTick = profile.tickCalls == 0 ? 0.0 : carverMs / profile.tickCalls;
        double maxCarverMsTick = profile.maxCarverTickNanos / 1_000_000.0;

        ExampleMod.LOGGER.info(
                "[ExplosionProfiling] id={} world={} power={} origin={} radius={} ticks={} wallMs={} carverMs={} avgCarverMs={} maxCarverMs={} nodes={} blocks={} drops={} seed={}",
                profile.id,
                profile.worldId,
                format2(profile.power),
                formatPos(profile.origin),
                profile.boundingRadius,
                profile.tickCalls,
                format2(wallMs),
                format2(carverMs),
                format2(avgCarverMsPerTick),
                format2(maxCarverMsTick),
                profile.nodesExpanded,
                profile.blocksBroken,
                profile.dropBlocks,
                profile.seed
        );

        writeSummaryJson(profile, wallMs, carverMs, avgCarverMsPerTick, maxCarverMsTick, jfrRecordingPath);
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static String format2(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String format2(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static void writeSummaryJson(
            TaskProfile profile,
            double wallMs,
            double carverMs,
            double avgCarverMsPerTick,
            double maxCarverMsTick,
            Path jfrPath
    ) {
        try {
            Path outDir = FabricLoader.getInstance().getGameDir().resolve("modid-profiles");
            Files.createDirectories(outDir);

            Path outFile = outDir.resolve("last-explosion-carver-summary.json");
            String json = "{\n"
                    + "  \"id\": " + profile.id + ",\n"
                    + "  \"world\": \"" + jsonEscape(profile.worldId) + "\",\n"
                    + "  \"power\": " + format2(profile.power) + ",\n"
                    + "  \"origin\": {\"x\": " + profile.origin.getX() + ", \"y\": " + profile.origin.getY() + ", \"z\": " + profile.origin.getZ() + "},\n"
                    + "  \"radius\": " + profile.boundingRadius + ",\n"
                    + "  \"ticks\": " + profile.tickCalls + ",\n"
                    + "  \"wallMs\": " + format2(wallMs) + ",\n"
                    + "  \"carverMs\": " + format2(carverMs) + ",\n"
                    + "  \"avgCarverMs\": " + format2(avgCarverMsPerTick) + ",\n"
                    + "  \"maxCarverMs\": " + format2(maxCarverMsTick) + ",\n"
                    + "  \"nodesExpanded\": " + profile.nodesExpanded + ",\n"
                    + "  \"blocksBroken\": " + profile.blocksBroken + ",\n"
                    + "  \"dropBlocks\": " + profile.dropBlocks + ",\n"
                    + "  \"seed\": " + profile.seed + ",\n"
                    + "  \"jfrPath\": " + (jfrPath == null ? "null" : ("\"" + jsonEscape(jfrPath.toAbsolutePath().toString()) + "\"")) + "\n"
                    + "}\n";

            Files.writeString(outFile, json);
        } catch (Exception e) {
            ExampleMod.LOGGER.warn("[ExplosionProfiling] Failed to write summary JSON", e);
        }
    }

    private static String jsonEscape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private static float parseMinPower() {
        String raw = System.getProperty(MIN_POWER_PROPERTY);
        if (raw == null || raw.isBlank()) {
            return ExplosionCarver.HIGH_POWER_THRESHOLD;
        }
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException ignored) {
            ExampleMod.LOGGER.warn(
                    "[ExplosionProfiling] Invalid {}='{}' (expected float); defaulting to {}",
                    MIN_POWER_PROPERTY,
                    raw,
                    ExplosionCarver.HIGH_POWER_THRESHOLD
            );
            return ExplosionCarver.HIGH_POWER_THRESHOLD;
        }
    }

    private static void startJfrIfNeeded() {
        synchronized (JFR_LOCK) {
            if (jfrRecording != null) {
                return;
            }

            Optional<JfrApi> jfr = JfrApi.tryLoad();
            if (jfr.isEmpty()) {
                return;
            }

            try {
                Path outDir = FabricLoader.getInstance().getGameDir().resolve("modid-profiles");
                Files.createDirectories(outDir);

                String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
                jfrRecordingPath = outDir.resolve("explosion-carver-" + timestamp + ".jfr");
                jfrRecording = jfr.get().startProfileRecording("modid-explosion-carver");

                ExampleMod.LOGGER.info("[ExplosionProfiling] JFR recording started: {}", jfrRecordingPath.toAbsolutePath());
            } catch (Exception e) {
                ExampleMod.LOGGER.warn("[ExplosionProfiling] Failed to start JFR recording", e);
                jfrRecording = null;
                jfrRecordingPath = null;
            }
        }
    }

    private static void stopAndDumpJfrIfNeeded() {
        synchronized (JFR_LOCK) {
            if (jfrRecording == null || jfrRecordingPath == null) {
                jfrRecording = null;
                jfrRecordingPath = null;
                return;
            }

            Optional<JfrApi> jfr = JfrApi.tryLoad();
            if (jfr.isEmpty()) {
                jfrRecording = null;
                jfrRecordingPath = null;
                return;
            }

            try {
                jfr.get().stopAndDump(jfrRecording, jfrRecordingPath);
                ExampleMod.LOGGER.info("[ExplosionProfiling] JFR recording saved: {}", jfrRecordingPath.toAbsolutePath());
            } catch (Exception e) {
                ExampleMod.LOGGER.warn("[ExplosionProfiling] Failed to stop/dump JFR recording", e);
            } finally {
                jfrRecording = null;
                jfrRecordingPath = null;
            }
        }
    }

    private static final class TaskProfile {
        final long id;
        final String worldId;
        final BlockPos origin;
        final float power;
        final int boundingRadius;
        final long seed;
        final long scheduledAtNanos;

        long finishedAtNanos;
        int tickCalls;
        long totalCarverNanos;
        long maxCarverTickNanos;
        long nodesExpanded;
        long blocksBroken;
        long dropBlocks;

        private TaskProfile(
                long id,
                String worldId,
                BlockPos origin,
                float power,
                int boundingRadius,
                long seed,
                long scheduledAtNanos
        ) {
            this.id = id;
            this.worldId = worldId;
            this.origin = origin;
            this.power = power;
            this.boundingRadius = boundingRadius;
            this.seed = seed;
            this.scheduledAtNanos = scheduledAtNanos;
        }
    }

    /**
     * Wraps JFR access so the mod still works on runtimes without JFR enabled/available.
     */
    private interface JfrApi {
        Object startProfileRecording(String name) throws Exception;

        void stopAndDump(Object recording, Path path) throws Exception;

        static Optional<JfrApi> tryLoad() {
            try {
                Class.forName("jdk.jfr.Recording");
                return Optional.of(new DefaultJfrApi());
            } catch (Throwable ignored) {
                ExampleMod.LOGGER.warn(
                        "[ExplosionProfiling] JFR not available; disable with -D{}=false",
                        JFR_ENABLED_PROPERTY
                );
                return Optional.empty();
            }
        }
    }

    private static final class DefaultJfrApi implements JfrApi {
        @Override
        public Object startProfileRecording(String name) throws Exception {
            jdk.jfr.Configuration configuration = jdk.jfr.Configuration.getConfiguration("profile");
            jdk.jfr.Recording recording = new jdk.jfr.Recording(configuration);
            recording.setName(name);
            recording.setToDisk(true);
            recording.start();
            return recording;
        }

        @Override
        public void stopAndDump(Object recording, Path path) throws Exception {
            jdk.jfr.Recording jfr = (jdk.jfr.Recording) recording;
            try {
                jfr.stop();
                jfr.dump(path);
            } finally {
                jfr.close();
            }
        }
    }
}
