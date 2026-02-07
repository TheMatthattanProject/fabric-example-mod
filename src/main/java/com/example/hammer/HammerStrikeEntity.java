package com.example.hammer;

import com.example.explosion.ExplosionCarver;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.TintedParticleEffect;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.explosion.ExplosionImpl;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * Server-authoritative tick-based state machine for THE HAMMER.
 *
 * <p>All stage transitions and timeline control flow are driven by {@code strikeTicks}.
 * The server emits {@link HammerNetworking#sendStage} packets at each stage transition; clients are
 * responsible for the cinematic VFX (fog, shaders, camera shake, rendering, particles).
 */
public class HammerStrikeEntity extends Entity {
    private static final TrackedData<BlockPos> TARGET_POS = DataTracker.registerData(HammerStrikeEntity.class, TrackedDataHandlerRegistry.BLOCK_POS);
    private static final TrackedData<Integer> SEED = DataTracker.registerData(HammerStrikeEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> OWNER_ID = DataTracker.registerData(HammerStrikeEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> PREVIEW = DataTracker.registerData(HammerStrikeEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private static final TagKey<Block> GLASS_BLOCKS = TagKey.of(RegistryKeys.BLOCK, Identifier.ofVanilla("glass"));
    private static final TagKey<Block> GLASS_PANES = TagKey.of(RegistryKeys.BLOCK, Identifier.ofVanilla("glass_panes"));

    private static final int ERUPTION_RADIUS = 38;
    private static final int ERUPTION_LAYERS = 6;
    private static final int ERUPTION_BLOCKS_PER_TICK = 240;
    private static final int IMPACT_DEPTH_BLOCKS = 128;
    private static final int TOP_CLEAR_HEIGHT_BLOCKS = ERUPTION_RADIUS;
    private static final int WAVE_CLEAR_HEIGHT_BLOCKS = 128;

    private static final int WAVE_RADIUS = 192;
    private static final int WAVE_SWEEP_INTERVAL_TICKS = 2;
    private static final int WAVE_COLUMNS_PER_TICK = 600;
    private static final int WAVE_BAND_THICKNESS_BLOCKS = 6;
    private static final int WAVE_SCAN_DEPTH_BLOCKS = 64;
    private static final int LOW_FOLIAGE_SCAN_HEIGHT_BLOCKS = 24;
    private static final int PLAYER_EFFECT_RADIUS = 100;

    private final Deque<BlockPos> eruptionQueue = new ArrayDeque<>();
    private float lastWaveRadius;

    private int forcedChunkX;
    private int forcedChunkZ;
    private int forcedChunkRadius = -1;

    private int strikeTicks;
    private boolean craterCarveScheduled;

    private int waveTargetRadius;
    private int waveFoliageClearedRadius;
    private boolean waveSweepActive;
    private int waveSweepRStart;
    private int waveSweepREnd;
    private int waveSweepDx;
    private int waveSweepDz;
    private int waveSweepDzMin;
    private int waveSweepDzMax;
    private boolean waveSweepNegPending;

    public HammerStrikeEntity(EntityType<? extends HammerStrikeEntity> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
        this.setInvisible(true);
    }

    public static HammerStrikeEntity spawn(ServerWorld world, BlockPos target, @Nullable ServerPlayerEntity owner) {
        return spawn(world, target, owner, false);
    }

    public static HammerStrikeEntity spawn(ServerWorld world, BlockPos target, @Nullable ServerPlayerEntity owner, boolean preview) {
        HammerStrikeEntity strike = new HammerStrikeEntity(HammerEntities.HAMMER_STRIKE, world);
        strike.setPosition(target.getX() + 0.5D, target.getY() + 0.02D, target.getZ() + 0.5D);
        strike.setTargetPos(target);
        strike.setOwner(owner);
        strike.setPreview(preview);
        strike.setSeed(strike.random.nextInt());
        strike.forceLoadAtSpawn(world, target);
        if (!world.spawnEntity(strike)) {
            strike.releaseForceLoadedChunks(world);
        }
        return strike;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(TARGET_POS, BlockPos.ORIGIN);
        builder.add(SEED, 0);
        builder.add(OWNER_ID, -1);
        builder.add(PREVIEW, false);
    }

    public BlockPos getTargetPos() {
        return dataTracker.get(TARGET_POS);
    }

    public void setTargetPos(BlockPos pos) {
        dataTracker.set(TARGET_POS, pos);
    }

    public int getSeed() {
        return dataTracker.get(SEED);
    }

    private void setSeed(int seed) {
        dataTracker.set(SEED, seed);
    }

    public int getOwnerId() {
        return dataTracker.get(OWNER_ID);
    }

    public void setOwner(@Nullable ServerPlayerEntity owner) {
        dataTracker.set(OWNER_ID, owner == null ? -1 : owner.getId());
    }

    public boolean isPreview() {
        return dataTracker.get(PREVIEW);
    }

    public void setPreview(boolean preview) {
        dataTracker.set(PREVIEW, preview);
    }

    @Override
    protected void readCustomData(ReadView view) {
        dataTracker.set(TARGET_POS, BlockPos.fromLong(view.getLong("Target", BlockPos.ORIGIN.asLong())));
        dataTracker.set(SEED, view.getInt("Seed", 0));
        dataTracker.set(OWNER_ID, view.getInt("OwnerId", -1));
        dataTracker.set(PREVIEW, view.getBoolean("Preview", false));

        strikeTicks = view.getInt("StrikeTicks", 0);
        lastWaveRadius = view.getFloat("LastWaveRadius", 0.0F);
        craterCarveScheduled = view.getBoolean("CraterCarveScheduled", false);
        waveTargetRadius = view.getInt("WaveTargetRadius", 0);
        waveFoliageClearedRadius = view.getInt("WaveFoliageClearedRadius", 0);
        waveSweepActive = false;
    }

    @Override
    protected void writeCustomData(WriteView view) {
        view.putLong("Target", getTargetPos().asLong());
        view.putInt("Seed", getSeed());
        view.putInt("OwnerId", getOwnerId());
        view.putBoolean("Preview", isPreview());

        view.putInt("StrikeTicks", strikeTicks);
        view.putFloat("LastWaveRadius", lastWaveRadius);
        view.putBoolean("CraterCarveScheduled", craterCarveScheduled);
        view.putInt("WaveTargetRadius", waveTargetRadius);
        view.putInt("WaveFoliageClearedRadius", waveFoliageClearedRadius);
    }

    @Override
    public void tick() {
        super.tick();

        if (getEntityWorld().isClient()) {
            return;
        }

        ServerWorld world = (ServerWorld) getEntityWorld();
        ensureForceLoadedChunks(world);
        if (!isPreview() && waveTargetRadius > waveFoliageClearedRadius && (strikeTicks % WAVE_SWEEP_INTERVAL_TICKS) == 0) {
            tickWaveFoliageSweep(world);
        }

        if (strikeTicks == HammerStrikeTimeline.STAGE_1_TARGETING_START) {
            enterStage(world, HammerStage.TARGETING);
            if (!isPreview() && !craterCarveScheduled) {
                craterCarveScheduled = true;
                scheduleCraterCarve(world);
            }
        }
        if (strikeTicks == HammerStrikeTimeline.STAGE_2_BREACH_START) {
            enterStage(world, HammerStage.ATMOSPHERIC_BREACH);
        }
        if (strikeTicks == HammerStrikeTimeline.STAGE_3_STROKE_START) {
            enterStage(world, HammerStage.HAMMER_STROKE);
        }
        if (strikeTicks == HammerStrikeTimeline.STAGE_4_ERUPTION_START) {
            enterStage(world, HammerStage.KINETIC_ERUPTION);
            beginEruption(world);
        }
        if (strikeTicks == HammerStrikeTimeline.STAGE_5_WAVE_START) {
            enterStage(world, HammerStage.PRESSURE_WAVE);
        }
        if (strikeTicks == HammerStrikeTimeline.STAGE_6_AFTERMATH_START) {
            enterStage(world, HammerStage.AFTERMATH_SIGNAL_LOSS);
            buildScorchedFloor(world);
        }

        if (!isPreview()) {
            if (strikeTicks >= HammerStrikeTimeline.STAGE_4_ERUPTION_START && strikeTicks <= HammerStrikeTimeline.STAGE_4_ERUPTION_END) {
                tickEruption(world);
            }
            if (strikeTicks >= HammerStrikeTimeline.STAGE_5_WAVE_START && strikeTicks <= HammerStrikeTimeline.STAGE_5_WAVE_END) {
                tickPressureWave(world);
            }
        }

        if (strikeTicks >= (HammerStrikeTimeline.STAGE_6_AFTERMATH_END + 1)) {
            if (!isPreview() && hasPendingFoliageWork()) {
                // Keep the entity alive until the foliage sweep catches up, so the full wave radius is processed.
            } else {
                releaseForceLoadedChunks(world);
                discard();
                return;
            }
        }

        strikeTicks++;
    }

    private void ensureForceLoadedChunks(ServerWorld world) {
        BlockPos target = getTargetPos();
        int chunkX = target.getX() >> 4;
        int chunkZ = target.getZ() >> 4;
        int requiredRadius = MathHelper.ceil(Math.max(ERUPTION_RADIUS, lastWaveRadius) / 16.0F);

        if (forcedChunkRadius < 0 || chunkX != forcedChunkX || chunkZ != forcedChunkZ) {
            releaseForceLoadedChunks(world);
            forceLoadChunksAround(world, chunkX, chunkZ, requiredRadius, true);
            forcedChunkX = chunkX;
            forcedChunkZ = chunkZ;
            forcedChunkRadius = requiredRadius;
            return;
        }

        if (requiredRadius > forcedChunkRadius) {
            forceLoadChunksAround(world, chunkX, chunkZ, requiredRadius, true);
            forcedChunkRadius = requiredRadius;
        }
    }

    private void forceLoadAtSpawn(ServerWorld world, BlockPos target) {
        int chunkX = target.getX() >> 4;
        int chunkZ = target.getZ() >> 4;
        forceLoadChunksAround(world, chunkX, chunkZ, 0, true);
        world.getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
        forcedChunkX = chunkX;
        forcedChunkZ = chunkZ;
        forcedChunkRadius = 0;
    }

    private void releaseForceLoadedChunks(ServerWorld world) {
        if (forcedChunkRadius < 0) {
            return;
        }
        forceLoadChunksAround(world, forcedChunkX, forcedChunkZ, forcedChunkRadius, false);
        forcedChunkRadius = -1;
    }

    private static void forceLoadChunksAround(ServerWorld world, int chunkX, int chunkZ, int radius, boolean forced) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                world.setChunkForced(chunkX + dx, chunkZ + dz, forced);
            }
        }
    }

    private boolean hasPendingFoliageWork() {
        return waveTargetRadius > waveFoliageClearedRadius || waveSweepActive;
    }

    private void scheduleCraterCarve(ServerWorld world) {
        BlockPos center = getTargetPos();
        int centerX = center.getX();
        int centerZ = center.getZ();
        ServerPlayerEntity owner = getOwnerPlayer(world);

        int surfaceY = center.getY();
        int maxBuildY = world.getBottomY() + world.getHeight() - 1;
        int maxY = Math.min(maxBuildY, surfaceY + TOP_CLEAR_HEIGHT_BLOCKS);
        int minY = Math.max(world.getBottomY(), surfaceY - IMPACT_DEPTH_BLOCKS);
        int actualDepth = Math.max(1, surfaceY - minY);
        int craterRadiusSquared = ERUPTION_RADIUS * ERUPTION_RADIUS;

        int requiredBoundingRadius = MathHelper.ceil(MathHelper.sqrt(craterRadiusSquared + actualDepth * actualDepth)) + 2;
        float power = Math.max(64.0F, requiredBoundingRadius / 1.25F);
        float energyMultiplier = MathHelper.clamp(1.0F + (actualDepth / 16.0F), 1.0F, 12.0F);

        Vec3d explosionCenter = new Vec3d(centerX + 0.5D, surfaceY + 0.5D, centerZ + 0.5D);
        ExplosionImpl explosion = new ExplosionImpl(world, null, null, null, explosionCenter, power, false, Explosion.DestructionType.DESTROY);

        BiPredicate<BlockPos, BlockState> canAffectBlock = (pos, state) -> {
            int y = pos.getY();
            if (y < minY || y > maxY) {
                return false;
            }

            int dx = pos.getX() - centerX;
            int dz = pos.getZ() - centerZ;

            if (y > surfaceY) {
                if ((dx * dx + dz * dz) > craterRadiusSquared) {
                    return false;
                }
            } else {
                int depth = surfaceY - y;
                float t = depth / (float) actualDepth;
                float radiusAtDepth = MathHelper.lerp(t, (float) ERUPTION_RADIUS, (float) ERUPTION_RADIUS * 0.45F);
                if ((dx * dx + dz * dz) > (radiusAtDepth * radiusAtDepth)) {
                    return false;
                }
            }

            if (state.isAir()) {
                return true;
            }
            if (state.isOf(Blocks.BEDROCK) || state.isOf(Blocks.END_PORTAL_FRAME)) {
                return false;
            }
            if (world.getBlockEntity(pos) != null) {
                return false;
            }
            return owner == null || HammerProtection.canDamageBlock(world, pos, owner);
        };

        long seed = (((long) getSeed()) << 32) ^ center.asLong() ^ world.getTime();
        ExplosionCarver.schedule(
                world,
                explosion,
                seed,
                canAffectBlock,
                false,
                energyMultiplier,
                () -> HammerNetworking.sendCraterComplete(world, center, getId(), world.getTime())
        );
    }

    private void enterStage(ServerWorld world, HammerStage stage) {
        HammerNetworking.sendStage(world, getTargetPos(), getId(), getSeed(), stage, strikeTicks, world.getTime());
    }

    private void beginEruption(ServerWorld world) {
        eruptionQueue.clear();
        lastWaveRadius = 0.0F;

        if (isPreview()) {
            return;
        }

        vaporizeWeakMobs(world);
    }

    private void tickEruption(ServerWorld world) {
        if (eruptionQueue.isEmpty()) {
            return;
        }

        int remainingTicks = Math.max(1, (HammerStrikeTimeline.STAGE_4_ERUPTION_END + 1) - strikeTicks);
        int dynamicBudget = (int) Math.ceil(eruptionQueue.size() / (double) remainingTicks);
        int budget = MathHelper.clamp(Math.max(dynamicBudget, ERUPTION_BLOCKS_PER_TICK), 1, 10_000);

        ServerPlayerEntity owner = getOwnerPlayer(world);

        for (int i = 0; i < budget; i++) {
            BlockPos pos = eruptionQueue.pollFirst();
            if (pos == null) {
                break;
            }
            if (owner != null && !HammerProtection.canDamageBlock(world, pos, owner)) {
                continue;
            }
            if (world.getBlockEntity(pos) != null) {
                continue;
            }

            BlockState state = world.getBlockState(pos);
            if (state.isAir() || state.isOf(Blocks.BEDROCK) || state.isOf(Blocks.END_PORTAL_FRAME)) {
                continue;
            }
            if (state.getHardness(world, pos) < 0.0F) {
                continue;
            }

            world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        }
    }

    private void tickPressureWave(ServerWorld world) {
        float progress = (strikeTicks - HammerStrikeTimeline.STAGE_5_WAVE_START) / (float) Math.max(1, (HammerStrikeTimeline.STAGE_5_WAVE_END - HammerStrikeTimeline.STAGE_5_WAVE_START));
        float radius = MathHelper.clamp(progress, 0.0F, 1.0F) * WAVE_RADIUS;

        lastWaveRadius = radius;
        waveTargetRadius = Math.max(waveTargetRadius, MathHelper.ceil(radius));
    }

    private void tickWaveFoliageSweep(ServerWorld world) {
        int targetRadius = waveTargetRadius;
        int startRadius = waveFoliageClearedRadius;
        if (targetRadius <= startRadius) {
            return;
        }

        if (!waveSweepActive) {
            waveSweepRStart = startRadius;
            waveSweepREnd = Math.min(targetRadius, startRadius + WAVE_BAND_THICKNESS_BLOCKS);
            waveSweepDx = -waveSweepREnd;
            setupWaveSweepForDx();
            waveSweepActive = true;
        }

        ServerPlayerEntity owner = getOwnerPlayer(world);
        BlockPos center = getTargetPos();
        int centerX = center.getX();
        int centerZ = center.getZ();
        int minY = Math.max(world.getBottomY(), center.getY() - 8);
        int maxY = Math.min(world.getTopYInclusive(), center.getY() + WAVE_CLEAR_HEIGHT_BLOCKS);

        BlockPos.Mutable pos = new BlockPos.Mutable();
        int columnsProcessed = 0;
        while (columnsProcessed < WAVE_COLUMNS_PER_TICK && waveSweepActive) {
            int rEnd = waveSweepREnd;
            if (waveSweepDx > rEnd) {
                waveFoliageClearedRadius = waveSweepREnd;
                waveSweepActive = false;
                break;
            }

            if (waveSweepDz > waveSweepDzMax) {
                waveSweepDx++;
                setupWaveSweepForDx();
                continue;
            }

            int x = centerX + waveSweepDx;
            int dz = waveSweepDz;
            int z = centerZ + (waveSweepNegPending ? -dz : dz);

            if (world.isChunkLoaded(ChunkPos.toLong(x >> 4, z >> 4))) {
                clearWaveColumnDynamicY(world, owner, pos, x, z, minY, maxY);
            }
            columnsProcessed++;

            if (dz == 0) {
                waveSweepDz++;
                continue;
            }

            if (!waveSweepNegPending) {
                waveSweepNegPending = true;
            } else {
                waveSweepNegPending = false;
                waveSweepDz++;
            }
        }

    }

    private void setupWaveSweepForDx() {
        waveSweepNegPending = false;

        int rStart = waveSweepRStart;
        int rEnd = waveSweepREnd;

        int dxSq = waveSweepDx * waveSweepDx;
        int dzMaxSq = rEnd * rEnd - dxSq;
        if (dzMaxSq < 0) {
            waveSweepDzMin = 0;
            waveSweepDzMax = -1;
            waveSweepDz = 0;
            return;
        }

        waveSweepDzMax = MathHelper.floor(MathHelper.sqrt(dzMaxSq));
        int dzMinSq = rStart * rStart - dxSq;
        waveSweepDzMin = dzMinSq > 0 ? MathHelper.floor(MathHelper.sqrt(dzMinSq)) : -1;
        waveSweepDz = waveSweepDzMin + 1;
    }

    private void clearWaveColumnDynamicY(ServerWorld world, @Nullable ServerPlayerEntity owner, BlockPos.Mutable pos, int x, int z, int minY, int maxY) {
        int topY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
        int yStart = Math.min(maxY, topY);

        if (yStart >= minY) {
            int yEnd = Math.max(minY, yStart - WAVE_SCAN_DEPTH_BLOCKS);
            for (int y = yStart; y >= yEnd; y--) {
                pos.set(x, y, z);
                BlockState state = world.getBlockState(pos);
                if (state.isAir() || !shouldWaveShatterBlock(state)) {
                    continue;
                }
                if (owner != null && !HammerProtection.canDamageBlock(world, pos, owner)) {
                    continue;
                }
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            }
        }

        int groundY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        int lowStart = Math.max(minY, groundY);
        int lowEnd = Math.min(maxY, groundY + LOW_FOLIAGE_SCAN_HEIGHT_BLOCKS);
        if (lowEnd < lowStart) {
            return;
        }

        for (int y = lowStart; y <= lowEnd; y++) {
            pos.set(x, y, z);
            BlockState state = world.getBlockState(pos);
            if (state.isAir() || !shouldWaveShatterBlock(state)) {
                continue;
            }
            if (owner != null && !HammerProtection.canDamageBlock(world, pos, owner)) {
                continue;
            }
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        }
    }

    private boolean shouldWaveShatterBlock(BlockState state) {
        if (state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.FLOWERS) || state.isIn(BlockTags.SMALL_FLOWERS)) {
            return true;
        }
        if (state.isOf(Blocks.VINE)
                || state.isOf(Blocks.CAVE_VINES)
                || state.isOf(Blocks.CAVE_VINES_PLANT)
                || state.isOf(Blocks.WEEPING_VINES)
                || state.isOf(Blocks.WEEPING_VINES_PLANT)
                || state.isOf(Blocks.TWISTING_VINES)
                || state.isOf(Blocks.TWISTING_VINES_PLANT)) {
            return true;
        }
        if (state.isOf(Blocks.SHORT_GRASS) || state.isOf(Blocks.TALL_GRASS) || state.isOf(Blocks.FERN) || state.isOf(Blocks.LARGE_FERN)) {
            return true;
        }
        if (state.isIn(GLASS_BLOCKS) || state.isIn(GLASS_PANES) || state.isOf(Blocks.TINTED_GLASS)) {
            return true;
        }
        return false;
    }

    private void buildScorchedFloor(ServerWorld world) {
    }

    private void vaporizeWeakMobs(ServerWorld world) {
        Vec3d center = new Vec3d(getX(), getY(), getZ());
        float radius = PLAYER_EFFECT_RADIUS;
        Box box = new Box(center.x - radius, center.y - 24.0D, center.z - radius, center.x + radius, center.y + 64.0D, center.z + radius);

        List<Entity> entities = world.getOtherEntities(this, box, entity -> entity instanceof LivingEntity living && living.isAlive());
        for (Entity entity : entities) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (entity instanceof ServerPlayerEntity) {
                continue;
            }
            if (living.getMaxHealth() >= 100.0F) {
                continue;
            }

            world.spawnParticles(TintedParticleEffect.create(ParticleTypes.FLASH, 0xFFFFFFFF), living.getX(), living.getBodyY(0.5D), living.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
            world.spawnParticles(ParticleTypes.ASH, living.getX(), living.getBodyY(0.5D), living.getZ(), 24, 0.35D, 0.35D, 0.35D, 0.02D);
            world.spawnParticles(ParticleTypes.LARGE_SMOKE, living.getX(), living.getBodyY(0.5D), living.getZ(), 12, 0.30D, 0.30D, 0.30D, 0.02D);
            living.discard();
        }
    }

    @Nullable
    private ServerPlayerEntity getOwnerPlayer(ServerWorld world) {
        int ownerId = getOwnerId();
        if (ownerId < 0) {
            return null;
        }
        Entity entity = world.getEntityById(ownerId);
        return entity instanceof ServerPlayerEntity player ? player : null;
    }

    @Override
    public boolean canHit() {
        return false;
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        return false;
    }
}
