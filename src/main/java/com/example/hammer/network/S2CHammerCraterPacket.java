package com.example.hammer.network;

import com.example.ExampleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record S2CHammerCraterPacket(
        int strikeEntityId,
        BlockPos targetPos,
        long completionWorldTime
) implements CustomPayload {
    public static final Id<S2CHammerCraterPacket> ID = new CustomPayload.Id<>(Identifier.of(ExampleMod.MOD_ID, "hammer_crater_done"));

    public static final PacketCodec<RegistryByteBuf, S2CHammerCraterPacket> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT.cast(), S2CHammerCraterPacket::strikeEntityId,
            BlockPos.PACKET_CODEC.cast(), S2CHammerCraterPacket::targetPos,
            PacketCodecs.VAR_LONG.cast(), S2CHammerCraterPacket::completionWorldTime,
            S2CHammerCraterPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
