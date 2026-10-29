package cc.sighs.dreamworld.dream;

import cc.sighs.dreamworld.DreamWorldNeoForge121;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Operator helper for the dream state: {@code /dreamworld dream} and {@code /dreamworld wake}.
 *
 * <p>The normal way in and out is sleeping, but server operators (and automated checks) need a
 * deterministic switch that does not depend on the current time of day.</p>
 */
@EventBusSubscriber(modid = DreamWorldNeoForge121.MOD_ID)
public final class DreamCommand {
    private DreamCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dreamworld")
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
