package com.example.hammer;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Server-authoritative tick-based state machine for THE HAMMER.
 *
 * <p>All stage transitions and timeline control flow are driven by {@code switch (strikeTicks)}.
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

    private static final int STAGE_1_TARGETING_START = 0;
    private static final int STAGE_1_TARGETING_END = 30;
    private static final int STAGE_2_BREACH_START = 31;
    private static final int STAGE_2_BREACH_END = 45;
    private static final int STAGE_3_STROKE_START = 46;
    private static final int STAGE_3_STROKE_END = 80;
    private static final int STAGE_4_ERUPTION_START = 81;
    private static final int STAGE_4_ERUPTION_END = 100;
    private static final int STAGE_5_WAVE_START = 85;
    private static final int STAGE_5_WAVE_END = 130;
    private static final int STAGE_6_AFTERMATH_START = 131;
    private static final int STAGE_6_AFTERMATH_END = 220;

    private static final int ERUPTION_RADIUS = 15;
    private static final int ERUPTION_LAYERS = 6;
    private static final int ERUPTION_BLOCKS_PER_TICK = 240;

    private static final int WAVE_RADIUS = 80;
    private static final int PLAYER_EFFECT_RADIUS = 100;

    private final Deque<BlockPos> eruptionQueue = new ArrayDeque<>();
    private final ObjectArrayList<BlockPos> craterColumns = new ObjectArrayList<>();
    private float lastWaveRadius;

    private int strikeTicks;

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
        world.spawnEntity(strike);
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
    }

    @Override
    protected void writeCustomData(WriteView view) {
        view.putLong("Target", getTargetPos().asLong());
        view.putInt("Seed", getSeed());
        view.putInt("OwnerId", getOwnerId());
        view.putBoolean("Preview", isPreview());

        view.putInt("StrikeTicks", strikeTicks);
        view.putFloat("LastWaveRadius", lastWaveRadius);
    }

    @Override
    public void tick() {
        super.tick();

        if (getEntityWorld().isClient()) {
            return;
        }

        ServerWorld world = (ServerWorld) getEntityWorld();

        switch (strikeTicks) {
            case STAGE_1_TARGETING_START -> {
                enterStage(world, HammerStage.TARGETING);
                if (!isPreview()) {
                    if (strikeTicks >= STAGE_4_ERUPTION_START && strikeTicks <= STAGE_4_ERUPTION_END) {
                        tickEruption(world);
                    }
                    if (strikeTicks >= STAGE_5_WAVE_START && strikeTicks <= STAGE_5_WAVE_END) {
                        tickPressureWave(world);
                    }
                }

                if (strikeTicks >= (STAGE_6_AFTERMATH_END + 1)) {
                    discard();
                    return;
                }

                strikeTicks++;
            }
            case STAGE_2_BREACH_START -> {
                enterStage(world, HammerStage.ATMOSPHERIC_BREACH);
                if (!isPreview()) {
                    if (strikeTicks >= STAGE_4_ERUPTION_START && strikeTicks <= STAGE_4_ERUPTION_END) {
                        tickEruption(world);
                    }
                    if (strikeTicks >= STAGE_5_WAVE_START && strikeTicks <= STAGE_5_WAVE_END) {
                        tickPressureWave(world);
                    }
                }

                if (strikeTicks >= (STAGE_6_AFTERMATH_END + 1)) {
                    discard();
                    return;
                }

                strikeTicks++;
            }
            case STAGE_3_STROKE_START -> {
                enterStage(world, HammerStage.HAMMER_STROKE);
                if (!isPreview()) {
                    if (strikeTicks >= STAGE_4_ERUPTION_START && strikeTicks <= STAGE_4_ERUPTION_END) {
                        tickEruption(world);
                    }
                    if (strikeTicks >= STAGE_5_WAVE_START && strikeTicks <= STAGE_5_WAVE_END) {
                        tickPressureWave(world);
                    }
                }

                if (strikeTicks >= (STAGE_6_AFTERMATH_END + 1)) {
                    discard();
                    return;
                }

                strikeTicks++;
            }
            case STAGE_4_ERUPTION_START -> {
                enterStage(world, HammerStage.KINETIC_ERUPTION);
                beginEruption(world);
                if (!isPreview()) {
                    if (strikeTicks >= STAGE_4_ERUPTION_START && strikeTicks <= STAGE_4_ERUPTION_END) {
                        tickEruption(world);
                    }
                    if (strikeTicks >= STAGE_5_WAVE_START && strikeTicks <= STAGE_5_WAVE_END) {
                        tickPressureWave(world);
                    }
                }

                if (strikeTicks >= (STAGE_6_AFTERMATH_END + 1)) {
                    discard();
                    return;
                }

                strikeTicks++;
            }
            case STAGE_5_WAVE_START -> {
                enterStage(world, HammerStage.PRESSURE_WAVE);
                if (!isPreview()) {
                    if (strikeTicks >= STAGE_4_ERUPTION_START && strikeTicks <= STAGE_4_ERUPTION_END) {
                        tickEruption(world);
                    }
                    if (strikeTicks >= STAGE_5_WAVE_START && strikeTicks <= STAGE_5_WAVE_END) {
                        tickPressureWave(world);
                    }
                }

                if (strikeTicks >= (STAGE_6_AFTERMATH_END + 1)) {
                    discard();
                    return;
                }

                strikeTicks++;
            }
            case STAGE_6_AFTERMATH_START -> {
                enterStage(world, HammerStage.AFTERMATH_SIGNAL_LOSS);
                buildScorchedFloor(world);
                if (!isPreview()) {
                    if (strikeTicks >= STAGE_4_ERUPTION_START && strikeTicks <= STAGE_4_ERUPTION_END) {
                        tickEruption(world);
                    }
                    if (strikeTicks >= STAGE_5_WAVE_START && strikeTicks <= STAGE_5_WAVE_END) {
                        tickPressureWave(world);
                    }
                }

                if (strikeTicks >= (STAGE_6_AFTERMATH_END + 1)) {
                    discard();
                    return;
                }

                strikeTicks++;
            }
            default -> {
                if (!isPreview()) {
                    if (strikeTicks >= STAGE_4_ERUPTION_START && strikeTicks <= STAGE_4_ERUPTION_END) {
                        tickEruption(world);
                    }
                    if (strikeTicks >= STAGE_5_WAVE_START && strikeTicks <= STAGE_5_WAVE_END) {
                        tickPressureWave(world);
                    }
                }

                if (strikeTicks >= (STAGE_6_AFTERMATH_END + 1)) {
                    discard();
                    return;
                }

                strikeTicks++;
            }
        }
    }

    private void enterStage(ServerWorld world, HammerStage stage) {
        HammerNetworking.sendStage(world, getTargetPos(), getId(), getSeed(), stage, strikeTicks, world.getTime());
    }

    private void beginEruption(ServerWorld world) {
        eruptionQueue.clear();
        craterColumns.clear();
        lastWaveRadius = 0.0F;

        BlockPos center = getTargetPos();
        for (int dx = -ERUPTION_RADIUS; dx <= ERUPTION_RADIUS; dx++) {
            for (int dz = -ERUPTION_RADIUS; dz <= ERUPTION_RADIUS; dz++) {
                if ((dx * dx + dz * dz) > (ERUPTION_RADIUS * ERUPTION_RADIUS)) {
                    continue;
                }

                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                craterColumns.add(new BlockPos(x, center.getY(), z));

                int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
                int surfaceY = Math.max(world.getBottomY(), topY - 1);
                for (int dy = 0; dy < ERUPTION_LAYERS; dy++) {
                    eruptionQueue.add(new BlockPos(x, surfaceY - dy, z));
                }
            }
        }

        vaporizeWeakMobs(world);
    }

    private void tickEruption(ServerWorld world) {
        int remainingTicks = Math.max(1, (STAGE_4_ERUPTION_END + 1) - strikeTicks);
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
        float progress = (strikeTicks - STAGE_5_WAVE_START) / (float) Math.max(1, (STAGE_5_WAVE_END - STAGE_5_WAVE_START));
        float radius = MathHelper.clamp(progress, 0.0F, 1.0F) * WAVE_RADIUS;

        breakWaveBlocks(world, lastWaveRadius, radius);
        lastWaveRadius = radius;
    }

    private void breakWaveBlocks(ServerWorld world, float previousRadius, float currentRadius) {
        if (currentRadius <= previousRadius + 0.001F) {
            return;
        }

        ServerPlayerEntity owner = getOwnerPlayer(world);
        BlockPos center = getTargetPos();
        int minY = Math.max(world.getBottomY(), center.getY() - 2);
        int maxY = Math.min(world.getTopYInclusive(), center.getY() + 32);

        float thickness = 2.0F;
        float start = Math.max(previousRadius, Math.max(0.0F, currentRadius - thickness));

        int samples = MathHelper.clamp((int) Math.ceil(MathHelper.TAU * currentRadius / 0.65F), 24, 1024);
        for (int i = 0; i < samples; i++) {
            double theta = (Math.PI * 2.0D) * (i / (double) samples);
            double cos = Math.cos(theta);
            double sin = Math.sin(theta);

            for (float r = start; r <= currentRadius; r += 0.85F) {
                int x = MathHelper.floor(center.getX() + 0.5D + cos * r);
                int z = MathHelper.floor(center.getZ() + 0.5D + sin * r);

                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (owner != null && !HammerProtection.canDamageBlock(world, pos, owner)) {
                        continue;
                    }
                    if (world.getBlockEntity(pos) != null) {
                        continue;
                    }

                    BlockState state = world.getBlockState(pos);
                    if (state.isAir() || !shouldWaveShatterBlock(state)) {
                        continue;
                    }

                    world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                    world.syncWorldEvent(WorldEvents.BLOCK_BROKEN, pos, Block.getRawIdFromState(state));
                }
            }
        }
    }

    private boolean shouldWaveShatterBlock(BlockState state) {
        if (state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.FLOWERS) || state.isIn(BlockTags.SMALL_FLOWERS)) {
            return true;
        }
        if (state.isIn(GLASS_BLOCKS) || state.isIn(GLASS_PANES) || state.isOf(Blocks.TINTED_GLASS)) {
            return true;
        }
        return false;
    }

    private void buildScorchedFloor(ServerWorld world) {
        if (craterColumns.isEmpty()) {
            BlockPos center = getTargetPos();
            for (int dx = -ERUPTION_RADIUS; dx <= ERUPTION_RADIUS; dx++) {
                for (int dz = -ERUPTION_RADIUS; dz <= ERUPTION_RADIUS; dz++) {
                    if ((dx * dx + dz * dz) > (ERUPTION_RADIUS * ERUPTION_RADIUS)) {
                        continue;
                    }
                    craterColumns.add(new BlockPos(center.getX() + dx, center.getY(), center.getZ() + dz));
                }
            }
        }

        ServerPlayerEntity owner = getOwnerPlayer(world);
        for (int i = 0; i < craterColumns.size(); i++) {
            BlockPos column = craterColumns.get(i);
            int x = column.getX();
            int z = column.getZ();

            int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            int surfaceY = Math.max(world.getBottomY(), topY - 1);
            BlockPos pos = new BlockPos(x, surfaceY, z);

            if (owner != null && !HammerProtection.canDamageBlock(world, pos, owner)) {
                continue;
            }
            if (world.getBlockEntity(pos) != null) {
                continue;
            }

            BlockState current = world.getBlockState(pos);
            if (current.isAir() || current.getHardness(world, pos) < 0.0F) {
                continue;
            }

            float roll = world.getRandom().nextFloat();
            BlockState replacement = roll < 0.40F
                    ? Blocks.BLACKSTONE.getDefaultState()
                    : roll < 0.70F
                    ? Blocks.MAGMA_BLOCK.getDefaultState()
                    : Blocks.CRYING_OBSIDIAN.getDefaultState();
            world.setBlockState(pos, replacement, Block.NOTIFY_ALL);
        }
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
