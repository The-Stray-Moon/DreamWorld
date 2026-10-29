package cc.sighs.dreamworld.entity;

import cc.sighs.dreamworld.DWRegistry;
import cc.sighs.dreamworld.DreamWorldNeoForge121;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * Dream rabbit: the only creature found in the dream world. It cannot be attacked and produces
 * dream clay from time to time, the same way a chicken lays eggs.
 *
 * <p>It must only be created through its spawn egg or by the dream side code, there is no natural
 * spawn placement for it.</p>
 */
@EventBusSubscriber(modid = DreamWorldNeoForge121.MOD_ID)
public class DreamRabbitEntity extends Rabbit {
    /** Shortest delay between two dream clay drops. */
    public static final int CLAY_DELAY_MIN = 20 * 60 * 4;
    /** Longest delay between two dream clay drops. */
    public static final int CLAY_DELAY_MAX = 20 * 60 * 6;

    private int clayDelay = -1;

    public DreamRabbitEntity(EntityType<? extends DreamRabbitEntity> type, Level level) {
        super(type, level);
        // Dream rabbits are the only dream creatures, they must not despawn on their own.
        setPersistenceRequired();
    }

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(DWRegistry.DREAM_RABBIT.get(), Rabbit.createAttributes().build());
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (this.clayDelay < 0) {
            this.clayDelay = nextClayDelay();
        }
        if (--this.clayDelay <= 0) {
            if (level() instanceof ServerLevel server) {
                DreamClayEntity.spawnNear(server, position().add(0.0D, 0.4D, 0.0D));
            }
            this.clayDelay = nextClayDelay();
        }
    }

    private int nextClayDelay() {
        return CLAY_DELAY_MIN + random.nextInt(CLAY_DELAY_MAX - CLAY_DELAY_MIN + 1);
    }

    @Override
    public Rabbit getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return DWRegistry.DREAM_RABBIT.get().create(level);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }
}
