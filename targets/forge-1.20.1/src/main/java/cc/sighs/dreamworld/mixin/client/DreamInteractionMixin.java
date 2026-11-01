package cc.sighs.dreamworld.mixin.client;

import cc.sighs.dreamworld.dream.DreamWorldRules;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Refuses dream-side block and entity interaction at the client entry point, before any packet is
 * sent.
 *
 * <p>Cancelling {@code PlayerInteractEvent.RightClickBlock} is not enough. That event only decides
 * the return value of {@code performUseItemOn}, while the {@code ServerboundUseItemOnPacket} is
 * built inside the prediction lambda and sent unconditionally; {@code interact} and
 * {@code interactAt} send their {@code ServerboundInteractPacket} before anything else at all. The
 * server keeps no dream rule of its own - the layer is a client-side view - so a packet that leaves
 * the client is an interaction that happened. That is the "right click sometimes still works" leak,
 * and it is why these hooks sit in front of {@code MultiPlayerGameMode} instead of on the events.</p>
 *
 * <p>Only the block/entity interaction is refused, never the item itself. Returning {@code PASS} from
 * {@code useItemOn} skips the block handler and the packet while still letting
 * {@code Minecraft#startUseItem} fall through to {@code useItem}, so a dreamer can keep drinking
 * dream tea or swinging the wand at a block without interacting with that block.</p>
 *
 * <p>Attacks are handled the same way: {@code attack} sends its packet first, so a dreamer must not
 * reach it at all unless holding the dream alloy sword.</p>
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class DreamInteractionMixin {
    /**
     * Skips the block interaction (and its packet) but leaves the item path intact.
     *
     * <p>{@code PASS} rather than {@code FAIL}: a failure would make {@code startUseItem} abort
     * entirely, which also swallows the item's own use.</p>
     */
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void dreamworld$blockUseOnBlock(LocalPlayer player, InteractionHand hand,
            BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (DreamWorldRules.isDreaming(player)
                && !DreamWorldRules.mayInteractBlockAt(player, hit.getBlockPos(), hit.getDirection())) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    /**
     * Refuses a dream-side attack that does not come from the dream alloy sword.
     *
     * <p>Cancelling here means no {@code ServerboundInteractPacket} and no local swing, so a fist, a
     * pickaxe or the wand cannot hurt anything inside the dream.</p>
     */
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void dreamworld$blockAttack(Player player, Entity target, CallbackInfo ci) {
        if (!DreamWorldRules.mayAttack(player, target)) {
            ci.cancel();
        }
    }

    /** Blocks right-clicking an entity; the packet is otherwise the first thing this method does. */
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void dreamworld$blockEntityInteract(Player player, Entity target, InteractionHand hand,
            CallbackInfoReturnable<InteractionResult> cir) {
        if (DreamWorldRules.isDreaming(player)
                && !DreamWorldRules.mayInteractEntity(player, target)) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    /** Blocks the precise entity hit path, which is the one a normal crosshair click uses. */
    @Inject(method = "interactAt", at = @At("HEAD"), cancellable = true)
    private void dreamworld$blockEntityInteractAt(Player player, Entity target, EntityHitResult hit,
            InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (DreamWorldRules.isDreaming(player)
                && !DreamWorldRules.mayInteractEntity(player, target)) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }
}
