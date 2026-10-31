package cc.sighs.dreamworld.client;

import cc.sighs.dreamworld.DWRegistry;
import cc.sighs.dreamworld.DreamWorldNeoForge121;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Hotbar hints of the dream world.
 *
 * <p>Three hints share the strip above the hotbar: reversing a graffiti tunnel and jumping out of
 * it while boating over one, and the way to clear an anchor that blocks a second spear.</p>
 */
@EventBusSubscriber(modid = DreamWorldNeoForge121.MOD_ID, value = Dist.CLIENT)
public final class DreamHud {
    /** Distance in blocks at which a boat counts as sitting on a tunnel. */
    private static final double TUNNEL_HINT_RANGE = 3.0D;
    /** Height of the hotbar texture, from the bottom of the screen. */
    private static final int HOTBAR_HEIGHT = 22;
    /** Vertical step between two stacked hints. */
    private static final int LINE_HEIGHT = 11;
    private static final int HINT_COLOR = 0xFFB8EAFF;

    private DreamHud() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.options.hideGui) {
            return;
        }
        List<Component> hints = new ArrayList<>(3);
        if (isOnTunnel(player)) {
            hints.add(Component.translatable("hud.dreamworld.reverse_tunnel"));
            hints.add(Component.translatable("hud.dreamworld.leave_tunnel"));
        }
        if (holdsSpear(player) && DreamClientState.isAnchorBound()) {
            hints.add(Component.translatable("hud.dreamworld.destroy_anchor"));
        }
        if (hints.isEmpty()) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        Font font = minecraft.font;
        int x = graphics.guiWidth() / 2;
        for (int index = 0; index < hints.size(); index++) {
            Component hint = hints.get(index);
            int width = font.width(hint);
            int y = graphics.guiHeight() - HOTBAR_HEIGHT - 12 - (hints.size() - 1 - index) * LINE_HEIGHT;
            graphics.drawStringWithBackdrop(font, hint, x - width / 2, y, width, HINT_COLOR);
        }
    }

    /** {@return whether the player is boating over a graffiti tunnel} */
    private static boolean isOnTunnel(Player player) {
        if (!(player.getVehicle() instanceof Boat boat)) {
            return false;
        }
        if (DreamVisuals.tunnels().isEmpty()) {
            return false;
        }
        Vec3 position = boat.position();
        return DreamVisuals.isNearTunnel(position, TUNNEL_HINT_RANGE);
    }

    /** {@return whether a dream alloy spear is held in either hand} */
    private static boolean holdsSpear(Player player) {
        return isSpear(player.getMainHandItem()) || isSpear(player.getOffhandItem());
    }

    private static boolean isSpear(ItemStack stack) {
        return stack.is(DWRegistry.DREAM_ALLOY_SPEAR.get());
    }
}
