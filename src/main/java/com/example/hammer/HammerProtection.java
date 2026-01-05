package com.example.hammer;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Best-effort protection hooks for grief control.
 *
 * <p>We intentionally consult Fabric's block-break event so claim/protection mods that hook into
 * {@link PlayerBlockBreakEvents#BEFORE} can veto block damage without a hard dependency.
 */
public final class HammerProtection {
    private HammerProtection() {
    }

    public static boolean canDamageBlock(ServerWorld world, BlockPos pos, ServerPlayerEntity source) {
        if (source == null) {
            return false;
        }
        if (!world.getWorldBorder().contains(pos)) {
            return false;
        }
        MinecraftServer server = world.getServer();
        if (server != null && server.isSpawnProtected(world, pos, source)) {
            return false;
        }
        if (!world.canEntityModifyAt(source, pos)) {
            return false;
        }

        BlockState state = world.getBlockState(pos);
        BlockEntity blockEntity = world.getBlockEntity(pos);
        return PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(world, source, pos, state, blockEntity);
    }
}

