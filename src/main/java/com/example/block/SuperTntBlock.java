package com.example.block;

import com.example.mixin.TntEntityAccessor;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.TntBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.block.WireOrientation;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.rule.GameRules;

public class SuperTntBlock extends TntBlock {
    private static final float EXPLOSION_POWER = 128.0F;

    public SuperTntBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        if (oldState.isOf(state.getBlock())) {
            return;
        }

        if (world.isReceivingRedstonePower(pos)) {
            if (primeSuperTnt(world, pos, null)) {
                world.removeBlock(pos, false);
            }
        }
    }

    @Override
    protected void neighborUpdate(
            BlockState state,
            World world,
            BlockPos pos,
            Block sourceBlock,
            WireOrientation wireOrientation,
            boolean notify
    ) {
        if (world.isReceivingRedstonePower(pos)) {
            if (primeSuperTnt(world, pos, null)) {
                world.removeBlock(pos, false);
            }
        }
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient() && !player.getAbilities().creativeMode && state.get(UNSTABLE)) {
            primeSuperTnt(world, pos, null);
        }

        return super.onBreak(world, pos, state, player);
    }

    @Override
    public void onDestroyedByExplosion(ServerWorld world, BlockPos pos, Explosion explosion) {
        if (!((Boolean) world.getGameRules().getValue(GameRules.TNT_EXPLODES)).booleanValue()) {
            return;
        }

        TntEntity tntEntity = new TntEntity(
                world,
                pos.getX() + 0.5D,
                pos.getY(),
                pos.getZ() + 0.5D,
                explosion.getCausingEntity()
        );
        ((TntEntityAccessor) tntEntity).modid$setExplosionPower(EXPLOSION_POWER);

        int fuse = tntEntity.getFuse();
        tntEntity.setFuse((short) (world.random.nextInt(fuse / 4) + fuse / 8));
        world.spawnEntity(tntEntity);
    }

    @Override
    protected ActionResult onUseWithItem(
            ItemStack stack,
            BlockState state,
            World world,
            BlockPos pos,
            PlayerEntity player,
            Hand hand,
            BlockHitResult hit
    ) {
        if (!stack.isOf(Items.FLINT_AND_STEEL) && !stack.isOf(Items.FIRE_CHARGE)) {
            return super.onUseWithItem(stack, state, world, pos, player, hand, hit);
        }

        if (primeSuperTnt(world, pos, player)) {
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), 11);

            Item usedItem = stack.getItem();
            if (stack.isOf(Items.FLINT_AND_STEEL)) {
                stack.damage(1, player, hand.getEquipmentSlot());
            } else {
                stack.decrementUnlessCreative(1, player);
            }

            player.incrementStat(Stats.USED.getOrCreateStat(usedItem));
        } else if (world instanceof ServerWorld serverWorld) {
            if (!((Boolean) serverWorld.getGameRules().getValue(GameRules.TNT_EXPLODES)).booleanValue()) {
                player.sendMessage(Text.translatable("block.minecraft.tnt.disabled"), true);
                return ActionResult.PASS;
            }
        }

        return ActionResult.SUCCESS;
    }

    @Override
    protected void onProjectileHit(World world, BlockState state, BlockHitResult hit, ProjectileEntity projectile) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }

        BlockPos pos = hit.getBlockPos();
        Entity owner = projectile.getOwner();
        if (!projectile.isOnFire() || !projectile.canModifyAt(serverWorld, pos)) {
            return;
        }

        LivingEntity livingOwner = owner instanceof LivingEntity livingEntity ? livingEntity : null;
        if (primeSuperTnt(world, pos, livingOwner)) {
            world.removeBlock(pos, false);
        }
    }

    private static boolean primeSuperTnt(World world, BlockPos pos, LivingEntity igniter) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return false;
        }

        if (!((Boolean) serverWorld.getGameRules().getValue(GameRules.TNT_EXPLODES)).booleanValue()) {
            return false;
        }

        TntEntity tntEntity = new TntEntity(
                world,
                pos.getX() + 0.5D,
                pos.getY(),
                pos.getZ() + 0.5D,
                igniter
        );
        ((TntEntityAccessor) tntEntity).modid$setExplosionPower(EXPLOSION_POWER);

        world.spawnEntity(tntEntity);
        world.playSound(
                null,
                tntEntity.getX(),
                tntEntity.getY(),
                tntEntity.getZ(),
                SoundEvents.ENTITY_TNT_PRIMED,
                SoundCategory.BLOCKS,
                1.0F,
                1.0F
        );
        world.emitGameEvent(igniter, GameEvent.PRIME_FUSE, pos);
        return true;
    }
}
