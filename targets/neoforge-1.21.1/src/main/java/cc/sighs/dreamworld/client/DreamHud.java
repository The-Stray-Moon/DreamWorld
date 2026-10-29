package cc.sighs.dreamworld.client;

import cc.sighs.dreamworld.DreamWorldNeoForge121;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * HUD hints of the dream world. Currently only the tunnel hint shown while boating on a tunnel.
 */
@EventBusSubscriber(modid = DreamWorldNeoForge121.MOD_ID, value = Dist.CLIENT)
public final class DreamHud {
    /** Distance in blocks at which a boat counts as sitting on a tunnel. */
    private static final double TUNNEL_HINT_RANGE = 3.0D;
    /** Height of the hotbar texture, from the bottom of the screen. */
    private static final int HOTBAR_HEIGHT = 22;
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
        if (!(player.getVehicle() instanceof Boat boat)) {
            return;
        }
        if (DreamVisuals.tunnels().isEmpty()) {
            return;
        }
        Vec3 position = boat.position();
        if (!DreamVisuals.isNearTunnel(position, TUNNEL_HINT_RANGE)) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        Font font = minecraft.font;
        Component hint = Component.translatable("hud.dreamworld.leave_tunnel");
        int width = font.width(hint);
        int x = (graphics.guiWidth() - width) / 2;
        int y = graphics.guiHeight() - HOTBAR_HEIGHT - 12;
        graphics.drawStringWithBackdrop(font, hint, x, y, width, HINT_COLOR);
    }
}
