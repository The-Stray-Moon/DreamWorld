package cc.sighs.dreamworld.platform;

import cc.sighs.dreamworld.DWObjects;
import cc.sighs.dreamworld.config.ForgeClientConfig;
import cc.sighs.dreamworld.config.ForgeServerConfig;
import cc.sighs.dreamworld.spi.DreamConfig;
import cc.sighs.dreamworld.spi.DreamHolder;
import cc.sighs.dreamworld.spi.DreamNetwork;
import cc.sighs.dreamworld.spi.DreamPersistentData;
import cc.sighs.dreamworld.spi.DreamPlatform;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Forge 1.19.2 implementation of the shared platform SPI. */
public final class ForgePlatform implements DreamPlatform {
    private final DreamNetwork network = new ForgeNetwork();
    private final DreamPersistentData persistentData = ForgePlatform::persistentTag;

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
            public boolean swapPalettes() {
                return ForgeClientConfig.swapPalettes();
            }

            @Override
            public int ambientSkyLight() {
                return ForgeClientConfig.ambientSkyLight();
            }

            @Override
            public int maxTunnelsPerGraffiti() {
                return ForgeServerConfig.maxTunnelsPerGraffiti();
            }

            @Override
            public int dreamEntryDelayTicks() {
                return ForgeServerConfig.dreamEntryDelayTicks();
            }

            @Override
            public double graffitiSleepRange() {
                return ForgeServerConfig.graffitiSleepRange();
            }

            @Override
            public double maxGraffitiSleepSpeedup() {
                return ForgeServerConfig.maxGraffitiSleepSpeedup();
            }
        };
    }

    @Override
    public DreamPersistentData persistentData() {
        return persistentData;
    }

    @Override
    public void execute(Runnable task) {
        if (FMLEnvironment.dist.isClient()) {
            ForgeClientTasks.execute(task);
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
    public ResourceLocation id(String path) {
        return new ResourceLocation(modId(), path);
    }

    @Override
    public boolean hasEffect(LivingEntity entity, DreamHolder<MobEffect> effect) {
        MobEffect wanted = effect.get();
        return entity.getActiveEffects().stream()
                .anyMatch(instance -> instance.getEffect() == wanted);
    }

    @Override
    public void addEffect(LivingEntity entity, DreamHolder<MobEffect> effect, int duration,
            int amplifier) {
        // The effect is deliberately silent: 1.19.2 decides the swirl particles from the
        // instance's visible flag, so creating it with visible=false is the whole hook.
        entity.addEffect(new MobEffectInstance(effect.get(), duration, amplifier, false, false,
                true));
    }

    @Override
    public ResourceLocation entityTypeId(EntityType<?> type) {
        return net.minecraft.core.Registry.ENTITY_TYPE.getKey(type);
    }

    @Override
    public boolean canGrindstone(ItemStack stack) {
        return stack.getItem() == DWObjects.DREAM_CRYSTAL.get();
    }

    @Override
    public boolean hasInfiniteMaterials(Player player) {
        return player.getAbilities().instabuild;
    }

    @Override
    public float rainLevel(Level level) {
        return level.rainLevel;
    }

    @Override
    public float thunderLevel(Level level) {
        return level.thunderLevel;
    }

    @Override
    public void refreshEntityTracking(ServerPlayer player) {
        var server = player.getServer();
        if (server == null || player.isChangingDimension()) {
            return;
        }
        // Deferred to the next tick: right after a teleport/dimension change the chunk map's
        // tracking sets are being rebuilt and ChunkMap#move would dereference a null set.
        server.execute(() -> {
            if (player.isRemoved() || player.hasDisconnected()) {
                return;
            }
            if (player.level instanceof ServerLevel level) {
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
        // Handled by the invisible MobEffectInstance in addEffect.
    }
}
