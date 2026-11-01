package cc.sighs.dreamworld.platform;

import cc.sighs.dreamworld.DreamWorld;
import cc.sighs.dreamworld.config.FabricConfig;
import cc.sighs.dreamworld.spi.DreamConfig;
import cc.sighs.dreamworld.spi.DreamHolder;
import cc.sighs.dreamworld.spi.DreamNetwork;
import cc.sighs.dreamworld.spi.DreamPersistentData;
import cc.sighs.dreamworld.spi.DreamPlatform;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Fabric implementation of the shared platform SPI. */
public final class FabricPlatform implements DreamPlatform {
    private final FabricConfig config = new FabricConfig();
    private final DreamNetwork network = new FabricNetwork();
    private final DreamPersistentData persistentData = new FabricPersistentData();

    public void init() {
        config.load();
    }

    @Override
    public String modId() {
        return DreamWorld.MOD_ID;
    }

    @Override
    public DreamNetwork network() {
        return network;
    }

    @Override
    public DreamConfig config() {
        return config;
    }

    @Override
    public DreamPersistentData persistentData() {
        return persistentData;
    }

    @Override
    public void execute(Runnable task) {
        // Shared code only schedules from places that already run on the game thread.
        task.run();
    }

    @Override
    public Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(modId(), path);
    }

    @Override
    public boolean hasEffect(LivingEntity entity, DreamHolder<MobEffect> effect) {
        MobEffect wanted = effect.get();
        return entity.getActiveEffects().stream()
                .anyMatch(instance -> instance.getEffect().value() == wanted);
    }

    @Override
    public void addEffect(LivingEntity entity, DreamHolder<MobEffect> effect, int duration,
            int amplifier) {
        entity.addEffect(new MobEffectInstance(
                BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect.get()), duration, amplifier));
    }

    @Override
    public Identifier entityTypeId(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(type);
    }

    @Override
    public boolean canGrindstone(ItemStack stack) {
        return stack.getItem() instanceof cc.sighs.dreamworld.item.DreamCrystalItem;
    }

    @Override
    public boolean hasInfiniteMaterials(Player player) {
        return player.hasInfiniteMaterials();
    }

    @Override
    public float rainLevel(net.minecraft.world.level.Level level) {
        return ((cc.sighs.dreamworld.mixin.LevelAccessor) level).dreamworld$rainLevel();
    }

    @Override
    public float thunderLevel(net.minecraft.world.level.Level level) {
        return ((cc.sighs.dreamworld.mixin.LevelAccessor) level).dreamworld$thunderLevel();
    }

    @Override
    public void refreshEntityTracking(ServerPlayer player) {
        var server = player.level().getServer();
        if (server == null || player.isChangingDimension()) {
            return;
        }
        server.execute(() -> {
            if (player.isRemoved() || player.hasDisconnected()) {
                return;
            }
            if (player.level() instanceof ServerLevel level) {
                try {
                    level.getChunkSource().chunkMap.move(player);
                } catch (RuntimeException ignored) {
                    // Tracking is also refreshed by the next natural move; never fail the dream entry.
                }
            }
        });
    }

    @Override
    public void hideEffectParticles() {
        // Fabric has no particle-modification event; the effect keeps vanilla swirls on this loader.
    }
}
