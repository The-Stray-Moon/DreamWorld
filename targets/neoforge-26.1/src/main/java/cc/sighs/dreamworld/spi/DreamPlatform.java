package cc.sighs.dreamworld.spi;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The one place where the shared tree talks to its loader.
 *
 * <p>A target installs its implementation during mod construction, before any shared code runs.</p>
 */
public interface DreamPlatform {
    static DreamPlatform get() {
        DreamPlatform current = Holder.INSTANCE;
        if (current == null) {
            throw new IllegalStateException("DreamWorld platform is not installed yet");
        }
        return current;
    }

    static void install(DreamPlatform platform) {
        Holder.INSTANCE = platform;
    }

    String modId();

    DreamNetwork network();

    DreamConfig config();

    DreamPersistentData persistentData();

    /** Runs a task on the game thread of the current side. */
    void execute(Runnable task);

    /** {@return the namespaced id of a {@code dreamworld} resource} */
    Identifier id(String path);

    boolean hasEffect(LivingEntity entity, DreamHolder<MobEffect> effect);

    void addEffect(LivingEntity entity, DreamHolder<MobEffect> effect, int duration, int amplifier);

    /** {@return the registry id of an entity type} */
    Identifier entityTypeId(EntityType<?> type);

    /** {@return whether the item may be put into a grindstone slot} */
    boolean canGrindstone(ItemStack stack);

    /** {@return whether the player may throw without consuming the item} */
    boolean hasInfiniteMaterials(Player player);

    /** {@return the raw rain level of a level, bypassing a render-side weather override} */
    float rainLevel(Level level);

    /** {@return the raw thunder level of a level, bypassing a render-side weather override} */
    float thunderLevel(Level level);

    /** Re-evaluates which entities are tracked for a player after a dream change. */
    void refreshEntityTracking(ServerPlayer player);

    /** Hides the swirl particles of the dream walk effect on the client. */
    void hideEffectParticles();

    final class Holder {
        private static volatile DreamPlatform INSTANCE;

        private Holder() {
        }
    }
}
