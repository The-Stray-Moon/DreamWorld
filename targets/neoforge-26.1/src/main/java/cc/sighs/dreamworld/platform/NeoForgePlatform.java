package cc.sighs.dreamworld.platform;

import cc.sighs.dreamworld.DWObjects;
import cc.sighs.dreamworld.config.DreamClientConfig;
import cc.sighs.dreamworld.config.DreamServerConfig;
import cc.sighs.dreamworld.effect.DreamWalkEffect;
import cc.sighs.dreamworld.item.NeoForgeDreamCrystalItem;
import cc.sighs.dreamworld.spi.DreamConfig;
import cc.sighs.dreamworld.spi.DreamColorMode;
import cc.sighs.dreamworld.spi.DreamHolder;
import cc.sighs.dreamworld.spi.DreamNetwork;
import cc.sighs.dreamworld.spi.DreamPersistentData;
import cc.sighs.dreamworld.spi.DreamPlatform;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.EffectParticleModificationEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** NeoForge implementation of the shared platform SPI. */
public final class NeoForgePlatform implements DreamPlatform {
    private final DreamNetwork network = new NeoForgeNetwork();
    private final DreamPersistentData persistentData = NeoForgePlatform::persistentTag;

    public NeoForgePlatform() {
        // The effect is intentionally silent; only the loader can remove it from the synced list.
        NeoForge.EVENT_BUS.addListener(EffectParticleModificationEvent.class, event -> {
            if (event.getEffect().getEffect().value() instanceof DreamWalkEffect) {
                event.setVisible(false);
            }
        });
    }

    private static CompoundTag persistentTag(Entity entity) {
        return entity.getPersistentData();
    }

    @Override
    public String modId() {
        return cc.sighs.dreamworld.DreamWorld.MOD_ID;
    }

    @Override
    public DreamNetwork network() {
        return network;
    }

    @Override
    public DreamConfig config() {
        return new DreamConfig() {
            @Override
            public DreamColorMode colorMode() {
                return DreamClientConfig.colorMode();
            }

            @Override
            public int ambientSkyLight() {
                return DreamClientConfig.ambientSkyLight();
            }

            @Override
            public int maxTunnelsPerGraffiti() {
                return DreamServerConfig.maxTunnelsPerGraffiti();
            }

            @Override
            public int dreamEntryDelayTicks() {
                return DreamServerConfig.dreamEntryDelayTicks();
            }

            @Override
            public double graffitiSleepRange() {
                return DreamServerConfig.graffitiSleepRange();
            }

            @Override
            public double maxGraffitiSleepSpeedup() {
                return DreamServerConfig.maxGraffitiSleepSpeedup();
            }
        };
    }

    @Override
    public DreamPersistentData persistentData() {
        return persistentData;
    }

    @Override
    public void execute(Runnable task) {
        if (FMLEnvironment.getDist().isClient()) {
            NeoForgeClientTasks.execute(task);
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            task.run();
        } else {
            server.execute(task);
        }
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
        return stack.getItem() instanceof NeoForgeDreamCrystalItem;
    }

    @Override
    public boolean hasInfiniteMaterials(Player player) {
        return player.hasInfiniteMaterials();
    }

    @Override
    public float rainLevel(net.minecraft.world.level.Level level) {
        return level.getRainLevel(1.0F);
    }

    @Override
    public float thunderLevel(net.minecraft.world.level.Level level) {
        return level.getThunderLevel(1.0F);
    }

    @Override
    public void refreshEntityTracking(ServerPlayer player) {
        var server = player.level().getServer();
        if (server == null || player.isChangingDimension()) {
            return;
        }
        // Deferred to the next tick: right after a teleport/dimension change the chunk map's
        // tracking sets are being rebuilt and ChunkMap#move would dereference a null set.
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
        // Registered in the constructor.
    }
}
