package com.example.hammer;

import com.example.hammer.network.S2CHammerCraterPacket;
import com.example.hammer.network.S2CHammerPacket;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class HammerNetworking {
    private static final double STAGE_SYNC_RADIUS = 2048.0D;

    private HammerNetworking() {
    }

    public static void initialize() {
        PayloadTypeRegistry.playS2C().register(S2CHammerPacket.ID, S2CHammerPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(S2CHammerCraterPacket.ID, S2CHammerCraterPacket.CODEC);
    }

    public static void sendStage(
            ServerWorld world,
            BlockPos targetPos,
            int strikeEntityId,
            int seed,
            HammerConfig.ClientFxPreset fxPreset,
            HammerStage stage,
            int strikeTick,
            long stageStartWorldTime
    ) {
        S2CHammerPacket payload = new S2CHammerPacket(strikeEntityId, targetPos, seed, fxPreset, stage, strikeTick, stageStartWorldTime);
        Vec3d center = new Vec3d(targetPos.getX() + 0.5D, targetPos.getY() + 0.5D, targetPos.getZ() + 0.5D);
        double radiusSq = STAGE_SYNC_RADIUS * STAGE_SYNC_RADIUS;

        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.squaredDistanceTo(center) > radiusSq) {
                continue;
            }
            if (!ServerPlayNetworking.canSend(player, S2CHammerPacket.ID)) {
                continue;
            }
            ServerPlayNetworking.send(player, payload);
        }
    }

    public static void sendCraterComplete(ServerWorld world, BlockPos targetPos, int strikeEntityId, long completionWorldTime) {
        S2CHammerCraterPacket payload = new S2CHammerCraterPacket(strikeEntityId, targetPos, completionWorldTime);
        Vec3d center = new Vec3d(targetPos.getX() + 0.5D, targetPos.getY() + 0.5D, targetPos.getZ() + 0.5D);
        double radiusSq = STAGE_SYNC_RADIUS * STAGE_SYNC_RADIUS;

        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.squaredDistanceTo(center) > radiusSq) {
                continue;
            }
            if (!ServerPlayNetworking.canSend(player, S2CHammerCraterPacket.ID)) {
                continue;
            }
            ServerPlayNetworking.send(player, payload);
        }
    }
}

