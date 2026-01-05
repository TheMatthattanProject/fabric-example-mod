package com.example.hammer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.DefaultPermissions;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public final class HammerCommands {
    private HammerCommands() {
    }

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("hammer")
                    .requires(source -> source.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS))
                    .then(CommandManager.literal("fire")
                            .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                    .executes(context -> {
                                        ServerWorld world = context.getSource().getWorld();
                                        BlockPos pos = BlockPosArgumentType.getLoadedBlockPos(context, "pos");
                                        HammerStrikeEntity.spawn(world, pos, context.getSource().getPlayer());
                                        return 1;
                                    })))
            );

            dispatcher.register(CommandManager.literal("hammer_preview")
                    .requires(source -> source.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS))
                    .executes(context -> {
                        if (context.getSource().getEntity() == null) {
                            return 0;
                        }
                        ServerWorld world = context.getSource().getWorld();
                        BlockPos pos = BlockPos.ofFloored(context.getSource().getPosition());
                        HammerStrikeEntity.spawn(world, pos, context.getSource().getPlayer(), true);
                        return 1;
                    })
                    .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                            .executes(context -> {
                                ServerWorld world = context.getSource().getWorld();
                                BlockPos pos = BlockPosArgumentType.getLoadedBlockPos(context, "pos");
                                HammerStrikeEntity.spawn(world, pos, context.getSource().getPlayer(), true);
                                return 1;
                            }))
            );
        });
    }
}
