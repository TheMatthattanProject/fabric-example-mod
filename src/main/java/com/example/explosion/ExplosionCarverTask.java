package com.example.explosion;

import com.example.mixin.ExplosionImplAccessor;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.explosion.ExplosionBehavior;
import net.minecraft.world.explosion.ExplosionImpl;

import java.util.Optional;
import java.util.function.BiConsumer;

public final class ExplosionCarverTask {
    private static final float ENERGY_SCALE = 0.60F;
    private static final float STEP_COST = 1.00F;
    private static final float MIN_PROPAGATION_ENERGY = 0.05F;
    private static final float MIN_BREAK_ENERGY = 0.25F;
    private static final float RESISTANCE_JITTER = 0.15F;

    private static final float VANILLA_RESISTANCE_ADD = 0.3F;
    private static final float VANILLA_RESISTANCE_MULT = 0.3F;

    // Dense fixed-point energy grid (major speedup vs hash map at high powers).
    // Uses 1/64th precision (good enough for jitter/thresholds) and fits typical energies into a short.
    private static final int ENERGY_FP_SHIFT = 6;
    private static final int ENERGY_FP_ONE = 1 << ENERGY_FP_SHIFT;
    private static final int STEP_COST_FP = (int) (STEP_COST * ENERGY_FP_ONE);
    private static final int MIN_PROPAGATION_ENERGY_FP = (int) (MIN_PROPAGATION_ENERGY * ENERGY_FP_ONE);
    private static final int MIN_BREAK_ENERGY_FP = (int) (MIN_BREAK_ENERGY * ENERGY_FP_ONE);
    // Fixed-point analogue of ENERGY_EPSILON; require at least 1 unit (1/64) improvement to relax.
    private static final int ENERGY_EPSILON_FP = 1;

    private static final float BOUNDING_RADIUS_MULTIPLIER = 1.25F;
    private static final int MAX_BOUNDING_RADIUS = 192;

    // Dense arrays are fast but can be memory-heavy at very large radii; fall back to the map if needed.
    private static final int MAX_DENSE_CUBE_VOLUME = 40_000_000;

    // High-power carving default: no drops. Allow drops only very close to the origin, and still budgeted per tick.
    private static final int DROPS_INNER_RADIUS_MIN = 4;
    private static final int DROPS_INNER_RADIUS_MAX = 8;

    private static final BiConsumer<ItemStack, BlockPos> NO_DROPS_CONSUMER = (stack, dropPos) -> {
    };

    private final ExplosionImpl explosion;
    private final ExplosionBehavior behavior;
    private final ServerWorld world;
    private final BlockPos origin;
    private final int originX;
    private final int originY;
    private final int originZ;
    private final int boundingRadius;
    private final int boundingRadiusSquared;
    private final long seed;

    private final LongArrayFIFOQueue frontier = new LongArrayFIFOQueue();
    private final LongArrayFIFOQueue blocksToBreak = new LongArrayFIFOQueue();

    private final BlockPos.Mutable scratchPos = new BlockPos.Mutable();
    private final BiConsumer<ItemStack, BlockPos> dropConsumer;
    private final int dropInnerRadiusSquared;

    // Per-task resistance cache keyed by state raw id (jitter is applied after caching).
    private final Int2FloatOpenHashMap baseResistanceCostByStateId = new Int2FloatOpenHashMap();

    // Energy storage:
    // - Dense mode: a fixed cube centered at origin, indexed by local offsets in [-R, R].
    //   index = ((dy+R) * side + (dz+R)) * side + (dx+R) with x as the fastest axis for neighbor locality.
    // - Fallback: Long2FloatOpenHashMap, for cases where the dense cube would be too large.
    private final boolean useDenseEnergy;
    private final int denseSide;
    private final short[] bestEnergyFpByIndex;
    private final Long2FloatOpenHashMap bestEnergyByPos;

    // Tiny chunk-loaded cache to avoid repeated lookups when the frontier stays within a chunk.
    private long lastChunkLong = Long.MIN_VALUE;
    private boolean lastChunkLoaded = false;

    private boolean bfsFinished = false;

    public record Progress(int nodesExpanded, int blocksBroken, int dropBlocks, boolean done) {
    }

    public ExplosionCarverTask(ExplosionImpl explosion, long seed) {
        this.explosion = explosion;
        this.seed = seed;
        this.world = explosion.getWorld();
        this.behavior = ((ExplosionImplAccessor) explosion).modid$getBehavior();

        Vec3d pos = explosion.getPosition();
        this.origin = BlockPos.ofFloored(pos);
        this.originX = origin.getX();
        this.originY = origin.getY();
        this.originZ = origin.getZ();

        this.dropConsumer = (stack, dropPos) -> Block.dropStack(world, dropPos, stack);

        float power = explosion.getPower();
        this.boundingRadius = MathHelper.clamp(MathHelper.ceil(power * BOUNDING_RADIUS_MULTIPLIER), 1, MAX_BOUNDING_RADIUS);
        this.boundingRadiusSquared = boundingRadius * boundingRadius;

        int dropsRadius = MathHelper.clamp((int) Math.floor(Math.sqrt(power)), DROPS_INNER_RADIUS_MIN, DROPS_INNER_RADIUS_MAX);
        this.dropInnerRadiusSquared = dropsRadius * dropsRadius;

        this.baseResistanceCostByStateId.defaultReturnValue(Float.NaN);

        int side = boundingRadius * 2 + 1;
        int volume = side * side * side;
        this.useDenseEnergy = volume <= MAX_DENSE_CUBE_VOLUME;
        if (useDenseEnergy) {
            this.denseSide = side;
            this.bestEnergyFpByIndex = new short[volume]; // 0 = unseen; energies are always > 0 once stored.
            this.bestEnergyByPos = null;
        } else {
            this.denseSide = 0;
            this.bestEnergyFpByIndex = null;
            this.bestEnergyByPos = new Long2FloatOpenHashMap();
            this.bestEnergyByPos.defaultReturnValue(Float.NEGATIVE_INFINITY);
        }

        int initialEnergyFp = toEnergyFp(computeInitialEnergy(power));
        long originLong = origin.asLong();
        setBestEnergy(originLong, 0, 0, 0, initialEnergyFp);
        frontier.enqueue(originLong);

        BlockState originState = world.getBlockState(origin);
        maybeQueueForBreaking(originLong, 0, initialEnergyFp, originState, origin);
    }

    public Progress tick(int maxNodeExpansions, int maxBlockBreaks, int maxDropBlocks) {
        int nodesExpanded = 0;
        int blocksBroken = 0;
        int dropBlocks = 0;

        if (!bfsFinished) {
            nodesExpanded = expandWavefront(maxNodeExpansions);
            if (frontier.isEmpty()) {
                bfsFinished = true;
            }
        }

        if (bfsFinished) {
            BreakResult result = breakQueuedBlocks(maxBlockBreaks, maxDropBlocks);
            blocksBroken = result.blocksBroken();
            dropBlocks = result.dropBlocks();
        }

        return new Progress(nodesExpanded, blocksBroken, dropBlocks, bfsFinished && blocksToBreak.isEmpty());
    }

    private int expandWavefront(int maxNodeExpansions) {
        int nodesExpanded = 0;

        while (nodesExpanded < maxNodeExpansions && !frontier.isEmpty()) {
            long posLong = frontier.dequeueLong();
            int x = BlockPos.unpackLongX(posLong);
            int y = BlockPos.unpackLongY(posLong);
            int z = BlockPos.unpackLongZ(posLong);
            int dx = x - originX;
            int dy = y - originY;
            int dz = z - originZ;

            int energyFp = getBestEnergyFp(posLong, dx, dy, dz);
            if (energyFp <= MIN_PROPAGATION_ENERGY_FP) {
                continue;
            }

            // Early-out: even with 0 resistance, none of the neighbors can propagate.
            if (energyFp <= STEP_COST_FP + MIN_PROPAGATION_ENERGY_FP) {
                nodesExpanded++;
                continue;
            }

            tryNeighbor(x + 1, y, z, energyFp);
            tryNeighbor(x - 1, y, z, energyFp);
            tryNeighbor(x, y + 1, z, energyFp);
            tryNeighbor(x, y - 1, z, energyFp);
            tryNeighbor(x, y, z + 1, energyFp);
            tryNeighbor(x, y, z - 1, energyFp);

            nodesExpanded++;
        }

        return nodesExpanded;
    }

    private void tryNeighbor(int x, int y, int z, int fromEnergyFp) {
        int dx = x - originX;
        int dy = y - originY;
        int dz = z - originZ;
        if (dx * dx + dy * dy + dz * dz > boundingRadiusSquared) {
            return;
        }

        long posLong = BlockPos.asLong(x, y, z);
        int previousBestFp = getBestEnergyFp(posLong, dx, dy, dz);

        // Early-out: can't possibly beat existing energy at this node, even with 0 resistance.
        int optimisticEnergyFp = fromEnergyFp - STEP_COST_FP;
        if (optimisticEnergyFp <= previousBestFp + ENERGY_EPSILON_FP || optimisticEnergyFp <= MIN_PROPAGATION_ENERGY_FP) {
            return;
        }

        long chunkLong = packChunkLong(x >> 4, z >> 4);
        if (!isChunkLoadedCached(chunkLong)) {
            return;
        }

        scratchPos.set(x, y, z);
        if (!world.isInBuildLimit(scratchPos)) {
            return;
        }
        BlockState state = world.getBlockState(scratchPos);
        int resistanceCostFp = getResistanceCostFp(state, scratchPos, posLong);
        int newEnergyFp = fromEnergyFp - STEP_COST_FP - resistanceCostFp;
        if (newEnergyFp <= MIN_PROPAGATION_ENERGY_FP) {
            return;
        }

        if (newEnergyFp <= previousBestFp + ENERGY_EPSILON_FP) {
            return;
        }

        setBestEnergy(posLong, dx, dy, dz, newEnergyFp);
        frontier.enqueue(posLong);
        maybeQueueForBreaking(posLong, previousBestFp, newEnergyFp, state, scratchPos);
    }

    private void maybeQueueForBreaking(long posLong, int previousEnergyFp, int newEnergyFp, BlockState state, BlockPos pos) {
        // De-dup without a set: energy only ever increases, so each position crosses the break threshold at most once.
        if (previousEnergyFp > MIN_BREAK_ENERGY_FP || newEnergyFp <= MIN_BREAK_ENERGY_FP) {
            return;
        }

        if (state.isAir()) {
            return;
        }

        if (state.getHardness(world, pos) < 0.0F) {
            return;
        }

        blocksToBreak.enqueue(posLong);
    }

    private record BreakResult(int blocksBroken, int dropBlocks) {
    }

    private BreakResult breakQueuedBlocks(int maxBlockBreaks, int maxDropBlocks) {
        int blocksBroken = 0;
        int dropBlocks = 0;
        int dropsRemaining = maxDropBlocks;
        long lastChunkLong = Long.MIN_VALUE;
        boolean lastChunkLoaded = false;

        while (blocksBroken < maxBlockBreaks && !blocksToBreak.isEmpty()) {
            long posLong = blocksToBreak.dequeueLong();

            int x = BlockPos.unpackLongX(posLong);
            int y = BlockPos.unpackLongY(posLong);
            int z = BlockPos.unpackLongZ(posLong);
            int dx = x - originX;
            int dy = y - originY;
            int dz = z - originZ;

            long chunkLong = packChunkLong(x >> 4, z >> 4);
            if (chunkLong != lastChunkLong) {
                lastChunkLong = chunkLong;
                lastChunkLoaded = world.isChunkLoaded(chunkLong);
            }
            if (!lastChunkLoaded) {
                continue;
            }

            scratchPos.set(x, y, z);
            if (!world.isInBuildLimit(scratchPos)) {
                continue;
            }

            BlockState state = world.getBlockState(scratchPos);
            if (state.isAir()) {
                continue;
            }

            float hardness = state.getHardness(world, scratchPos);
            if (hardness < 0.0F) {
                continue;
            }

            float energy = getBestEnergyFloat(posLong, dx, dy, dz);
            if (energy <= MIN_BREAK_ENERGY) {
                continue;
            }

            if (!behavior.canDestroyBlock(explosion, world, scratchPos, state, energy)) {
                continue;
            }

            boolean allowDrops = dropsRemaining > 0 && dx * dx + dy * dy + dz * dz <= dropInnerRadiusSquared;
            // Default to NO DROPS for performance; only allow a small inner core, still tick-budgeted.
            state.onExploded(world, scratchPos, explosion, allowDrops ? dropConsumer : NO_DROPS_CONSUMER);
            if (allowDrops) {
                dropsRemaining--;
                dropBlocks++;
            }
            blocksBroken++;
        }

        return new BreakResult(blocksBroken, dropBlocks);
    }

    private float computeInitialEnergy(float power) {
        float rand01 = hashToUnitFloat(seed);
        float vanillaLike = power * (0.7F + 0.6F * rand01);
        return vanillaLike * ENERGY_SCALE;
    }

    private int getResistanceCostFp(BlockState state, BlockPos pos, long posLong) {
        int stateId = Block.getRawIdFromState(state);
        float baseCost = baseResistanceCostByStateId.get(stateId);
        if (Float.isNaN(baseCost)) {
            // Cache only the base resistance; positional jitter stays per-block to keep the crater irregular.
            Optional<Float> blastResistance = behavior.getBlastResistance(explosion, world, pos, state, world.getFluidState(pos));
            baseCost = blastResistance.map(value -> (value + VANILLA_RESISTANCE_ADD) * VANILLA_RESISTANCE_MULT).orElse(0.0F);
            baseResistanceCostByStateId.put(stateId, baseCost);
        }

        float jitter = (hashToUnitFloat(posLong ^ seed) - 0.5F) * RESISTANCE_JITTER;
        float cost = baseCost + jitter;
        if (cost <= 0.0F) {
            return 0;
        }
        return Math.round(cost * ENERGY_FP_ONE);
    }

    private boolean isChunkLoadedCached(long chunkLong) {
        if (chunkLong != lastChunkLong) {
            lastChunkLong = chunkLong;
            lastChunkLoaded = world.isChunkLoaded(chunkLong);
        }
        return lastChunkLoaded;
    }

    private int getBestEnergyFp(long posLong, int dx, int dy, int dz) {
        if (useDenseEnergy) {
            int index = denseIndex(dx, dy, dz);
            return bestEnergyFpByIndex[index];
        }
        return toEnergyFp(bestEnergyByPos.get(posLong));
    }

    private float getBestEnergyFloat(long posLong, int dx, int dy, int dz) {
        if (useDenseEnergy) {
            int energyFp = bestEnergyFpByIndex[denseIndex(dx, dy, dz)];
            return energyFp / (float) ENERGY_FP_ONE;
        }
        return bestEnergyByPos.get(posLong);
    }

    private void setBestEnergy(long posLong, int dx, int dy, int dz, int energyFp) {
        energyFp = MathHelper.clamp(energyFp, 0, Short.MAX_VALUE);
        if (useDenseEnergy) {
            bestEnergyFpByIndex[denseIndex(dx, dy, dz)] = (short) energyFp;
        } else {
            bestEnergyByPos.put(posLong, energyFp / (float) ENERGY_FP_ONE);
        }
    }

    private int denseIndex(int dx, int dy, int dz) {
        int lx = dx + boundingRadius;
        int ly = dy + boundingRadius;
        int lz = dz + boundingRadius;
        return (ly * denseSide + lz) * denseSide + lx;
    }

    private static int toEnergyFp(float energy) {
        if (energy <= 0.0F) {
            return 0;
        }
        // Round to fixed-point; clamp to short range for dense mode.
        return Math.min(Short.MAX_VALUE, Math.round(energy * ENERGY_FP_ONE));
    }

    private static float hashToUnitFloat(long value) {
        long x = value;
        x ^= (x >>> 33);
        x *= 0xff51afd7ed558ccdL;
        x ^= (x >>> 33);
        x *= 0xc4ceb9fe1a85ec53L;
        x ^= (x >>> 33);
        return (x & 0xFFFFFFL) / (float) 0x1000000;
    }

    private static long packChunkLong(int chunkX, int chunkZ) {
        return ((long) chunkX & 0xFFFFFFFFL) | (((long) chunkZ & 0xFFFFFFFFL) << 32);
    }
}
