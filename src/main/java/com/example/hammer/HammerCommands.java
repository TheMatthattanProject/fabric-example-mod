package com.example.hammer;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.DefaultPermissions;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public final class HammerCommands {
    private static final SuggestionProvider<ServerCommandSource> MODE_SUGGESTIONS = (context, builder) ->
            CommandSource.suggestMatching(new String[]{"subtle", "cinematic", "apocalyptic"}, builder);
    private static final DynamicCommandExceptionType INVALID_MODE = new DynamicCommandExceptionType(mode ->
            Text.literal("Unknown HAMMER mode '" + mode + "'. Use subtle, cinematic, or apocalyptic."));

    private HammerCommands() {
    }

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("hammer")
                    .requires(source -> source.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS))
                    .then(CommandManager.literal("fire")
                            .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                    .executes(context -> executeStrike(context, false, true, false))
                                    .then(modeArgument()
                                            .executes(context -> executeStrike(context, false, true, true)))))
            );

            dispatcher.register(CommandManager.literal("hammer_preview")
                    .requires(source -> source.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS))
                    .executes(context -> executeStrike(context, true, false, false))
                    .then(modeArgument()
                            .executes(context -> executeStrike(context, true, false, true)))
                    .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                            .executes(context -> executeStrike(context, true, true, false))
                            .then(modeArgument()
                                    .executes(context -> executeStrike(context, true, true, true))))
            );
        });
    }

    private static RequiredArgumentBuilder<ServerCommandSource, String> modeArgument() {
        return CommandManager.argument("mode", StringArgumentType.word()).suggests(MODE_SUGGESTIONS);
    }

    private static int executeStrike(
            CommandContext<ServerCommandSource> context,
            boolean preview,
            boolean hasPosArgument,
            boolean hasModeArgument
    ) throws CommandSyntaxException {
        if (!hasPosArgument && context.getSource().getEntity() == null) {
            return 0;
        }

        ServerWorld world = context.getSource().getWorld();
        BlockPos pos = hasPosArgument
                ? BlockPosArgumentType.getLoadedBlockPos(context, "pos")
                : BlockPos.ofFloored(context.getSource().getPosition());

        HammerConfig.ClientFxPreset preset = hasModeArgument
                ? readMode(context, "mode")
                : HammerConfig.clientFxPreset();

        HammerStrikeEntity.spawn(world, pos, context.getSource().getPlayer(), preview, preset);
        return 1;
    }

    private static HammerConfig.ClientFxPreset readMode(CommandContext<ServerCommandSource> context, String argumentName) throws CommandSyntaxException {
        String raw = StringArgumentType.getString(context, argumentName);
        String normalized = raw.trim().toLowerCase();
        return switch (normalized) {
            case "subtle", "low", "lite" -> HammerConfig.ClientFxPreset.SUBTLE;
            case "cinematic", "default", "normal" -> HammerConfig.ClientFxPreset.CINEMATIC;
            case "apocalyptic", "extreme", "high", "max" -> HammerConfig.ClientFxPreset.APOCALYPTIC;
            default -> throw INVALID_MODE.create(raw);
        };
    }
}
