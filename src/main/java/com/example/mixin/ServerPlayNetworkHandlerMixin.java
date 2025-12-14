package com.example.mixin;

import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin {
    @ModifyConstant(method = "onPlayerMove", constant = @Constant(floatValue = 300.0F))
    private float modid$disableMovedTooQuicklyWhileGliding(float value) {
        return Float.MAX_VALUE;
    }

    @ModifyConstant(method = "onPlayerMove", constant = @Constant(floatValue = 100.0F))
    private float modid$disableMovedTooQuicklyWhileNotGliding(float value) {
        return Float.MAX_VALUE;
    }

    @ModifyConstant(method = "onPlayerMove", constant = @Constant(doubleValue = 0.0625D))
    private double modid$disableMovedWrongly(double value) {
        return Double.MAX_VALUE;
    }

    @ModifyConstant(method = "onVehicleMove", constant = @Constant(doubleValue = 100.0D))
    private double modid$disableVehicleMovedTooQuickly(double value) {
        return Double.MAX_VALUE;
    }

    @ModifyConstant(method = "onVehicleMove", constant = @Constant(doubleValue = 0.0625D))
    private double modid$disableVehicleMovedWrongly(double value) {
        return Double.MAX_VALUE;
    }
}

