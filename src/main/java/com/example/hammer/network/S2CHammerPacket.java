package com.example.hammer.network;

import com.example.ExampleMod;
import com.example.hammer.HammerConfig;
import com.example.hammer.HammerStage;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record S2CHammerPacket(
        int strikeEntityId,
        BlockPos targetPos,
        int seed,
        HammerConfig.ClientFxPreset fxPreset,
        HammerStage stage,
        int stageStrikeTick,
        long stageStartWorldTime
) implements CustomPayload {
    public static final Id<S2CHammerPacket> ID = new CustomPayload.Id<>(Identifier.of(ExampleMod.MOD_ID, "hammer_stage"));

    private static final PacketCodec<RegistryByteBuf, HammerStage> STAGE_CODEC = PacketCodecs.VAR_INT
            .xmap(HammerStage::fromNetworkId, HammerStage::networkId)
            .cast();
    private static final PacketCodec<RegistryByteBuf, HammerConfig.ClientFxPreset> FX_PRESET_CODEC = PacketCodecs.VAR_INT
            .xmap(HammerConfig.ClientFxPreset::fromNetworkId, HammerConfig.ClientFxPreset::networkId)
            .cast();

    public static final PacketCodec<RegistryByteBuf, S2CHammerPacket> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT.cast(), S2CHammerPacket::strikeEntityId,
            BlockPos.PACKET_CODEC.cast(), S2CHammerPacket::targetPos,
            PacketCodecs.VAR_INT.cast(), S2CHammerPacket::seed,
            FX_PRESET_CODEC, S2CHammerPacket::fxPreset,
            STAGE_CODEC, S2CHammerPacket::stage,
            PacketCodecs.VAR_INT.cast(), S2CHammerPacket::stageStrikeTick,
            PacketCodecs.VAR_LONG.cast(), S2CHammerPacket::stageStartWorldTime,
            S2CHammerPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
