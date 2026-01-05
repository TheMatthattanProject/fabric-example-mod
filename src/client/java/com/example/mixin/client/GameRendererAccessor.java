package com.example.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Invoker("setPostProcessor")
    void modid$setPostProcessor(Identifier id);

    @Accessor("postProcessorEnabled")
    void modid$setPostProcessorEnabled(boolean enabled);

    @Accessor("postProcessorEnabled")
    boolean modid$isPostProcessorEnabled();
}

