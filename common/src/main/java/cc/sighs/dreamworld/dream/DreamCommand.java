package cc.sighs.dreamworld.dream;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/**
 * Operator helper for the dream state: {@code /dreamworld dream} and {@code /dreamworld wake}.
 *
 * <p>The normal way in and out is sleeping, but server operators (and automated checks) need a
 * deterministic switch that does not depend on the current time of day.</p>
 */
public final class DreamCommand {
    private DreamCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("dreamworld")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("dream").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    DreamManager.enterDream(player);
                    return 1;
                }))
                .then(Commands.literal("wake").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    DreamManager.wakeUp(player);
                    return 1;
                })));
    }
}
